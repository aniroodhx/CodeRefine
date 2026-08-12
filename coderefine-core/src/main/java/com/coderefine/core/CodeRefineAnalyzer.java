package com.coderefine.core;

import com.coderefine.core.detector.NPlusOneDetector;
import com.coderefine.core.model.AnalysisResult;
import com.coderefine.core.model.EntityRelationship;
import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.core.parser.EntityParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CodeRefineAnalyzer {

    private final EntityParser entityParser;

    public CodeRefineAnalyzer() {
        this.entityParser = new EntityParser();
    }

    public AnalysisResult analyze(Path projectRoot) throws IOException {
        Map<String, List<EntityRelationship>> entityMap = entityParser.parseEntities(projectRoot);
        NPlusOneDetector detector = new NPlusOneDetector(entityMap);
        List<NPlusOneIssue> issues = detector.detect(projectRoot);

        return new AnalysisResult(projectRoot, entityMap, issues,
                detector.getFilesScanned(), detector.getParseFailures());
    }
}
