package com.coderefine.core.model;

/**
 * Common contract for every detected anti-pattern, regardless of type.
 * Detectors emit implementations of this; the LLM and verification layers
 * dispatch on {@link #type()}.
 */
public interface Issue {
    String filePath();
    String className();
    String methodName();
    int lineNumber();
    IssueType type();
    String description();
}
