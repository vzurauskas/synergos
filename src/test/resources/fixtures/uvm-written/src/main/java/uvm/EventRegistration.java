package uvm;

public final class EventRegistration {
    private final Product product;
    private int days;

    public EventRegistration(Product product, int days) {
        this.product = product;
        this.days = days;
    }

    public void newDay() {
        days--;
        if (days == 0) {
            product.die();
        }
    }
}
