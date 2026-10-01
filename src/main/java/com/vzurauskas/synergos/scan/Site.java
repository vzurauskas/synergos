package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One place in the code where an object reaches another: a message send, a
 * procedure call, a creation, or a write into another object's field.
 */
public record Site(
    Kind kind,
    String from,
    String member,
    int memberLine,
    String target,
    String name,
    int arity,
    boolean used,
    boolean usesParameters,
    String receiver,
    String receiverSource,
    String file,
    int line,
    String expression
) {
    public enum Kind {
        /** An instance message to a project object. */
        MESSAGE,
        /** A static call to a project type. */
        PROCEDURE,
        /** A call an object makes on itself (or another instance of its own class). */
        SELF,
        /** An instance message to a library or JDK object. */
        FOREIGN,
        /** A static call into a library or the JDK. */
        FOREIGN_PROCEDURE,
        /** Creation of a project object. */
        CREATES,
        /** Assignment to another project object's field. */
        WRITE,
        /** A call the symbol solver couldn't resolve. */
        UNRESOLVED
    }

    public boolean is(Kind other) {
        return kind == other;
    }

    public ObjectNode json() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("from", from);
        node.put("member", member);
        node.put("target", target);
        node.put("name", name);
        node.put("receiver", receiver);
        node.put("receiverSource", receiverSource);
        node.put("usesParameters", usesParameters);
        node.put("foreign", kind == Kind.FOREIGN || kind == Kind.FOREIGN_PROCEDURE);
        node.put("file", file);
        node.put("line", line);
        node.put("expression", expression);
        return node;
    }
}
