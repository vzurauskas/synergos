package com.vzurauskas.synergos.web;

import java.io.IOException;
import java.net.URI;

/**
 * The user's web browser, if they want it opened.
 */
public final class Browser {
    private final boolean enabled;

    public Browser(boolean enabled) {
        this.enabled = enabled;
    }

    public void open(URI uri) {
        if (!enabled) {
            return;
        }
        String command = System.getProperty("os.name").toLowerCase().contains("mac") ? "open" : "xdg-open";
        try {
            new ProcessBuilder(command, uri.toString()).inheritIO().start();
        } catch (IOException e) {
            System.err.println("Couldn't open a browser; visit " + uri + " yourself.");
        }
    }
}
