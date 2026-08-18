package com.coderefine.core;

import com.coderefine.core.detector.Detector;
import com.coderefine.core.detector.NPlusOneDetector;
import com.coderefine.core.detector.UnboundedCollectionDetector;
import com.coderefine.core.model.AnalysisResult;
import com.coderefine.core.model.Issue;
import com.coderefine.core.scan.AstScanner;
import com.coderefine.core.scan.ParsedProject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans a project once, then runs every registered {@link Detector} against the
 * shared parse result. Adding a detector is a one-line registration here.
 */
public class CodeRefineAnalyzer {

    private final AstScanner scanner;
    private final List<Detector> detectors;

    public CodeRefineAnalyzer() {
        this(List.of(
                new NPlusOneDetector(),
                new UnboundedCollectionDetector()
        ));
    }

    public CodeRefineAnalyzer(List<Detector> detectors) {
        this.scanner = new AstScanner();
        this.detectors = detectors;
    }

    public AnalysisResult analyze(Path projectRoot) throws IOException {
        ParsedProject project = scanner.scan(projectRoot);

        List<Issue> issues = new ArrayList<>();
        for (Detector detector : detectors) {
            issues.addAll(detector.detect(project));
        }

        return new AnalysisResult(projectRoot, issues,
                project.filesScanned(), project.parseFailures());
    }
}
