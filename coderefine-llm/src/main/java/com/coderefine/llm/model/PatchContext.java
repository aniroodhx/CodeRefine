package com.coderefine.llm.model;

import com.coderefine.core.model.Issue;

/**
 * The minimal context sent to the LLM for one issue: the issue itself plus the
 * few source files relevant to fixing it. Which files are populated depends on
 * the issue type (an N+1 needs the entity; an unbounded call needs the repo).
 */
public record PatchContext(
        Issue issue,
        String primarySource,
        String entitySource,
        String repositorySource
) {}
