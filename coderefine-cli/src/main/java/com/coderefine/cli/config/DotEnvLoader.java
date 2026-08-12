package com.coderefine.cli.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads key=value pairs from a `.env` file into an in-memory map.
 * Real environment variables always take precedence over `.env` values,
 * so CI/exported vars are never overridden.
 */
public class DotEnvLoader {

    private static final Logger log = LoggerFactory.getLogger(DotEnvLoader.class);

    private final Map<String, String> values = new HashMap<>();

    public DotEnvLoader load(Path envFile) {
        if (!Files.isRegularFile(envFile)) {
            return this;
        }

        try {
            for (String rawLine : Files.readAllLines(envFile)) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).strip();
                String value = stripQuotes(line.substring(eq + 1).strip());
                if (!value.isEmpty()) {
                    values.put(key, value);
                }
            }
            log.info("Loaded {} value(s) from {}", values.size(), envFile);
        } catch (IOException e) {
            log.warn("Could not read {}: {}", envFile, e.getMessage());
        }

        return this;
    }

    /** Real environment variables win; `.env` is only a fallback. */
    public String get(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return values.get(key);
    }

    public String getOrDefault(String key, String defaultValue) {
        String value = get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 &&
                ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
