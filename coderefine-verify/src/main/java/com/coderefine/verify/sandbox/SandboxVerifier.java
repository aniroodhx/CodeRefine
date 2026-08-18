package com.coderefine.verify.sandbox;

import com.coderefine.core.model.Issue;
import com.coderefine.core.model.IssueType;
import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.verify.model.VerificationResult;
import com.coderefine.verify.strategy.QueryCountStrategy;
import com.coderefine.verify.strategy.ResultSetSizeStrategy;
import com.coderefine.verify.strategy.VerificationStrategy;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.Map;

/**
 * Runs a patch through a real Postgres sandbox and returns a verdict. The
 * measurement is delegated to a {@link VerificationStrategy} chosen by the
 * issue type, so each anti-pattern is proven with the metric that fits it
 * (queries for N+1, rows for unbounded collections).
 */
public class SandboxVerifier {

    static {
        if(System.getProperty("api.version")== null){
            System.setProperty("api.version", "1.44");
        }
    }
    private final Map<IssueType, VerificationStrategy> strategies = new EnumMap<>(IssueType.class);

    public SandboxVerifier() {
        register(new QueryCountStrategy());
        register(new ResultSetSizeStrategy());
    }

    private void register(VerificationStrategy strategy) {
        strategies.put(strategy.type(), strategy);
    }

    public VerificationResult verify(Issue issue, PatchSuggestion patch) {
        VerificationStrategy strategy = strategies.get(issue.type());
        if (strategy == null) {
            return VerificationResult.error(patch.issueDescription(),
                    "No verification strategy for issue type " + issue.type());
        }

        VerificationScenario scenario = strategy.buildScenario(issue);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")) {
            postgres.start();

            DataSource ds = createDataSource(postgres);
            initialize(ds, scenario.schemaSetup());
            initialize(ds, scenario.dataSetup());

            int before = scenario.measureBefore().applyAsInt(ds);
            int after = scenario.measureAfter().applyAsInt(ds);

            if (strategy.isImprovement(before, after)) {
                return VerificationResult.approved(
                        patch.issueDescription(), scenario.metricName(), before, after);
            }
            return VerificationResult.rejected(
                    patch.issueDescription(), scenario.metricName(), before, after,
                    String.format("No improvement: %d %s before, %d after",
                            before, scenario.metricName(), after));

        } catch (Exception e) {
            return VerificationResult.error(patch.issueDescription(),
                    "Verification failed: " + e.getMessage());
        }
    }

    private DataSource createDataSource(PostgreSQLContainer<?> postgres) {
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }

    private void initialize(DataSource ds, String sql) {
        if (sql == null || sql.isBlank()) return;
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException("Sandbox setup failed: " + e.getMessage(), e);
        }
    }
}
