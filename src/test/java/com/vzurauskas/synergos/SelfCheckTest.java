package com.vzurauskas.synergos;

import com.vzurauskas.synergos.scan.FixedClasspath;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The viewer's own code, seen through its own eyes, raises no red flags.
 */
final class SelfCheckTest {
    @Test
    void raisesNoFlagsOnItself() {
        Model model = new Model(
            Paths.get(""),
            new FixedClasspath(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")))
        );
        assertEquals(java.util.List.of(), model.findings());
    }
}
