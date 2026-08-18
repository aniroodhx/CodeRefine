package com.coderefine.verify.strategy;

import com.coderefine.core.model.Issue;
import com.coderefine.core.model.IssueType;
import com.coderefine.verify.sandbox.VerificationScenario;

/**
 * Defines how a given issue type is proven in the sandbox: what scenario to run
 * and how to judge whether the "after" metric is an improvement over "before".
 * Mirrors the detection-side {@link com.coderefine.core.detector.Detector} —
 * pluggable verification to match pluggable detection.
 */
public interface VerificationStrategy {

    /** The issue type this strategy can verify. */
    IssueType type();

    /** Build the sandbox scenario (schema, data, before/after measurements) for this issue. */
    VerificationScenario buildScenario(Issue issue);

    /**
     * Decide whether the measured "after" value improves on "before".
     * @return true if the patch should be APPROVED on the metric.
     */
    boolean isImprovement(int before, int after);
}
