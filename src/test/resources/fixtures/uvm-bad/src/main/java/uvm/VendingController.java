package uvm;

/** The master control module: it knows everything and tells everyone what to do. */
public final class VendingController {
    private final CoinBox coins = new CoinBox();
    private final Display display = new Display();
    private final Keypad keypad = new Keypad();
    private final Inventory inventory = new Inventory();
    private final ChangeMaker change = new ChangeMaker();

    public void run() {
        String selection = keypad.selection();
        int price = inventory.price(selection);
        if (coins.total() < price) {
            display.show("Insert more coins");
            return;
        }
        inventory.release(selection);
        change.pay(coins.total() - price);
    }
}
