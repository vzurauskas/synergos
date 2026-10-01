package uvm;

/** Dispenses a given quantity and disables itself when its product dies. */
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
