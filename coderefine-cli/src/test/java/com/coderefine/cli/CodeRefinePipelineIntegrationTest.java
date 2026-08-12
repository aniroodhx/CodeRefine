package com.coderefine.cli;

import com.coderefine.cli.pipeline.CodeRefinePipeline;
import com.coderefine.cli.report.PipelineReport;
import com.coderefine.cli.report.ReportPrinter;
import com.coderefine.llm.PatchGenerator;
import com.coderefine.llm.client.LLMClient;
import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.llm.model.PatchSuggestion.FileChange;
import com.coderefine.llm.model.PatchSuggestion.FixStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeRefinePipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void fullPipelineDetectsPatchesAndVerifies() throws IOException {
        writeEntity();
        writeService();

        LLMClient mockClient = context -> new PatchSuggestion(
                "N+1 on Order.items",
                FixStrategy.JOIN_FETCH_QUERY,
                List.of(new FileChange(
                        "OrderRepository.java",
                        "",
                        "@Query(\"SELECT o FROM Order o JOIN FETCH o.items\") List<Order> findAllWithItems();",
                        FileChange.ChangeType.ADD_NEW_METHOD
                )),
                "Added JOIN FETCH query"
        );

        PatchGenerator patchGenerator = new PatchGenerator(mockClient);
        CodeRefinePipeline pipeline = new CodeRefinePipeline(patchGenerator, true);

        PipelineReport report = pipeline.run(tempDir);

        assertEquals(1, report.totalIssuesDetected());
        assertEquals(1, report.approvedCount());
        assertEquals(0, report.rejectedCount());
        assertTrue(report.totalQueriesReduced() > 0,
                "Verified patch should reduce query count");

        String output = new ReportPrinter().format(report);
        assertTrue(output.contains("Fixes approved"));
        assertTrue(output.contains("JOIN_FETCH_QUERY"));
        System.out.println(output);
    }

    @Test
    void pipelineWithVerificationDisabledSkipsSandbox() throws IOException {
        writeEntity();
        writeService();

        LLMClient mockClient = context -> new PatchSuggestion(
                "N+1 on Order.items", FixStrategy.ENTITY_GRAPH,
                List.of(), "test");

        CodeRefinePipeline pipeline = new CodeRefinePipeline(
                new PatchGenerator(mockClient), false);

        PipelineReport report = pipeline.run(tempDir);

        assertEquals(1, report.totalIssuesDetected());
        assertEquals(1, report.errorCount()); // "verification skipped" recorded as error verdict
    }

    @Test
    void cleanProjectProducesNoIssues() throws IOException {
        Files.writeString(tempDir.resolve("Clean.java"), """
                package com.shop;
                public class Clean {
                    public int add(int a, int b) { return a + b; }
                }
                """);

        LLMClient mockClient = context -> { throw new AssertionError("should not be called"); };
        CodeRefinePipeline pipeline = new CodeRefinePipeline(
                new PatchGenerator(mockClient), true);

        PipelineReport report = pipeline.run(tempDir);

        assertEquals(0, report.totalIssuesDetected());
    }

    private void writeEntity() throws IOException {
        Files.writeString(tempDir.resolve("Order.java"), """
                package com.shop;

                import jakarta.persistence.*;
                import java.util.List;

                @Entity
                public class Order {
                    @Id
                    private Long id;

                    @OneToMany(mappedBy = "order")
                    private List<OrderItem> items;

                    public List<OrderItem> getItems() { return items; }
                }
                """);
    }

    private void writeService() throws IOException {
        Files.writeString(tempDir.resolve("OrderService.java"), """
                package com.shop;

                import java.util.List;
                import java.util.ArrayList;

                public class OrderService {
                    public List<OrderItem> collectAllItems(List<Order> orders) {
                        List<OrderItem> all = new ArrayList<>();
                        for (Order order : orders) {
                            all.addAll(order.getItems());
                        }
                        return all;
                    }
                }
                """);
    }
}
