package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.OptionalInt;

/**
 * A {@code {@link ...}} inside a responsibility. {@code {@link Stamp}} names a
 * collaborator; {@code {@link #drive(TransactionsFile)}} names the message
 * that carries the responsibility out; {@code {@link Stamp#stamp}} names a
 * collaborator and one of its messages, and counts as the collaborator.
 */
final class Link {
    private final String reference;

    Link(String content) {
        this.reference = referenceOf(content.strip());
    }

    boolean message() {
        return reference.startsWith("#");
    }

    /**
     * The type part: "Stamp" for both {@code Stamp} and {@code Stamp#stamp}.
     */
    String typeName() {
        int hash = reference.indexOf('#');
        return hash < 0 ? reference : reference.substring(0, hash);
    }

    /**
     * The message part, without the '#': "drive(TransactionsFile)".
     */
    String messageReference() {
        return reference.substring(reference.indexOf('#') + 1);
    }

    boolean matches(JavaMethod method) {
        if (!message()) {
            return false;
        }
        String signature = messageReference();
        int paren = signature.indexOf('(');
        String name = paren < 0 ? signature : signature.substring(0, paren);
        OptionalInt arity = arity(signature);
        return method.name().equals(name) && (arity.isEmpty() || arity.getAsInt() == method.arity());
    }

    ObjectNode json() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("kind", message() ? "message" : "type");
        node.put("reference", reference);
        node.put("name", message() ? messageReference() : typeName());
        return node;
    }

    private static OptionalInt arity(String signature) {
        int open = signature.indexOf('(');
        int close = signature.lastIndexOf(')');
        if (open < 0 || close < open) {
            return OptionalInt.empty();
        }
        String parameters = signature.substring(open + 1, close).strip();
        if (parameters.isEmpty()) {
            return OptionalInt.of(0);
        }
        int count = 1;
        int depth = 0;
        for (char ch : parameters.toCharArray()) {
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                count++;
            }
        }
        return OptionalInt.of(count);
    }

    /**
     * The reference is everything up to the label: up to the closing paren of
     * a signature, or up to the first space.
     */
    private static String referenceOf(String content) {
        int paren = content.indexOf('(');
        int space = content.indexOf(' ');
        if (paren >= 0 && (space < 0 || paren < space)) {
            int close = content.indexOf(')', paren);
            return close < 0 ? content : content.substring(0, close + 1).replaceAll("\\s+", " ");
        }
        return space < 0 ? content : content.substring(0, space);
    }
}
