package com.vzurauskas.synergos;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FacesTest {
    private final Model model = Model.fixture("uvm-good");

    @Test
    void crcListsCollaboratorsWithTheirMessages() {
        JsonNode crc = model.cube("Dispenser").get("faces").get("crc");
        assertEquals(List.of("uvm.DispensingMechanism", "uvm.Product"), model.texts(crc.get("collaborators"), "id"));
        assertEquals("dispense", crc.get("collaborators").get(0).get("messages").get(0).asText());
    }

    @Test
    void crcOfARoleListsWhoPlaysIt() {
        JsonNode crc = model.cube("DispensingMechanism").get("faces").get("crc");
        assertEquals("uvm.Valve", crc.get("playedBy").get(0).asText());
    }

    @Test
    void messagesArriveThroughTheRoleTheObjectPlays() {
        JsonNode dispense = model.cube("Valve").get("faces").get("afferent").get(0);
        assertEquals("dispense()", dispense.get("signature").asText());
        JsonNode sender = dispense.get("senders").get(0);
        assertEquals("uvm.Dispenser", sender.get("from").asText());
        assertEquals("uvm.DispensingMechanism", sender.get("target").asText());
    }

    @Test
    void knowledgeSaysWhereEachPieceComesFrom() {
        JsonNode knowledge = model.cube("Dispenser").get("faces").get("knowledge");
        assertEquals(List.of("given", "given", "made"), model.texts(knowledge.get("fields"), "origin"));
        JsonNode dispense = knowledge.get("methods").get(0);
        assertEquals("dispense(int)", dispense.get("signature").asText());
        assertEquals(List.of("param", "field", "field"), model.texts(dispense.get("items"), "kind"));
    }

    @Test
    void asksAreCollaboratorQuestionsWhoseAnswersAreUsed() {
        JsonNode items = model.cube("DispenserCollection").get("faces").get("knowledge")
            .get("methods").get(0).get("items");
        assertTrue(model.texts(items, "kind").contains("asks"), items::toString);
    }

    @Test
    void eventsShowRegistrationsBothWays() {
        JsonNode calendar = model.cube("SystemClockCalendar").get("faces").get("events");
        assertEquals("register(EventRegistration)", calendar.get("accepts").get(0).get("signature").asText());
        assertEquals("uvm.Product", calendar.get("accepts").get(0).get("senders").get(0).get("from").asText());
        JsonNode product = model.cube("Product").get("faces").get("events");
        assertEquals("register", product.get("registersWith").get(0).get("name").asText());
    }

    @Test
    void guessesStereotypesWithReasons() {
        JsonNode collection = model.cube("DispenserCollection").get("faces").get("description").get("stereotype");
        assertEquals("structurer", collection.get("value").asText());
        assertEquals("guessed", collection.get("state").asText());
        assertTrue(model.cube("DispensingMechanism").get("faces").get("description").get("stereotype").isNull());
    }

    @Test
    void existingJavadocProseIsTheAuthoredDescription() {
        JsonNode description = model.cube("Dispenser").get("faces").get("description");
        assertEquals("Dispenses a given quantity and disables itself when its product dies.", description.get("text").asText());
        assertEquals("authored", description.get("state").asText());
    }

    @Test
    void contractsStayEmptyForNow() {
        assertEquals(0, model.cube("Dispenser").get("faces").get("contracts").size());
    }

    @Test
    void resolvesEveryCallInThePlainFixture() {
        assertEquals(1.0, model.stats().get("resolved").asDouble());
    }
}
