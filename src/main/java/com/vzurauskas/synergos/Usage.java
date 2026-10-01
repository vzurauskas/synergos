package com.vzurauskas.synergos;

import java.io.PrintStream;

public final class Usage implements Action {
    private final PrintStream out;
    private final String problem;

    public Usage(PrintStream out, String problem) {
        this.out = out;
        this.problem = problem;
    }

    @Override
    public void run() {
        if (!problem.isEmpty()) {
            out.println(problem);
            out.println();
        }
        out.println("Usage: syg <java-repo> [--port N] [--no-open] [--dump model.json]");
        out.println("       syg --list");
        out.println("       syg --stop <port | repo | all>");
        out.println();
        out.println("  <java-repo>        root of a Maven project to show as a society of objects");
        out.println("  --port N           port for the viewer (default 7070, next free one if taken)");
        out.println("  --no-open          don't open the browser");
        out.println("  --dump model.json  scan only and write the model to a file");
        out.println("  --list             show the viewers that are running");
        out.println("  --stop <which>     close viewers: by port, by repo (path or folder name), or all");
    }
}
