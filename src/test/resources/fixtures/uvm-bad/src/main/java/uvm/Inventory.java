package uvm;

public final class Inventory {
    private int count;

    public int price(String selection) {
        return 80;
    }

    public void release(String selection) {
        count--;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
