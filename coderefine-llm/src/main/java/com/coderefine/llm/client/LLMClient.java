package com.coderefine.llm.client;

import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.llm.model.PatchContext;

public interface LLMClient {
    PatchSuggestion generatePatch(PatchContext context);
}
