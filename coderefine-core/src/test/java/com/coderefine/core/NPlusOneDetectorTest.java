package com.coderefine.core;

import com.coderefine.core.model.AnalysisResult;
import com.coderefine.core.model.NPlusOneIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NPlusOneDetectorTest {

    @TempDir
    Path tempDir;

    private CodeRefineAnalyzer analyzer;

    @BeforeEach
    void setup() {
        analyzer = new CodeRefineAnalyzer();
    }

    @Test
    void detectsForEachLoopOverLazyCollection() throws IOException {
        writeEntity();
        writeServiceWithNPlusOne();

        AnalysisResult result = analyzer.analyze(tempDir);

        assertTrue(result.hasIssues());
        assertEquals(1, result.issueCount());

        NPlusOneIssue issue = result.issues().get(0);
        assertEquals("OrderService", issue.className());
        assertEquals("getAllOrderItems", issue.methodName());
        assertEquals("Order", issue.entityType());
        assertEquals("items", issue.lazyField());
        assertEquals("for-each", issue.loopType());
    }

    @Test
    void noFalsePositiveForEagerFetch() throws IOException {
        writeEagerEntity();
        writeServiceWithNPlusOne();

        AnalysisResult result = analyzer.analyze(tempDir);

        assertFalse(result.hasIssues());
    }

    @Test
    void detectsStreamMapAccess() throws IOException {
        writeEntity();
        writeServiceWithStreamNPlusOne();

        AnalysisResult result = analyzer.analyze(tempDir);

        assertTrue(result.hasIssues());
        NPlusOneIssue issue = result.issues().get(0);
        assertEquals("stream", issue.loopType());
    }

    @Test
    void detectsMultipleIssuesInSameMethod() throws IOException {
        writeEntityWithMultipleRelations();
        writeServiceAccessingMultipleLazyFields();

        AnalysisResult result = analyzer.analyze(tempDir);

        assertEquals(2, result.issueCount());
    }

    private void writeEntity() throws IOException {
        String code = """
                package com.example;

                import jakarta.persistence.*;
                import java.util.List;

                @Entity
                public class Order {
                    @Id
                    private Long id;

                    @OneToMany(mappedBy = "order")
                    private List<OrderItem> items;

                    public Long getId() { return id; }
                    public List<OrderItem> getItems() { return items; }
                }
                """;
        writeFile("Order.java", code);
    }

    private void writeEagerEntity() throws IOException {
        String code = """
                package com.example;

                import jakarta.persistence.*;
                import java.util.List;

                @Entity
                public class Order {
                    @Id
                    private Long id;

                    @OneToMany(mappedBy = "order", fetch = FetchType.EAGER)
                    private List<OrderItem> items;

                    public Long getId() { return id; }
                    public List<OrderItem> getItems() { return items; }
                }
                """;
        writeFile("Order.java", code);
    }

    private void writeEntityWithMultipleRelations() throws IOException {
        String code = """
                package com.example;

                import jakarta.persistence.*;
                import java.util.List;
                import java.util.Set;

                @Entity
                public class Order {
                    @Id
                    private Long id;

                    @OneToMany(mappedBy = "order")
                    private List<OrderItem> items;

                    @ManyToMany
                    private Set<Tag> tags;

                    public Long getId() { return id; }
                    public List<OrderItem> getItems() { return items; }
                    public Set<Tag> getTags() { return tags; }
                }
                """;
        writeFile("Order.java", code);
    }

    private void writeServiceWithNPlusOne() throws IOException {
        String code = """
                package com.example;

                import java.util.List;
                import java.util.ArrayList;

                public class OrderService {
                    public List<OrderItem> getAllOrderItems(List<Order> orders) {
                        List<OrderItem> allItems = new ArrayList<>();
                        for (Order order : orders) {
                            allItems.addAll(order.getItems());
                        }
                        return allItems;
                    }
                }
                """;
        writeFile("OrderService.java", code);
    }

    private void writeServiceWithStreamNPlusOne() throws IOException {
        String code = """
                package com.example;

                import java.util.List;
                import java.util.stream.Collectors;

                public class OrderService {
                    public List<List<OrderItem>> getItemsByOrder(List<Order> orders) {
                        return orders.stream()
                                .map(Order::getItems)
                                .collect(Collectors.toList());
                    }
                }
                """;
        writeFile("OrderService.java", code);
    }

    private void writeServiceAccessingMultipleLazyFields() throws IOException {
        String code = """
                package com.example;

                import java.util.List;
                import java.util.ArrayList;

                public class OrderService {
                    public void processOrders(List<Order> orders) {
                        for (Order order : orders) {
                            order.getItems().size();
                            order.getTags().size();
                        }
                    }
                }
                """;
        writeFile("OrderService.java", code);
    }

    private void writeFile(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }
}
