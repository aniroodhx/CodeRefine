package com.coderefine.cli.report;

import com.coderefine.verify.model.VerificationResult;

public class ReportPrinter {

    private static final String SEPARATOR = "═".repeat(70);

    public String format(PipelineReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n").append(SEPARATOR).append("\n");
        sb.append("  CodeRefine — Performance Analysis Report\n");
        sb.append(SEPARATOR).append("\n\n");

        sb.append(String.format("  Issues detected:    %d%n", report.totalIssuesDetected()));
        sb.append(String.format("  ✅ Fixes approved:   %d%n", report.approvedCount()));
        sb.append(String.format("  ❌ Fixes rejected:   %d%n", report.rejectedCount()));
        sb.append(String.format("  ⚠️  Errors:          %d%n", report.errorCount()));
        sb.append(String.format("  🔧 Patch failures:   %d%n%n", report.patchFailureCount()));

        if(report.entries().isEmpty() && report.patchFailures().isEmpty()){
            sb.append("  No issues to report.\n");
            sb.append(SEPARATOR).append("\n");
            return sb.toString();
        }

        sb.append(SEPARATOR).append("\n");
        sb.append("  Details\n");
        sb.append(SEPARATOR).append("\n\n");

        int index = 1;
        for(PipelineReport.Entry entry : report.entries()) {
            VerificationResult v = entry.verification();
            String icon = switch (v.verdict()) {
                case APPROVED -> "✅";
                case REJECTED -> "❌";
                case ERROR -> "⚠️";
            };

            sb.append(String.format("  %d. %s [%s] %s.%s (line %d)%n",
                    index++, icon,
                    entry.issue().type().label(),
                    entry.issue().className(),
                    entry.issue().methodName(),
                    entry.issue().lineNumber()));
            sb.append(String.format("     %s%n", entry.issue().description()));
            if (entry.patch() != null) {
                sb.append(String.format("     Strategy: %s%n", entry.patch().strategy()));
                sb.append(String.format("     Fix: %s%n", entry.patch().explanation()));
            }
            if (v.verdict() != VerificationResult.Verdict.ERROR) {
                sb.append(String.format("     %s: %d → %d | %s%n%n",
                        capitalize(v.metricName()), v.valueBefore(), v.valueAfter(), v.reason()));
            } else {
                sb.append(String.format("     %s%n%n", v.reason()));
            }
        }
            if(!report.patchFailures().isEmpty()) {
                sb.append(String.format("  🔧 Detected but no patch produced (%d):%n",
                        report.patchFailures().size()));
                for(com.coderefine.core.model.Issue issue : report.patchFailures()) {
                    sb.append(String.format("     • [%s] %s.%s (line %d)%n",
                            issue.type().label(), issue.className(),
                            issue.methodName(), issue.lineNumber()));
                }
                sb.append("\n");
            }

        if(report.approvedCount() > 0) {
            sb.append(String.format("  ➜ %d verified fix(es) accepted after proving measurable improvement.%n%n",
                    report.approvedCount()));
        }

        sb.append(SEPARATOR).append("\n");
        return sb.toString();
    }

    private String capitalize(String s) {
        return (s==null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
