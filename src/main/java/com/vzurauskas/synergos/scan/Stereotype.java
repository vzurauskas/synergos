package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A guess at the object's Wirfs-Brock role stereotype, with the reason for
 * the guess. The first rule that matches wins. Controllers are never guessed;
 * the puppet-master flag covers them.
 */
final class Stereotype {
    private static final int COORDINATION = 3;
    /**
     * Channels to the world outside the program: streams, files, sockets,
     * databases. Plain values such as URI or Path don't count.
     */
    private static final Pattern BOUNDARY = Pattern.compile(
        "java\\.io\\.\\w*(Stream|Reader|Writer)|java\\.io\\.Console|java\\.nio\\.channels\\..*"
            + "|java\\.nio\\.file\\.Files|java\\.net\\.(Socket|ServerSocket|URLConnection|HttpURLConnection)"
            + "|java\\.net\\.http\\.HttpClient|java\\.sql\\.(Connection|Statement|PreparedStatement|ResultSet)"
            + "|javax\\.sql\\.DataSource|org\\.jooq\\.DSLContext"
    );

    private final JavaType type;
    private final Project project;
    private final Traffic traffic;

    Stereotype(JavaType type, Project project, Traffic traffic) {
        this.type = type;
        this.project = project;
        this.traffic = traffic;
    }

    JsonNode json() {
        if (type.isInterface()) {
            return JsonNodeFactory.instance.nullNode();
        }
        List<JavaField> fields = type.fields().stream().filter(field -> !field.constant()).toList();
        for (JavaField field : fields) {
            List<String> held = field.collectionOf(project);
            if (!held.isEmpty()) {
                return guess("structurer", "holds a collection of " + shortName(held.get(0)));
            }
        }
        Set<String> collaborators = traffic.targets(type.id(), Site.Kind.MESSAGE);
        if (collaborators.size() >= COORDINATION) {
            return guess("coordinator", "sends messages to " + collaborators.size() + " project types");
        }
        Optional<String> boundary = fields.stream()
            .flatMap(field -> field.qualified().stream())
            .filter(this::boundary)
            .findFirst()
            .or(() -> traffic.targets(type.id(), Site.Kind.FOREIGN).stream().filter(this::boundary).findFirst())
            .or(() -> traffic.targets(type.id(), Site.Kind.FOREIGN_PROCEDURE).stream().filter(this::boundary).findFirst());
        if (boundary.isPresent()) {
            return guess("interfacer", "uses " + shortName(boundary.get()));
        }
        if (!fields.isEmpty() && fields.stream().allMatch(JavaField::value) && collaborators.isEmpty()) {
            return guess("information holder", "holds only values and sends no messages to project objects");
        }
        return guess("service provider", "none of the other rules matched");
    }

    private boolean boundary(String qualified) {
        return BOUNDARY.matcher(qualified).matches();
    }

    private ObjectNode guess(String value, String why) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("value", value);
        node.put("state", "guessed");
        node.put("why", why);
        return node;
    }

    private String shortName(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }
}
