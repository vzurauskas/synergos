package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a person wrote about a type in its Javadoc: the description, the
 * stereotype and the responsibilities. Everything else on a cube comes from
 * the code; this is the part someone authored.
 */
public final class CubeText {
    private static final Set<String> STEREOTYPES = Set.of(
        "information holder", "structurer", "service provider", "coordinator", "controller", "interfacer"
    );

    private final Optional<Javadoc> javadoc;

    public CubeText(Optional<Javadoc> javadoc) {
        this.javadoc = javadoc;
    }

    /**
     * The description, with paragraphs separated by a blank line and the
     * Javadoc's own line breaks joined up.
     */
    public Optional<String> description() {
        return javadoc
            .map(doc -> Arrays.stream(doc.getDescription().toText().strip().split("\\n\\s*\\n"))
                .map(CubeText::normalized)
                .filter(paragraph -> !paragraph.isEmpty())
                .collect(Collectors.joining("\n\n")))
            .filter(text -> !text.isEmpty());
    }

    public Optional<String> stereotype() {
        return tags("stereotype").stream()
            .map(tag -> normalized(tag.getContent().toText()))
            .filter(value -> !value.isEmpty())
            .findFirst();
    }

    public List<Responsibility> responsibilities() {
        return tags("responsibility").stream()
            .map(tag -> new Responsibility(tag.getContent()))
            .filter(responsibility -> !responsibility.text().isEmpty())
            .toList();
    }

    public boolean written() {
        return !responsibilities().isEmpty();
    }

    /**
     * Whether this is the text an editor saw before changing it. Whitespace
     * doesn't count, since the writer wraps lines as it likes.
     */
    public boolean matches(JsonNode seen) {
        List<String> responsibilities = new ArrayList<>();
        seen.path("responsibilities").forEach(text -> responsibilities.add(normalized(text.asText())));
        return normalized(description().orElse("")).equals(normalized(text(seen, "description")))
            && normalized(stereotype().orElse("")).equals(normalized(text(seen, "stereotype")))
            && responsibilities().stream().map(Responsibility::text).toList().equals(
                responsibilities.stream().filter(text -> !text.isEmpty()).toList()
            );
    }

    /**
     * The authored stereotype, if there is one, and whether it's one of the six.
     */
    Optional<ObjectNode> stereotypeJson() {
        return stereotype().map(value -> {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            String canonical = canonical(value);
            boolean valid = STEREOTYPES.contains(canonical);
            node.put("value", valid ? canonical : value);
            node.put("text", value);
            node.put("state", "authored");
            node.put("valid", valid);
            return node;
        });
    }

    /**
     * The owner's own responsibilities, then those it inherits from the roles
     * it plays and the classes it extends.
     */
    ArrayNode responsibilitiesJson(JavaType owner, Project project) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (Responsibility responsibility : responsibilities()) {
            array.add(responsibility.json(owner, project).put("state", "authored"));
        }
        for (String ancestor : project.ancestors(owner.id())) {
            project.type(ancestor).ifPresent(parent -> parent.text().responsibilities().forEach(
                responsibility -> array.add(
                    responsibility.json(parent, project).put("state", "inherited").put("from", parent.id())
                )
            ));
        }
        return array;
    }

    /**
     * Contradictions between this text and the owner's code, as amber findings.
     */
    public List<Finding> contradictions(JavaType owner, Project project, Traffic traffic) {
        List<Finding> findings = new ArrayList<>();
        int line = owner.javadocLine();
        List<Responsibility> own = responsibilities();
        for (Responsibility responsibility : own) {
            for (String reason : responsibility.contradictions(owner, project, traffic)) {
                findings.add(contradiction(responsibility.text(), line, reason));
            }
        }
        if (own.stream().anyMatch(Responsibility::linksMessages)) {
            List<Responsibility> all = new ArrayList<>(own);
            for (String ancestor : project.ancestors(owner.id())) {
                project.type(ancestor).ifPresent(parent -> all.addAll(parent.text().responsibilities()));
            }
            for (JavaMethod method : owner.methods()) {
                boolean message = !method.isPrivate() && !method.isStatic() && !method.isIdentity();
                if (message && all.stream().noneMatch(responsibility -> responsibility.links(method))) {
                    findings.add(contradiction(method.signature(), method.line(),
                        method.signature() + " serves no responsibility: none of " + owner.simpleName()
                            + "'s responsibilities links it."));
                }
            }
        }
        stereotype().filter(value -> !STEREOTYPES.contains(canonical(value))).ifPresent(value -> findings.add(
            contradiction("@stereotype", line, "\"" + value + "\" isn't one of the six stereotypes: information holder, "
                + "structurer, service provider, coordinator, controller, interfacer.")
        ));
        return findings;
    }

    private List<JavadocBlockTag> tags(String name) {
        return javadoc.map(doc -> doc.getBlockTags().stream().filter(tag -> tag.getTagName().equals(name)).toList())
            .orElse(List.of());
    }

    private static Finding contradiction(String where, int line, String why) {
        return new Finding(
            "contradiction", "amber", where, line, why,
            "The cube's text and the code must agree. Only contradictions the code can prove are shown."
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private static String canonical(String stereotype) {
        return stereotype.toLowerCase().replaceAll("[-_\\s]+", " ").strip();
    }

    static String normalized(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }
}
