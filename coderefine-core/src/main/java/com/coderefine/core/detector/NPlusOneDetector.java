package com.coderefine.core.detector;

import com.coderefine.core.model.EntityRelationship;
import com.coderefine.core.model.Issue;
import com.coderefine.core.model.IssueType;
import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.core.parser.EntityParser;
import com.coderefine.core.scan.ParsedProject;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.nio.file.Path;
import java.util.*;

public class NPlusOneDetector implements Detector {

    private static final Set<String> STREAM_OPERATIONS = Set.of("map", "forEach", "flatMap", "peek");

    private final EntityParser entityParser = new EntityParser();

    @Override
    public IssueType type() {
        return IssueType.N_PLUS_ONE;
    }

    @Override
    public List<Issue> detect(ParsedProject project) {
        Map<String, List<EntityRelationship>> entityMap = entityParser.parseEntities(project);
        List<Issue> issues = new ArrayList<>();

        for (Map.Entry<Path, CompilationUnit> entry : project.compilationUnits().entrySet()) {
            String filePath = entry.getKey().toString();
            entry.getValue().findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                String className = cls.getNameAsString();
                cls.findAll(MethodDeclaration.class).forEach(method ->
                        analyzeMethod(entityMap, filePath, className, method, issues));
            });
        }

        return issues;
    }

    private void analyzeMethod(Map<String, List<EntityRelationship>> entityMap,
                               String filePath, String className,
                               MethodDeclaration method, List<Issue> issues) {
        String methodName = method.getNameAsString();
        Map<String, String> variableTypes = resolveVariableTypes(method);

        method.findAll(ForEachStmt.class).forEach(loop -> {
            String iteratorType = resolveIteratorEntityType(entityMap, loop, variableTypes);
            if (iteratorType != null) {
                detectLazyAccessInBody(entityMap, loop.getBody(), filePath, className, methodName,
                        iteratorType, loop.getVariableDeclarator().getNameAsString(),
                        "for-each", issues);
            }
        });

        method.findAll(ForStmt.class).forEach(loop ->
                detectLazyAccessInNode(entityMap, loop.getBody(), filePath, className, methodName,
                        variableTypes, "for", issues));

        method.findAll(WhileStmt.class).forEach(loop ->
                detectLazyAccessInNode(entityMap, loop.getBody(), filePath, className, methodName,
                        variableTypes, "while", issues));

        detectStreamLazyAccess(entityMap, method, filePath, className, methodName, variableTypes, issues);
    }

    private void detectLazyAccessInBody(Map<String, List<EntityRelationship>> entityMap, Node body,
                                        String filePath, String className, String methodName,
                                        String entityType, String variableName,
                                        String loopType, List<Issue> issues) {
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

    private void detectLazyAccessInNode(Map<String, List<EntityRelationship>> entityMap, Node node,
                                        String filePath, String className, String methodName,
                                        Map<String, String> variableTypes,
                                        String loopType, List<Issue> issues) {
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

    private void detectStreamLazyAccess(Map<String, List<EntityRelationship>> entityMap,
                                        MethodDeclaration method,
                                        String filePath, String className, String methodName,
                                        Map<String, String> variableTypes,
                                        List<Issue> issues) {
        method.findAll(MethodCallExpr.class).stream()
                .filter(call -> STREAM_OPERATIONS.contains(call.getNameAsString()))
                .forEach(streamCall -> streamCall.getArguments().forEach(arg -> {
                    if (arg.isMethodReferenceExpr()) {
                        handleMethodReference(entityMap, streamCall, arg.asMethodReferenceExpr(),
                                variableTypes, filePath, className, methodName, issues);
                    } else if (arg.isLambdaExpr()) {
                        handleLambda(entityMap, streamCall, arg.asLambdaExpr(),
                                variableTypes, filePath, className, methodName, issues);
                    }
                }));
    }

    private void handleMethodReference(Map<String, List<EntityRelationship>> entityMap,
                                       MethodCallExpr streamCall,
                                       com.github.javaparser.ast.expr.MethodReferenceExpr ref,
                                       Map<String, String> variableTypes,
                                       String filePath, String className, String methodName,
                                       List<Issue> issues) {
        String identifier = ref.getIdentifier();
        String scope = ref.getScope().toString();
        String entityType = variableTypes.getOrDefault(scope, scope);
        if (!entityMap.containsKey(entityType)) return;

        for (EntityRelationship rel : entityMap.get(entityType)) {
            if (isLazyRelationForField(rel, entityType, identifier)) {
                issues.add(streamIssue(filePath, className, methodName, streamCall,
                        entityType, rel.fieldName()));
            }
        }
    }

    private void handleLambda(Map<String, List<EntityRelationship>> entityMap,
                              MethodCallExpr streamCall, LambdaExpr lambda,
                              Map<String, String> variableTypes,
                              String filePath, String className, String methodName,
                              List<Issue> issues) {
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
                                    List<Issue> issues) {
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

    private String resolveIteratorEntityType(Map<String, List<EntityRelationship>> entityMap,
                                             ForEachStmt loop, Map<String, String> variableTypes) {
        String iterableExpr = loop.getIterable().toString();
        String varType = loop.getVariableDeclarator().getTypeAsString();

        if (entityMap.containsKey(varType)) {
            return varType;
        }

        String rootVar = extractRootVariable(iterableExpr);
        String declaredType = variableTypes.get(rootVar);
        if (declaredType == null) return null;
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

    private String extractGenericElementType(String type) {
        if (type == null) return null;
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (open < 0 || close <= open) return null;
        String inner = type.substring(open + 1, close).strip();
        if (inner.contains(",") || inner.contains("<")) return null;
        return inner;
    }
}
