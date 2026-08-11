package com.coderefine.llm.model;

public record PatchContext(
        String entitySource,
        String serviceSource,
        String repositorySource,
        String entityName,
        String lazyField,
        String methodName,
        int lineNumber
) {}
