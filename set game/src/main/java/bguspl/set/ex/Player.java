package bguspl.set.ex;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    private final Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    public volatile ConcurrentLinkedQueue<Integer> actions;

    public int penaltyOrPoint; //0 = nothing, 1 = point, -1 = penalty.

    public boolean checked;
    public boolean ignoreInput;
    public Object inputLock;
    public Object aiLock;
    public Object playerThreadLock;
    private Random rnd;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.score = 0;
        this.actions = new ConcurrentLinkedQueue<Integer>();
        this.checked = false;
        this.ignoreInput = false;
        this.inputLock = new Object();
        this.aiLock = new Object();
        this.rnd = new Random();
        playerThreadLock = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human){
            createArtificialIntelligence();
        }
        while (!terminate) {
            synchronized (inputLock) {
                try {
                    while (actions.isEmpty() && !terminate) {
                        inputLock.wait();
                    }
                } catch (InterruptedException ignore) {
                }
            }
            
            makeAction();
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
                
            while (!terminate) {
                synchronized (aiLock) {
                    while (actions.size() == 3 && !terminate) {
                        try {
                            aiLock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                keyPressed(rnd.nextInt(env.config.tableSize));
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        if (aiThread != null)
            aiThread.interrupt();
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized(inputLock){
            if(!ignoreInput && actions.size() < 3){
                actions.add(slot);
                inputLock.notifyAll();
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        freeze(env.config.pointFreezeMillis);
        penaltyOrPoint = 0;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        freeze(env.config.penaltyFreezeMillis);
        penaltyOrPoint = 0;
    }

    public int score() {
        return score;
    }

    ///////////////////////////////////////////////////////
    private void freeze(long freezeTime) {
        try {
            while (freezeTime >= 1000) {
                env.ui.setFreeze(this.id, freezeTime);
                freezeTime -= 1000;
                Thread.sleep(1000);
            }
            env.ui.setFreeze(id,freezeTime);
            Thread.sleep(freezeTime);
            env.ui.setFreeze(id, 0);
            ignoreInput = false;
        } catch (InterruptedException ignore) {
        }
    }

    private void checkMe() {
        ignoreInput = true;
        synchronized(dealer.listLock){
            dealer.waitToBeChecked.add(this);
        }
        synchronized (dealer.dealerLock) {
            dealer.dealerLock.notifyAll();
        }
        // thread should wait until get checked.
        try {
            synchronized(playerThreadLock){
                while (!checked) {
                    playerThreadLock.wait();
                }
            }

            checked = false;
            if (penaltyOrPoint == 1)
                point();
            else if (penaltyOrPoint == -1)
                penalty();
            else
                ignoreInput = false;
        } catch (InterruptedException ignore) {
        }
    }

    private void makeAction() {
        if (!actions.isEmpty()) {
            int slot = actions.remove();
            if (!human) {
                synchronized (aiLock) {
                    aiLock.notifyAll();
                }
            }
            if (table.playersSlots[id].isExist(slot) != 0) {
                table.removeToken(id, slot);
            } else if(!table.playersSlots[id].isFull()){
                table.placeToken(id, slot);
                if (table.playersSlots[id].isFull())
                    checkMe();
            }
        }
    }

    public Thread getPlayerThread(){
        return playerThread;
    }
}
