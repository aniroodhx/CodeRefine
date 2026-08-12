package com.coderefine.core.detector;

import com.coderefine.core.model.EntityRelationship;
import com.coderefine.core.model.NPlusOneIssue;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class NPlusOneDetector {

    private static final Logger log = LoggerFactory.getLogger(NPlusOneDetector.class);

    private static final Set<String> STREAM_OPERATIONS = Set.of("map", "forEach", "flatMap", "peek");

    private final Map<String, List<EntityRelationship>> entityMap;

    private int filesScanned;
    private int parseFailures;

    public NPlusOneDetector(Map<String, List<EntityRelationship>> entityMap) {
        this.entityMap = entityMap;
    }

    public List<NPlusOneIssue> detect(Path projectRoot) throws IOException {
        List<NPlusOneIssue> issues = new ArrayList<>();
        AtomicInteger scanned = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        try (Stream<Path> files = Files.walk(projectRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        scanned.incrementAndGet();
                        if (!analyzeFile(file, issues)) {
                            failed.incrementAndGet();
                        }
                    });
        }

        this.filesScanned = scanned.get();
        this.parseFailures = failed.get();

        if (parseFailures > 0) {
            log.warn("Parsed {}/{} Java files; {} file(s) could not be parsed and were skipped. "
                            + "Detection coverage on this project is partial.",
                    filesScanned - parseFailures, filesScanned, parseFailures);
        } else {
            log.info("Parsed {}/{} Java files successfully.", filesScanned, filesScanned);
        }

        return issues;
    }

    /** Number of .java files walked in the last {@link #detect} run. */
    public int getFilesScanned() {
        return filesScanned;
    }

    /** Number of files that failed to parse (and were silently skipped) in the last run. */
    public int getParseFailures() {
        return parseFailures;
    }

    /** @return true if the file parsed, false if it was skipped due to a parse error. */
    private boolean analyzeFile(Path file, List<NPlusOneIssue> issues) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            String filePath = file.toString();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                String className = cls.getNameAsString();
                cls.findAll(MethodDeclaration.class).forEach(method ->
                        analyzeMethod(filePath, className, method, issues));
            });
            return true;
        } catch (Exception e) {
            log.debug("Skipping unparseable file {}: {}", file, e.getMessage());
            return false;
        }
    }

    private void analyzeMethod(String filePath, String className,
                               MethodDeclaration method, List<NPlusOneIssue> issues) {
        String methodName = method.getNameAsString();
        Map<String, String> variableTypes = resolveVariableTypes(method);

        method.findAll(ForEachStmt.class).forEach(loop -> {
            String iteratorType = resolveIteratorEntityType(loop, variableTypes);
            if (iteratorType != null) {
                detectLazyAccessInBody(loop.getBody(), filePath, className, methodName,
                        iteratorType, loop.getVariableDeclarator().getNameAsString(),
                        "for-each", issues);
            }
        });

        method.findAll(ForStmt.class).forEach(loop ->
                detectLazyAccessInNode(loop.getBody(), filePath, className, methodName,
                        variableTypes, "for", issues));

        method.findAll(WhileStmt.class).forEach(loop ->
                detectLazyAccessInNode(loop.getBody(), filePath, className, methodName,
                        variableTypes, "while", issues));

        detectStreamLazyAccess(method, filePath, className, methodName, variableTypes, issues);
    }

    private void detectLazyAccessInBody(Node body,
                                        String filePath, String className, String methodName,
                                        String entityType, String variableName,
                                        String loopType, List<NPlusOneIssue> issues) {
        List<EntityRelationship> relationships = entityMap.get(entityType);
        if (relationships == null) return;

        body.findAll(MethodCallExpr.class).forEach(call -> {
            call.getScope().ifPresent(scope -> {
                if (scope.toString().startsWith(variableName + ".") ||
                        scope.toString().equals(variableName)) {
                    recordIfLazyAccess(call, entityType, relationships, loopType,
                            filePath, className, methodName, issues);
                }
            });
        });
    }

    private void detectLazyAccessInNode(Node node,
                                        String filePath, String className, String methodName,
                                        Map<String, String> variableTypes,
                                        String loopType, List<NPlusOneIssue> issues) {
        node.findAll(MethodCallExpr.class).forEach(call -> {
            call.getScope().ifPresent(scope -> {
                String scopeName = extractRootVariable(scope.toString());
                String entityType = variableTypes.get(scopeName);
                if (entityType != null && entityMap.containsKey(entityType)) {
                    recordIfLazyAccess(call, entityType, entityMap.get(entityType), loopType,
                            filePath, className, methodName, issues);
                }
            });
        });
    }

    private void detectStreamLazyAccess(MethodDeclaration method,
                                        String filePath, String className, String methodName,
                                        Map<String, String> variableTypes,
                                        List<NPlusOneIssue> issues) {
        method.findAll(MethodCallExpr.class).stream()
                .filter(call -> STREAM_OPERATIONS.contains(call.getNameAsString()))
                .forEach(streamCall -> streamCall.getArguments().forEach(arg -> {
                    if (arg.isMethodReferenceExpr()) {
                        handleMethodReference(streamCall, arg.asMethodReferenceExpr(),
                                variableTypes, filePath, className, methodName, issues);
                    } else if (arg.isLambdaExpr()) {
                        handleLambda(streamCall, arg.asLambdaExpr(),
                                variableTypes, filePath, className, methodName, issues);
                    }
                }));
    }

    private void handleMethodReference(MethodCallExpr streamCall,
                                       com.github.javaparser.ast.expr.MethodReferenceExpr ref,
                                       Map<String, String> variableTypes,
                                       String filePath, String className, String methodName,
                                       List<NPlusOneIssue> issues) {
        String identifier = ref.getIdentifier();
        String scope = ref.getScope().toString();
        // Entity::getX -> scope is the entity type directly.
        String entityType = variableTypes.getOrDefault(scope, scope);
        if (!entityMap.containsKey(entityType)) return;

        for (EntityRelationship rel : entityMap.get(entityType)) {
            if (isLazyRelationForField(rel, entityType, identifier)) {
                issues.add(streamIssue(filePath, className, methodName, streamCall,
                        entityType, rel.fieldName()));
            }
        }
    }

    private void handleLambda(MethodCallExpr streamCall, LambdaExpr lambda,
                              Map<String, String> variableTypes,
                              String filePath, String className, String methodName,
                              List<NPlusOneIssue> issues) {
        // Element type comes from the stream source, e.g. orders.stream() -> List<Order> -> Order.
        String sourceRoot = extractRootVariable(streamCall.getScope()
                .map(Object::toString).orElse(""));
        String entityType = extractGenericElementType(variableTypes.get(sourceRoot));
        if (entityType == null || !entityMap.containsKey(entityType)) return;

        List<EntityRelationship> relationships = entityMap.get(entityType);
        lambda.getParameters().forEach(param -> {
            String paramName = param.getNameAsString();
            lambda.getBody().findAll(MethodCallExpr.class).forEach(call ->
                    call.getScope().ifPresent(scope -> {
                        if (scope.toString().startsWith(paramName + ".") ||
                                scope.toString().equals(paramName)) {
                            recordIfLazyAccess(call, entityType, relationships, "stream",
                                    filePath, className, methodName, issues);
                        }
                    }));
        });
    }

    private void recordIfLazyAccess(MethodCallExpr call, String entityType,
                                    List<EntityRelationship> relationships, String loopType,
                                    String filePath, String className, String methodName,
                                    List<NPlusOneIssue> issues) {
        String accessedMethod = call.getNameAsString();
        for (EntityRelationship rel : relationships) {
            if (isLazyRelationForField(rel, entityType, accessedMethod)) {
                issues.add(new NPlusOneIssue(
                        filePath, className, methodName,
                        call.getBegin().map(p -> p.line).orElse(0),
                        entityType, rel.fieldName(), loopType,
                        String.format("Lazy field '%s' of '%s' accessed inside %s loop — "
                                + "triggers N+1 SELECT per iteration",
                                rel.fieldName(), entityType, loopType)));
            }
        }
    }

    private NPlusOneIssue streamIssue(String filePath, String className, String methodName,
                                      MethodCallExpr streamCall, String entityType, String field) {
        return new NPlusOneIssue(
                filePath, className, methodName,
                streamCall.getBegin().map(p -> p.line).orElse(0),
                entityType, field, "stream",
                String.format("Lazy field '%s' of '%s' accessed via stream operation — "
                        + "triggers N+1 SELECT per element", field, entityType));
    }

    /**
     * True only if {@code rel} is a lazy relationship that (a) actually belongs to
     * {@code entityType} and (b) is the target of the accessed getter. Guards against
     * misattributing lazy risk to the wrong class when variable-type resolution is imperfect.
     */
    private boolean isLazyRelationForField(EntityRelationship rel, String entityType,
                                           String accessedMethod) {
        return rel.isLazy()
                && rel.entityClass().equals(entityType)
                && isGetterForField(accessedMethod, rel.fieldName());
    }

    private Map<String, String> resolveVariableTypes(MethodDeclaration method) {
        Map<String, String> types = new HashMap<>();

        method.getParameters().forEach(param ->
                types.put(param.getNameAsString(), param.getTypeAsString()));

        method.findAll(VariableDeclarator.class).forEach(var ->
                types.put(var.getNameAsString(), var.getTypeAsString()));

        return types;
    }

    private String resolveIteratorEntityType(ForEachStmt loop, Map<String, String> variableTypes) {
        String iterableExpr = loop.getIterable().toString();
        String varType = loop.getVariableDeclarator().getTypeAsString();

        if (entityMap.containsKey(varType)) {
            return varType;
        }

        String rootVar = extractRootVariable(iterableExpr);
        String declaredType = variableTypes.get(rootVar);
        if (declaredType == null) return null;
        // The iterable may be a collection (List<Order>) or the entity itself.
        return entityMap.containsKey(declaredType)
                ? declaredType
                : extractGenericElementType(declaredType);
    }

    private boolean isGetterForField(String methodName, String fieldName) {
        String expectedGetter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        return methodName.equals(expectedGetter) || methodName.equals(fieldName);
    }

    private String extractRootVariable(String expression) {
        int dotIndex = expression.indexOf('.');
        return dotIndex > 0 ? expression.substring(0, dotIndex) : expression;
    }

    /**
     * Extracts the element type from a generic collection type string.
     * e.g. {@code "List<Order>"} -> {@code "Order"}, {@code "Set<Tag>"} -> {@code "Tag"}.
     * Returns {@code null} if there is no single generic argument.
     */
    private String extractGenericElementType(String type) {
        if (type == null) return null;
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (open < 0 || close <= open) return null;
        String inner = type.substring(open + 1, close).strip();
        // Only handle single-arg collections; skip Map<K,V> and nested generics.
        if (inner.contains(",") || inner.contains("<")) return null;
        return inner;
    }
}
