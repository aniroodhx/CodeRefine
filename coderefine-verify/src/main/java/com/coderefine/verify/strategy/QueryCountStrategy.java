package com.coderefine.verify.strategy;

import com.coderefine.core.model.IssueType;
import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.core.model.Issue;
import com.coderefine.verify.counter.QueryCounter;
import com.coderefine.verify.counter.QueryCountingDataSource;
import com.coderefine.verify.sandbox.VerificationScenario;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Proves an N+1 fix by counting SQL queries: the buggy version fires one query
 * per parent row; the fixed (JOIN FETCH) version fires one. Approved when the
 * "after" query count is strictly lower.
 */
public class QueryCountStrategy implements VerificationStrategy {

    private static final int PARENT_COUNT = 10;
    private static final int CHILDREN_PER_PARENT = 5;

    @Override
    public IssueType type() {
        return IssueType.N_PLUS_ONE;
    }

    @Override
    public VerificationScenario buildScenario(Issue issue) {
        NPlusOneIssue n = (NPlusOneIssue) issue;
        String parentTable = n.entityType().toLowerCase() + "s";
        String childTable = n.lazyField();
        String fkColumn = n.entityType().toLowerCase() + "_id";

        String schema = String.format("""
                CREATE TABLE %s (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255)
                );
                CREATE TABLE %s (
                    id BIGSERIAL PRIMARY KEY,
                    %s BIGINT REFERENCES %s(id),
                    description VARCHAR(255)
                );
                """, parentTable, childTable, fkColumn, parentTable);

        StringBuilder data = new StringBuilder();
        for (int i = 1; i <= PARENT_COUNT; i++) {
            data.append(String.format("INSERT INTO %s (id, name) VALUES (%d, 'parent_%d');%n",
                    parentTable, i, i));
        }
        for (int i = 1; i <= PARENT_COUNT; i++) {
            for (int j = 1; j <= CHILDREN_PER_PARENT; j++) {
                int childId = (i - 1) * CHILDREN_PER_PARENT + j;
                data.append(String.format(
                        "INSERT INTO %s (id, %s, description) VALUES (%d, %d, 'child_%d_%d');%n",
                        childTable, fkColumn, childId, i, i, j));
            }
        }

        return VerificationScenario.builder()
                .metric("queries")
                .schema(schema)
                .data(data.toString())
                .measureBefore(ds -> countQueries(ds,
                        conn -> simulateNPlusOne(conn, parentTable, childTable, fkColumn)))
                .measureAfter(ds -> countQueries(ds,
                        conn -> simulateJoinFetch(conn, parentTable, childTable, fkColumn)))
                .build();
    }

    @Override
    public boolean isImprovement(int before, int after) {
        return after < before;
    }

    /** Runs the given work through a query-counting proxy and returns the count. */
    private int countQueries(DataSource ds, ConnectionWork work) {
        QueryCounter counter = new QueryCounter();
        QueryCountingDataSource counting = new QueryCountingDataSource(ds, counter);
        try (Connection conn = counting.getConnection()) {
            work.run(conn);
        } catch (Exception e) {
            throw new RuntimeException("Query-count run failed: " + e.getMessage(), e);
        }
        return counter.getCount();
    }

    private void simulateNPlusOne(Connection conn, String parentTable,
                                  String childTable, String fkColumn) throws Exception {
        PreparedStatement ps = conn.prepareStatement("SELECT id FROM " + parentTable);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            long parentId = rs.getLong("id");
            PreparedStatement childPs = conn.prepareStatement(
                    "SELECT * FROM " + childTable + " WHERE " + fkColumn + " = ?");
            childPs.setLong(1, parentId);
            childPs.executeQuery();
            childPs.close();
        }
        rs.close();
        ps.close();
    }

    private void simulateJoinFetch(Connection conn, String parentTable,
                                   String childTable, String fkColumn) throws Exception {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT p.*, c.* FROM " + parentTable + " p "
                        + "LEFT JOIN " + childTable + " c ON c." + fkColumn + " = p.id");
        ps.executeQuery();
        ps.close();
    }

    @FunctionalInterface
    private interface ConnectionWork {
        void run(Connection conn) throws Exception;
    }
}
