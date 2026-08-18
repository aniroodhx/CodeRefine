package com.coderefine.llm.context;

import com.coderefine.core.model.Issue;
import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.llm.model.PatchContext;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Gathers the minimal set of source files the LLM needs to fix one issue.
 * The primary source (the file containing the bug) is always included; entity
 * and repository sources are added when relevant to the issue type.
 */
public class ContextBuilder {

    public PatchContext buildContext(Issue issue, Path projectRoot) throws IOException {
        String primarySource = readFileSafe(Path.of(issue.filePath()));

        String entitySource = "";
        String repositorySource = "";

        if (issue instanceof NPlusOneIssue n) {
            entitySource = findAndReadFile(projectRoot, n.entityType() + ".java");
            repositorySource = findAndReadFile(projectRoot, n.entityType() + "Repository.java");
        } else {
            // For unbounded (and future) issues, try to surface any repository in the project.
            repositorySource = findFirstRepository(projectRoot);
        }

        return new PatchContext(issue, primarySource, entitySource, repositorySource);
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

    private String findFirstRepository(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith("Repository.java"))
                    .findFirst()
                    .map(this::readFileSafe)
                    .orElse("");
        }
    }

    private String readFileSafe(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}
