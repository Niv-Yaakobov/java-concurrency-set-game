package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public List<Player> waitToBeChecked;

    public int[] winningCards;
    private boolean needToDelete;
    public Object dealerLock;
    public long dealerSleepMillis;
    public Object listLock;
    boolean alreadyRunned;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        waitToBeChecked = new LinkedList<>();
        winningCards = new int[3];
        needToDelete = false;
        dealerLock = new Object();
        this.dealerSleepMillis = calculateDealerSleep(100);
        listLock = new Object();
        this.alreadyRunned = false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

        while (!shouldFinish()) {
            Collections.shuffle(deck);
            placeCardsOnTable();
            startPlayers();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        for(int i = 0; i < players.length; i++){
            players[i].terminate();
            try { players[i].getPlayerThread().join(); } catch (InterruptedException ignore) {}

        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    private void startPlayers() {
        if (!alreadyRunned) {
            for (int i = 0; i < players.length; i++) {
                new Thread(players[i]).start();
            }
            alreadyRunned = true;
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        if (needToDelete) {
            table.removeCardsFromTable(winningCards);
            removeWinningSlotFromActions();
            needToDelete = false;
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        boolean isPlaced = false;
        for (int i = 0; i < env.config.rows * env.config.columns; i++) {
            if (table.slotToCard[i] == null && !deck.isEmpty()) {
                Integer card = deck.remove(0);
                table.placeCard(card, i);
                isPlaced = true;
            }
        }
        if(env.config.hints && isPlaced)
            table.hints();
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized (dealerLock) {
            try {
                dealerLock.wait(dealerSleepMillis);
            } catch (InterruptedException ignored) {
            }
        }
        if (!waitToBeChecked.isEmpty()) {
            checkPlayer();
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset)
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        else {
            long remainingTime = reshuffleTime - System.currentTimeMillis();
            if (remainingTime < 0) {
                env.ui.setCountdown(0, true);
            }
            env.ui.setCountdown(remainingTime, remainingTime <= env.config.turnTimeoutWarningMillis);
            if (remainingTime <= env.config.turnTimeoutWarningMillis) {
                dealerSleepMillis = calculateDealerSleep(10);
            } else {
                dealerSleepMillis = calculateDealerSleep(100);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        removeAllActions();
        for (int i = 0; i < env.config.rows * env.config.columns; i++) {
            if (table.slotToCard[i] != null) {
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
        }
        clearWaitToBeChecked();
    }

    private void clearWaitToBeChecked() {
        while (!waitToBeChecked.isEmpty()) {
            Player p = waitToBeChecked.remove(0);
            p.checked = true;
            synchronized (p.playerThreadLock) {
                p.playerThreadLock.notifyAll();
            }

        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int size = 0;
        int maxScore = 0;
        for (Player p : players) {
            if (maxScore < p.score()) {
                maxScore = p.score();
                size = 1;
            } else if (maxScore == p.score())
                size++;
        }
        int[] winners = new int[size];
        int i = 0;
        for (Player p : players) {
            if (p.score() == maxScore) {
                winners[i] = p.id;
                i++;
            }
        }
        env.ui.announceWinner(winners);
    }

    ///////////////////////////////////////

    private void checkPlayer() {
        synchronized (listLock) {
            if (waitToBeChecked.isEmpty())
                return;
            Player p = waitToBeChecked.remove(0);
            if (!table.playersSlots[p.id].isFull()) {
                p.checked = true;
                synchronized (p.playerThreadLock) {
                    p.playerThreadLock.notifyAll();
                }
                return;
            }
            mySlots stam = table.playersSlots[p.id];
            int[] test = { stam.first, stam.second, stam.third };
            if (table.slotToCard[test[0]] == null || table.slotToCard[test[1]] == null ||
                    table.slotToCard[test[2]] == null) {
                p.checked = true;
                synchronized (p.playerThreadLock) {
                    p.playerThreadLock.notifyAll();
                }
                return;
            }
            int[] cards = { table.slotToCard[test[0]], table.slotToCard[test[1]],
                    table.slotToCard[test[2]] };
            if (env.util.testSet(cards)) {
                p.penaltyOrPoint = 1;
                winningCards = test;
                needToDelete = true;
            } else {
                p.penaltyOrPoint = -1;
            }
            p.checked = true;
            synchronized (p.playerThreadLock) {
                p.playerThreadLock.notifyAll();
            }
        }
    }

    private void removeAllActions() {
        for (Player p : players) {
            p.actions.clear();
            synchronized (p.aiLock) {
                p.aiLock.notifyAll();
            }
        }
    }

    private void removeWinningSlotFromActions() {
        for (Player p : players) {
            if (p.actions.contains(winningCards[0]))
                p.actions.remove(winningCards[0]);
            if (p.actions.contains(winningCards[1]))
                p.actions.remove(winningCards[1]);
            if (p.actions.contains(winningCards[2]))
                p.actions.remove(winningCards[2]);
            synchronized (p.aiLock) {
                p.aiLock.notifyAll();
            }
        }
    }

    private long calculateDealerSleep(long min) {
        if (env.config.tableDelayMillis < min)
            min = env.config.tableDelayMillis;
        if (env.config.pointFreezeMillis < min)
            min = env.config.pointFreezeMillis;
        if (env.config.penaltyFreezeMillis < min)
            min = env.config.penaltyFreezeMillis;
        if (env.config.turnTimeoutMillis > 0 && env.config.turnTimeoutMillis < min)
            min = env.config.turnTimeoutMillis;
        if (min == 0)
            min = (long) 1; // wait(0) is like regular wait()...
        return min;
    }
}
