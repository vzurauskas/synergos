package com.vzurauskas.synergos.running;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The folder where running viewers leave notes about themselves. It answers
 * which viewers are running, and forgets notes whose viewer is gone (one that
 * crashed or was killed hard never got to remove its own note).
 */
public final class Registry {
    private final Path dir;

    public Registry(Path dir) {
        this.dir = dir;
    }

    public List<Running> running() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Running> live = new ArrayList<>();
        try (Stream<Path> notes = Files.list(dir)) {
            for (Path note : notes.filter(file -> file.toString().endsWith(".json")).sorted().toList()) {
                Running running = new Running(note);
                if (running.alive()) {
                    live.add(running);
                } else {
                    running.forget();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return live;
    }
}
