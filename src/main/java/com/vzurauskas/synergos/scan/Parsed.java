package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * The outcome of parsing a repo: the files that parsed, and those that didn't.
 */
public final class Parsed {
    private final List<ParsedFile> files;
    private final List<String> failures;

    Parsed(List<ParsedFile> files, List<String> failures) {
        this.files = List.copyOf(files);
        this.failures = List.copyOf(failures);
    }

    List<JavaType> types() {
        return files.stream().flatMap(file -> file.types().stream()).toList();
    }

    /**
     * Adds how many files were parsed, and which ones couldn't be, to the header numbers.
     */
    void describe(ObjectNode stats) {
        stats.put("files", files.size());
        ArrayNode unparsed = stats.putArray("unparsed");
        failures.forEach(unparsed::add);
    }
}
