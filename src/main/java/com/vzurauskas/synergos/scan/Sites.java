package com.vzurauskas.synergos.scan;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every place in one type's code where its objects reach another object:
 * messages, procedure calls, creations and field writes. Nested named types
 * are left out (they are cubes of their own); lambdas and anonymous classes
 * belong to the type around them.
 */
final class Sites {
    private static final int EXPRESSION_LENGTH = 140;

    private final JavaType type;
    private final Project project;

    Sites(JavaType type, Project project) {
        this.type = type;
        this.project = project;
    }

    List<Site> all() {
        List<Site> sites = new ArrayList<>();
        walk(type.declaration(), type.declaration(), sites);
        return sites;
    }

    private void walk(Node node, Node owner, List<Site> sites) {
        if (node != owner && node instanceof TypeDeclaration<?>
            && node.getParentNode().filter(parent -> parent instanceof TypeDeclaration).isPresent()) {
            return;
        }
        if (node instanceof MethodCallExpr call) {
            sites.add(call(call, new Member(call, owner)));
        } else if (node instanceof MethodReferenceExpr reference) {
            reference(reference, new Member(reference, owner)).ifPresent(sites::add);
        } else if (node instanceof ObjectCreationExpr creation) {
            creation(creation, new Member(creation, owner)).ifPresent(sites::add);
        } else if (node instanceof AssignExpr assign) {
            write(assign, new Member(assign, owner)).ifPresent(sites::add);
        }
        for (Node child : node.getChildNodes()) {
            walk(child, owner, sites);
        }
    }

    private Site call(MethodCallExpr call, Member member) {
        try {
            ResolvedMethodDeclaration method = call.resolve();
            String declaring = method.declaringType().getQualifiedName();
            Optional<Expression> scope = call.getScope();
            boolean implicit = scope.isEmpty() || scope.get().isThisExpr() || scope.get().isSuperExpr();
            String target;
            Site.Kind kind;
            if (method.isStatic()) {
                target = declaring;
                kind = statically(declaring);
            } else if (implicit) {
                boolean outer = project.has(declaring) && !own(declaring)
                    && !project.ancestors(type.id()).contains(declaring);
                target = outer ? declaring : type.id();
                kind = outer ? Site.Kind.MESSAGE : Site.Kind.SELF;
            } else {
                target = staticType(scope.get()).orElse(declaring);
                kind = instance(target);
            }
            boolean used = !method.getReturnType().isVoid()
                && !(call.getParentNode().orElse(null) instanceof ExpressionStmt);
            return site(kind, member, target, method.getName(), method.getNumberOfParams(), used,
                call.getArguments(), scope, call);
        } catch (RuntimeException | StackOverflowError e) {
            return receiverOnly(call, member);
        }
    }

    /**
     * When the method itself can't be resolved (the symbol solver doesn't know
     * record accessors, for example) but the receiver's type can, the message
     * still goes to that type; only its return type stays unknown.
     */
    private Site receiverOnly(MethodCallExpr call, Member member) {
        Optional<String> target = call.getScope()
            .filter(receiver -> !receiver.isThisExpr() && !receiver.isSuperExpr())
            .flatMap(this::staticType);
        if (target.isEmpty()) {
            return site(Site.Kind.UNRESOLVED, member, "", call.getNameAsString(), call.getArguments().size(),
                false, call.getArguments(), call.getScope(), call);
        }
        return site(instance(target.get()), member, target.get(), call.getNameAsString(),
            call.getArguments().size(), !(call.getParentNode().orElse(null) instanceof ExpressionStmt),
            call.getArguments(), call.getScope(), call);
    }

    private Optional<Site> reference(MethodReferenceExpr reference, Member member) {
        Expression scope = reference.getScope();
        if (reference.getIdentifier().equals("new")) {
            if (!scope.isTypeExpr()) {
                return Optional.empty();
            }
            return qualified(() -> scope.asTypeExpr().getType().resolve())
                .filter(id -> project.has(id) && !own(id))
                .map(id -> site(Site.Kind.CREATES, member, id, "new", 0, true, new NodeList<>(),
                    Optional.empty(), reference));
        }
        try {
            ResolvedMethodDeclaration method = reference.resolve();
            String declaring = method.declaringType().getQualifiedName();
            String target;
            Site.Kind kind;
            if (method.isStatic()) {
                target = declaring;
                kind = statically(declaring);
            } else if (scope.isThisExpr() || scope.isSuperExpr()) {
                target = type.id();
                kind = Site.Kind.SELF;
            } else if (scope.isTypeExpr()) {
                target = qualified(() -> scope.asTypeExpr().getType().resolve()).orElse(declaring);
                kind = instance(target);
            } else {
                target = staticType(scope).orElse(declaring);
                kind = instance(target);
            }
            return Optional.of(site(kind, member, target, method.getName(), method.getNumberOfParams(), true,
                new NodeList<>(), Optional.of(scope), reference));
        } catch (RuntimeException | StackOverflowError e) {
            Optional<String> target = scope.isTypeExpr()
                ? qualified(() -> scope.asTypeExpr().getType().resolve())
                : staticType(scope);
            return Optional.of(target
                .map(id -> site(instance(id), member, id, reference.getIdentifier(), 0, true,
                    new NodeList<>(), Optional.of(scope), reference))
                .orElseGet(() -> site(Site.Kind.UNRESOLVED, member, "", reference.getIdentifier(), 0, false,
                    new NodeList<>(), Optional.of(scope), reference)));
        }
    }

    private Optional<Site> creation(ObjectCreationExpr creation, Member member) {
        return qualified(() -> creation.getType().resolve())
            .filter(id -> project.has(id) && !own(id))
            .map(id -> site(Site.Kind.CREATES, member, id, "new", creation.getArguments().size(), true,
                creation.getArguments(), Optional.empty(), creation));
    }

    private Optional<Site> write(AssignExpr assign, Member member) {
        if (!assign.getTarget().isFieldAccessExpr()) {
            return Optional.empty();
        }
        FieldAccessExpr field = assign.getTarget().asFieldAccessExpr();
        if (field.getScope().isThisExpr() || field.getScope().isSuperExpr()) {
            return Optional.empty();
        }
        try {
            ResolvedValueDeclaration value = field.resolve();
            if (!value.isField()) {
                return Optional.empty();
            }
            String declaring = value.asField().declaringType().getQualifiedName();
            if (!project.has(declaring) || own(declaring)) {
                return Optional.empty();
            }
            return Optional.of(site(Site.Kind.WRITE, member, declaring, field.getNameAsString(), 1, false,
                new NodeList<>(), Optional.of(field.getScope()), assign));
        } catch (RuntimeException | StackOverflowError e) {
            return Optional.empty();
        }
    }

    private Site site(
        Site.Kind kind, Member member, String target, String name, int arity, boolean used,
        NodeList<Expression> arguments, Optional<Expression> scope, Node code
    ) {
        String expression = code.toString().replaceAll("\\s+", " ");
        return new Site(
            kind, type.id(), member.name(), member.line(), target, name, arity, used,
            member.usesParameters(arguments),
            scope.map(Expression::toString).orElse(""),
            receiverSource(scope, member),
            type.file(),
            code.getBegin().map(position -> position.line).orElse(0),
            expression.length() > EXPRESSION_LENGTH ? expression.substring(0, EXPRESSION_LENGTH) + "…" : expression
        );
    }

    private String receiverSource(Optional<Expression> scope, Member member) {
        if (scope.isEmpty() || scope.get().isThisExpr() || scope.get().isSuperExpr()) {
            return "self";
        }
        Expression receiver = scope.get();
        if (receiver.isNameExpr()) {
            String name = receiver.asNameExpr().getNameAsString();
            if (member.parameters().contains(name)) {
                return "param";
            }
            if (type.fieldNames().contains(name)) {
                return "field";
            }
            return "local";
        }
        if (receiver.isFieldAccessExpr() && receiver.asFieldAccessExpr().getScope().isThisExpr()) {
            return "field";
        }
        if (receiver.isMethodCallExpr()) {
            return "call";
        }
        if (receiver.isObjectCreationExpr()) {
            return "new";
        }
        if (receiver.isTypeExpr()) {
            return "type";
        }
        return "expression";
    }

    private Site.Kind statically(String declaring) {
        if (own(declaring)) {
            return Site.Kind.SELF;
        }
        return project.has(declaring) ? Site.Kind.PROCEDURE : Site.Kind.FOREIGN_PROCEDURE;
    }

    private Site.Kind instance(String target) {
        if (own(target)) {
            return Site.Kind.SELF;
        }
        return project.has(target) ? Site.Kind.MESSAGE : Site.Kind.FOREIGN;
    }

    private boolean own(String id) {
        return id.equals(type.id());
    }

    private Optional<String> staticType(Expression expression) {
        return qualified(expression::calculateResolvedType);
    }

    private Optional<String> qualified(java.util.function.Supplier<ResolvedType> type) {
        try {
            ResolvedType resolved = type.get();
            if (resolved.isReferenceType()) {
                return Optional.of(resolved.asReferenceType().getQualifiedName());
            }
        } catch (RuntimeException | StackOverflowError e) {
            // Unknown type.
        }
        return Optional.empty();
    }
}
