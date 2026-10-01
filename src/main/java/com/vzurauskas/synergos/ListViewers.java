package com.vzurauskas.synergos;

import com.vzurauskas.synergos.running.Registry;
import com.vzurauskas.synergos.running.Running;

import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code syg --list}: every viewer that is running, where to find it and what
 * it shows.
 */
public final class ListViewers implements Action {
    private final Registry registry;
    private final PrintStream out;

    public ListViewers(Registry registry, PrintStream out) {
        this.registry = registry;
        this.out = out;
    }

    @Override
    public void run() {
        List<Running> running = registry.running();
        if (running.isEmpty()) {
            out.println("No viewers are running.");
            return;
        }
        Instant now = Instant.now();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("URL", "REPO", "UP", "PID"));
        running.forEach(viewer -> rows.add(viewer.row(now)));
        int[] widths = new int[rows.get(0).size()];
        for (List<String> row : rows) {
            for (int column = 0; column < row.size(); column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }
        for (List<String> row : rows) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < row.size(); column++) {
                String cell = row.get(column);
                line.append(cell).append(" ".repeat(column < row.size() - 1 ? widths[column] - cell.length() + 2 : 0));
            }
            out.println(line.toString().stripTrailing());
        }
    }
}
