package com.vzurauskas.synergos.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * The library jars a repo's code talks to, so calls into them can be resolved.
 */
public interface Classpath {
    List<Path> jars();
}
