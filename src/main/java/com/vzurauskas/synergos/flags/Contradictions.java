package com.vzurauskas.synergos.flags;

import com.vzurauskas.synergos.scan.Finding;
import com.vzurauskas.synergos.scan.Flag;
import com.vzurauskas.synergos.scan.JavaType;
import com.vzurauskas.synergos.scan.Project;
import com.vzurauskas.synergos.scan.Traffic;

import java.util.List;

/**
 * Places where the text written on a cube and the code disagree, in ways the
 * code can prove. They are amber and kept apart from red flags: they are
 * about the text, not about the design.
 */
public final class Contradictions implements Flag {
    @Override
    public List<Finding> findings(JavaType type, Project project, Traffic traffic) {
        return type.text().contradictions(type, project, traffic);
    }
}
