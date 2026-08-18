package com.coderefine.core;

import com.coderefine.core.model.AnalysisResult;
import com.coderefine.core.model.IssueType;
import com.coderefine.core.model.UnboundedCollectionIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UnboundedCollectionDetectorTest {

    @TempDir
    Path tempDir;

    private CodeRefineAnalyzer analyzer;

    @BeforeEach
    void setup() {
        analyzer = new CodeRefineAnalyzer();
    }

    @Test
    void detectsUnboundedFindAll() throws IOException {
        writeRepository();
        writeFile("OrderService.java", """
                package com.example;

                import java.util.List;

                public class OrderService {
                    private final OrderRepository orderRepository;

                    public OrderService(OrderRepository orderRepository) {
                        this.orderRepository = orderRepository;
                    }

                    public List<Order> loadEverything() {
                        return orderRepository.findAll();
                    }
                }
                """);

        AnalysisResult result = analyzer.analyze(tempDir);

        assertEquals(1, result.countOf(IssueType.UNBOUNDED_COLLECTION));
        UnboundedCollectionIssue issue = (UnboundedCollectionIssue) result.issues().stream()
                .filter(i -> i.type() == IssueType.UNBOUNDED_COLLECTION)
                .findFirst().orElseThrow();
        assertEquals("OrderService", issue.className());
        assertEquals("loadEverything", issue.methodName());
        assertEquals("findAll", issue.repositoryCall());
    }

    @Test
    void ignoresPaginatedFindAll() throws IOException {
        writeRepository();
        writeFile("OrderService.java", """
                package com.example;

                import java.util.List;
                import org.springframework.data.domain.Pageable;

                public class OrderService {
                    private final OrderRepository orderRepository;

                    public OrderService(OrderRepository orderRepository) {
                        this.orderRepository = orderRepository;
                    }

                    public List<Order> loadPage(Pageable pageable) {
                        return orderRepository.findAll(pageable).getContent();
                    }
                }
                """);

        AnalysisResult result = analyzer.analyze(tempDir);

        assertEquals(0, result.countOf(IssueType.UNBOUNDED_COLLECTION),
                "findAll(Pageable) is bounded and must not be flagged");
    }

    @Test
    void ignoresFindAllOnNonRepository() throws IOException {
        writeFile("PlainService.java", """
                package com.example;

                import java.util.List;
                import java.util.ArrayList;

                public class PlainService {
                    public void run() {
                        List<String> items = new ArrayList<>();
                        // 'items' is a plain collection, not a repository — findAll here
                        // wouldn't even compile as JPA, but the detector must not guess.
                        items.size();
                    }
                }
                """);

        AnalysisResult result = analyzer.analyze(tempDir);

        assertEquals(0, result.countOf(IssueType.UNBOUNDED_COLLECTION));
    }

    @Test
    void ignoresFindAllInTestSources() throws IOException {
        writeRepository();
        writeFile("VetControllerTests.java", """
                package com.example;

                import java.util.List;

                public class VetControllerTests {
                    private final OrderRepository orderRepository = null;

                    public void shouldReturnAll() {
                        List<Order> all = orderRepository.findAll();
                        assert all != null;
                    }
                }
                """);

        AnalysisResult result = analyzer.analyze(tempDir);

        assertEquals(0, result.countOf(IssueType.UNBOUNDED_COLLECTION),
                "findAll() in a *Tests file must not be flagged");
    }

    private void writeRepository() throws IOException {
        writeFile("OrderRepository.java", """
                package com.example;

                import org.springframework.data.jpa.repository.JpaRepository;

                public interface OrderRepository extends JpaRepository<Order, Long> {
                }
                """);
    }

    private void writeFile(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }
}
