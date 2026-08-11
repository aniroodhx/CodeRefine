package com.coderefine.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record AnalysisResult(
        Path projectPath,
        Map<String, List<EntityRelationship>> entityMap,
        List<NPlusOneIssue> issues
) {
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public int issueCount() {
        return issues.size();
    }
}
