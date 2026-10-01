package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * A change to the text on one cube, as the viewer asks for it: the file and
 * type, the text the editor saw, and the text it wants. Applying it rewrites
 * that type's Javadoc, unless the Javadoc on disk is no longer what the editor
 * saw.
 */
public final class CubeTextEdit {
    private final JavaRepo repo;
    private final JsonNode request;

    public CubeTextEdit(JavaRepo repo, JsonNode request) {
        this.repo = repo;
        this.request = request;
    }

    public void apply() {
        String file = request.path("file").asText("");
        String id = request.path("id").asText("");
        if (!request.path("before").isObject() || !request.path("after").isObject()) {
            throw new Refusal(400, "Expected the text the editor saw (before) and the text it wants (after).");
        }
        String source = repo.source(file)
            .orElseThrow(() -> new Refusal(404, "No such source file in the repo: " + file));
        ParseResult<CompilationUnit> parsed = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
        ).parse(source);
        CompilationUnit unit = parsed.getResult()
            .filter(result -> parsed.isSuccessful())
            .orElseThrow(() -> new Refusal(422, file + " doesn't parse at the moment. Fix it first."));
        TypeDeclaration<?> type = unit.findAll(TypeDeclaration.class).stream()
            .map(declaration -> (TypeDeclaration<?>) declaration)
            .filter(declaration -> declaration.getFullyQualifiedName().map(id::equals).orElse(false))
            .findFirst()
            .orElseThrow(() -> new Refusal(404, "No type " + id + " in " + file + "."));
        if (!new CubeText(type.getJavadoc()).matches(request.path("before"))) {
            throw new Refusal(409, "The Javadoc of " + type.getNameAsString()
                + " changed on disk since the last scan, so saving now could overwrite that change.");
        }
        repo.write(file, new CubeComment(type, request.path("after")).spliced(source));
    }
}
