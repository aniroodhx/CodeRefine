package com.coderefine.llm.prompt;

import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.core.model.UnboundedCollectionIssue;
import com.coderefine.llm.model.PatchContext;

public class PatchPromptBuilder {

    public String buildSystemPrompt() {
        return """
                You are a senior backend engineer specializing in JPA/Hibernate performance optimization.
                You fix performance anti-patterns in Spring Data JPA code.

                Rules:
                - Make the smallest change that fixes the reported issue; do not refactor unrelated code
                - Never change the public API of the affected method (same signature, same return type)
                  unless the fix inherently requires it (e.g. adding a Pageable parameter) — if so, keep it minimal
                - If a repository already exists, add a new method rather than modifying existing ones
                - If no repository exists, create one
                - Use Spring Data JPA conventions (derived method names, @Query, @EntityGraph, Pageable)

                For N+1 queries: prefer @EntityGraph or a JOIN FETCH query.
                For unbounded collections: introduce pagination (Pageable/Page) or an explicit Limit.

                Respond ONLY with valid JSON matching this exact schema:
                {
                  "strategy": "JOIN_FETCH_QUERY" | "ENTITY_GRAPH" | "BATCH_SIZE" | "DTO_PROJECTION" | "PAGINATION",
                  "explanation": "one-line explanation of the fix",
                  "changes": [
                    {
                      "filePath": "relative path to file",
                      "originalCode": "the original code block being replaced (empty if new file)",
                      "patchedCode": "the fixed/new code",
                      "changeType": "MODIFY_EXISTING" | "ADD_NEW_METHOD" | "ADD_NEW_FILE"
                    }
                  ]
                }
                """;
    }

    public String buildUserPrompt(PatchContext context) {
        return switch (context.issue().type()) {
            case N_PLUS_ONE -> buildNPlusOnePrompt(context);
            case UNBOUNDED_COLLECTION -> buildUnboundedPrompt(context);
        };
    }

    private String buildNPlusOnePrompt(PatchContext context) {
        NPlusOneIssue issue = (NPlusOneIssue) context.issue();
        StringBuilder prompt = new StringBuilder();

        prompt.append("## N+1 Query Issue Detected\n\n");
        prompt.append(String.format("**Entity:** %s\n", issue.entityType()));
        prompt.append(String.format("**Lazy field:** %s\n", issue.lazyField()));
        prompt.append(String.format("**Accessed in method:** %s (line %d)\n\n",
                issue.methodName(), issue.lineNumber()));

        appendSource(prompt, "Entity Source", context.entitySource());
        appendSource(prompt, "Service Source (contains the N+1 bug)", context.primarySource());
        appendRepository(prompt, context.repositorySource());

        prompt.append("Fix the N+1 issue. Return JSON only.");
        return prompt.toString();
    }

    private String buildUnboundedPrompt(PatchContext context) {
        UnboundedCollectionIssue issue = (UnboundedCollectionIssue) context.issue();
        StringBuilder prompt = new StringBuilder();

        prompt.append("## Unbounded Collection Issue Detected\n\n");
        prompt.append(String.format("**Repository call:** %s.%s()\n",
                issue.repositoryVariable(), issue.repositoryCall()));
        prompt.append(String.format("**In method:** %s (line %d)\n\n",
                issue.methodName(), issue.lineNumber()));
        prompt.append("This call loads the entire table into memory with no pagination. "
                + "Introduce a bound (Pageable/Page or an explicit limit).\n\n");

        appendSource(prompt, "Source (contains the unbounded call)", context.primarySource());
        appendRepository(prompt, context.repositorySource());

        prompt.append("Fix the unbounded query. Return JSON only.");
        return prompt.toString();
    }

    private void appendSource(StringBuilder prompt, String heading, String source) {
        if (source == null || source.isEmpty()) return;
        prompt.append("## ").append(heading).append("\n```java\n");
        prompt.append(source);
        prompt.append("\n```\n\n");
    }

    private void appendRepository(StringBuilder prompt, String repositorySource) {
        if (repositorySource != null && !repositorySource.isEmpty()) {
            prompt.append("## Existing Repository\n```java\n");
            prompt.append(repositorySource);
            prompt.append("\n```\n\n");
        } else {
            prompt.append("## Repository: None found. Create one if needed.\n\n");
        }
    }
}
