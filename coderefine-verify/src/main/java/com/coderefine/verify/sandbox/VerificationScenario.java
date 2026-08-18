package com.coderefine.verify.sandbox;

import javax.sql.DataSource;
import java.util.function.ToIntFunction;

/**
 * A self-contained sandbox scenario: how to build the schema and seed data, and
 * how to measure the "before" and "after" states as an integer metric.
 * The measurement function returns the metric value (query count, row count, …)
 * for the run it performs.
 */
public record VerificationScenario(
        String metricName,
        String schemaSetup,
        String dataSetup,
        ToIntFunction<DataSource> measureBefore,
        ToIntFunction<DataSource> measureAfter
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String metricName = "value";
        private String schemaSetup;
        private String dataSetup;
        private ToIntFunction<DataSource> measureBefore;
        private ToIntFunction<DataSource> measureAfter;

        public Builder metric(String name) {
            this.metricName = name;
            return this;
        }

        public Builder schema(String sql) {
            this.schemaSetup = sql;
            return this;
        }

        public Builder data(String sql) {
            this.dataSetup = sql;
            return this;
        }

        public Builder measureBefore(ToIntFunction<DataSource> fn) {
            this.measureBefore = fn;
            return this;
        }

        public Builder measureAfter(ToIntFunction<DataSource> fn) {
            this.measureAfter = fn;
            return this;
        }

        public VerificationScenario build() {
            return new VerificationScenario(metricName, schemaSetup, dataSetup,
                    measureBefore, measureAfter);
        }
    }
}
