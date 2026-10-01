package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * Side 4: what the object needs to know, and where that comes from: a field
 * (given at construction or made by the object), a parameter, or a
 * collaborator it asks.
 */
final class KnowledgeFace implements Face {
    private final JavaType type;
    private final Project project;
    private final Traffic traffic;

    KnowledgeFace(JavaType type, Project project, Traffic traffic) {
        this.type = type;
        this.project = project;
        this.traffic = traffic;
    }

    @Override
    public String key() {
        return "knowledge";
    }

    @Override
    public JsonNode json() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        ArrayNode fields = node.putArray("fields");
        for (JavaField field : type.fields()) {
            fields.add(field.json(project));
        }
        Set<String> names = type.fieldNames();
        ArrayNode methods = node.putArray("methods");
        for (JavaMethod method : type.methods()) {
            if (method.isPrivate() || method.isIdentity()) {
                continue;
            }
            ObjectNode entry = methods.addObject();
            entry.put("signature", method.signature());
            entry.put("line", method.line());
            entry.set("items", method.knowledge(traffic, project, names));
        }
        return node;
    }
}
