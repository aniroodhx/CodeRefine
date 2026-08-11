package com.coderefine.llm.prompt;

import com.coderefine.llm.model.PatchContext;

public class PatchPromptBuilder {

    public String buildSystemPrompt() {
        return """
                You are a senior backend engineer specializing in JPA/Hibernate performance optimization.
                Your task is to fix N+1 query problems in Spring Data JPA code.

                Rules:
                - Prefer @EntityGraph or JOIN FETCH in a custom repository method
                - If a repository already exists, add a new method rather than modifying existing ones
                - If no repository exists, create one
                - Never change the public API of the service method (same signature, same return type)
                - Keep changes minimal — only fix the N+1 issue, don't refactor unrelated code
                - Use Spring Data JPA conventions (method naming, @Query annotation)

                Respond ONLY with valid JSON matching this exact schema:
                {
                  "strategy": "JOIN_FETCH_QUERY" | "ENTITY_GRAPH" | "BATCH_SIZE" | "DTO_PROJECTION",
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
        StringBuilder prompt = new StringBuilder();

        prompt.append("## N+1 Query Issue Detected\n\n");
        prompt.append(String.format("**Entity:** %s\n", context.entityName()));
        prompt.append(String.format("**Lazy field:** %s\n", context.lazyField()));
        prompt.append(String.format("**Accessed in method:** %s (line %d)\n\n", context.methodName(), context.lineNumber()));

        prompt.append("## Entity Source\n```java\n");
        prompt.append(context.entitySource());
        prompt.append("\n```\n\n");

        prompt.append("## Service Source (contains the N+1 bug)\n```java\n");
        prompt.append(context.serviceSource());
        prompt.append("\n```\n\n");

        if (!context.repositorySource().isEmpty()) {
            prompt.append("## Existing Repository\n```java\n");
            prompt.append(context.repositorySource());
            prompt.append("\n```\n\n");
        } else {
            prompt.append("## Repository: None exists yet. Create one if needed.\n\n");
        }

        prompt.append("Fix the N+1 issue. Return JSON only.");

        return prompt.toString();
    }
}
