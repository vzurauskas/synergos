package com.vzurauskas.synergos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FlagsTest {
    @Test
    void raisesEveryFlagOnTheBadVendingMachine() {
        assertEquals(
            List.of(
                "PriceUtils: procedure-bag @ PriceUtils",
                "PriceUtils: controller-name @ PriceUtils",
                "ReadOnlyDispenser: refused-message @ refill(int)",
                "Restocker: manipulates-others @ restock",
                "Restocker: manipulates-others @ restock",
                "VendingController: puppet-master @ VendingController",
                "VendingController: controller-name @ VendingController"
            ),
            Model.fixture("uvm-bad").findings()
        );
    }

    @Test
    void staysQuietOnTheGoodVendingMachine() {
        assertTrue(Model.fixture("uvm-good").findings().isEmpty(), () -> Model.fixture("uvm-good").findings().toString());
    }
}
