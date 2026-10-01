package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.javaparser.Range;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Javadoc comment of one type, rewritten around new cube text. It owns the
 * description and the {@code @responsibility} and {@code @stereotype} tags;
 * every other block tag keeps its lines and its order, and the rest of the
 * source file stays exactly as it was.
 */
final class CubeComment {
    private static final int WIDTH = 100;
    private static final Set<String> OWNED = Set.of("responsibility", "stereotype");

    private final TypeDeclaration<?> type;
    private final JsonNode text;

    CubeComment(TypeDeclaration<?> type, JsonNode text) {
        this.type = type;
        this.text = text;
    }

    String spliced(String source) {
        String eol = source.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(source.split("\r?\n", -1)));
        int declaration = type.getBegin()
            .orElseThrow(() -> new Refusal(422, "The scanner can't tell where " + type.getNameAsString() + " starts."))
            .line;
        String indent = lines.get(declaration - 1).replaceAll("^(\\s*).*$", "$1");
        Optional<JavadocComment> existing = type.getJavadocComment();
        List<String> rendered = rendered(indent, existing.map(comment -> preserved(comment, indent)).orElse(List.of()));
        if (existing.isPresent()) {
            Range range = existing.get().getRange()
                .orElseThrow(() -> new Refusal(422, "The scanner can't tell where the Javadoc of " + type.getNameAsString() + " is."));
            String before = lines.get(range.begin.line - 1).substring(0, range.begin.column - 1);
            String after = lines.get(range.end.line - 1).substring(range.end.column);
            if (!before.isBlank() || !after.isBlank()) {
                throw new Refusal(422, "The Javadoc of " + type.getNameAsString()
                    + " shares a line with other code. Please move it onto lines of its own first.");
            }
            for (int line = range.end.line; line >= range.begin.line; line--) {
                lines.remove(line - 1);
            }
            lines.addAll(range.begin.line - 1, rendered);
        } else {
            lines.addAll(declaration - 1, rendered);
        }
        return String.join(eol, lines);
    }

    private List<String> rendered(String indent, List<String> preserved) {
        String description = field("description");
        String stereotype = CubeText.normalized(field("stereotype"));
        List<String> responsibilities = new ArrayList<>();
        text.path("responsibilities").forEach(node -> {
            String responsibility = CubeText.normalized(node.isTextual() ? node.asText() : "");
            if (!responsibility.isEmpty()) {
                responsibilities.add(responsibility);
            }
        });
        boolean tags = !responsibilities.isEmpty() || !stereotype.isEmpty() || !preserved.isEmpty();
        if (description.isBlank() && !tags) {
            return List.of();
        }
        int width = WIDTH - indent.length() - " * ".length();
        List<String> out = new ArrayList<>();
        out.add(indent + "/**");
        boolean first = true;
        for (String paragraph : description.strip().split("\\n\\s*\\n")) {
            String normalized = CubeText.normalized(paragraph);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!first) {
                out.add(indent + " *");
            }
            first = false;
            wrapped(escaped(normalized, true), width, width).forEach(line -> out.add(indent + " * " + line));
        }
        if (!first && tags) {
            out.add(indent + " *");
        }
        for (String responsibility : responsibilities) {
            List<String> wrapped = wrapped("@responsibility " + escaped(responsibility, false), width, width - 4);
            out.add(indent + " * " + wrapped.get(0));
            wrapped.stream().skip(1).forEach(line -> out.add(indent + " *     " + line));
        }
        if (!stereotype.isEmpty()) {
            out.add(indent + " * @stereotype " + escaped(stereotype, false));
        }
        out.addAll(preserved);
        out.add(indent + " */");
        return out;
    }

    /**
     * The block tags this comment doesn't own, e.g. {@code @param <T>} or
     * {@code @see}, with their lines as they were.
     */
    private static List<String> preserved(JavadocComment comment, String indent) {
        List<String> kept = new ArrayList<>();
        boolean tags = false;
        boolean keep = false;
        for (String raw : comment.getContent().split("\r?\n", -1)) {
            String content = raw.replaceFirst("^\\s*\\*?", "");
            content = content.startsWith(" ") ? content.substring(1) : content;
            String trimmed = content.strip();
            if (trimmed.startsWith("@")) {
                tags = true;
                keep = !OWNED.contains(trimmed.substring(1).split("[\\s{]", 2)[0]);
            }
            if (tags && keep) {
                kept.add(content.isBlank() ? indent + " *" : indent + " * " + content.stripTrailing());
            }
        }
        while (!kept.isEmpty() && kept.get(kept.size() - 1).equals(indent + " *")) {
            kept.remove(kept.size() - 1);
        }
        return kept;
    }

    /**
     * Lines of at most the given widths, never breaking inside an inline tag
     * such as {@code {@link #drive(TransactionsFile)}} and never starting a
     * line with '@', which Javadoc would read as a new block tag.
     */
    private static List<String> wrapped(String text, int firstWidth, int restWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String token : tokens(text)) {
            int width = lines.isEmpty() ? firstWidth : restWidth;
            if (line.length() > 0 && line.length() + 1 + token.length() > width && !token.startsWith("@")) {
                lines.add(line.toString());
                line = new StringBuilder(token);
            } else {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(token);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char ch : text.toCharArray()) {
            if (Character.isWhitespace(ch) && depth == 0) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
                continue;
            }
            current.append(ch);
            if (ch == '{') {
                depth++;
            } else if (ch == '}' && depth > 0) {
                depth--;
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String escaped(String text, boolean description) {
        String safe = text.replace("*/", "*&#47;");
        return description && safe.startsWith("@") ? "{@literal @}" + safe.substring(1) : safe;
    }

    private String field(String name) {
        JsonNode value = text.path(name);
        return value.isTextual() ? value.asText() : "";
    }
}
