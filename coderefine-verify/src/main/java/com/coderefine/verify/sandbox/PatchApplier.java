package com.coderefine.verify.sandbox;

import com.coderefine.llm.model.PatchSuggestion;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class PatchApplier {

    public void applyPatch(PatchSuggestion patch, Path projectRoot) throws IOException {
        for (PatchSuggestion.FileChange change : patch.changes()) {
            Path targetFile = resolveFile(projectRoot, change.filePath());

            switch (change.changeType()) {
                case ADD_NEW_FILE -> {
                    Files.createDirectories(targetFile.getParent());
                    Files.writeString(targetFile, change.patchedCode());
                }
                case ADD_NEW_METHOD -> {
                    String existing = Files.readString(targetFile);
                    String patched = insertMethodBeforeLastBrace(existing, change.patchedCode());
                    Files.writeString(targetFile, patched);
                }
                case MODIFY_EXISTING -> {
                    String existing = Files.readString(targetFile);
                    String patched = existing.replace(change.originalCode(), change.patchedCode());
                    Files.writeString(targetFile, patched);
                }
            }
        }
    }

    public void revertPatch(PatchSuggestion patch, Path projectRoot) throws IOException {
        for (PatchSuggestion.FileChange change : patch.changes()) {
            Path targetFile = resolveFile(projectRoot, change.filePath());

            switch (change.changeType()) {
                case ADD_NEW_FILE -> Files.deleteIfExists(targetFile);
                case ADD_NEW_METHOD -> {
                    String current = Files.readString(targetFile);
                    String reverted = current.replace(change.patchedCode(), "");
                    Files.writeString(targetFile, reverted);
                }
                case MODIFY_EXISTING -> {
                    String current = Files.readString(targetFile);
                    String reverted = current.replace(change.patchedCode(), change.originalCode());
                    Files.writeString(targetFile, reverted);
                }
            }
        }
    }

    private Path resolveFile(Path projectRoot, String filePath) throws IOException {
        Path direct = projectRoot.resolve(filePath);
        if (Files.exists(direct)) return direct;

        try (Stream<Path> files = Files.walk(projectRoot)) {
            return files
                    .filter(p -> p.getFileName().toString().equals(filePath) ||
                            p.toString().endsWith(filePath))
                    .findFirst()
                    .orElse(direct);
        }
    }

    private String insertMethodBeforeLastBrace(String source, String method) {
        int lastBrace = source.lastIndexOf('}');
        if (lastBrace < 0) return source + "\n" + method;

        return source.substring(0, lastBrace) +
                "\n    " + method.strip() + "\n" +
                source.substring(lastBrace);
    }
}
