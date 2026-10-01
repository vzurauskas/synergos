package com.vzurauskas.synergos.flags;

import com.vzurauskas.synergos.scan.Finding;
import com.vzurauskas.synergos.scan.Flag;
import com.vzurauskas.synergos.scan.JavaMethod;
import com.vzurauskas.synergos.scan.JavaType;
import com.vzurauskas.synergos.scan.Project;
import com.vzurauskas.synergos.scan.Traffic;

import java.util.ArrayList;
import java.util.List;

/**
 * An object that inherits a message and then refuses it by throwing
 * UnsupportedOperationException. That is specialization by constraint: you
 * can't tell whether the object is usable without looking at how it works.
 */
public final class RefusedMessage implements Flag {
    @Override
    public List<Finding> findings(JavaType type, Project project, Traffic traffic) {
        List<Finding> findings = new ArrayList<>();
        for (JavaMethod method : type.methods()) {
            if (method.onlyThrows("UnsupportedOperationException") && inherited(method, type, project)) {
                findings.add(new Finding(
                    "refused-message",
                    "red",
                    method.signature(),
                    method.line(),
                    "Inherits " + method.signature() + " and only throws UnsupportedOperationException.",
                    "Composability: specialization by constraint makes an object unusable without "
                        + "looking at how it works (Object Thinking, ch. 3, p. 79)."
                ));
            }
        }
        return findings;
    }

    private boolean inherited(JavaMethod method, JavaType type, Project project) {
        return method.annotatedOverride() || project.ancestors(type.id()).stream()
            .flatMap(ancestor -> project.type(ancestor).stream())
            .anyMatch(ancestor -> ancestor.declares(method.name(), method.arity()));
    }
}
