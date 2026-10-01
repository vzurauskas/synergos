package com.vzurauskas.synergos;

import com.vzurauskas.synergos.running.Registration;
import com.vzurauskas.synergos.running.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * syg --list and syg --stop, with sleeping processes standing in for viewers.
 */
final class RunningViewersTest {
    @TempDir
    private Path dir;

    private final List<Process> spawned = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        spawned.forEach(Process::destroyForcibly);
    }

    @Test
    void listsAViewerThatRecordedItself() {
        new Registration(dir, "/repos/alpha").record(URI.create("http://127.0.0.1:7999/"));
        String listed = list();
        assertTrue(listed.startsWith("URL"), listed);
        assertTrue(listed.contains("http://127.0.0.1:7999/"), listed);
        assertTrue(listed.contains("/repos/alpha"), listed);
        assertTrue(listed.contains(String.valueOf(ProcessHandle.current().pid())), listed);
    }

    @Test
    void saysWhenNoneAreRunning() {
        assertEquals("No viewers are running.", list().strip());
    }

    @Test
    void forgetsNotesOfViewersThatAreGone() throws Exception {
        Process finished = new ProcessBuilder("true").start();
        finished.waitFor();
        new Registration(dir, "/repos/gone", finished.toHandle()).record(URI.create("http://127.0.0.1:7998/"));
        assertEquals("No viewers are running.", list().strip());
        assertFalse(Files.exists(dir.resolve(finished.pid() + ".json")));
    }

    @Test
    void forgetsNotesWhoseProcessIdWasReusedByAnotherProcess() throws IOException {
        assumeTrue(ProcessHandle.current().info().startInstant().isPresent());
        long pid = ProcessHandle.current().pid();
        Files.writeString(dir.resolve(pid + ".json"), "{\"pid\": " + pid + ", \"port\": 7997,"
            + " \"url\": \"http://127.0.0.1:7997/\", \"repo\": \"/repos/reused\","
            + " \"startedAt\": \"2000-01-01T00:00:00Z\", \"processStart\": \"2000-01-01T00:00:00Z\"}");
        assertEquals("No viewers are running.", list().strip());
        assertFalse(Files.exists(dir.resolve(pid + ".json")));
    }

    @Test
    void stopsAViewerByTheFolderNameOfItsRepo() throws Exception {
        Process viewer = sleeping("/repos/beta", 7996);
        String said = stop("beta");
        assertTrue(viewer.waitFor(10, TimeUnit.SECONDS));
        assertEquals("Stopped http://127.0.0.1:7996/ (/repos/beta)", said.strip());
        assertFalse(Files.exists(dir.resolve(viewer.pid() + ".json")));
    }

    @Test
    void stopsAViewerByTheFullPathOfItsRepo() throws Exception {
        Process viewer = sleeping("/repos/gamma", 7993);
        stop("/repos/gamma");
        assertTrue(viewer.waitFor(10, TimeUnit.SECONDS));
    }

    @Test
    void stopsByPortAndThenAllTheRest() throws Exception {
        Process one = sleeping("/repos/one", 7995);
        Process two = sleeping("/repos/two", 7994);
        stop("7995");
        assertTrue(one.waitFor(10, TimeUnit.SECONDS));
        assertTrue(two.isAlive());
        assertTrue(list().contains("/repos/two"));
        stop("all");
        assertTrue(two.waitFor(10, TimeUnit.SECONDS));
        assertEquals("No viewers are running.", list().strip());
    }

    @Test
    void saysWhenNothingMatches() throws Exception {
        sleeping("/repos/delta", 7992);
        assertEquals("No running viewer matches \"epsilon\". See syg --list.", stop("epsilon").strip());
    }

    private Process sleeping(String repo, int port) throws IOException {
        Process process = new ProcessBuilder("sleep", "60").start();
        spawned.add(process);
        new Registration(dir, repo, process.toHandle()).record(URI.create("http://127.0.0.1:" + port + "/"));
        return process;
    }

    private String list() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ListViewers(new Registry(dir), new PrintStream(bytes, true, StandardCharsets.UTF_8)).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private String stop(String target) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new StopViewers(new Registry(dir), target, new PrintStream(bytes, true, StandardCharsets.UTF_8)).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
