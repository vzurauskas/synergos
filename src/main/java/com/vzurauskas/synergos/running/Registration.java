package com.vzurauskas.synergos.running;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * The note a running viewer leaves in the registry, so {@code syg --list} can
 * find it and {@code syg --stop} can close it. A viewer removes its own note
 * when it exits.
 */
public final class Registration {
    private final Path dir;
    private final String repo;
    private final ProcessHandle process;

    public Registration(Path dir, String repo) {
        this(dir, repo, ProcessHandle.current());
    }

    public Registration(Path dir, String repo, ProcessHandle process) {
        this.dir = dir;
        this.repo = repo;
        this.process = process;
    }

    public void record(URI uri) {
        ObjectNode note = JsonNodeFactory.instance.objectNode();
        note.put("pid", process.pid());
        note.put("port", uri.getPort());
        note.put("url", uri.toString());
        note.put("repo", repo);
        note.put("startedAt", Instant.now().toString());
        process.info().startInstant().ifPresent(start -> note.put("processStart", start.toString()));
        Path file = dir.resolve(process.pid() + ".json");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, note.toString());
        } catch (IOException e) {
            System.err.println("Couldn't record this viewer in " + dir + ", so syg --list won't show it.");
            return;
        }
        if (process.pid() == ProcessHandle.current().pid()) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    // The next syg --list will find the note stale and remove it.
                }
            }));
        }
    }
}
