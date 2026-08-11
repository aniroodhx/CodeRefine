package com.coderefine.llm.model;

import java.util.List;

public record PatchSuggestion(
        String issueDescription,
        FixStrategy strategy,
        List<FileChange> changes,
        String explanation
) {
    public enum FixStrategy {
        JOIN_FETCH_QUERY,
        ENTITY_GRAPH,
        BATCH_SIZE,
        DTO_PROJECTION
    }

    public record FileChange(
            String filePath,
            String originalCode,
            String patchedCode,
            ChangeType changeType
    ) {
        public enum ChangeType {
            MODIFY_EXISTING,
            ADD_NEW_METHOD,
            ADD_NEW_FILE
        }
    }
}
