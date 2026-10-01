package com.vzurauskas.synergos.flags;

import com.vzurauskas.synergos.scan.Finding;
import com.vzurauskas.synergos.scan.Flag;
import com.vzurauskas.synergos.scan.JavaType;
import com.vzurauskas.synergos.scan.Project;
import com.vzurauskas.synergos.scan.Traffic;

import java.util.List;

/**
 * A name that announces central control. Names ending in -er in general are
 * fine (a Butler serves, a Header heads a table); these particular ones
 * usually hide a controller.
 */
public final class ControllerName implements Flag {
    private static final List<String> SUFFIXES = List.of(
        "Manager", "Controller", "Handler", "Processor", "Coordinator", "Helper", "Util", "Utils"
    );

    @Override
    public List<Finding> findings(JavaType type, Project project, Traffic traffic) {
        return SUFFIXES.stream()
            .filter(suffix -> type.simpleName().endsWith(suffix) && !type.simpleName().equals(suffix))
            .findFirst()
            .map(suffix -> List.of(new Finding(
                "controller-name",
                "amber",
                type.simpleName(),
                type.line(),
                "The name ends in \"" + suffix + "\".",
                "Distributed cooperation must replace centralized control; there should be no "
                    + "controllers (Object Thinking, ch. 3, p. 81)."
            )))
            .orElse(List.of());
    }
}
