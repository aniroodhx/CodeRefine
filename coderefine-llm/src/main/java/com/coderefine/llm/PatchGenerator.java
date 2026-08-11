package com.coderefine.llm;

import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.llm.client.LLMClient;
import com.coderefine.llm.context.ContextBuilder;
import com.coderefine.llm.model.PatchContext;
import com.coderefine.llm.model.PatchSuggestion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PatchGenerator {

    private final LLMClient llmClient;
    private final ContextBuilder contextBuilder;

    public PatchGenerator(LLMClient llmClient) {
        this.llmClient = llmClient;
        this.contextBuilder = new ContextBuilder();
    }

    public List<PatchSuggestion> generatePatches(List<NPlusOneIssue> issues, Path projectRoot)
            throws IOException {
        List<PatchSuggestion> patches = new ArrayList<>();

        for (NPlusOneIssue issue : issues) {
            PatchContext context = contextBuilder.buildContext(issue, projectRoot);
            PatchSuggestion patch = llmClient.generatePatch(context);
            patches.add(patch);
        }

        return patches;
    }
}
