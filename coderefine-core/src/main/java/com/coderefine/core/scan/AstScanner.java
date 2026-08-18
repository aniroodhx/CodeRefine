package com.coderefine.core.scan;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Walks a project, parses every .java file exactly once, and records how many
 * parsed vs. failed. All detectors share the resulting {@link ParsedProject},
 * so parsing cost is paid once and coverage is reported honestly.
 */
public class AstScanner {

    private static final Logger log = LoggerFactory.getLogger(AstScanner.class);

    public ParsedProject scan(Path projectRoot) throws IOException {
        Map<Path, CompilationUnit> units = new LinkedHashMap<>();
        int scanned = 0;
        int failed = 0;

        try (Stream<Path> files = Files.walk(projectRoot)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                scanned++;
                try {
                    units.put(file, StaticJavaParser.parse(file));
                } catch (Exception e) {
                    failed++;
                    log.debug("Skipping unparseable file {}: {}", file, e.getMessage());
                }
            }
        }

        if (failed > 0) {
            log.warn("Parsed {}/{} Java files; {} could not be parsed and were skipped. "
                    + "Detection coverage is partial.", scanned - failed, scanned, failed);
        } else {
            log.info("Parsed {}/{} Java files successfully.", scanned, scanned);
        }

        return new ParsedProject(projectRoot, units, scanned, failed);
    }
}
