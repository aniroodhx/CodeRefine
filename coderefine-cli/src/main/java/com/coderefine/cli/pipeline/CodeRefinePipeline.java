package com.coderefine.cli.pipeline;

import com.coderefine.cli.report.PipelineReport;
import com.coderefine.core.CodeRefineAnalyzer;
import com.coderefine.core.model.AnalysisResult;
import com.coderefine.core.model.Issue;
import com.coderefine.llm.PatchGenerator;
import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.verify.model.VerificationResult;
import com.coderefine.verify.sandbox.SandboxVerifier;
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
    private final boolean verificationEnabled;

    public CodeRefinePipeline(PatchGenerator patchGenerator, boolean verificationEnabled) {
        this.analyzer = new CodeRefineAnalyzer();
        this.patchGenerator = patchGenerator;
        this.verifier = new SandboxVerifier();
        this.verificationEnabled = verificationEnabled;
    }

    public PipelineReport run(Path projectRoot) throws IOException {
        PipelineReport report = new PipelineReport();

        log.info("Layer 1: Analyzing {} for performance anti-patterns...", projectRoot);
        AnalysisResult analysis = analyzer.analyze(projectRoot);
        List<Issue> issues = analysis.issues();
        log.info("Scanned {} Java file(s); {} could not be parsed.",
                analysis.filesScanned(), analysis.parseFailures());
        if (analysis.hasPartialCoverage()) {
            log.warn("Detection coverage is PARTIAL — {} file(s) were skipped. "
                    + "Results may under-report issues.", analysis.parseFailures());
        }
        log.info("Detected {} issue(s)", issues.size());

        if (issues.isEmpty()) {
            return report;
        }

        for (Issue issue : issues) {
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
                verification = verifier.verify(issue, patch);
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
