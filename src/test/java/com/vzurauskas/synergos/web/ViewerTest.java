package com.vzurauskas.synergos.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vzurauskas.synergos.flags.Contradictions;
import com.vzurauskas.synergos.flags.Flags;
import com.vzurauskas.synergos.scan.FixedClasspath;
import com.vzurauskas.synergos.scan.JavaRepo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ViewerTest {
    private static URI base;

    @BeforeAll
    static void start() {
        base = viewer(Paths.get("src/test/resources/fixtures/uvm-good"));
    }

    @Test
    void servesTheModel() throws Exception {
        HttpResponse<String> response = get(base, "api/model");
        assertEquals(200, response.statusCode());
        JsonNode model = new ObjectMapper().readTree(response.body());
        assertEquals("uvm-good", model.get("repo").get("name").asText());
        assertTrue(model.get("cubes").size() > 0);
    }

    @Test
    void servesThePage() throws Exception {
        HttpResponse<String> response = get(base, "");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<html"));
    }

    @Test
    void servesSourceFilesOfTheRepo() throws Exception {
        HttpResponse<String> response = get(base, "api/source?file=src/main/java/uvm/Dispenser.java");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("class Dispenser"));
    }

    @Test
    void refusesFilesOutsideTheRepo() throws Exception {
        assertEquals(404, get(base, "api/source?file=../../../../pom.xml").statusCode());
        assertEquals(404, get(base, "api/source?file=..%2F..%2F..%2F..%2Fsrc%2Fmain%2Fjava%2Fcom%2Fvzurauskas%2Fsynergos%2FMain.java").statusCode());
    }

    @Test
    void rescansWhenItsOwnPageAsks() throws Exception {
        assertEquals(200, post(base, "api/rescan", "{}", origin(base), "application/json").statusCode());
    }

    @Test
    void refusesChangesFromOtherPages() throws Exception {
        assertEquals(403, post(base, "api/rescan", "{}", "http://evil.example", "application/json").statusCode());
    }

    @Test
    void refusesChangesThatArentJson() throws Exception {
        assertEquals(415, post(base, "api/rescan", "{}", origin(base), "text/plain").statusCode());
    }

    @Test
    void refusesRequestsAddressedToOtherHosts() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", base.getPort())) {
            socket.getOutputStream().write(
                "GET /api/model HTTP/1.1\r\nHost: evil.example\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
            );
            String status = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            assertTrue(status.contains(" 403 "), status);
        }
    }

    @Test
    void writesCubeTextIntoJavadocAndAnswersWithTheNewModel(@TempDir Path dir) throws Exception {
        URI copy = viewer(copied(dir));
        HttpResponse<String> response = post(
            copy,
            "api/cube/text",
            "{\"file\": \"src/main/java/uvm/Valve.java\", \"id\": \"uvm.Valve\","
                + " \"before\": {\"description\": null, \"stereotype\": null, \"responsibilities\": []},"
                + " \"after\": {\"description\": \"I open for a while.\", \"stereotype\": null,"
                + " \"responsibilities\": [\"Let product flow for a while. {@link #dispense()}\"]}}",
            origin(copy),
            "application/json"
        );
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(Files.readString(dir.resolve("src/main/java/uvm/Valve.java")).contains(
            " * @responsibility Let product flow for a while. {@link #dispense()}"
        ));
        for (JsonNode cube : new ObjectMapper().readTree(response.body()).get("cubes")) {
            if (cube.get("id").asText().equals("uvm.Valve")) {
                assertTrue(cube.get("written").asBoolean());
                assertEquals("I open for a while.", cube.get("faces").get("description").get("text").asText());
            }
        }
    }

    @Test
    void answersConflictWhenTheJavadocChangedOnDisk(@TempDir Path dir) throws Exception {
        URI copy = viewer(copied(dir));
        HttpResponse<String> response = post(
            copy,
            "api/cube/text",
            "{\"file\": \"src/main/java/uvm/Dispenser.java\", \"id\": \"uvm.Dispenser\","
                + " \"before\": {\"description\": \"Not what the file says.\", \"stereotype\": null, \"responsibilities\": []},"
                + " \"after\": {\"description\": \"New.\", \"stereotype\": null, \"responsibilities\": []}}",
            origin(copy),
            "application/json"
        );
        assertEquals(409, response.statusCode());
    }

    private static URI viewer(Path root) {
        JavaRepo repo = new JavaRepo(root, new FixedClasspath(List.of()));
        return new Viewer(new LiveModel(repo, new Flags(new Contradictions())), repo, 0).start();
    }

    private static Path copied(Path dir) throws IOException {
        Path source = Paths.get("src/test/resources/fixtures/uvm-written");
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path target = dir.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        return dir;
    }

    private static String origin(URI uri) {
        return "http://127.0.0.1:" + uri.getPort();
    }

    private static HttpResponse<String> get(URI root, String path) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(root.resolve(path)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> post(URI root, String path, String body, String origin, String type)
        throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(root.resolve(path))
                .header("Origin", origin)
                .header("Content-Type", type)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }
}
