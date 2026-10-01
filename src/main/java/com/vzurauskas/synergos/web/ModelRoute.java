package com.vzurauskas.synergos.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * GET /api/model: the current model.
 */
final class ModelRoute implements HttpHandler {
    private final LiveModel model;

    ModelRoute(LiveModel model) {
        this.model = model;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        new Reply(exchange).send(200, "application/json; charset=utf-8", model.json());
    }
}
