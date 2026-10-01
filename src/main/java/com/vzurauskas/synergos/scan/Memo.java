package com.vzurauskas.synergos.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Works something out once, on first request, and remembers it.
 */
final class Memo<T> {
    private final Supplier<T> origin;
    private final List<T> cache;

    Memo(Supplier<T> origin) {
        this.origin = origin;
        this.cache = new ArrayList<>(1);
    }

    T value() {
        if (cache.isEmpty()) {
            cache.add(origin.get());
        }
        return cache.get(0);
    }
}
