package com.coderefine.llm;

import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.llm.client.LLMClient;
import com.coderefine.llm.model.PatchContext;
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

class PatchGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesPatchForNPlusOneIssue() throws IOException {
        writeEntity();
        writeService();

        LLMClient mockClient = context -> new PatchSuggestion(
                "N+1 on Order.items",
                FixStrategy.JOIN_FETCH_QUERY,
                List.of(new FileChange(
                        "OrderRepository.java",
                        "",
                        """
                        @Query("SELECT o FROM Order o JOIN FETCH o.items")
                        List<Order> findAllWithItems();
                        """,
                        FileChange.ChangeType.ADD_NEW_METHOD
                )),
                "Added JOIN FETCH query to load items eagerly in a single query"
        );

        PatchGenerator generator = new PatchGenerator(mockClient);

        NPlusOneIssue issue = new NPlusOneIssue(
                tempDir.resolve("OrderService.java").toString(),
                "OrderService",
                "getAllOrderItems",
                9,
                "Order",
                "items",
                "for-each",
                "Lazy collection 'items' accessed inside for-each loop"
        );

        List<PatchSuggestion> patches = generator.generatePatches(List.of(issue), tempDir);

        assertEquals(1, patches.size());
        PatchSuggestion patch = patches.get(0);
        assertEquals(FixStrategy.JOIN_FETCH_QUERY, patch.strategy());
        assertEquals(1, patch.changes().size());
        assertTrue(patch.changes().get(0).patchedCode().contains("JOIN FETCH"));
    }

    @Test
    void contextBuilderFindsEntityAndService() throws IOException {
        writeEntity();
        writeService();
        writeRepository();

        final PatchContext[] captured = new PatchContext[1];

        LLMClient capturingClient = context -> {
            captured[0] = context;
            return new PatchSuggestion("test", FixStrategy.ENTITY_GRAPH,
                    List.of(), "test");
        };

        PatchGenerator generator = new PatchGenerator(capturingClient);

        NPlusOneIssue issue = new NPlusOneIssue(
                tempDir.resolve("OrderService.java").toString(),
                "OrderService", "getAllOrderItems", 9,
                "Order", "items", "for-each", "test"
        );

        generator.generatePatches(List.of(issue), tempDir);

        assertNotNull(captured[0]);
        assertTrue(captured[0].entitySource().contains("@Entity"));
        assertTrue(captured[0].serviceSource().contains("OrderService"));
        assertTrue(captured[0].repositorySource().contains("OrderRepository"));
    }

    private void writeEntity() throws IOException {
        Files.writeString(tempDir.resolve("Order.java"), """
                package com.example;

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
                """);
    }

    private void writeRepository() throws IOException {
        Files.writeString(tempDir.resolve("OrderRepository.java"), """
                package com.example;

                import org.springframework.data.jpa.repository.JpaRepository;

                public interface OrderRepository extends JpaRepository<Order, Long> {
                }
                """);
    }
}
