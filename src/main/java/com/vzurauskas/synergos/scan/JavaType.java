package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A named type declared in the scanned sources: a class, interface, enum or
 * record. Each one becomes a cube.
 */
public final class JavaType {
    private final TypeDeclaration<?> decl;
    private final String file;
    private final boolean test;
    private final String pkg;
    private final Memo<List<String>> supertypes;
    private final Memo<CubeText> text;

    JavaType(TypeDeclaration<?> decl, String file, boolean test, String pkg) {
        this.decl = decl;
        this.file = file;
        this.test = test;
        this.pkg = pkg;
        this.supertypes = new Memo<>(this::resolvedSupertypes);
        this.text = new Memo<>(() -> new CubeText(decl.getJavadoc()));
    }

    public String id() {
        return decl.getFullyQualifiedName().orElse(decl.getNameAsString());
    }

    public String simpleName() {
        return decl.getNameAsString();
    }

    public boolean test() {
        return test;
    }

    public boolean isInterface() {
        return decl instanceof ClassOrInterfaceDeclaration type && type.isInterface();
    }

    public String kind() {
        if (decl instanceof EnumDeclaration) {
            return "enum";
        }
        if (decl instanceof RecordDeclaration) {
            return "record";
        }
        if (isInterface()) {
            return "interface";
        }
        if (decl instanceof ClassOrInterfaceDeclaration type && type.isAbstract()) {
            return "abstract";
        }
        return "class";
    }

    public int line() {
        return decl.getName().getBegin().map(position -> position.line).orElse(1);
    }

    public String file() {
        return file;
    }

    /**
     * Who the type is: the part of the model that identifies it and says
     * where it lives.
     */
    public ObjectNode identity() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", id());
        node.put("name", id().startsWith(pkg + '.') ? id().substring(pkg.length() + 1) : id());
        node.put("simpleName", simpleName());
        node.put("package", pkg);
        node.put("kind", kind());
        outer().ifPresentOrElse(outer -> node.put("outer", outer), () -> node.putNull("outer"));
        node.put("test", test);
        node.put("written", text().written());
        ObjectNode source = node.putObject("source");
        source.put("file", file);
        source.put("line", line());
        node.put("javadocLine", javadocLine());
        return node;
    }

    /**
     * What a person wrote about this type in its Javadoc.
     */
    public CubeText text() {
        return text.value();
    }

    /**
     * Where the Javadoc starts, or the declaration if there's none yet.
     */
    public int javadocLine() {
        return decl.getJavadocComment()
            .flatMap(comment -> comment.getBegin())
            .map(position -> position.line)
            .orElse(line());
    }

    /**
     * The type a {@code {@link Name}} in this type's Javadoc refers to, the
     * way Javadoc finds it: this type and the types around it, then imports,
     * then the package, then java.lang. As a last resort, a type with that
     * simple name, if the project has exactly one.
     */
    public Optional<String> resolve(String name, Project project) {
        if (name.isEmpty()) {
            return Optional.empty();
        }
        String first = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        String rest = name.substring(first.length());
        for (Optional<Node> scope = Optional.of(decl); scope.isPresent(); scope = scope.get().getParentNode()) {
            if (scope.get() instanceof TypeDeclaration<?> type) {
                String id = type.getFullyQualifiedName().orElse(type.getNameAsString());
                if (type.getNameAsString().equals(first) && project.has(id + rest)) {
                    return Optional.of(id + rest);
                }
                if (project.has(id + '.' + name)) {
                    return Optional.of(id + '.' + name);
                }
            }
        }
        List<ImportDeclaration> imports = decl.findCompilationUnit()
            .map(unit -> (List<ImportDeclaration>) unit.getImports())
            .orElse(List.of());
        for (ImportDeclaration imported : imports) {
            if (!imported.isAsterisk() && !imported.isStatic()
                && imported.getName().getIdentifier().equals(first)) {
                return Optional.of(imported.getNameAsString() + rest);
            }
        }
        String local = pkg.isEmpty() ? name : pkg + '.' + name;
        if (project.has(local)) {
            return Optional.of(local);
        }
        for (ImportDeclaration imported : imports) {
            if (imported.isAsterisk() && !imported.isStatic() && project.has(imported.getNameAsString() + '.' + name)) {
                return Optional.of(imported.getNameAsString() + '.' + name);
            }
        }
        if (project.has(name)) {
            return Optional.of(name);
        }
        try {
            Class.forName("java.lang." + name, false, ClassLoader.getSystemClassLoader());
            return Optional.of("java.lang." + name);
        } catch (ClassNotFoundException | LinkageError e) {
            // Not a java.lang type.
        }
        List<String> named = project.types().stream()
            .filter(type -> type.id().equals(name) || type.id().endsWith('.' + name))
            .map(JavaType::id)
            .toList();
        return named.size() == 1 ? Optional.of(named.get(0)) : Optional.empty();
    }

    /**
     * Whether this type, or a project type it's a kind of, responds to the
     * message a {@code {@link #message}} names.
     */
    boolean respondsTo(Link link, Project project) {
        List<JavaType> types = new ArrayList<>(List.of(this));
        project.ancestors(id()).forEach(ancestor -> project.type(ancestor).ifPresent(types::add));
        return types.stream()
            .flatMap(type -> type.methods().stream())
            .filter(method -> !method.isPrivate())
            .anyMatch(link::matches);
    }

    public Optional<String> outer() {
        return decl.getParentNode()
            .filter(parent -> parent instanceof TypeDeclaration)
            .flatMap(parent -> ((TypeDeclaration<?>) parent).getFullyQualifiedName());
    }

    /**
     * Fully qualified names of the types this one extends or implements.
     * A supertype that can't be resolved keeps the name written in the source.
     */
    public List<String> supertypes() {
        return supertypes.value();
    }

    public List<JavaMethod> methods() {
        return decl.getMethods().stream().map(method -> new JavaMethod(method, id())).toList();
    }

    public boolean declares(String name, int arity) {
        return methods().stream().anyMatch(method -> method.is(name, arity));
    }

    public List<JavaField> fields() {
        List<ConstructorDeclaration> constructors = decl.getConstructors();
        List<JavaField> fields = new ArrayList<>();
        for (FieldDeclaration field : decl.getFields()) {
            field.getVariables().forEach(variable -> fields.add(new JavaField(field, variable, constructors)));
        }
        if (decl instanceof RecordDeclaration record) {
            record.getParameters().forEach(parameter -> fields.add(new JavaField(parameter)));
        }
        return fields;
    }

    public Set<String> fieldNames() {
        Set<String> names = decl.getFields().stream()
            .flatMap(field -> field.getVariables().stream())
            .map(variable -> variable.getNameAsString())
            .collect(Collectors.toSet());
        if (decl instanceof RecordDeclaration record) {
            record.getParameters().forEach(parameter -> names.add(parameter.getNameAsString()));
        }
        return names;
    }

    /**
     * The declaration itself, for walking the code inside it.
     */
    Node declaration() {
        return decl;
    }

    private List<String> resolvedSupertypes() {
        List<ClassOrInterfaceType> declared = new ArrayList<>();
        if (decl instanceof ClassOrInterfaceDeclaration type) {
            declared.addAll(type.getExtendedTypes());
            declared.addAll(type.getImplementedTypes());
        } else if (decl instanceof EnumDeclaration type) {
            declared.addAll(type.getImplementedTypes());
        } else if (decl instanceof RecordDeclaration type) {
            declared.addAll(type.getImplementedTypes());
        }
        return declared.stream().map(this::qualified).toList();
    }

    private String qualified(ClassOrInterfaceType type) {
        try {
            ResolvedType resolved = type.resolve();
            if (resolved.isReferenceType()) {
                return resolved.asReferenceType().getQualifiedName();
            }
        } catch (RuntimeException | StackOverflowError e) {
            // Falls back to the name as written.
        }
        return type.getNameWithScope();
    }
}
