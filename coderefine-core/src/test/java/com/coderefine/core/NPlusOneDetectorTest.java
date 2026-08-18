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

        NPlusOneIssue issue = (NPlusOneIssue) result.issues().get(0);
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
        NPlusOneIssue issue = (NPlusOneIssue) result.issues().get(0);
        assertEquals("stream", issue.loopType());
    }

    @Test
    void detectsMultipleIssuesInSameMethod() throws IOException {
        writeEntityWithMultipleRelations();
        writeServiceAccessingMultipleLazyFields();

        AnalysisResult result = analyzer.analyze(tempDir);

        assertEquals(2, result.issueCount());
    }

    @Test
    void detectsLambdaForEachAccess() throws IOException {
        writeEntity();
        writeFile("OrderService.java", """
                package com.example;

                import java.util.List;

                public class OrderService {
                    public void printItemCounts(List<Order> orders) {
                        orders.forEach(order -> {
                            int count = order.getItems().size();
                            System.out.println(count);
                        });
                    }
                }
                """);

        AnalysisResult result = analyzer.analyze(tempDir);

        assertTrue(result.hasIssues(), "Lambda-based lazy access should be detected");
        NPlusOneIssue issue = (NPlusOneIssue) result.issues().get(0);
        assertEquals("Order", issue.entityType());
        assertEquals("items", issue.lazyField());
        assertEquals("stream", issue.loopType());
    }

    @Test
    void detectsLambdaExpressionBodyAccess() throws IOException {
        writeEntity();
        writeFile("OrderService.java", """
                package com.example;

                import java.util.List;
                import java.util.stream.Collectors;

                public class OrderService {
                    public List<Integer> itemCounts(List<Order> orders) {
                        return orders.stream()
                                .map(order -> order.getItems().size())
                                .collect(Collectors.toList());
                    }
                }
                """);

        AnalysisResult result = analyzer.analyze(tempDir);

        assertTrue(result.hasIssues(), "Lambda expression-body lazy access should be detected");
        assertEquals("items", ((NPlusOneIssue) result.issues().get(0)).lazyField());
    }

    @Test
    void noFalsePositiveWhenGetterNameMatchesUnrelatedEntity() throws IOException {
        // Order has a lazy 'items'. Customer has a non-lazy 'name'.
        // Iterating Customers and calling getName() must NOT be flagged as Order.items.
        writeEntity();
        writeFile("Customer.java", """
                package com.example;

                import jakarta.persistence.*;

                @Entity
                public class Customer {
                    @Id
                    private Long id;

                    @ManyToOne(fetch = FetchType.LAZY)
                    private Region region;

                    public Region getRegion() { return region; }
                }
                """);
        writeFile("CustomerService.java", """
                package com.example;

                import java.util.List;

                public class CustomerService {
                    public void listItems(List<Order> orders) {
                        for (Order order : orders) {
                            // Only a non-lazy getter on Order — not the lazy 'items'.
                            order.getId();
                        }
                    }
                }
                """);

        AnalysisResult result = analyzer.analyze(tempDir);

        assertFalse(result.hasIssues(),
                "Accessing a non-lazy getter must not be misattributed as N+1");
    }

    @Test
    void parseFailuresAreCountedNotSilentlyHidden() throws IOException {
        writeEntity();
        writeServiceWithNPlusOne();
        // A file with a syntax error that JavaParser cannot handle.
        writeFile("Broken.java", "package com.example; public class Broken { this is not java ");

        AnalysisResult result = analyzer.analyze(tempDir);

        assertEquals(1, result.parseFailures(),
                "The broken file should be counted as a parse failure");
        assertTrue(result.filesScanned() >= 3);
        assertTrue(result.hasPartialCoverage());
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
