package com.vzurauskas.synergos;

import com.vzurauskas.synergos.scan.MavenClasspath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance on a real codebase. Runs only when SYG_RAILCOUNT points at a
 * checkout of railcount.
 */
@EnabledIfEnvironmentVariable(named = "SYG_RAILCOUNT", matches = ".+")
final class RailcountTest {
    private final Model model = new Model(
        repo(),
        new MavenClasspath(repo(), Paths.get(System.getProperty("user.home"), ".cache", "synergos"), System.err)
    );

    @Test
    void raisesExactlyTheExpectedFlags() {
        assertEquals(
            List.of(
                "UnrecognizedService: refused-message @ command()",
                "UnrecognizedService: refused-message @ description()"
            ),
            model.findings()
        );
    }

    @Test
    void resolvesAtLeastNinetyPercentOfCalls() {
        assertTrue(model.stats().get("resolved").asDouble() >= 0.9, model.stats()::toString);
    }

    private static Path repo() {
        return Paths.get(System.getenv("SYG_RAILCOUNT"));
    }
}
