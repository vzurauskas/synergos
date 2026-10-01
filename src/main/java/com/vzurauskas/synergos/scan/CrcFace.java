package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Side 1: the CRC card. Name, what it is a kind of, its responsibilities
 * (written in its Javadoc, or inherited from the roles it plays) and its
 * collaborators, i.e. whom it sends messages to.
 */
final class CrcFace implements Face {
    private final JavaType type;
    private final Project project;
    private final Traffic traffic;

    CrcFace(JavaType type, Project project, Traffic traffic) {
        this.type = type;
        this.project = project;
        this.traffic = traffic;
    }

    @Override
    public String key() {
        return "crc";
    }

    @Override
    public JsonNode json() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        ArrayNode kindOf = node.putArray("kindOf");
        for (String parent : type.supertypes()) {
            kindOf.addObject().put("id", parent).put("project", project.has(parent));
        }
        ArrayNode playedBy = node.putArray("playedBy");
        project.implementers(type.id()).forEach(playedBy::add);
        node.set("responsibilities", type.text().responsibilitiesJson(type, project));
        node.set("collaborators", traffic.grouped(type.id(), Site.Kind.MESSAGE));
        node.set("procedures", traffic.grouped(type.id(), Site.Kind.PROCEDURE));
        node.set("creates", traffic.grouped(type.id(), Site.Kind.CREATES));
        ArrayNode foreign = node.putArray("foreign");
        traffic.targets(type.id(), Site.Kind.FOREIGN).stream().sorted().forEach(foreign::add);
        return node;
    }
}
