package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * All types declared in the scanned sources, and how they are related by
 * is-a-kind-of. Anything else (JDK, libraries) is foreign.
 */
public final class Project {
    private final Map<String, JavaType> types;

    public Project(List<JavaType> types) {
        this.types = new LinkedHashMap<>();
        types.forEach(type -> this.types.putIfAbsent(type.id(), type));
    }

    public boolean has(String id) {
        return types.containsKey(id);
    }

    public Optional<JavaType> type(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public Collection<JavaType> types() {
        return types.values();
    }

    /**
     * Adds how many cubes there are, in main code and in tests, to the header numbers.
     */
    void describe(ObjectNode stats) {
        long tests = types.values().stream().filter(JavaType::test).count();
        stats.put("cubes", types.size() - tests);
        stats.put("testCubes", tests);
        stats.put("written", types.values().stream().filter(type -> !type.test() && type.text().written()).count());
    }

    /**
     * Project types this one is a kind of, directly or further up.
     */
    public Set<String> ancestors(String id) {
        Set<String> found = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>(List.of(id));
        while (!todo.isEmpty()) {
            JavaType type = types.get(todo.pop());
            if (type == null) {
                continue;
            }
            for (String parent : type.supertypes()) {
                if (has(parent) && found.add(parent)) {
                    todo.push(parent);
                }
            }
        }
        return found;
    }

    /**
     * Project types that are a kind of this one, directly or further down.
     */
    public Set<String> descendants(String id) {
        Set<String> found = new LinkedHashSet<>();
        for (JavaType type : types.values()) {
            if (ancestors(type.id()).contains(id)) {
                found.add(type.id());
            }
        }
        return found;
    }

    /**
     * Project types that directly extend or implement this one.
     */
    public List<String> implementers(String id) {
        return types.values().stream()
            .filter(type -> type.supertypes().contains(id))
            .map(JavaType::id)
            .toList();
    }
}
