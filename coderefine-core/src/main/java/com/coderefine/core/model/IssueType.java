package com.coderefine.core.model;

/**
 * The category of performance anti-pattern an {@link Issue} represents.
 * Each type is produced by its own detector and verified by its own strategy.
 */
public enum IssueType {
    N_PLUS_ONE("N+1 query"),
    UNBOUNDED_COLLECTION("Unbounded collection");

    private final String label;

    IssueType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
