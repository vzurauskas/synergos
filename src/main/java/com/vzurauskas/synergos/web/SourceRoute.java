package com.vzurauskas.synergos.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.vzurauskas.synergos.scan.JavaRepo;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * GET /api/source?file=relative/path/Foo.java: a source file of the repo.
 */
final class SourceRoute implements HttpHandler {
    private final JavaRepo repo;

    SourceRoute(JavaRepo repo) {
        this.repo = repo;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Optional<String> file = Optional.ofNullable(exchange.getRequestURI().getRawQuery()).stream()
            .flatMap(query -> Arrays.stream(query.split("&")))
            .filter(pair -> pair.startsWith("file="))
            .map(pair -> URLDecoder.decode(pair.substring("file=".length()), StandardCharsets.UTF_8))
            .findFirst();
        Optional<String> text = file.flatMap(repo::source);
        Reply reply = new Reply(exchange);
        if (text.isEmpty()) {
            reply.send(404, "text/plain; charset=utf-8", "No such source file in the repo.");
            return;
        }
        reply.send(200, "text/plain; charset=utf-8", text.get());
    }
}
