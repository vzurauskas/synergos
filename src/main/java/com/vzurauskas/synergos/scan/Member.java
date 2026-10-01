package com.vzurauskas.synergos.scan;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The member of a type (method, constructor, field, initializer) that some
 * piece of code sits in. Code inside lambdas and anonymous classes belongs to
 * the member around them.
 */
final class Member {
    private final Node member;

    Member(Node code, Node owner) {
        this.member = around(code, owner);
    }

    String name() {
        if (member instanceof MethodDeclaration method) {
            return method.getNameAsString();
        }
        if (member instanceof FieldDeclaration field) {
            return field.getVariable(0).getNameAsString();
        }
        if (member instanceof EnumConstantDeclaration constant) {
            return constant.getNameAsString();
        }
        return "<init>";
    }

    int line() {
        if (member instanceof MethodDeclaration method) {
            return method.getName().getBegin().map(position -> position.line).orElse(0);
        }
        return member.getBegin().map(position -> position.line).orElse(0);
    }

    Set<String> parameters() {
        if (member instanceof MethodDeclaration method) {
            return method.getParameters().stream().map(p -> p.getNameAsString()).collect(Collectors.toSet());
        }
        if (member instanceof ConstructorDeclaration constructor) {
            return constructor.getParameters().stream().map(p -> p.getNameAsString()).collect(Collectors.toSet());
        }
        return Set.of();
    }

    boolean usesParameters(NodeList<Expression> arguments) {
        Set<String> parameters = parameters();
        return arguments.stream()
            .flatMap(argument -> argument.findAll(NameExpr.class).stream())
            .anyMatch(name -> parameters.contains(name.getNameAsString()));
    }

    private static Node around(Node code, Node owner) {
        Node node = code;
        while (node.getParentNode().isPresent() && node.getParentNode().get() != owner) {
            node = node.getParentNode().get();
        }
        return node;
    }
}
