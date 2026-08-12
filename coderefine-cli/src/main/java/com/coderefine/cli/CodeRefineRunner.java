package com.coderefine.cli;

import com.coderefine.cli.pipeline.CodeRefinePipeline;
import com.coderefine.cli.report.PipelineReport;
import com.coderefine.cli.report.ReportPrinter;
import com.coderefine.llm.PatchGenerator;
import com.coderefine.llm.client.ClaudeClient;
import com.coderefine.llm.client.GeminiClient;
import com.coderefine.llm.client.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class CodeRefineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CodeRefineRunner.class);

    @Override
    public void run(String... args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        Path projectRoot = Path.of(args[0]);
        if (!Files.isDirectory(projectRoot)) {
            System.err.println("Error: '" + projectRoot + "' is not a directory.");
            return;
        }

        boolean skipVerification = hasFlag(args, "--no-verify");

        LLMClient llmClient = resolveLLMClient();
        if (llmClient == null) {
            System.err.println("""
                    Error: No LLM API key found.
                    Set one of:
                      export GEMINI_API_KEY=your-key
                      export ANTHROPIC_API_KEY=your-key
                    """);
            return;
        }

        PatchGenerator patchGenerator = new PatchGenerator(llmClient);
        CodeRefinePipeline pipeline = new CodeRefinePipeline(patchGenerator, !skipVerification);

        log.info("Starting CodeRefine analysis on {}", projectRoot);
        PipelineReport report = pipeline.run(projectRoot);

        ReportPrinter printer = new ReportPrinter();
        System.out.println(printer.format(report));
    }

    private LLMClient resolveLLMClient() {
        String geminiKey = System.getenv("GEMINI_API_KEY");
        if (geminiKey != null && !geminiKey.isBlank()) {
            String model = envOrDefault("GEMINI_MODEL", "gemini-2.5-flash");
            log.info("Using Gemini ({})", model);
            return new GeminiClient(geminiKey, model);
        }

        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            String model = envOrDefault("ANTHROPIC_MODEL", "claude-sonnet-5");
            log.info("Using Claude ({})", model);
            return new ClaudeClient(anthropicKey, model);
        }

        return null;
    }

    private String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) return true;
        }
        return false;
    }

    private void printUsage() {
        System.out.println("""
                CodeRefine — Autonomous N+1 query detection and fixing

                Usage:
                  coderefine <project-path> [options]

                Options:
                  --no-verify    Skip sandboxed verification (Layer 3)

                Environment:
                  GEMINI_API_KEY      Gemini API key (preferred if set)
                  ANTHROPIC_API_KEY   Claude API key (fallback)
                  GEMINI_MODEL        Model override (default: gemini-2.5-flash)
                  ANTHROPIC_MODEL     Model override (default: claude-sonnet-5)

                Example:
                  export GEMINI_API_KEY=your-key
                  coderefine /path/to/spring-project
                """);
    }
}
