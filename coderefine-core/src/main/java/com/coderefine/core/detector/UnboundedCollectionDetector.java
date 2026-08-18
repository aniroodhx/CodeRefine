package com.coderefine.core.detector;

import com.coderefine.core.model.Issue;
import com.coderefine.core.model.IssueType;
import com.coderefine.core.model.UnboundedCollectionIssue;
import com.coderefine.core.scan.ParsedProject;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.nio.file.Path;
import java.util.*;

/**
 * Flags repository calls that load an entire table with no bound —
 * {@code findAll()} and friends returning a Collection with no {@code Pageable}
 * or {@code Limit} argument. This is a single query (invisible to N+1 analysis)
 * but scales linearly with table size and risks OOM.
 */
public class UnboundedCollectionDetector implements Detector {

    /** Repository finder methods that return everything when called with no bound. */
    private static final Set<String> UNBOUNDED_CALLS = Set.of("findAll", "readAll", "getAll");

    @Override
    public IssueType type() {
        return IssueType.UNBOUNDED_COLLECTION;
    }

    @Override
    public List<Issue> detect(ParsedProject project) {
        List<Issue> issues = new ArrayList<>();

        for (Map.Entry<Path, CompilationUnit> entry : project.compilationUnits().entrySet()) {
            String filePath = entry.getKey().toString();
            entry.getValue().findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                // Skip repository interface declarations themselves; we want call sites.
                if (cls.isInterface()) return;
                String className = cls.getNameAsString();
                cls.findAll(MethodDeclaration.class).forEach(method ->
                        analyzeMethod(filePath, className, method, issues));
            });
        }

        return issues;
    }

    private void analyzeMethod(String filePath, String className,
                               MethodDeclaration method, List<Issue> issues) {
        String methodName = method.getNameAsString();
        Set<String> repositoryVars = collectRepositoryVariables(method);

        method.findAll(MethodCallExpr.class).forEach(call -> {
            String callName = call.getNameAsString();
            if (!UNBOUNDED_CALLS.contains(callName)) return;
            if (hasBoundingArgument(call)) return;

            String scope = call.getScope().map(Object::toString).orElse("");
            String rootVar = extractRootVariable(scope);

            // Only flag when the call target looks like a repository, to avoid
            // flagging unrelated findAll() on plain collections/maps.
            if (!looksLikeRepository(rootVar, repositoryVars)) return;

            issues.add(new UnboundedCollectionIssue(
                    filePath, className, methodName,
                    call.getBegin().map(p -> p.line).orElse(0),
                    rootVar.isEmpty() ? "repository" : rootVar,
                    callName,
                    String.format("'%s.%s()' loads the entire table with no pagination — "
                            + "add a Pageable/Limit to bound the result set",
                            rootVar.isEmpty() ? "repository" : rootVar, callName)));
        });
    }

    /** Variables whose declared type name ends in "Repository" or "Dao". */
    private Set<String> collectRepositoryVariables(MethodDeclaration method) {
        Set<String> vars = new HashSet<>();

        method.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(cls ->
                cls.getFields().forEach(field -> {
                    String type = field.getElementType().asString();
                    if (isRepositoryType(type)) {
                        field.getVariables().forEach(v -> vars.add(v.getNameAsString()));
                    }
                }));

        method.getParameters().forEach(param -> {
            if (isRepositoryType(param.getTypeAsString())) {
                vars.add(param.getNameAsString());
            }
        });

        method.findAll(VariableDeclarator.class).forEach(v -> {
            if (isRepositoryType(v.getTypeAsString())) {
                vars.add(v.getNameAsString());
            }
        });

        return vars;
    }

    private boolean isRepositoryType(String typeName) {
        String simple = typeName.replaceAll("<.*>", "");
        return simple.endsWith("Repository") || simple.endsWith("Dao") || simple.endsWith("DAO");
    }

    private boolean looksLikeRepository(String rootVar, Set<String> repositoryVars) {
        if (repositoryVars.contains(rootVar)) return true;
        // Fall back to a name heuristic for inline/uninferred targets.
        String lower = rootVar.toLowerCase();
        return lower.endsWith("repository") || lower.endsWith("repo") || lower.endsWith("dao");
    }

    private boolean hasBoundingArgument(MethodCallExpr call) {
        // Any argument at all to findAll/readAll/getAll implies a bound in
        // Spring Data (Pageable, Sort, Limit, Example, Specification, an id
        // collection, …). The unbounded case is the no-arg call. Being
        // conservative here keeps the false-positive rate low.
        return !call.getArguments().isEmpty();
    }

    private String extractRootVariable(String expression) {
        int dotIndex = expression.indexOf('.');
        return dotIndex > 0 ? expression.substring(0, dotIndex) : expression;
    }
}
