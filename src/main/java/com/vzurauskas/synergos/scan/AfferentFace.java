package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Side 5: the messages the object responds to, each with the objects that
 * send it.
 */
final class AfferentFace implements Face {
    private final JavaType type;
    private final Traffic traffic;

    AfferentFace(JavaType type, Traffic traffic) {
        this.type = type;
        this.traffic = traffic;
    }

    @Override
    public String key() {
        return "afferent";
    }

    @Override
    public JsonNode json() {
        ArrayNode messages = JsonNodeFactory.instance.arrayNode();
        for (JavaMethod method : type.methods()) {
            if (method.isPrivate()) {
                continue;
            }
            ObjectNode entry = messages.addObject();
            entry.put("signature", method.signature());
            entry.put("name", method.name());
            entry.put("line", method.line());
            entry.put("static", method.isStatic());
            entry.put("identity", method.isIdentity());
            ArrayNode senders = entry.putArray("senders");
            for (Site site : traffic.senders(type, method)) {
                senders.add(site.json());
            }
        }
        return messages;
    }
}
