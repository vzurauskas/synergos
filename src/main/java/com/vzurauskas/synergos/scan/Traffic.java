package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * All the reaching-out between objects in the project: who sends which
 * messages to whom, who calls procedures, who creates what.
 */
public final class Traffic {
    private static final Set<Site.Kind> CALLS = EnumSet.of(
        Site.Kind.MESSAGE, Site.Kind.PROCEDURE, Site.Kind.SELF,
        Site.Kind.FOREIGN, Site.Kind.FOREIGN_PROCEDURE, Site.Kind.UNRESOLVED
    );
    private static final Set<Site.Kind> REACH = EnumSet.of(
        Site.Kind.MESSAGE, Site.Kind.PROCEDURE, Site.Kind.FOREIGN,
        Site.Kind.FOREIGN_PROCEDURE, Site.Kind.CREATES, Site.Kind.WRITE
    );

    private final Project project;
    private final Memo<List<Site>> sites;

    public Traffic(Project project) {
        this.project = project;
        this.sites = new Memo<>(
            () -> project.types().stream()
                .flatMap(type -> new Sites(type, project).all().stream())
                .toList()
        );
    }

    public List<Site> all() {
        return sites.value();
    }

    public List<Site> from(String id) {
        return all().stream().filter(site -> site.from().equals(id)).toList();
    }

    /**
     * Distinct targets this type reaches in the given way.
     */
    public Set<String> targets(String id, Site.Kind kind) {
        Set<String> targets = new LinkedHashSet<>();
        for (Site site : from(id)) {
            if (site.is(kind)) {
                targets.add(site.target());
            }
        }
        return targets;
    }

    /**
     * The types this one reaches in the given way, each with the names used:
     * e.g. its collaborators and the messages it sends them.
     */
    public ArrayNode grouped(String id, Site.Kind kind) {
        Map<String, Set<String>> byTarget = new LinkedHashMap<>();
        for (Site site : from(id)) {
            if (site.is(kind)) {
                byTarget.computeIfAbsent(site.target(), target -> new TreeSet<>()).add(site.name());
            }
        }
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        byTarget.forEach((target, names) -> {
            ObjectNode entry = array.addObject();
            entry.put("id", target);
            ArrayNode messages = entry.putArray("messages");
            names.forEach(messages::add);
        });
        return array;
    }

    /**
     * Whether one type reaches another at all (sends it a message, calls it,
     * creates it or writes into it), counting kinds of the target as the target.
     */
    public boolean reaches(String from, String target) {
        Set<String> related = new LinkedHashSet<>(List.of(target));
        related.addAll(project.ancestors(target));
        related.addAll(project.descendants(target));
        return from(from).stream()
            .filter(site -> REACH.contains(site.kind()))
            .anyMatch(site -> related.contains(site.target()));
    }

    /**
     * Questions a member asks other objects: calls with an explicit receiver
     * whose answers it uses.
     */
    public List<Site> asks(String id, int memberLine) {
        return from(id).stream()
            .filter(site -> site.memberLine() == memberLine)
            .filter(site -> site.used())
            .filter(site -> site.is(Site.Kind.MESSAGE) || site.is(Site.Kind.FOREIGN))
            .filter(site -> !site.receiverSource().equals("self"))
            .toList();
    }

    /**
     * Where the given message of the given type is sent from. A message can
     * arrive directly, or through a supertype that declares it, or through a
     * subtype that inherits it; the site's target tells which.
     */
    public List<Site> senders(JavaType type, JavaMethod method) {
        Set<String> targets = new LinkedHashSet<>();
        targets.add(type.id());
        targets.addAll(project.ancestors(type.id()));
        targets.addAll(project.descendants(type.id()));
        return all().stream()
            .filter(site -> site.is(Site.Kind.MESSAGE) || site.is(Site.Kind.PROCEDURE))
            .filter(site -> targets.contains(site.target()))
            .filter(site -> method.is(site.name(), site.arity()))
            .filter(site -> !site.from().equals(type.id()))
            .toList();
    }

    /**
     * Distinct types that send this type any of its own messages. Tests don't
     * count for types in main code.
     */
    public Set<String> senders(JavaType type) {
        Set<String> senders = new LinkedHashSet<>();
        for (JavaMethod method : type.methods()) {
            for (Site site : senders(type, method)) {
                if (type.test() || project.type(site.from()).map(sender -> !sender.test()).orElse(true)) {
                    senders.add(site.from());
                }
            }
        }
        return senders;
    }

    /**
     * Adds what the traffic knows about itself to the header numbers.
     */
    public void describe(ObjectNode stats) {
        long calls = all().stream().filter(site -> CALLS.contains(site.kind())).count();
        long unresolved = all().stream().filter(site -> site.is(Site.Kind.UNRESOLVED)).count();
        stats.put("messages", all().stream().filter(site -> site.is(Site.Kind.MESSAGE)).count());
        stats.put("calls", calls);
        stats.put("resolved", calls == 0 ? 1.0 : (double) (calls - unresolved) / calls);
    }

    /**
     * Library and JDK types that project objects send messages to.
     */
    public ArrayNode foreign() {
        Set<String> ids = new TreeSet<>();
        all().stream().filter(site -> site.is(Site.Kind.FOREIGN)).forEach(site -> ids.add(site.target()));
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        ids.forEach(array::add);
        return array;
    }

    /**
     * Calls the scanner couldn't resolve, so nothing is hidden silently.
     */
    public ArrayNode unresolved(int limit) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        all().stream().filter(site -> site.is(Site.Kind.UNRESOLVED)).limit(limit).forEach(site -> array.add(site.json()));
        return array;
    }
}
