package com.coderefine.core.model;

import java.nio.file.Path;
import java.util.List;

public record AnalysisResult(
        Path projectPath,
        List<Issue> issues,
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

    public long countOf(IssueType type) {
        return issues.stream().filter(i -> i.type() == type).count();
    }
}
