package com.coderefine.verify.sandbox;

import javax.sql.DataSource;
import java.util.function.Consumer;

public record VerificationScenario(
        String schemaSetup,
        String dataSetup,
        Consumer<DataSource> beforeExecution,
        Consumer<DataSource> afterExecution
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String schemaSetup;
        private String dataSetup;
        private Consumer<DataSource> beforeExecution;
        private Consumer<DataSource> afterExecution;

        public Builder schema(String sql) {
            this.schemaSetup = sql;
            return this;
        }

        public Builder data(String sql) {
            this.dataSetup = sql;
            return this;
        }

        public Builder before(Consumer<DataSource> execution) {
            this.beforeExecution = execution;
            return this;
        }

        public Builder after(Consumer<DataSource> execution) {
            this.afterExecution = execution;
            return this;
        }

        public VerificationScenario build() {
            return new VerificationScenario(schemaSetup, dataSetup, beforeExecution, afterExecution);
        }
    }
}
