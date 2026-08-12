package com.coderefine.verify;

import com.coderefine.verify.counter.QueryCounter;
import com.coderefine.verify.counter.QueryCountingDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class QueryCountingTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test
    void countsQueriesAccurately() throws Exception {
        DataSource raw = createDataSource();
        QueryCounter counter = new QueryCounter();
        QueryCountingDataSource ds = new QueryCountingDataSource(raw, counter);

        try (Connection conn = ds.getConnection()) {
            conn.prepareStatement("SELECT 1").executeQuery();
            conn.prepareStatement("SELECT 2").executeQuery();
            conn.prepareStatement("SELECT 3").executeQuery();
        }

        assertEquals(3, counter.getCount());
    }

    @Test
    void resetClearsCount() throws Exception {
        DataSource raw = createDataSource();
        QueryCounter counter = new QueryCounter();
        QueryCountingDataSource ds = new QueryCountingDataSource(raw, counter);

        try (Connection conn = ds.getConnection()) {
            conn.prepareStatement("SELECT 1").executeQuery();
        }

        assertEquals(1, counter.getCount());
        counter.reset();
        assertEquals(0, counter.getCount());
    }

    @Test
    void demonstratesNPlusOneVsJoinFetch() throws Exception {
        DataSource raw = createDataSource();
        QueryCounter counter = new QueryCounter();
        QueryCountingDataSource ds = new QueryCountingDataSource(raw, counter);

        try (Connection conn = raw.getConnection()) {
            conn.createStatement().execute("""
                    CREATE TABLE orders (id BIGSERIAL PRIMARY KEY, name VARCHAR(255));
                    CREATE TABLE items (id BIGSERIAL PRIMARY KEY, order_id BIGINT, desc_ VARCHAR(255));
                    """);
            for (int i = 1; i <= 10; i++) {
                conn.createStatement().execute(
                        "INSERT INTO orders (id, name) VALUES (" + i + ", 'order_" + i + "')");
                for (int j = 1; j <= 3; j++) {
                    conn.createStatement().execute(
                            "INSERT INTO items (order_id, desc_) VALUES (" + i + ", 'item')");
                }
            }
        }

        // N+1 pattern: 1 query for orders + 10 queries for items
        counter.reset();
        try (Connection conn = ds.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT id FROM orders");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long id = rs.getLong("id");
                conn.prepareStatement("SELECT * FROM items WHERE order_id = " + id).executeQuery();
            }
        }
        int nPlusOneCount = counter.getCount();

        // JOIN FETCH pattern: 1 query
        counter.reset();
        try (Connection conn = ds.getConnection()) {
            conn.prepareStatement(
                    "SELECT o.*, i.* FROM orders o LEFT JOIN items i ON i.order_id = o.id"
            ).executeQuery();
        }
        int joinFetchCount = counter.getCount();

        assertTrue(nPlusOneCount > 5, "N+1 should cause many queries, got: " + nPlusOneCount);
        assertEquals(1, joinFetchCount);
        assertTrue(nPlusOneCount > joinFetchCount,
                "JOIN FETCH should use fewer queries than N+1");
    }

    private DataSource createDataSource() {
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }
}
