package com.coderefine.verify.sandbox;

import com.coderefine.llm.model.PatchSuggestion;
import com.coderefine.verify.counter.QueryCounter;
import com.coderefine.verify.counter.QueryCountingDataSource;
import com.coderefine.verify.model.VerificationResult;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.function.Consumer;

public class SandboxVerifier {

    public VerificationResult verify(PatchSuggestion patch, VerificationScenario scenario) {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")) {
            postgres.start();

            DataSource rawDataSource = createDataSource(postgres);
            QueryCounter counter = new QueryCounter();
            QueryCountingDataSource countingDs = new QueryCountingDataSource(rawDataSource, counter);

            initializeSchema(rawDataSource, scenario.schemaSetup());
            insertTestData(rawDataSource, scenario.dataSetup());

            counter.reset();
            boolean testsBefore = runScenario(countingDs, scenario.beforeExecution());
            int queryCountBefore = counter.getCount();

            counter.reset();
            boolean testsAfter = runScenario(countingDs, scenario.afterExecution());
            int queryCountAfter = counter.getCount();

            if (!testsAfter) {
                return VerificationResult.rejected(
                        patch.issueDescription(), queryCountBefore, queryCountAfter,
                        testsBefore, testsAfter,
                        "Patched code breaks existing tests");
            }

            if (queryCountAfter >= queryCountBefore) {
                return VerificationResult.rejected(
                        patch.issueDescription(), queryCountBefore, queryCountAfter,
                        testsBefore, testsAfter,
                        String.format("No improvement: %d queries before, %d after",
                                queryCountBefore, queryCountAfter));
            }

            return VerificationResult.approved(
                    patch.issueDescription(), queryCountBefore, queryCountAfter);

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

    private void initializeSchema(DataSource ds, String schemaSql) {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(schemaSql);
        } catch (Exception e) {
            throw new RuntimeException("Schema setup failed: " + e.getMessage(), e);
        }
    }

    private void insertTestData(DataSource ds, String dataSql) {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(dataSql);
        } catch (Exception e) {
            throw new RuntimeException("Data setup failed: " + e.getMessage(), e);
        }
    }

    private boolean runScenario(DataSource ds, Consumer<DataSource> execution) {
        try {
            execution.accept(ds);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
