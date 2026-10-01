package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing cube text into Javadoc: only the one comment changes, and it
 * changes the way a person would have written it.
 */
final class CubeTextEditTest {
    private static final String FILE = "src/main/java/p/X.java";
    private static final String NOTHING = "{\"description\": null, \"stereotype\": null, \"responsibilities\": []}";

    @TempDir
    private Path dir;

    @Test
    void insertsJavadocAboveTheAnnotationsWhereThereWasNone() throws IOException {
        assertEquals(
            String.join("\n",
                "package p;",
                "",
                "import java.util.List;",
                "",
                "/**",
                " * I carry transactions past a stamp.",
                " *",
                " * @responsibility Stamp every unstamped transaction. {@link Stamp}",
                " * @stereotype coordinator",
                " */",
                "@SuppressWarnings(\"unused\")",
                "public final class Rail {",
                "    public void drive() {",
                "    }",
                "}",
                ""
            ),
            edit(
                String.join("\n",
                    "package p;",
                    "",
                    "import java.util.List;",
                    "",
                    "@SuppressWarnings(\"unused\")",
                    "public final class Rail {",
                    "    public void drive() {",
                    "    }",
                    "}",
                    ""
                ),
                "p.Rail",
                NOTHING,
                "{\"description\": \"I carry transactions past a stamp.\", \"stereotype\": \"coordinator\","
                    + " \"responsibilities\": [\"Stamp every unstamped transaction. {@link Stamp}\"]}"
            )
        );
    }

    @Test
    void replacesTheCubeTextAndKeepsEveryOtherTag() throws IOException {
        assertEquals(
            String.join("\n",
                "package p;",
                "",
                "/**",
                " * New words.",
                " *",
                " * @responsibility New one.",
                " * @param <T> the kind of thing",
                " * @see Other",
                " * @since 1.0",
                " */",
                "public final class Box<T> {",
                "}",
                ""
            ),
            edit(
                String.join("\n",
                    "package p;",
                    "",
                    "/**",
                    " * Old words.",
                    " *",
                    " * @param <T> the kind of thing",
                    " * @responsibility Old responsibility.",
                    " * @see Other",
                    " * @since 1.0",
                    " */",
                    "public final class Box<T> {",
                    "}",
                    ""
                ),
                "p.Box",
                "{\"description\": \"Old words.\", \"stereotype\": null, \"responsibilities\": [\"Old responsibility.\"]}",
                "{\"description\": \"New words.\", \"stereotype\": null, \"responsibilities\": [\"New one.\"]}"
            )
        );
    }

    @Test
    void indentsTheCommentOfANestedType() throws IOException {
        assertEquals(
            String.join("\n",
                "package p;",
                "",
                "public final class Outer {",
                "    /**",
                "     * @responsibility Hold things.",
                "     */",
                "    static final class Inner {",
                "    }",
                "}",
                ""
            ),
            edit(
                String.join("\n",
                    "package p;",
                    "",
                    "public final class Outer {",
                    "    static final class Inner {",
                    "    }",
                    "}",
                    ""
                ),
                "p.Outer.Inner",
                NOTHING,
                "{\"responsibilities\": [\"Hold things.\"]}"
            )
        );
    }

    @Test
    void keepsWindowsLineEndings() throws IOException {
        String written = edit(
            "package p;\r\n\r\npublic final class Rail {\r\n}\r\n",
            "p.Rail",
            NOTHING,
            "{\"description\": \"Carries.\", \"responsibilities\": [\"Carry.\"]}"
        );
        assertEquals(
            "package p;\r\n\r\n/**\r\n * Carries.\r\n *\r\n * @responsibility Carry.\r\n */\r\npublic final class Rail {\r\n}\r\n",
            written
        );
    }

    @Test
    void wrapsLongLinesWithoutBreakingLinksOrStartingALineWithAnAt() throws IOException {
        String responsibility = "Stamp every transaction in the file that hasn't been stamped yet, asking a human "
            + "which category it belongs to whenever it isn't obvious from the transaction itself. "
            + "{@link Stamp} {@link #drive(TransactionsFile, int)} @notATag";
        String written = edit(
            "package p;\n\npublic final class Rail {\n}\n",
            "p.Rail",
            NOTHING,
            new ObjectMapper().createObjectNode().set(
                "responsibilities", new ObjectMapper().createArrayNode().add(responsibility)
            ).toString()
        );
        List<String> lines = Arrays.asList(written.split("\n"));
        assertTrue(lines.stream().allMatch(line -> line.length() <= 100), written);
        assertTrue(lines.stream().anyMatch(line -> line.contains("{@link #drive(TransactionsFile, int)}")), written);
        assertTrue(lines.stream().filter(line -> line.startsWith(" *     ")).count() >= 1, written);
        assertFalse(lines.stream().anyMatch(line -> line.strip().startsWith("* @notATag")), written);
        assertTrue(new CubeText(StaticJavaParser.parse(written).getType(0).getJavadoc()).matches(
            json("{\"description\": null, \"stereotype\": null, \"responsibilities\": ["
                + new ObjectMapper().writeValueAsString(responsibility) + "]}")
        ), written);
    }

    @Test
    void escapesWhatWouldBreakTheComment() throws IOException {
        String written = edit(
            "package p;\n\npublic final class Rail {\n}\n",
            "p.Rail",
            NOTHING,
            "{\"description\": \"@home in comments that end with */ early.\"}"
        );
        assertTrue(written.contains(" * {@literal @}home in comments that end with *&#47; early."), written);
        StaticJavaParser.parse(written);
    }

    @Test
    void readsBackWhatItWrote() throws IOException {
        String after = "{\"description\": \"First paragraph.\\n\\nSecond one.\", \"stereotype\": \"structurer\","
            + " \"responsibilities\": [\"Keep dispensers. {@link Dispenser}\", \"Find one by id. {@link #find(String)}\"]}";
        String written = edit("package p;\n\npublic final class Box {\n}\n", "p.Box", NOTHING, after);
        CubeText text = new CubeText(StaticJavaParser.parse(written).getType(0).getJavadoc());
        assertTrue(text.matches(json(after)), written);
        assertEquals("First paragraph.\n\nSecond one.", text.description().orElseThrow());
    }

    @Test
    void removesTheCommentWhenEverythingIsDeleted() throws IOException {
        assertEquals(
            "package p;\n\npublic final class Rail {\n}\n",
            edit(
                "package p;\n\n/**\n * Old.\n *\n * @responsibility Old.\n */\npublic final class Rail {\n}\n",
                "p.Rail",
                "{\"description\": \"Old.\", \"responsibilities\": [\"Old.\"]}",
                NOTHING
            )
        );
    }

    @Test
    void refusesWhenTheJavadocChangedOnDiskMeanwhile() throws IOException {
        String source = "package p;\n\n/**\n * Somebody changed this.\n */\npublic final class Rail {\n}\n";
        Refusal refusal = assertThrows(
            Refusal.class,
            () -> edit(source, "p.Rail", "{\"description\": \"What the editor saw.\"}", "{\"description\": \"New.\"}")
        );
        assertEquals(409, refusal.status());
        assertEquals(source, Files.readString(dir.resolve(FILE)));
    }

    @Test
    void ignoresChangesInsideMethodBodies() throws IOException {
        String written = edit(
            "package p;\n\n/**\n * Old.\n */\npublic final class Rail {\n    void run() {\n        int changed = 1;\n    }\n}\n",
            "p.Rail",
            "{\"description\": \"Old.\"}",
            "{\"description\": \"New.\"}"
        );
        assertTrue(written.contains(" * New.") && written.contains("int changed = 1;"), written);
    }

    @Test
    void refusesFilesOutsideTheRepo() throws IOException {
        Files.createDirectories(dir.resolve("repo"));
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("file", "../outside.java");
        request.put("id", "p.Rail");
        request.set("before", json(NOTHING));
        request.set("after", json(NOTHING));
        Files.writeString(dir.resolve("outside.java"), "package p;\npublic final class Rail {}\n");
        Refusal refusal = assertThrows(
            Refusal.class,
            () -> new CubeTextEdit(new JavaRepo(dir.resolve("repo"), new FixedClasspath(List.of())), request).apply()
        );
        assertEquals(404, refusal.status());
    }

    private String edit(String source, String id, String before, String after) throws IOException {
        Path file = dir.resolve(FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("file", FILE);
        request.put("id", id);
        request.set("before", json(before));
        request.set("after", json(after));
        new CubeTextEdit(new JavaRepo(dir, new FixedClasspath(List.of())), request).apply();
        return Files.readString(file);
    }

    private static JsonNode json(String text) throws IOException {
        return new ObjectMapper().readTree(text);
    }
}
