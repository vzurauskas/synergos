package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Side 2: who the object is. The description is the Javadoc prose, authored
 * as it stands; the stereotype is the {@code @stereotype} tag if there is
 * one, or else the tool's guess.
 */
final class DescriptionFace implements Face {
    private final JavaType type;
    private final Stereotype stereotype;

    DescriptionFace(JavaType type, Stereotype stereotype) {
        this.type = type;
        this.stereotype = stereotype;
    }

    @Override
    public String key() {
        return "description";
    }

    @Override
    public JsonNode json() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        CubeText text = type.text();
        text.description().ifPresentOrElse(
            description -> node.put("text", description).put("state", "authored"),
            () -> node.putNull("text").put("state", "none")
        );
        node.set("stereotype", text.stereotypeJson().map(JsonNode.class::cast).orElseGet(stereotype::json));
        return node;
    }
}
