package bguspl.set.ex;

public class mySlots {
    public int first, second, third;
    public Object lock;

    public mySlots() {
        first = -1;
        second = -1;
        third = -1;
        lock = new Object();
    }

    public int isExist(int slot) {
        if (slot == first)
            return 1;
        else if (slot == second)
            return 2;
        else if (slot == third)
            return 3;
        return 0;
    }

    public boolean addSlot(int slot) {
        synchronized (lock) {
            int x = isExist(slot);
            if (x == 0) {
                if (first == -1)
                    first = slot;
                else if (second == -1)
                    second = slot;
                else if (third == -1)
                    third = slot;
                else
                    return false;
                return true;
            }
            return false;
        }
    }

    public boolean deleteSlot(int slot) {
        synchronized (lock) {
            int x = isExist(slot);
            if (x == 1)
                first = -1;
            else if (x == 2)
                second = -1;
            else if (x == 3)
                third = -1;
            return x != 0;
        }
    }

    public boolean isFull() {
        synchronized (lock) {
            return first != -1 && second != -1 && third != -1;
        }
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return first == -1 && second == -1 && third == -1;
        }
    }
}
