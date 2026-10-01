package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lines of the society view: messages, procedure calls, creations and
 * messages to foreign objects between pairs of types, plus is-a-kind-of.
 */
final class Edges {
    private final Project project;
    private final Traffic traffic;

    Edges(Project project, Traffic traffic) {
        this.project = project;
        this.traffic = traffic;
    }

    ArrayNode json() {
        Map<String, Map<String, List<Site>>> grouped = new LinkedHashMap<>();
        for (Site site : traffic.all()) {
            String kind = kind(site);
            if (kind.isEmpty()) {
                continue;
            }
            grouped
                .computeIfAbsent(site.from() + '\n' + site.target() + '\n' + kind, key -> new LinkedHashMap<>())
                .computeIfAbsent(site.name(), name -> new ArrayList<>())
                .add(site);
        }
        ArrayNode edges = JsonNodeFactory.instance.arrayNode();
        grouped.forEach((key, byName) -> {
            String[] parts = key.split("\n");
            ObjectNode edge = edges.addObject();
            edge.put("from", parts[0]);
            edge.put("to", parts[1]);
            edge.put("kind", parts[2]);
            ArrayNode messages = edge.putArray("messages");
            byName.forEach((name, sites) -> {
                ObjectNode message = messages.addObject();
                message.put("name", name);
                ArrayNode where = message.putArray("sites");
                sites.forEach(site -> where.addObject()
                    .put("member", site.member())
                    .put("file", site.file())
                    .put("line", site.line()));
            });
        });
        for (JavaType type : project.types()) {
            for (String parent : type.supertypes()) {
                if (project.has(parent)) {
                    edges.addObject().put("from", type.id()).put("to", parent).put("kind", "kindOf").putArray("messages");
                }
            }
        }
        return edges;
    }

    private String kind(Site site) {
        return switch (site.kind()) {
            case MESSAGE -> "message";
            case PROCEDURE -> "procedure";
            case CREATES -> "creates";
            case WRITE -> "write";
            case FOREIGN -> "foreign";
            default -> "";
        };
    }
}
