package com.coderefine.core.model;

public record NPlusOneIssue(
        String filePath,
        String className,
        String methodName,
        int lineNumber,
        String entityType,
        String lazyField,
        String loopType,
        String description
) implements Issue {

    @Override
    public IssueType type() {
        return IssueType.N_PLUS_ONE;
    }

    @Override
    public String toString() {
        return String.format("[N+1] %s.%s (line %d): Accessing lazy field '%s' of '%s' inside %s loop",
                className, methodName, lineNumber, lazyField, entityType, loopType);
    }
}
