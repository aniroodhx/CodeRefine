package com.coderefine.cli.report;

import com.coderefine.core.model.Issue;
import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.verify.model.VerificationResult;

import java.util.ArrayList;
import java.util.List;

public class PipelineReport {

    public record Entry(
            Issue issue,
            PatchSuggestion patch,
            VerificationResult verification
    ) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<Issue> patchFailures = new ArrayList<>();

    public void addEntry(Issue issue, PatchSuggestion patch, VerificationResult verification) {
        entries.add(new Entry(issue, patch, verification));
    }

    public void addPatchFailure(Issue issue) {
        patchFailures.add(issue);
    }

    public List<Entry> entries() {
        return entries;
    }

    public long approvedCount() {
        return entries.stream()
                .filter(e -> e.verification().verdict() == VerificationResult.Verdict.APPROVED)
                .count();
    }

    public long rejectedCount() {
        return entries.stream()
                .filter(e -> e.verification().verdict() == VerificationResult.Verdict.REJECTED)
                .count();
    }

    public long errorCount() {
        return entries.stream()
                .filter(e -> e.verification().verdict() == VerificationResult.Verdict.ERROR)
                .count();
    }

    public int totalQueriesReduced() {
        return entries.stream()
                .filter(e -> e.verification().verdict() == VerificationResult.Verdict.APPROVED)
                .mapToInt(e -> e.verification().reduced())
                .sum();
    }

    public int patchFailureCount() {
        return patchFailures.size();
    }

    public int totalIssuesDetected() {
        return entries.size() + patchFailures.size();
    }
}
