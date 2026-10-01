package com.vzurauskas.synergos.web;

import com.sun.net.httpserver.HttpServer;
import com.vzurauskas.synergos.scan.JavaRepo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

/**
 * The local web server behind the viewer. It listens on localhost only and
 * takes the next free port if the requested one is busy.
 */
public final class Viewer {
    private static final int ATTEMPTS = 20;

    private final LiveModel model;
    private final JavaRepo repo;
    private final int port;

    public Viewer(LiveModel model, JavaRepo repo, int port) {
        this.model = model;
        this.repo = repo;
        this.port = port;
    }

    public URI start() {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int candidate = port == 0 ? 0 : port + attempt;
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", candidate), 0);
                server.createContext("/api/model", new Guarded(new ModelRoute(model), false));
                server.createContext("/api/rescan", new Guarded(new RescanRoute(model), true));
                server.createContext("/api/source", new Guarded(new SourceRoute(repo), false));
                server.createContext("/api/cube/text", new Guarded(new CubeTextRoute(repo, model), true));
                server.createContext("/", new Guarded(new StaticRoute(), false));
                server.setExecutor(Executors.newFixedThreadPool(4));
                server.start();
                return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            } catch (BindException e) {
                // Busy; try the next port.
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        throw new IllegalStateException("No free port found from " + port);
    }
}
