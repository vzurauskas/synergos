package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * A repo seen as a society of objects: every cube, the lines between them,
 * and what the scanner couldn't read. Printed as the JSON model the viewer
 * draws.
 */
public final class Society {
    private static final int UNRESOLVED_SHOWN = 500;

    private final JavaRepo repo;
    private final Flag flag;

    public Society(JavaRepo repo, Flag flag) {
        this.repo = repo;
        this.flag = flag;
    }

    public String json() {
        Parsed parsed = repo.parsed();
        Project project = new Project(parsed.types());
        Traffic traffic = new Traffic(project);
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.putObject("repo").put("name", repo.name()).put("path", repo.path());
        root.put("scannedAt", Instant.now().toString());
        ObjectNode stats = root.putObject("stats");
        parsed.describe(stats);
        project.describe(stats);
        traffic.describe(stats);
        root.set("cubes", new Cubes(project, traffic, flag).json());
        root.set("edges", new Edges(project, traffic).json());
        root.set("foreign", traffic.foreign());
        root.set("unresolved", traffic.unresolved(UNRESOLVED_SHOWN));
        try {
            return new ObjectMapper().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
