package com.coderefine.cli.pipeline;

import com.coderefine.cli.report.PipelineReport;
import com.coderefine.core.CodeRefineAnalyzer;
import com.coderefine.core.model.AnalysisResult;
import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.llm.PatchGenerator;
import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.verify.model.VerificationResult;
import com.coderefine.verify.sandbox.SandboxVerifier;
import com.coderefine.verify.sandbox.VerificationScenario;
import com.coderefine.verify.scenario.NPlusOneScenarioBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class CodeRefinePipeline {

    private static final Logger log = LoggerFactory.getLogger(CodeRefinePipeline.class);

    private final CodeRefineAnalyzer analyzer;
    private final PatchGenerator patchGenerator;
    private final SandboxVerifier verifier;
    private final NPlusOneScenarioBuilder scenarioBuilder;
    private final boolean verificationEnabled;

    public CodeRefinePipeline(PatchGenerator patchGenerator, boolean verificationEnabled) {
        this.analyzer = new CodeRefineAnalyzer();
        this.patchGenerator = patchGenerator;
        this.verifier = new SandboxVerifier();
        this.scenarioBuilder = new NPlusOneScenarioBuilder();
        this.verificationEnabled = verificationEnabled;
    }

    public PipelineReport run(Path projectRoot) throws IOException {
        PipelineReport report = new PipelineReport();

        log.info("Layer 1: Analyzing {} for N+1 patterns...", projectRoot);
        AnalysisResult analysis = analyzer.analyze(projectRoot);
        List<NPlusOneIssue> issues = analysis.issues();
        log.info("Detected {} N+1 issue(s)", issues.size());

        if (issues.isEmpty()) {
            return report;
        }

        for (NPlusOneIssue issue : issues) {
            log.info("Processing: {}", issue);

            PatchSuggestion patch;
            try {
                log.info("Layer 2: Generating patch via LLM...");
                List<PatchSuggestion> patches =
                        patchGenerator.generatePatches(List.of(issue), projectRoot);
                if (patches.isEmpty()) {
                    report.addPatchFailure(issue);
                    continue;
                }
                patch = patches.get(0);
            } catch (Exception e) {
                log.warn("Patch generation failed for {}: {}", issue, e.getMessage());
                report.addPatchFailure(issue);
                continue;
            }

            VerificationResult verification;
            if (verificationEnabled) {
                log.info("Layer 3: Verifying patch in sandbox...");
                VerificationScenario scenario = scenarioBuilder.buildScenario(issue);
                verification = verifier.verify(patch, scenario);
                log.info("Verdict: {} — {}", verification.verdict(), verification.reason());
            } else {
                verification = VerificationResult.error(
                        patch.issueDescription(), "Verification skipped (disabled)");
            }

            report.addEntry(issue, patch, verification);
        }

        return report;
    }
}
