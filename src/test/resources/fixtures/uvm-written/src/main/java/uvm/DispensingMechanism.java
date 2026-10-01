package uvm;

/**
 * Something that pushes one unit of product out: a valve, a motor, a push bar.
 *
 * @responsibility Push one unit of product out. {@link #dispense()}
 */
public interface DispensingMechanism {
    void dispense();
}
