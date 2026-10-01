package com.vzurauskas.synergos;

import com.vzurauskas.synergos.running.Registry;
import com.vzurauskas.synergos.running.Running;

import java.io.PrintStream;
import java.time.Instant;
import java.util.List;

/**
 * {@code syg --stop <port | repo | all>}: closes the viewers a person means.
 */
public final class StopViewers implements Action {
    private final Registry registry;
    private final String target;
    private final PrintStream out;

    public StopViewers(Registry registry, String target, PrintStream out) {
        this.registry = registry;
        this.target = target;
        this.out = out;
    }

    @Override
    public void run() {
        List<Running> meant = registry.running().stream().filter(viewer -> viewer.matches(target)).toList();
        if (meant.isEmpty()) {
            out.println("No running viewer matches \"" + target + "\". See syg --list.");
            return;
        }
        Instant now = Instant.now();
        for (Running viewer : meant) {
            List<String> row = viewer.row(now);
            String which = row.get(0) + " (" + row.get(1) + ")";
            out.println(viewer.stop() ? "Stopped " + which : "Couldn't stop " + which + ", process " + row.get(3));
        }
    }
}
