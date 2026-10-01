package com.vzurauskas.synergos;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cube text written in Javadoc: read back, linked to the code, and checked
 * against it.
 */
final class AuthoringTest {
    private final Model model = Model.fixture("uvm-written");

    @Test
    void readsResponsibilitiesWithTheirLinks() {
        JsonNode first = responsibilities("Dispenser").get(0);
        assertEquals("Dispense a given quantity. {@link DispensingMechanism} {@link #dispense(int)}", first.get("text").asText());
        assertEquals("authored", first.get("state").asText());
        assertEquals(List.of("type", "message"), model.texts(first.get("links"), "kind"));
        assertEquals("uvm.DispensingMechanism", first.get("links").get(0).get("target").asText());
        assertEquals("dispense(int)", first.get("links").get(1).get("name").asText());
    }

    @Test
    void readsAnAuthoredStereotypeAndTidiesItsSpelling() {
        JsonNode stereotype = model.cube("Dispenser").get("faces").get("description").get("stereotype");
        assertEquals("service provider", stereotype.get("value").asText());
        assertEquals("authored", stereotype.get("state").asText());
        assertTrue(stereotype.get("valid").asBoolean());
    }

    @Test
    void roleResponsibilitiesAreInheritedByThosePlayingTheRole() {
        JsonNode inherited = responsibilities("Valve").get(0);
        assertEquals("inherited", inherited.get("state").asText());
        assertEquals("uvm.DispensingMechanism", inherited.get("from").asText());
        assertFalse(model.cube("Valve").get("written").asBoolean());
    }

    @Test
    void aCubeIsWrittenOnceItHasAResponsibilityOfItsOwn() {
        assertTrue(model.cube("Dispenser").get("written").asBoolean());
        assertTrue(model.cube("DispensingMechanism").get("written").asBoolean());
        assertFalse(model.cube("SystemClockCalendar").get("written").asBoolean());
        assertEquals(4, model.stats().get("written").asInt());
    }

    @Test
    void findsOnlyContradictionsTheCodeCanProve() {
        assertEquals(
            List.of(
                "Dispenser: contradiction @ Keep time. {@link SystemClockCalendar}",
                "Dispenser: contradiction @ Refill myself. {@link #refill()}",
                "Dispenser: contradiction @ identifiedBy(String)",
                "Dispenser: contradiction @ disable()",
                "Product: contradiction @ Tell my mourners when I die. {@link Mourner}",
                "Product: contradiction @ @stereotype"
            ),
            model.findings()
        );
    }

    @Test
    void explainsEachContradiction() {
        JsonNode flags = model.cube("Dispenser").get("flags");
        assertEquals("amber", flags.get(0).get("severity").asText());
        assertEquals(
            "Links SystemClockCalendar, but Dispenser never sends it a message, calls it or creates it.",
            flags.get(0).get("why").asText()
        );
        assertEquals("Links #refill(), but Dispenser doesn't respond to that message.", flags.get(1).get("why").asText());
    }

    private JsonNode responsibilities(String cube) {
        return model.cube(cube).get("faces").get("crc").get("responsibilities");
    }
}
