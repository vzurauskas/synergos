package uvm;

public final class ReadOnlyDispenser implements DispenserRole {
    @Override
    public void dispense() {
        System.out.println("Dispensed");
    }

    @Override
    public void refill(int quantity) {
        throw new UnsupportedOperationException();
    }
}
