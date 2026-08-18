package com.coderefine.verify;

import com.coderefine.core.model.UnboundedCollectionIssue;
import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.llm.model.PatchSuggestion.FixStrategy;
import com.coderefine.verify.model.VerificationResult;
import com.coderefine.verify.sandbox.SandboxVerifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the row-count verification path: an unbounded findAll() (10k rows)
 * versus a paginated fix (20 rows) is APPROVED via the ResultSetSizeStrategy,
 * measured in "rows" rather than "queries".
 */
class UnboundedVerificationTest {

    @Test
    void paginationFixIsApprovedByRowCount() {
        UnboundedCollectionIssue issue = new UnboundedCollectionIssue(
                "OrderService.java", "OrderService", "loadEverything", 12,
                "orderRepository", "findAll",
                "loads the entire table with no pagination");

        PatchSuggestion patch = new PatchSuggestion(
                issue.description(),
                FixStrategy.PAGINATION,
                List.of(),
                "Introduced Pageable to bound the result set");

        VerificationResult result = new SandboxVerifier().verify(issue, patch);

        assertEquals(VerificationResult.Verdict.APPROVED, result.verdict(), result.reason());
        assertEquals("rows", result.metricName());
        assertTrue(result.valueBefore() > result.valueAfter(),
                "Paginated fix should return fewer rows than the unbounded query");
        assertTrue(result.valueAfter() <= 20, "Fixed version should be bounded to a page");
    }
}
