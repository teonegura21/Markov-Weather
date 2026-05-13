package com.sgbd.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Teste de integrare pentru pool-ul de conexiuni HikariCP.
 * Verifica ca pool-ul se initializeaza, returneaza conexiuni valide
 * si raspunde la health check.
 *
 * NOTA: Aceste teste necesita un server PostgreSQL activ.
 * Daca PostgreSQL nu e disponibil, testele se sar (assumption).
 */
class DatabaseConnectionPoolTest {

    private static boolean dbAvailable = false;

    @BeforeAll
    static void checkDbAvailability() {
        dbAvailable = DatabaseConnectionPool.initialize();
        if (dbAvailable) {
            DatabaseConnectionPool.shutdown();
        }
    }

    @Test
    void initialize_createsHealthyPool() {
        assumeTrue(dbAvailable, "PostgreSQL indisponibil — se sare testul de integrare");
        boolean ok = DatabaseConnectionPool.initialize();
        assertTrue(ok, "Pool-ul ar trebui sa se initializeze");
        assertTrue(DatabaseConnectionPool.isHealthy(), "Pool-ul ar trebui sa fie healthy dupa init");
    }

    @Test
    void getConnection_returnsValidConnection() throws SQLException {
        assumeTrue(dbAvailable, "PostgreSQL indisponibil — se sare testul de integrare");
        DatabaseConnectionPool.initialize();
        try (Connection conn = DatabaseConnectionPool.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            var rs = conn.prepareStatement("SELECT 1 AS one").executeQuery();
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("one"));
        }
    }

    @Test
    void isHealthy_returnsFalse_whenPoolNotInitialized() {
        // Test comportamental: nu depinde de DB
        DatabaseConnectionPool.shutdown();
        assertFalse(DatabaseConnectionPool.isHealthy());
    }
}
