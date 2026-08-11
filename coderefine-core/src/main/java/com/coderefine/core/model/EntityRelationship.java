package com.coderefine.core.model;

public record EntityRelationship(
        String entityClass,
        String fieldName,
        String fieldType,
        RelationType relationType,
        FetchType fetchType
) {
    public enum RelationType {
        ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY, ONE_TO_ONE
    }

    public enum FetchType {
        LAZY, EAGER
    }

    public boolean isLazy() {
        return fetchType == FetchType.LAZY;
    }
}
