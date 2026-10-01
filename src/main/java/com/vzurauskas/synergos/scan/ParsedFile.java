package com.vzurauskas.synergos.scan;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;

import java.util.List;

/**
 * One parsed source file. Every named type declared in it, nested ones
 * included, is a type of its own; local and anonymous classes are not.
 */
final class ParsedFile {
    private final CompilationUnit unit;
    private final String path;
    private final boolean test;

    ParsedFile(CompilationUnit unit, String path, boolean test) {
        this.unit = unit;
        this.path = path;
        this.test = test;
    }

    List<JavaType> types() {
        return unit.findAll(TypeDeclaration.class).stream()
            .filter(decl -> !(decl instanceof AnnotationDeclaration))
            .filter(decl -> decl.getParentNode()
                .filter(parent -> parent instanceof LocalClassDeclarationStmt
                    || parent instanceof LocalRecordDeclarationStmt)
                .isEmpty())
            .map(decl -> new JavaType((TypeDeclaration<?>) decl, path, test, packageName()))
            .toList();
    }

    private String packageName() {
        return unit.getPackageDeclaration().map(pkg -> pkg.getNameAsString()).orElse("");
    }
}
