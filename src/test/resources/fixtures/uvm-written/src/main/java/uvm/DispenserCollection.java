package uvm;

import java.util.List;

/**
 * @responsibility Relay a dispense request to the right dispenser. {@link Dispenser}
 */
public final class DispenserCollection {
    private final List<Dispenser> dispensers;

    public DispenserCollection(List<Dispenser> dispensers) {
        this.dispensers = List.copyOf(dispensers);
    }

    public void dispense(String id, int quantity) {
        dispensers.stream().filter(d -> d.identifiedBy(id)).forEach(d -> d.dispense(quantity));
    }
}
