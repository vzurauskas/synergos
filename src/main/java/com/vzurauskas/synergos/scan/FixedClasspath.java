package com.vzurauskas.synergos.scan;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * A classpath known up front, e.g. the running JVM's own, or none at all.
 */
public final class FixedClasspath implements Classpath {
    private final List<Path> jars;

    public FixedClasspath(String classpath) {
        this(
            Arrays.stream(classpath.split(File.pathSeparator))
                .filter(entry -> entry.endsWith(".jar"))
                .map(Paths::get)
                .filter(Files::isRegularFile)
                .toList()
        );
    }

    public FixedClasspath(List<Path> jars) {
        this.jars = List.copyOf(jars);
    }

    @Override
    public List<Path> jars() {
        return jars;
    }
}
