package com.vzurauskas.synergos.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A route that only answers the viewer's own page. Every request must be
 * addressed to 127.0.0.1 or localhost, which stops DNS-rebinding tricks. A
 * request that changes something must also be a JSON POST from the viewer's
 * own origin, so no other web page open in the browser can make it.
 */
final class Guarded implements HttpHandler {
    private static final String TEXT = "text/plain; charset=utf-8";

    private final HttpHandler origin;
    private final boolean changes;

    Guarded(HttpHandler origin, boolean changes) {
        this.origin = origin;
        this.changes = changes;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        int port = exchange.getLocalAddress().getPort();
        Reply reply = new Reply(exchange);
        String host = header(exchange, "Host").toLowerCase(Locale.ROOT);
        if (!Set.of("127.0.0.1:" + port, "localhost:" + port).contains(host)) {
            reply.send(403, TEXT, "Refused: the viewer only answers requests addressed to 127.0.0.1 or localhost.");
            return;
        }
        if (changes) {
            if (!exchange.getRequestMethod().equals("POST")) {
                reply.send(405, TEXT, "Use POST.");
                return;
            }
            if (!Set.of("http://127.0.0.1:" + port, "http://localhost:" + port).contains(header(exchange, "Origin"))) {
                reply.send(403, TEXT, "Refused: changes are only accepted from the viewer's own page.");
                return;
            }
            if (!header(exchange, "Content-Type").startsWith("application/json")) {
                reply.send(415, TEXT, "Send JSON.");
                return;
            }
        }
        origin.handle(exchange);
    }

    private static String header(HttpExchange exchange, String name) {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst(name)).orElse("");
    }
}
