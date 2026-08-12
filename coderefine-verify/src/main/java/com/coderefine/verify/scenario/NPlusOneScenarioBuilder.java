package com.coderefine.verify.scenario;

import com.coderefine.core.model.NPlusOneIssue;
import com.coderefine.verify.sandbox.VerificationScenario;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class NPlusOneScenarioBuilder {

    private static final int TEST_PARENT_COUNT = 10;
    private static final int TEST_CHILDREN_PER_PARENT = 5;

    public VerificationScenario buildScenario(NPlusOneIssue issue) {
        String parentTable = issue.entityType().toLowerCase() + "s";
        String childTable = issue.lazyField();
        String fkColumn = issue.entityType().toLowerCase() + "_id";

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
        for (int i = 1; i <= TEST_PARENT_COUNT; i++) {
            data.append(String.format("INSERT INTO %s (id, name) VALUES (%d, 'parent_%d');\n",
                    parentTable, i, i));
        }
        for (int i = 1; i <= TEST_PARENT_COUNT; i++) {
            for (int j = 1; j <= TEST_CHILDREN_PER_PARENT; j++) {
                int childId = (i - 1) * TEST_CHILDREN_PER_PARENT + j;
                data.append(String.format("INSERT INTO %s (id, %s, description) VALUES (%d, %d, 'child_%d_%d');\n",
                        childTable, fkColumn, childId, i, i, j));
            }
        }

        return VerificationScenario.builder()
                .schema(schema)
                .data(data.toString())
                .before(ds -> simulateNPlusOne(ds, parentTable, childTable, fkColumn))
                .after(ds -> simulateJoinFetch(ds, parentTable, childTable, fkColumn))
                .build();
    }

    private void simulateNPlusOne(DataSource ds, String parentTable,
                                  String childTable, String fkColumn) {
        try (Connection conn = ds.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM " + parentTable);
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
        } catch (Exception e) {
            throw new RuntimeException("N+1 simulation failed", e);
        }
    }

    private void simulateJoinFetch(DataSource ds, String parentTable,
                                   String childTable, String fkColumn) {
        try (Connection conn = ds.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT p.*, c.* FROM " + parentTable + " p " +
                            "LEFT JOIN " + childTable + " c ON c." + fkColumn + " = p.id");
            ps.executeQuery();
            ps.close();
        } catch (Exception e) {
            throw new RuntimeException("JOIN FETCH simulation failed", e);
        }
    }
}
