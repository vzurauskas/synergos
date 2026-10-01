package com.vzurauskas.synergos.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The answer to one HTTP request.
 */
final class Reply {
    private final HttpExchange exchange;

    Reply(HttpExchange exchange) {
        this.exchange = exchange;
    }

    void send(int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    void send(int status, String contentType, String body) throws IOException {
        send(status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }
}
