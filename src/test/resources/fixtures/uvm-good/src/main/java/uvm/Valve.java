package uvm;

public final class Valve implements DispensingMechanism {
    private final int millis;

    public Valve(int millis) {
        this.millis = millis;
    }

    @Override
    public void dispense() {
        System.out.println("Valve open for " + millis + " ms");
    }
}
