package uvm;

public final class Restocker {
    private final Inventory inventory;
    private final Display display;

    public Restocker(Inventory inventory, Display display) {
        this.inventory = inventory;
        this.display = display;
    }

    public void restock() {
        inventory.setCount(20);
        display.text = "Restocked";
    }
}
