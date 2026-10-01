package com.vzurauskas.synergos.scan;

import java.util.List;

/**
 * A red flag: a sign that a type departs from one of the principles of
 * object thinking.
 */
public interface Flag {
    List<Finding> findings(JavaType type, Project project, Traffic traffic);
}
