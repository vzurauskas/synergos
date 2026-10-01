package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.javadoc.description.JavadocInlineTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One {@code @responsibility} written in a type's Javadoc: a service the
 * object offers, with links to its collaborators and to the messages that
 * carry it out.
 */
public final class Responsibility {
    private final JavadocDescription content;

    Responsibility(JavadocDescription content) {
        this.content = content;
    }

    public String text() {
        return content.toText().strip().replaceAll("\\s+", " ");
    }

    boolean linksMessages() {
        return links().stream().anyMatch(Link::message);
    }

    boolean links(JavaMethod method) {
        return links().stream().anyMatch(link -> link.matches(method));
    }

    /**
     * The responsibility as the viewer shows it, with each type link resolved
     * in the context of the type whose Javadoc it was written in.
     */
    ObjectNode json(JavaType context, Project project) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("text", text());
        ArrayNode links = node.putArray("links");
        for (Link link : links()) {
            ObjectNode entry = link.json();
            if (!link.message()) {
                context.resolve(link.typeName(), project).ifPresent(id -> entry.put("target", id));
            }
            links.add(entry);
        }
        return node;
    }

    /**
     * What the code contradicts in this responsibility, as reasons. Only
     * things the code can prove count.
     */
    List<String> contradictions(JavaType owner, Project project, Traffic traffic) {
        List<String> reasons = new ArrayList<>();
        for (Link link : links()) {
            if (link.message()) {
                if (!owner.respondsTo(link, project)) {
                    reasons.add("Links #" + link.messageReference() + ", but " + owner.simpleName()
                        + " doesn't respond to that message.");
                }
                continue;
            }
            Optional<String> target = owner.resolve(link.typeName(), project);
            if (target.isEmpty()) {
                reasons.add("Links " + link.typeName() + ", which the scanner can't find.");
            } else if (!owner.isInterface() && !target.get().equals(owner.id())
                && !traffic.reaches(owner.id(), target.get())) {
                reasons.add("Links " + link.typeName() + ", but " + owner.simpleName()
                    + " never sends it a message, calls it or creates it.");
            }
        }
        return reasons;
    }

    private List<Link> links() {
        return content.getElements().stream()
            .filter(element -> element instanceof JavadocInlineTag tag
                && (tag.getType() == JavadocInlineTag.Type.LINK || tag.getType() == JavadocInlineTag.Type.LINKPLAIN))
            .map(element -> new Link(((JavadocInlineTag) element).getContent()))
            .toList();
    }
}
