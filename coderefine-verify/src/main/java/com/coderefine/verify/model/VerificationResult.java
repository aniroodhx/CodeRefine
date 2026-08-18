package com.coderefine.verify.model;

/**
 * The outcome of verifying one patch. Metric-agnostic: {@code metricName}
 * says what was measured ("queries", "rows"), and before/after are that
 * metric's values. This lets different {@link
 * com.coderefine.verify.strategy.VerificationStrategy} implementations report
 * through one shape.
 */
public record VerificationResult(
        String issueDescription,
        String metricName,
        int valueBefore,
        int valueAfter,
        Verdict verdict,
        String reason
) {
    public enum Verdict {
        APPROVED,
        REJECTED,
        ERROR
    }

    public int reduced() {
        return valueBefore - valueAfter;
    }

    public double improvementPercentage() {
        if (valueBefore == 0) return 0;
        return ((double) reduced() / valueBefore) * 100;
    }

    public static VerificationResult approved(String issue, String metric, int before, int after) {
        return new VerificationResult(issue, metric, before, after, Verdict.APPROVED,
                String.format("%s reduced from %d to %d (%.0f%% improvement)",
                        capitalize(metric), before, after,
                        ((double) (before - after) / before) * 100));
    }

    public static VerificationResult rejected(String issue, String metric,
                                              int before, int after, String reason) {
        return new VerificationResult(issue, metric, before, after, Verdict.REJECTED, reason);
    }

    public static VerificationResult error(String issue, String reason) {
        return new VerificationResult(issue, "n/a", 0, 0, Verdict.ERROR, reason);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
