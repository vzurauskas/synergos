package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * The cubes of every type in the project.
 */
final class Cubes {
    private final Project project;
    private final Traffic traffic;
    private final Flag flag;

    Cubes(Project project, Traffic traffic, Flag flag) {
        this.project = project;
        this.traffic = traffic;
        this.flag = flag;
    }

    ArrayNode json() {
        ArrayNode cubes = JsonNodeFactory.instance.arrayNode();
        for (JavaType type : project.types()) {
            cubes.add(new Cube(type, project, traffic, flag).json());
        }
        return cubes;
    }
}
