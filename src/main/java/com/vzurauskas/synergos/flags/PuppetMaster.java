package com.vzurauskas.synergos.flags;

import com.vzurauskas.synergos.scan.Finding;
import com.vzurauskas.synergos.scan.Flag;
import com.vzurauskas.synergos.scan.JavaType;
import com.vzurauskas.synergos.scan.Project;
import com.vzurauskas.synergos.scan.Site;
import com.vzurauskas.synergos.scan.Traffic;

import java.util.List;
import java.util.Set;

/**
 * An object that directs many others while hardly anyone talks to it: the
 * master control module of a structure chart. Creating objects doesn't
 * count, so a composition root that only wires things up isn't flagged.
 */
public final class PuppetMaster implements Flag {
    private static final int PUPPETS = 5;
    private static final int MAX_SENDERS = 1;

    @Override
    public List<Finding> findings(JavaType type, Project project, Traffic traffic) {
        if (type.isInterface()) {
            return List.of();
        }
        Set<String> puppets = traffic.targets(type.id(), Site.Kind.MESSAGE);
        Set<String> senders = traffic.senders(type);
        if (puppets.size() < PUPPETS || senders.size() > MAX_SENDERS) {
            return List.of();
        }
        return List.of(new Finding(
            "puppet-master",
            "red",
            type.simpleName(),
            type.line(),
            "Sends messages to " + puppets.size() + " project types but is sent messages by "
                + (senders.isEmpty() ? "none" : "only " + senders.size()) + '.',
            "Distributed cooperation and communication must replace hierarchical centralized "
                + "control (Object Thinking, ch. 3, pp. 81-83, Fig. 3-1)."
        ));
    }
}
