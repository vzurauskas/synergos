package com.vzurauskas.synergos;

import com.vzurauskas.synergos.running.Registration;
import com.vzurauskas.synergos.web.Browser;
import com.vzurauskas.synergos.web.LiveModel;
import com.vzurauskas.synergos.web.Viewer;

import java.io.PrintStream;
import java.net.URI;

public final class Serve implements Action {
    private final LiveModel model;
    private final Viewer viewer;
    private final Browser browser;
    private final Registration registration;
    private final PrintStream out;

    public Serve(LiveModel model, Viewer viewer, Browser browser, Registration registration, PrintStream out) {
        this.model = model;
        this.viewer = viewer;
        this.browser = browser;
        this.registration = registration;
        this.out = out;
    }

    @Override
    public void run() {
        model.json();
        URI uri = viewer.start();
        registration.record(uri);
        out.println("Synergos is running at " + uri + " (Ctrl+C to stop)");
        browser.open(uri);
    }
}
