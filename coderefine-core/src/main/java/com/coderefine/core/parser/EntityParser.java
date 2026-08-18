package com.coderefine.core.parser;

import com.coderefine.core.model.EntityRelationship;
import com.coderefine.core.model.EntityRelationship.FetchType;
import com.coderefine.core.model.EntityRelationship.RelationType;
import com.coderefine.core.scan.ParsedProject;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class EntityParser {

    private static final Set<String> RELATION_ANNOTATIONS = Set.of(
            "OneToMany", "ManyToOne", "ManyToMany", "OneToOne"
    );

    private static final Map<String, RelationType> RELATION_TYPE_MAP = Map.of(
            "OneToMany", RelationType.ONE_TO_MANY,
            "ManyToOne", RelationType.MANY_TO_ONE,
            "ManyToMany", RelationType.MANY_TO_MANY,
            "OneToOne", RelationType.ONE_TO_ONE
    );

    private static final Map<RelationType, FetchType> DEFAULT_FETCH = Map.of(
            RelationType.ONE_TO_MANY, FetchType.LAZY,
            RelationType.MANY_TO_MANY, FetchType.LAZY,
            RelationType.MANY_TO_ONE, FetchType.EAGER,
            RelationType.ONE_TO_ONE, FetchType.EAGER
    );

    /** Build the entity map from an already-parsed project (preferred — no re-parsing). */
    public Map<String, List<EntityRelationship>> parseEntities(ParsedProject project) {
        Map<String, List<EntityRelationship>> entityMap = new HashMap<>();
        for (CompilationUnit cu : project.units()) {
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(this::isEntity)
                    .forEach(cls -> parseEntityClass(cls, entityMap));
        }
        return entityMap;
    }

    /** Walk and parse a project directory directly. Retained for standalone/test use. */
    public Map<String, List<EntityRelationship>> parseEntities(Path projectRoot) throws IOException {
        Map<String, List<EntityRelationship>> entityMap = new HashMap<>();

        try (Stream<Path> files = Files.walk(projectRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> parseFile(file, entityMap));
        }

        return entityMap;
    }

    private void parseFile(Path file, Map<String, List<EntityRelationship>> entityMap) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(this::isEntity)
                    .forEach(cls -> parseEntityClass(cls, entityMap));
        } catch (Exception e) {
            // Skip files that can't be parsed
        }
    }

    private boolean isEntity(ClassOrInterfaceDeclaration cls) {
        return cls.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals("Entity"));
    }

    private void parseEntityClass(ClassOrInterfaceDeclaration cls,
                                  Map<String, List<EntityRelationship>> entityMap) {
        String className = cls.getNameAsString();
        List<EntityRelationship> relationships = new ArrayList<>();

        for (FieldDeclaration field : cls.getFields()) {
            for (AnnotationExpr annotation : field.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if (RELATION_ANNOTATIONS.contains(annotationName)) {
                    RelationType relationType = RELATION_TYPE_MAP.get(annotationName);
                    FetchType fetchType = extractFetchType(annotation, relationType);
                    String fieldName = field.getVariables().get(0).getNameAsString();
                    String fieldType = field.getVariables().get(0).getTypeAsString();

                    relationships.add(new EntityRelationship(
                            className, fieldName, fieldType, relationType, fetchType
                    ));
                }
            }
        }

        if (!relationships.isEmpty()) {
            entityMap.put(className, relationships);
        }
    }

    private FetchType extractFetchType(AnnotationExpr annotation, RelationType relationType) {
        if (annotation.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("fetch")) {
                    String value = pair.getValue().toString();
                    if (value.contains("EAGER")) return FetchType.EAGER;
                    if (value.contains("LAZY")) return FetchType.LAZY;
                }
            }
        }
        return DEFAULT_FETCH.get(relationType);
    }
}
