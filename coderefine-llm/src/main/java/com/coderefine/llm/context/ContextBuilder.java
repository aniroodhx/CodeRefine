package com.coderefine.llm.context;

import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.llm.model.PatchContext;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class ContextBuilder {

    public PatchContext buildContext(NPlusOneIssue issue, Path projectRoot) throws IOException {
        String entitySource = findAndReadFile(projectRoot, issue.entityType() + ".java");
        String serviceSource = readFile(Path.of(issue.filePath()));
        String repositorySource = findAndReadFile(projectRoot, issue.entityType() + "Repository.java");

        return new PatchContext(
                entitySource,
                serviceSource,
                repositorySource,
                issue.entityType(),
                issue.lazyField(),
                issue.methodName(),
                issue.lineNumber()
        );
    }

    private String findAndReadFile(Path root, String fileName) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .map(this::readFileSafe)
                    .orElse("");
        }
    }

    private String readFile(Path path) {
        return readFileSafe(path);
    }

    private String readFileSafe(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}
