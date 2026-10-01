package com.vzurauskas.synergos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vzurauskas.synergos.flags.Contradictions;
import com.vzurauskas.synergos.flags.ControllerName;
import com.vzurauskas.synergos.flags.Flags;
import com.vzurauskas.synergos.flags.ManipulatesOthers;
import com.vzurauskas.synergos.flags.ProcedureBag;
import com.vzurauskas.synergos.flags.PuppetMaster;
import com.vzurauskas.synergos.flags.RefusedMessage;
import com.vzurauskas.synergos.scan.Classpath;
import com.vzurauskas.synergos.scan.FixedClasspath;
import com.vzurauskas.synergos.scan.JavaRepo;
import com.vzurauskas.synergos.scan.Society;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The scanned model of a repo, as a test sees it: parsed JSON with a few
 * ways to find things in it.
 */
final class Model {
    private final JsonNode root;

    Model(Path repo, Classpath classpath) {
        try {
            this.root = new ObjectMapper().readTree(
                new Society(
                    new JavaRepo(repo, classpath),
                    new Flags(
                        new RefusedMessage(), new PuppetMaster(), new ProcedureBag(),
                        new ManipulatesOthers(), new ControllerName(), new Contradictions()
                    )
                ).json()
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Model fixture(String name) {
        return new Model(Paths.get("src/test/resources/fixtures", name), new FixedClasspath(List.of()));
    }

    JsonNode stats() {
        return root.get("stats");
    }

    JsonNode cube(String simpleName) {
        for (JsonNode cube : root.get("cubes")) {
            if (cube.get("simpleName").asText().equals(simpleName)) {
                return cube;
            }
        }
        throw new AssertionError("No cube named " + simpleName);
    }

    /**
     * Every flag raised anywhere, as "Cube: flag @ where".
     */
    List<String> findings() {
        List<String> findings = new ArrayList<>();
        for (JsonNode cube : root.get("cubes")) {
            for (JsonNode flag : cube.get("flags")) {
                findings.add(cube.get("name").asText() + ": " + flag.get("flag").asText() + " @ " + flag.get("where").asText());
            }
        }
        return findings;
    }

    List<String> texts(JsonNode array, String field) {
        List<String> texts = new ArrayList<>();
        array.forEach(node -> texts.add(node.get(field).asText()));
        return texts;
    }
}
