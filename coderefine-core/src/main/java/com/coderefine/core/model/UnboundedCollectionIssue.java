package com.coderefine.core.model;

/**
 * A repository call that loads an entire table into memory with no bound —
 * e.g. {@code findAll()} returning a {@code List} with no {@code Pageable}.
 * Fires a single query, so it is invisible to query-count analysis, but can
 * exhaust heap and degrade latency as the table grows.
 */
public record UnboundedCollectionIssue(
        String filePath,
        String className,
        String methodName,
        int lineNumber,
        String repositoryVariable,
        String repositoryCall,
        String description
) implements Issue {

    @Override
    public IssueType type() {
        return IssueType.UNBOUNDED_COLLECTION;
    }

    @Override
    public String toString() {
        return String.format("[Unbounded] %s.%s (line %d): '%s.%s()' loads the whole table with no pagination",
                className, methodName, lineNumber, repositoryVariable, repositoryCall);
    }
}
