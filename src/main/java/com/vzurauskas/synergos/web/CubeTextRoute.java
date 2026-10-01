package com.vzurauskas.synergos.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.vzurauskas.synergos.scan.CubeTextEdit;
import com.vzurauskas.synergos.scan.JavaRepo;
import com.vzurauskas.synergos.scan.Refusal;

import java.io.IOException;

/**
 * POST /api/cube/text: write the text of one cube (description, stereotype,
 * responsibilities) into its type's Javadoc, then answer with the rescanned
 * model.
 */
final class CubeTextRoute implements HttpHandler {
    private static final String TEXT = "text/plain; charset=utf-8";

    private final JavaRepo repo;
    private final LiveModel model;

    CubeTextRoute(JavaRepo repo, LiveModel model) {
        this.repo = repo;
        this.model = model;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Reply reply = new Reply(exchange);
        try {
            new CubeTextEdit(repo, new ObjectMapper().readTree(exchange.getRequestBody())).apply();
        } catch (JsonProcessingException e) {
            reply.send(400, TEXT, "The request isn't JSON.");
            return;
        } catch (Refusal refusal) {
            reply.send(refusal.status(), TEXT, refusal.getMessage());
            return;
        }
        reply.send(200, "application/json; charset=utf-8", model.rescan());
    }
}
