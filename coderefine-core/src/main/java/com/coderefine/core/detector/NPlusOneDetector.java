package com.coderefine.core.detector;

import com.coderefine.core.model.EntityRelationship;
import com.coderefine.core.model.NPlusOneIssue;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class NPlusOneDetector {

    private final Map<String, List<EntityRelationship>> entityMap;

    public NPlusOneDetector(Map<String, List<EntityRelationship>> entityMap) {
        this.entityMap = entityMap;
    }

    public List<NPlusOneIssue> detect(Path projectRoot) throws IOException {
        List<NPlusOneIssue> issues = new ArrayList<>();

        try (Stream<Path> files = Files.walk(projectRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> analyzeFile(file, issues));
        }

        return issues;
    }

    private void analyzeFile(Path file, List<NPlusOneIssue> issues) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            String filePath = file.toString();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                String className = cls.getNameAsString();
                cls.findAll(MethodDeclaration.class).forEach(method ->
                        analyzeMethod(filePath, className, method, issues));
            });
        } catch (Exception e) {
            // Skip unparseable files
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

    private void detectLazyAccessInBody(com.github.javaparser.ast.Node body,
                                        String filePath, String className, String methodName,
                                        String entityType, String variableName,
                                        String loopType, List<NPlusOneIssue> issues) {
        List<EntityRelationship> relationships = entityMap.get(entityType);
        if (relationships == null) return;

        body.findAll(MethodCallExpr.class).forEach(call -> {
            call.getScope().ifPresent(scope -> {
                if (scope.toString().startsWith(variableName + ".") ||
                        scope.toString().equals(variableName)) {
                    String accessedMethod = call.getNameAsString();
                    for (EntityRelationship rel : relationships) {
                        if (rel.isLazy() && isGetterForField(accessedMethod, rel.fieldName())) {
                            issues.add(new NPlusOneIssue(
                                    filePath, className, methodName,
                                    call.getBegin().map(p -> p.line).orElse(0),
                                    entityType, rel.fieldName(), loopType,
                                    String.format("Lazy collection '%s' accessed inside %s loop — " +
                                            "triggers N+1 SELECT per iteration", rel.fieldName(), loopType)
                            ));
                        }
                    }
                }
            });
        });
    }

    private void detectLazyAccessInNode(com.github.javaparser.ast.Node node,
                                        String filePath, String className, String methodName,
                                        Map<String, String> variableTypes,
                                        String loopType, List<NPlusOneIssue> issues) {
        node.findAll(MethodCallExpr.class).forEach(call -> {
            call.getScope().ifPresent(scope -> {
                String scopeName = extractRootVariable(scope.toString());
                String entityType = variableTypes.get(scopeName);
                if (entityType != null && entityMap.containsKey(entityType)) {
                    List<EntityRelationship> relationships = entityMap.get(entityType);
                    String accessedMethod = call.getNameAsString();
                    for (EntityRelationship rel : relationships) {
                        if (rel.isLazy() && isGetterForField(accessedMethod, rel.fieldName())) {
                            issues.add(new NPlusOneIssue(
                                    filePath, className, methodName,
                                    call.getBegin().map(p -> p.line).orElse(0),
                                    entityType, rel.fieldName(), loopType,
                                    String.format("Lazy collection '%s' accessed inside %s loop — " +
                                            "triggers N+1 SELECT per iteration", rel.fieldName(), loopType)
                            ));
                        }
                    }
                }
            });
        });
    }

    private void detectStreamLazyAccess(MethodDeclaration method,
                                        String filePath, String className, String methodName,
                                        Map<String, String> variableTypes,
                                        List<NPlusOneIssue> issues) {
        method.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("map") ||
                        call.getNameAsString().equals("forEach") ||
                        call.getNameAsString().equals("flatMap"))
                .forEach(streamCall -> {
                    streamCall.getArguments().forEach(arg -> {
                        if (arg.isMethodReferenceExpr()) {
                            String identifier = arg.asMethodReferenceExpr().getIdentifier();
                            String scope = arg.asMethodReferenceExpr().getScope().toString();
                            String entityType = variableTypes.getOrDefault(scope, scope);
                            if (entityMap.containsKey(entityType)) {
                                List<EntityRelationship> relationships = entityMap.get(entityType);
                                for (EntityRelationship rel : relationships) {
                                    if (rel.isLazy() && isGetterForField(identifier, rel.fieldName())) {
                                        issues.add(new NPlusOneIssue(
                                                filePath, className, methodName,
                                                streamCall.getBegin().map(p -> p.line).orElse(0),
                                                entityType, rel.fieldName(), "stream",
                                                String.format("Lazy collection '%s' accessed via stream operation — " +
                                                        "triggers N+1 SELECT per element", rel.fieldName())
                                        ));
                                    }
                                }
                            }
                        }
                    });
                });
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
        return variableTypes.get(rootVar);
    }

    private boolean isGetterForField(String methodName, String fieldName) {
        String expectedGetter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        return methodName.equals(expectedGetter) || methodName.equals(fieldName);
    }

    private String extractRootVariable(String expression) {
        int dotIndex = expression.indexOf('.');
        return dotIndex > 0 ? expression.substring(0, dotIndex) : expression;
    }
}
