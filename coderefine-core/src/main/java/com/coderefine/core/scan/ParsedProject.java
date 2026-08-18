package com.coderefine.core.scan;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The result of walking and parsing a project once. Detectors run against this
 * shared representation instead of each re-reading and re-parsing the tree.
 */
public record ParsedProject(
        Path projectRoot,
        Map<Path, CompilationUnit> compilationUnits,
        int filesScanned,
        int parseFailures
) {
    public boolean hasPartialCoverage() {
        return parseFailures > 0;
    }

    public List<CompilationUnit> units() {
        return List.copyOf(compilationUnits.values());
    }
}
