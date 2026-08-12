package com.coderefine.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record AnalysisResult(
        Path projectPath,
        Map<String, List<EntityRelationship>> entityMap,
        List<NPlusOneIssue> issues,
        int filesScanned,
        int parseFailures
) {
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public int issueCount() {
        return issues.size();
    }

    /** True when some files could not be parsed, so detection coverage is partial. */
    public boolean hasPartialCoverage() {
        return parseFailures > 0;
    }
}
