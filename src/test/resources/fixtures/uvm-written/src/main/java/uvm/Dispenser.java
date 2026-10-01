package uvm;

/**
 * I dispense a given quantity of product, and disable myself when my product dies.
 *
 * @responsibility Dispense a given quantity. {@link DispensingMechanism} {@link #dispense(int)}
 * @responsibility Stop dispensing when my product dies. {@link Product} {@link #stock(Product)}
 * @responsibility Keep time. {@link SystemClockCalendar}
 * @responsibility Refill myself. {@link #refill()}
 * @stereotype Service-Provider
 */
public final class Dispenser {
    private final String id;
    private final DispensingMechanism mechanism;
    private boolean enabled;

    public Dispenser(String id, DispensingMechanism mechanism) {
        this.id = id;
        this.mechanism = mechanism;
        this.enabled = true;
    }

    public void dispense(int quantity) {
        if (!enabled) {
            return;
        }
        for (int i = 0; i < quantity; i++) {
            mechanism.dispense();
        }
    }

    public void stock(Product product) {
        product.registerForDeath(this::disable);
    }

    public boolean identifiedBy(String candidate) {
        return id.equals(candidate);
    }

    public void disable() {
        enabled = false;
    }
}
