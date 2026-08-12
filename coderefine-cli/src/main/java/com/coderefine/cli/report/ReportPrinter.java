package com.coderefine.cli.report;

import com.coderefine.verify.model.VerificationResult;

public class ReportPrinter {

    private static final String SEPARATOR = "═".repeat(70);

    public String format(PipelineReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n").append(SEPARATOR).append("\n");
        sb.append("  CodeRefine — N+1 Query Analysis Report\n");
        sb.append(SEPARATOR).append("\n\n");

        sb.append(String.format("  Issues detected:    %d%n", report.totalIssuesDetected()));
        sb.append(String.format("  ✅ Fixes approved:   %d%n", report.approvedCount()));
        sb.append(String.format("  ❌ Fixes rejected:   %d%n", report.rejectedCount()));
        sb.append(String.format("  ⚠️  Errors:          %d%n", report.errorCount()));
        sb.append(String.format("  🔧 Patch failures:   %d%n", report.patchFailureCount()));
        sb.append(String.format("  📉 Queries saved:    %d%n%n", report.totalQueriesReduced()));

        if (report.entries().isEmpty()) {
            sb.append("  No issues to report.\n");
            sb.append(SEPARATOR).append("\n");
            return sb.toString();
        }

        sb.append(SEPARATOR).append("\n");
        sb.append("  Details\n");
        sb.append(SEPARATOR).append("\n\n");

        int index = 1;
        for (PipelineReport.Entry entry : report.entries()) {
            VerificationResult v = entry.verification();
            String icon = switch (v.verdict()) {
                case APPROVED -> "✅";
                case REJECTED -> "❌";
                case ERROR -> "⚠️";
            };

            sb.append(String.format("  %d. %s %s.%s (line %d)%n",
                    index++, icon,
                    entry.issue().className(),
                    entry.issue().methodName(),
                    entry.issue().lineNumber()));
            sb.append(String.format("     Entity: %s | Lazy field: %s | Loop: %s%n",
                    entry.issue().entityType(),
                    entry.issue().lazyField(),
                    entry.issue().loopType()));
            if (entry.patch() != null) {
                sb.append(String.format("     Strategy: %s%n", entry.patch().strategy()));
                sb.append(String.format("     Fix: %s%n", entry.patch().explanation()));
            }
            sb.append(String.format("     Queries: %d → %d | %s%n%n",
                    v.queryCountBefore(), v.queryCountAfter(), v.reason()));
        }

        sb.append(SEPARATOR).append("\n");
        return sb.toString();
    }
}
