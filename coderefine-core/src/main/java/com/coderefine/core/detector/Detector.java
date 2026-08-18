package com.coderefine.core.detector;

import com.coderefine.core.model.Issue;
import com.coderefine.core.model.IssueType;
import com.coderefine.core.scan.ParsedProject;

import java.util.List;

/**
 * A single anti-pattern detector. Each implementation inspects the shared
 * {@link ParsedProject} and returns the issues it found. New detectors slot in
 * by implementing this interface and registering with the analyzer.
 */
public interface Detector {

    /** The category of issue this detector produces. */
    IssueType type();

    /** Inspect the parsed project and return any issues of {@link #type()}. */
    List<Issue> detect(ParsedProject project);
}
