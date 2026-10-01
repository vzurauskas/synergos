package com.vzurauskas.synergos.flags;

import com.vzurauskas.synergos.scan.Finding;
import com.vzurauskas.synergos.scan.Flag;
import com.vzurauskas.synergos.scan.JavaMethod;
import com.vzurauskas.synergos.scan.JavaType;
import com.vzurauskas.synergos.scan.Project;
import com.vzurauskas.synergos.scan.Traffic;

import java.util.List;

/**
 * A class that is only a home for static procedures: nothing in it is an
 * object you could send a message to.
 */
public final class ProcedureBag implements Flag {
    @Override
    public List<Finding> findings(JavaType type, Project project, Traffic traffic) {
        if (type.isInterface()) {
            return List.of();
        }
        List<JavaMethod> visible = type.methods().stream().filter(method -> !method.isPrivate()).toList();
        long procedures = visible.stream().filter(method -> method.isStatic() && !method.isMain()).count();
        boolean messages = visible.stream().anyMatch(method -> !method.isStatic());
        if (procedures == 0 || messages) {
            return List.of();
        }
        return List.of(new Finding(
            "procedure-bag",
            "red",
            type.simpleName(),
            type.line(),
            "Has " + procedures + " static procedure" + (procedures == 1 ? "" : "s") + " and no messages of its own.",
            "Everything is an object; procedures are not a separate kind of thing "
                + "(Object Thinking, ch. 3, p. 67)."
        ));
    }
}
