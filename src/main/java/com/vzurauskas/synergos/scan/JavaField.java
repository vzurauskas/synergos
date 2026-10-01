package com.vzurauskas.synergos.scan;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A field: something an object knows. It can tell whether it was given to the
 * object at construction or made by the object itself.
 */
public final class JavaField {
    private static final Set<String> COLLECTIONS = Set.of(
        "List", "Set", "Map", "Collection", "Iterable", "Queue", "Deque",
        "ArrayList", "LinkedList", "HashSet", "LinkedHashSet", "TreeSet",
        "HashMap", "LinkedHashMap", "TreeMap", "SortedMap", "SortedSet"
    );
    private static final Set<String> VALUES = Set.of(
        "java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Double",
        "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.lang.Boolean",
        "java.lang.Character", "java.math.BigDecimal", "java.math.BigInteger",
        "java.net.URI", "java.util.UUID"
    );

    private final String name;
    private final Type type;
    private final boolean constant;
    private final String origin;

    JavaField(FieldDeclaration field, VariableDeclarator variable, List<ConstructorDeclaration> constructors) {
        this(
            variable.getNameAsString(),
            variable.getType(),
            field.isStatic(),
            origin(field, variable, constructors)
        );
    }

    JavaField(Parameter component) {
        this(component.getNameAsString(), component.getType(), false, "given");
    }

    private JavaField(String name, Type type, boolean constant, String origin) {
        this.name = name;
        this.type = type;
        this.constant = constant;
        this.origin = origin;
    }

    public boolean constant() {
        return constant;
    }

    public ObjectNode json(Project project) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", name);
        node.put("type", type.asString());
        qualified().filter(project::has).ifPresent(id -> node.put("typeId", id));
        node.put("origin", constant ? "constant" : origin);
        return node;
    }

    /**
     * The project types this field holds a collection of, e.g. {@code Service}
     * for {@code Map<String, Service>}. Empty if it isn't such a collection.
     */
    public List<String> collectionOf(Project project) {
        try {
            ResolvedType resolved = type.resolve();
            if (resolved.isArray()) {
                ResolvedType component = resolved.asArrayType().getComponentType();
                if (component.isReferenceType() && project.has(component.asReferenceType().getQualifiedName())) {
                    return List.of(component.asReferenceType().getQualifiedName());
                }
                return List.of();
            }
            if (!resolved.isReferenceType()) {
                return List.of();
            }
            String qualified = resolved.asReferenceType().getQualifiedName();
            if (!COLLECTIONS.contains(qualified.substring(qualified.lastIndexOf('.') + 1))) {
                return List.of();
            }
            List<String> held = new ArrayList<>();
            for (var parameter : resolved.asReferenceType().typeParametersValues()) {
                if (parameter.isReferenceType() && project.has(parameter.asReferenceType().getQualifiedName())) {
                    held.add(parameter.asReferenceType().getQualifiedName());
                }
            }
            return held;
        } catch (RuntimeException | StackOverflowError e) {
            return List.of();
        }
    }

    /**
     * Whether this field holds a plain value (text, a number, a date, JSON)
     * rather than an object with behaviour of its own.
     */
    public boolean value() {
        if (type.isPrimitiveType()) {
            return true;
        }
        Optional<String> qualified = qualified();
        return qualified.isPresent() && (VALUES.contains(qualified.get())
            || qualified.get().startsWith("java.time.")
            || qualified.get().contains("Json"));
    }

    public Optional<String> qualified() {
        try {
            ResolvedType resolved = type.resolve();
            if (resolved.isReferenceType()) {
                return Optional.of(resolved.asReferenceType().getQualifiedName());
            }
        } catch (RuntimeException | StackOverflowError e) {
            // Unresolved field types are left unqualified.
        }
        return Optional.empty();
    }

    private static String origin(
        FieldDeclaration field, VariableDeclarator variable, List<ConstructorDeclaration> constructors
    ) {
        if (variable.getInitializer().isPresent()) {
            return "made";
        }
        String name = variable.getNameAsString();
        boolean given = false;
        boolean made = false;
        for (ConstructorDeclaration constructor : constructors) {
            Set<String> parameters = constructor.getParameters().stream()
                .map(parameter -> parameter.getNameAsString())
                .collect(Collectors.toSet());
            for (AssignExpr assign : constructor.findAll(AssignExpr.class)) {
                if (!assigns(assign.getTarget(), name)) {
                    continue;
                }
                boolean fromParameter = assign.getValue().findAll(NameExpr.class).stream()
                    .anyMatch(used -> parameters.contains(used.getNameAsString()));
                if (fromParameter) {
                    given = true;
                } else {
                    made = true;
                }
            }
        }
        if (given) {
            return "given";
        }
        return made ? "made" : "unset";
    }

    private static boolean assigns(Expression target, String field) {
        if (target.isFieldAccessExpr()) {
            return target.asFieldAccessExpr().getScope().isThisExpr()
                && target.asFieldAccessExpr().getNameAsString().equals(field);
        }
        return target.isNameExpr() && target.asNameExpr().getNameAsString().equals(field);
    }
}
