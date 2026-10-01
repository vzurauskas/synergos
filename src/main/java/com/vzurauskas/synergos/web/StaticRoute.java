package com.vzurauskas.synergos.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * The viewer's own page, scripts and styles, served from the jar.
 */
final class StaticRoute implements HttpHandler {
    private static final Map<String, String> TYPES = Map.of(
        "html", "text/html; charset=utf-8",
        "js", "text/javascript; charset=utf-8",
        "css", "text/css; charset=utf-8",
        "svg", "image/svg+xml",
        "txt", "text/plain; charset=utf-8"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String resource;
        if (path.equals("/") || path.equals("/index.html")) {
            resource = "web/index.html";
        } else if (path.startsWith("/static/") && !path.contains("..")) {
            resource = "web/" + path.substring("/static/".length());
        } else {
            new Reply(exchange).send(404, "text/plain; charset=utf-8", "Not found.");
            return;
        }
        try (InputStream in = StaticRoute.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                new Reply(exchange).send(404, "text/plain; charset=utf-8", "Not found.");
                return;
            }
            String extension = resource.substring(resource.lastIndexOf('.') + 1);
            new Reply(exchange).send(200, TYPES.getOrDefault(extension, "application/octet-stream"), in.readAllBytes());
        }
    }
}
