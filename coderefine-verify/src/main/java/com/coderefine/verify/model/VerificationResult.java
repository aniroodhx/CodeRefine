package com.coderefine.verify.model;

public record VerificationResult(
        String issueDescription,
        int queryCountBefore,
        int queryCountAfter,
        boolean testsPassBefore,
        boolean testsPassAfter,
        Verdict verdict,
        String reason
) {
    public enum Verdict {
        APPROVED,
        REJECTED,
        ERROR
    }

    public int queriesReduced() {
        return queryCountBefore - queryCountAfter;
    }

    public double improvementPercentage() {
        if (queryCountBefore == 0) return 0;
        return ((double) queriesReduced() / queryCountBefore) * 100;
    }

    public static VerificationResult approved(String issue, int before, int after) {
        return new VerificationResult(issue, before, after, true, true,
                Verdict.APPROVED,
                String.format("Query count reduced from %d to %d (%.0f%% improvement)",
                        before, after, ((double)(before - after) / before) * 100));
    }

    public static VerificationResult rejected(String issue, int before, int after,
                                              boolean testsBefore, boolean testsAfter, String reason) {
        return new VerificationResult(issue, before, after, testsBefore, testsAfter,
                Verdict.REJECTED, reason);
    }

    public static VerificationResult error(String issue, String reason) {
        return new VerificationResult(issue, 0, 0, false, false, Verdict.ERROR, reason);
    }
}
