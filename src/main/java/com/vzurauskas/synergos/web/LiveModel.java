package com.vzurauskas.synergos.web;

import com.vzurauskas.synergos.scan.Flag;
import com.vzurauskas.synergos.scan.JavaRepo;
import com.vzurauskas.synergos.scan.Society;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The model the viewer shows. It scans the repo on first request and again
 * whenever asked to rescan.
 */
public final class LiveModel {
    private final JavaRepo repo;
    private final Flag flag;
    private final AtomicReference<String> json;

    public LiveModel(JavaRepo repo, Flag flag) {
        this.repo = repo;
        this.flag = flag;
        this.json = new AtomicReference<>("");
    }

    public String json() {
        if (json.get().isEmpty()) {
            return rescan();
        }
        return json.get();
    }

    public synchronized String rescan() {
        long start = System.nanoTime();
        String fresh = new Society(repo, flag).json();
        json.set(fresh);
        System.err.printf("Scanned %s in %.1fs%n", repo.path(), (System.nanoTime() - start) / 1e9);
        return fresh;
    }
}
