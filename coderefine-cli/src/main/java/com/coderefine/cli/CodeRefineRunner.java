package com.coderefine.cli;

import com.coderefine.cli.config.DotEnvLoader;
import com.coderefine.cli.pipeline.CodeRefinePipeline;
import com.coderefine.cli.report.PipelineReport;
import com.coderefine.cli.report.ReportPrinter;
import com.coderefine.llm.PatchGenerator;
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

        DotEnvLoader env = new DotEnvLoader().load(Path.of(".env"));

        LLMClient llmClient = resolveLLMClient(env);
        if (llmClient == null) {
            System.err.println("""
                    Error: No Gemini API key found.
                    Set it in a .env file (see .env.example) or export one:
                      GEMINI_API_KEY=your-key
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

    private LLMClient resolveLLMClient(DotEnvLoader env) {
        String geminiKey = env.get("GEMINI_API_KEY");
        if (geminiKey != null && !geminiKey.isBlank()) {
            String model = env.getOrDefault("GEMINI_MODEL", "gemini-2.5-flash");
            log.info("Using Gemini ({})", model);
            return new GeminiClient(geminiKey, model);
        }

        return null;
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

                Configuration (via .env file or environment variables):
                  GEMINI_API_KEY      Gemini API key
                  GEMINI_MODEL        Model override (default: gemini-2.5-flash)

                Setup:
                  cp .env.example .env    # then add your key to .env
                  coderefine /path/to/spring-project
                """);
    }
}
