package com.coderefine.verify.strategy;

import com.coderefine.core.model.Issue;
import com.coderefine.core.model.IssueType;
import com.coderefine.verify.sandbox.VerificationScenario;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Proves an unbounded-collection fix by counting rows returned, not queries.
 * Seeds a large table; the buggy version ({@code findAll()}) returns every row,
 * the fixed version returns a bounded page. Approved when the "after" row count
 * is bounded (≤ page size) and materially smaller than "before".
 *
 * <p>This is the second verification metric in the pipeline — same Testcontainers
 * Postgres sandbox as {@link QueryCountStrategy}, different thing measured.
 */
public class ResultSetSizeStrategy implements VerificationStrategy {

    private static final int SEED_ROWS = 10_000;
    private static final int PAGE_SIZE = 20;
    private static final String TABLE = "records";

    @Override
    public IssueType type() {
        return IssueType.UNBOUNDED_COLLECTION;
    }

    @Override
    public VerificationScenario buildScenario(Issue issue) {
        String schema = String.format("""
                CREATE TABLE %s (
                    id BIGSERIAL PRIMARY KEY,
                    payload VARCHAR(64)
                );
                """, TABLE);

        // Bulk-seed SEED_ROWS rows using generate_series (fast, single statement).
        String data = String.format(
                "INSERT INTO %s (payload) SELECT 'row_' || g FROM generate_series(1, %d) g;",
                TABLE, SEED_ROWS);

        return VerificationScenario.builder()
                .metric("rows")
                .schema(schema)
                .data(data)
                .measureBefore(ds -> countRows(ds, "SELECT * FROM " + TABLE))
                .measureAfter(ds -> countRows(ds,
                        "SELECT * FROM " + TABLE + " LIMIT " + PAGE_SIZE))
                .build();
    }

    @Override
    public boolean isImprovement(int before, int after) {
        // The fix must bound the result set: no more than a page, and smaller
        // than the full unbounded fetch.
        return after <= PAGE_SIZE && after < before;
    }

    private int countRows(DataSource ds, String sql) {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int count = 0;
            while (rs.next()) {
                count++;
            }
            return count;
        } catch (Exception e) {
            throw new RuntimeException("Row-count run failed: " + e.getMessage(), e);
        }
    }
}
