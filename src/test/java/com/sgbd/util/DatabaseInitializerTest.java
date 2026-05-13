package com.sgbd.util;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Teste pentru initializatorul bazei de date.
 */
class DatabaseInitializerTest {

    @Test
    void testIsDatabasePopulatedReturnsTrue() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);
            when(stmt.executeQuery("SELECT COUNT(*) FROM cities")).thenReturn(rs);
            when(rs.next()).thenReturn(true);
            when(rs.getInt(1)).thenReturn(5);

            assertTrue(DatabaseInitializer.isDatabasePopulated());
        }
    }

    @Test
    void testIsDatabasePopulatedReturnsFalseWhenEmpty() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);
            when(stmt.executeQuery("SELECT COUNT(*) FROM cities")).thenReturn(rs);
            when(rs.next()).thenReturn(true);
            when(rs.getInt(1)).thenReturn(0);

            assertFalse(DatabaseInitializer.isDatabasePopulated());
        }
    }

    @Test
    void testIsDatabasePopulatedSQLException() {
        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenThrow(new SQLException("connection failed"));
            assertFalse(DatabaseInitializer.isDatabasePopulated());
        }
    }
}
