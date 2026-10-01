package com.vzurauskas.synergos.scan;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A Java repository on disk. It finds its own source roots, parses its
 * sources with symbol resolution, and reads source files back on request.
 */
public final class JavaRepo {
    private static final Set<String> SKIPPED = Set.of(
        "target", "build", "out", "node_modules", "resources", ".git", ".idea", ".synergos", ".cubes"
    );
    private static final int MAX_DEPTH = 8;

    private final Path root;
    private final Classpath classpath;

    public JavaRepo(Path root, Classpath classpath) {
        this.root = root.toAbsolutePath().normalize();
        this.classpath = classpath;
    }

    public String name() {
        return root.getFileName().toString();
    }

    public String path() {
        return root.toString();
    }

    /**
     * The text of a Java source file inside this repo, given its path relative
     * to the repo root. Anything outside the repo, or not a .java file, is absent.
     */
    public Optional<String> source(String relative) {
        return inside(relative).flatMap(file -> {
            try {
                return Optional.of(Files.readString(file));
            } catch (IOException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Replaces the text of a Java source file inside this repo. Like reading,
     * this only works for .java files that already exist inside the repo.
     */
    public void write(String relative, String text) {
        Path file = inside(relative)
            .orElseThrow(() -> new Refusal(404, "No such source file in the repo: " + relative));
        try {
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<Path> inside(String relative) {
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !file.toString().endsWith(".java") || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    public Parsed parsed() {
        List<SourceDir> dirs = sourceDirs();
        CombinedTypeSolver solver = new CombinedTypeSolver();
        ParserConfiguration config = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        for (SourceDir dir : dirs) {
            solver.add(new JavaParserTypeSolver(dir.path(), config));
        }
        for (Path jar : classpath.jars()) {
            try {
                solver.add(new JarTypeSolver(jar));
            } catch (IOException | RuntimeException e) {
                // An unreadable jar only means fewer resolved calls.
            }
        }
        solver.add(new ReflectionTypeSolver(true));
        JavaParser parser = new JavaParser(
            new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(solver))
        );
        List<ParsedFile> files = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (SourceDir dir : dirs) {
            for (Path file : javaFiles(dir.path())) {
                String relative = root.relativize(file).toString();
                try {
                    ParseResult<CompilationUnit> result = parser.parse(file);
                    if (result.getResult().isPresent() && result.isSuccessful()) {
                        files.add(new ParsedFile(result.getResult().get(), relative, dir.test()));
                    } else {
                        failures.add(relative);
                    }
                } catch (IOException | RuntimeException e) {
                    failures.add(relative);
                }
            }
        }
        return new Parsed(files, failures);
    }

    private List<SourceDir> sourceDirs() {
        List<SourceDir> dirs = new ArrayList<>();
        collect(root, 0, dirs);
        return dirs;
    }

    private void collect(Path dir, int depth, List<SourceDir> dirs) {
        if (depth > MAX_DEPTH) {
            return;
        }
        String unix = dir.toString().replace('\\', '/');
        if (unix.endsWith("src/main/java")) {
            dirs.add(new SourceDir(dir, false));
            return;
        }
        if (unix.endsWith("src/test/java")) {
            dirs.add(new SourceDir(dir, true));
            return;
        }
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : children.sorted().toList()) {
                if (Files.isDirectory(child) && !SKIPPED.contains(child.getFileName().toString())) {
                    collect(child, depth + 1, dirs);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> javaFiles(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                .filter(Files::isRegularFile)
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
