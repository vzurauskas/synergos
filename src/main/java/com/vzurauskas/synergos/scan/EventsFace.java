package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.regex.Pattern;

/**
 * Side 6: events. Guessed from names: registrations the object accepts
 * (register*, subscribe*, add*Listener, on*) and registrations it makes with
 * others.
 */
final class EventsFace implements Face {
    private static final Pattern REGISTRATION = Pattern.compile(
        "register.*|subscribe.*|add[A-Z]\\w*Listener|on[A-Z]\\w*"
    );

    private final JavaType type;
    private final Traffic traffic;

    EventsFace(JavaType type, Traffic traffic) {
        this.type = type;
        this.traffic = traffic;
    }

    @Override
    public String key() {
        return "events";
    }

    @Override
    public JsonNode json() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("guessed", true);
        ArrayNode accepts = node.putArray("accepts");
        for (JavaMethod method : type.methods()) {
            if (method.isPrivate() || !REGISTRATION.matcher(method.name()).matches()) {
                continue;
            }
            ObjectNode entry = accepts.addObject();
            entry.put("signature", method.signature());
            entry.put("line", method.line());
            ArrayNode senders = entry.putArray("senders");
            traffic.senders(type, method).forEach(site -> senders.add(site.json()));
        }
        ArrayNode registers = node.putArray("registersWith");
        for (Site site : traffic.from(type.id())) {
            if (site.is(Site.Kind.MESSAGE) && REGISTRATION.matcher(site.name()).matches()) {
                registers.add(site.json());
            }
        }
        return node;
    }
}
