package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A method declared by a type: a message its objects respond to.
 */
public final class JavaMethod {
    private static final Set<String> IDENTITY = Set.of("toString", "equals", "hashCode");

    private final MethodDeclaration decl;
    private final String owner;

    JavaMethod(MethodDeclaration decl, String owner) {
        this.decl = decl;
        this.owner = owner;
    }

    public String name() {
        return decl.getNameAsString();
    }

    public int arity() {
        return decl.getParameters().size();
    }

    public boolean is(String name, int arity) {
        return name().equals(name) && arity() == arity;
    }

    public int line() {
        return decl.getName().getBegin().map(position -> position.line).orElse(1);
    }

    public String signature() {
        return name() + decl.getParameters().stream()
            .map(parameter -> parameter.getType().asString() + (parameter.isVarArgs() ? "..." : ""))
            .collect(Collectors.joining(", ", "(", ")"));
    }

    public boolean isPrivate() {
        return decl.isPrivate();
    }

    public boolean isStatic() {
        return decl.isStatic();
    }

    public boolean isMain() {
        return isStatic() && name().equals("main") && arity() == 1;
    }

    public boolean isIdentity() {
        return IDENTITY.contains(name());
    }

    public boolean annotatedOverride() {
        return decl.isAnnotationPresent("Override");
    }

    /**
     * Whether the whole body is a single throw of the given exception, e.g.
     * {@code throw new UnsupportedOperationException();}.
     */
    public boolean onlyThrows(String exception) {
        Optional<BlockStmt> body = decl.getBody();
        return body.isPresent()
            && body.get().getStatements().size() == 1
            && body.get().getStatement(0) instanceof ThrowStmt thrown
            && thrown.getExpression() instanceof ObjectCreationExpr creation
            && creation.getType().getNameAsString().equals(exception);
    }

    /**
     * What this method needs to know to do its job, and where each piece comes
     * from: a field, a parameter, or a collaborator it asks.
     */
    public ArrayNode knowledge(Traffic traffic, Project project, Set<String> fields) {
        ArrayNode items = JsonNodeFactory.instance.arrayNode();
        for (Parameter parameter : decl.getParameters()) {
            var item = items.addObject();
            item.put("kind", "param");
            item.put("name", parameter.getNameAsString());
            item.put("type", parameter.getType().asString());
            projectType(parameter, project).ifPresent(id -> item.put("typeId", id));
        }
        for (String field : fieldsUsed(fields)) {
            var item = items.addObject();
            item.put("kind", "field");
            item.put("name", field);
        }
        for (Site ask : traffic.asks(owner, line())) {
            items.add(ask.json().put("kind", "asks"));
        }
        return items;
    }

    private Set<String> fieldsUsed(Set<String> fields) {
        Set<String> used = new TreeSet<>();
        decl.getBody().ifPresent(body -> {
            Set<String> locals = body.findAll(VariableDeclarator.class).stream()
                .map(variable -> variable.getNameAsString())
                .collect(Collectors.toSet());
            body.findAll(LambdaExpr.class).forEach(
                lambda -> lambda.getParameters().forEach(parameter -> locals.add(parameter.getNameAsString()))
            );
            decl.getParameters().forEach(parameter -> locals.add(parameter.getNameAsString()));
            body.findAll(NameExpr.class).stream()
                .map(name -> name.getNameAsString())
                .filter(fields::contains)
                .filter(name -> !locals.contains(name))
                .forEach(used::add);
            body.findAll(FieldAccessExpr.class).stream()
                .filter(access -> access.getScope().isThisExpr())
                .map(access -> access.getNameAsString())
                .filter(fields::contains)
                .forEach(used::add);
        });
        return used;
    }

    private Optional<String> projectType(Parameter parameter, Project project) {
        try {
            ResolvedType type = parameter.getType().resolve();
            if (type.isReferenceType() && project.has(type.asReferenceType().getQualifiedName())) {
                return Optional.of(type.asReferenceType().getQualifiedName());
            }
        } catch (RuntimeException | StackOverflowError e) {
            // Unresolved parameter types just stay unlinked.
        }
        return Optional.empty();
    }

    List<String> parameterNames() {
        return decl.getParameters().stream().map(parameter -> parameter.getNameAsString()).toList();
    }
}
