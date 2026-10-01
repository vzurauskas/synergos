package com.vzurauskas.synergos.running;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A viewer some note in the registry says is running. It can tell whether
 * that's still true, whether it's the one a person means, and it can stop.
 */
public final class Running {
    private static final long POLITE_SECONDS = 5;
    private static final long FORCED_SECONDS = 2;

    private final Path note;

    Running(Path note) {
        this.note = note;
    }

    /**
     * Whether the process the note talks about still runs. A process with the
     * same id but a different start time is a stranger that reused the id.
     */
    public boolean alive() {
        return process().isPresent();
    }

    /**
     * Whether this is the viewer a person means: "all", its port, its repo's
     * path, or just the repo's folder name.
     */
    public boolean matches(String target) {
        JsonNode note = read();
        String repo = note.path("repo").asText("");
        if (target.equals("all")) {
            return true;
        }
        if (target.matches("\\d+")) {
            return note.path("port").asInt(-1) == Integer.parseInt(target);
        }
        return repo.equals(Paths.get(target).toAbsolutePath().normalize().toString())
            || Paths.get(repo).getFileName() != null && Paths.get(repo).getFileName().toString().equals(target);
    }

    /**
     * The viewer as a row: where it is, what it shows, how long it has run,
     * and its process id.
     */
    public List<String> row(Instant now) {
        JsonNode note = read();
        return List.of(
            note.path("url").asText(""),
            note.path("repo").asText(""),
            uptime(started(note).map(start -> Duration.between(start, now)).orElse(Duration.ZERO)),
            String.valueOf(note.path("pid").asLong())
        );
    }

    /**
     * Asks the viewer to exit, and insists if it doesn't within a few seconds.
     * Answers whether it's gone.
     */
    public boolean stop() {
        Optional<ProcessHandle> process = process();
        if (process.isPresent()) {
            process.get().destroy();
            if (!exited(process.get(), POLITE_SECONDS)) {
                process.get().destroyForcibly();
                exited(process.get(), FORCED_SECONDS);
            }
            if (process.get().isAlive()) {
                return false;
            }
        }
        forget();
        return true;
    }

    /**
     * Removes the note, e.g. because its process is gone.
     */
    void forget() {
        try {
            Files.deleteIfExists(note);
        } catch (IOException e) {
            // A note that can't be removed now will be found stale again later.
        }
    }

    private Optional<ProcessHandle> process() {
        JsonNode note = read();
        if (!note.path("pid").canConvertToLong() || note.path("pid").asLong() <= 0) {
            return Optional.empty();
        }
        Optional<Instant> recorded = instant(note.path("processStart").asText(""));
        return ProcessHandle.of(note.path("pid").asLong())
            .filter(ProcessHandle::isAlive)
            .filter(process -> recorded.isEmpty() || process.info().startInstant().map(recorded.get()::equals).orElse(true));
    }

    private JsonNode read() {
        try {
            return new ObjectMapper().readTree(Files.readString(note));
        } catch (IOException e) {
            return new ObjectMapper().createObjectNode();
        }
    }

    private static Optional<Instant> started(JsonNode note) {
        return instant(note.path("startedAt").asText(""));
    }

    private static Optional<Instant> instant(String text) {
        try {
            return text.isEmpty() ? Optional.empty() : Optional.of(Instant.parse(text));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static boolean exited(ProcessHandle process, long seconds) {
        try {
            process.onExit().get(seconds, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException | ExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String uptime(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return seconds / 60 + "m";
        }
        if (seconds < 86_400) {
            return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
        }
        return seconds / 86_400 + "d " + seconds % 86_400 / 3600 + "h";
    }
}
