package com.vzurauskas.synergos.scan;

import java.nio.file.Path;

/**
 * A source root such as src/main/java, and whether it holds tests.
 */
record SourceDir(Path path, boolean test) {
}
