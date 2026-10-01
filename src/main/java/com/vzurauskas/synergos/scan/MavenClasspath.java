package com.vzurauskas.synergos.scan;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Asks Maven for a repo's dependency jars. The answer is cached on disk and
 * reused until pom.xml changes. If Maven isn't there or fails, the scan goes
 * on without library jars and simply resolves fewer calls.
 */
public final class MavenClasspath implements Classpath {
    private static final long TIMEOUT_MINUTES = 5;

    private final Path root;
    private final Path cacheDir;
    private final PrintStream log;

    public MavenClasspath(Path root, Path cacheDir, PrintStream log) {
        this.root = root;
        this.cacheDir = cacheDir;
        this.log = log;
    }

    @Override
    public List<Path> jars() {
        Path pom = root.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return List.of();
        }
        try {
            String stamp = String.valueOf(Files.getLastModifiedTime(pom).toMillis());
            Path cache = cacheDir.resolve(digest(root.toString()) + ".classpath");
            if (Files.isRegularFile(cache)) {
                List<String> lines = Files.readAllLines(cache);
                if (lines.size() == 2 && lines.get(0).equals(stamp)) {
                    return parsed(lines.get(1));
                }
            }
            String classpath = fromMaven();
            Files.createDirectories(cacheDir);
            Files.writeString(cache, stamp + '\n' + classpath + '\n');
            return parsed(classpath);
        } catch (IOException e) {
            log.println("Could not resolve the Maven classpath (" + e.getMessage() + "); library calls will stay unresolved.");
            return List.of();
        }
    }

    private String fromMaven() throws IOException {
        log.println("Asking Maven for the dependency classpath of " + root + " ...");
        Path output = Files.createTempFile("synergos-classpath", ".txt");
        Path console = Files.createTempFile("synergos-maven", ".log");
        try {
            Process maven = new ProcessBuilder(
                "mvn", "-q", "-B", "dependency:build-classpath",
                "-Dmdep.outputFile=" + output,
                "-Dmdep.appendOutput=true"
            ).directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(console.toFile())
                .start();
            if (!maven.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                maven.destroyForcibly();
                throw new IOException("Maven timed out");
            }
            if (maven.exitValue() != 0) {
                throw new IOException("Maven failed, see " + console);
            }
            return Files.readString(output).replace('\n', File.pathSeparatorChar).trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private List<Path> parsed(String classpath) {
        return Arrays.stream(classpath.split(File.pathSeparator))
            .map(String::trim)
            .filter(entry -> entry.endsWith(".jar"))
            .map(Paths::get)
            .filter(Files::isRegularFile)
            .distinct()
            .toList();
    }

    private String digest(String text) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
            ).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
