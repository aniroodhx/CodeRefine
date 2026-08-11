package com.coderefine.llm.client;

import com.coderefine.llm.model.PatchContext;
import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.llm.model.PatchSuggestion.FileChange;
import com.coderefine.llm.model.PatchSuggestion.FixStrategy;
import com.coderefine.llm.prompt.PatchPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeminiClient implements LLMClient {

    private final WebClient webClient;
    private final PatchPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiClient(String apiKey, String model) {
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("content-type", "application/json")
                .build();
        this.promptBuilder = new PatchPromptBuilder();
        this.objectMapper = new ObjectMapper();
        this.model = model;

        this.apiKey = apiKey;
    }

    private final String apiKey;

    @Override
    public PatchSuggestion generatePatch(PatchContext context) {
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(context);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(
                                Map.of("text", userPrompt)
                        ))
                ),
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "maxOutputTokens", 4096
                )
        );

        String response = webClient.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return parseResponse(response, context);
    }

    private PatchSuggestion parseResponse(String response, PatchContext context) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            String json = extractJson(content);
            JsonNode patch = objectMapper.readTree(json);

            FixStrategy strategy = FixStrategy.valueOf(patch.path("strategy").asText());
            String explanation = patch.path("explanation").asText();

            List<FileChange> changes = new ArrayList<>();
            for (JsonNode change : patch.path("changes")) {
                changes.add(new FileChange(
                        change.path("filePath").asText(),
                        change.path("originalCode").asText(),
                        change.path("patchedCode").asText(),
                        FileChange.ChangeType.valueOf(change.path("changeType").asText())
                ));
            }

            String issueDesc = String.format("N+1 on %s.%s in method %s",
                    context.entityName(), context.lazyField(), context.methodName());

            return new PatchSuggestion(issueDesc, strategy, changes, explanation);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
