package com.sgbd.util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Punct de acces centralizat pentru conexiuni la baza de date.
 * Delega catre DatabaseConnectionPool (HikariCP) pentru reutilizarea
 * eficienta a conexiunilor TCP.
 *
 * Toate serviciile existente folosesc DatabaseConnection.getConnection()
 * si vor beneficia automat de pooling fara modificari.
 */
public class DatabaseConnection {

    private DatabaseConnection() {}

    /**
     * Returneaza o conexiune din pool-ul HikariCP.
     * Pool-ul se initializeaza automat la primul apel.
     */
    public static Connection getConnection() throws SQLException {
        return DatabaseConnectionPool.getConnection();
    }

    /**
     * Verifica daca baza de date raspunde la query.
     */
    public static boolean isHealthy() {
        return DatabaseConnectionPool.isHealthy();
    }
}
