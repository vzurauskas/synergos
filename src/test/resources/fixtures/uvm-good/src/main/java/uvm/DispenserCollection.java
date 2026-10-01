package uvm;

import java.util.List;

public final class DispenserCollection {
    private final List<Dispenser> dispensers;

    public DispenserCollection(List<Dispenser> dispensers) {
        this.dispensers = List.copyOf(dispensers);
    }

    public void dispense(String id, int quantity) {
        dispensers.stream().filter(d -> d.identifiedBy(id)).forEach(d -> d.dispense(quantity));
    }
}
