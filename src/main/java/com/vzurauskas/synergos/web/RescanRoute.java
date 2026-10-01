package com.vzurauskas.synergos.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * POST /api/rescan: scan the repo again and answer with the fresh model.
 */
final class RescanRoute implements HttpHandler {
    private final LiveModel model;

    RescanRoute(LiveModel model) {
        this.model = model;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Reply reply = new Reply(exchange);
        String json;
        try {
            json = model.rescan();
        } catch (RuntimeException e) {
            reply.send(500, "text/plain; charset=utf-8", "Rescan failed: " + e);
            return;
        }
        reply.send(200, "application/json; charset=utf-8", json);
    }
}
