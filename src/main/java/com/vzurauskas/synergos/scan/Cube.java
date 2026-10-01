package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * An object cube: one type seen from six sides, plus the red flags raised
 * on it.
 */
final class Cube {
    private final JavaType type;
    private final Project project;
    private final Traffic traffic;
    private final Flag flag;

    Cube(JavaType type, Project project, Traffic traffic, Flag flag) {
        this.type = type;
        this.project = project;
        this.traffic = traffic;
        this.flag = flag;
    }

    ObjectNode json() {
        List<Face> faces = List.of(
            new CrcFace(type, project, traffic),
            new DescriptionFace(type, new Stereotype(type, project, traffic)),
            new ContractsFace(),
            new KnowledgeFace(type, project, traffic),
            new AfferentFace(type, traffic),
            new EventsFace(type, traffic)
        );
        ObjectNode node = type.identity();
        ObjectNode sides = node.putObject("faces");
        for (Face face : faces) {
            sides.set(face.key(), face.json());
        }
        ArrayNode flags = node.putArray("flags");
        for (Finding finding : flag.findings(type, project, traffic)) {
            flags.add(finding.json());
        }
        return node;
    }
}
