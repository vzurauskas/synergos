package com.vzurauskas.synergos.flags;

import com.vzurauskas.synergos.scan.Finding;
import com.vzurauskas.synergos.scan.Flag;
import com.vzurauskas.synergos.scan.JavaType;
import com.vzurauskas.synergos.scan.Project;
import com.vzurauskas.synergos.scan.Traffic;

import java.util.List;

/**
 * All red flags together. Tests are stories, not objects under design, so
 * they are never flagged.
 */
public final class Flags implements Flag {
    private final List<Flag> flags;

    public Flags(Flag... flags) {
        this.flags = List.of(flags);
    }

    @Override
    public List<Finding> findings(JavaType type, Project project, Traffic traffic) {
        if (type.test()) {
            return List.of();
        }
        return flags.stream().flatMap(flag -> flag.findings(type, project, traffic).stream()).toList();
    }
}
