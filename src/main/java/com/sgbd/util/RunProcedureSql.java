package com.sgbd.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Utilitar pentru executarea directa a unui fisier SQL cu proceduri stocate.
 * Foloseste connection pool-ul existent pentru a rula CREATE FUNCTION/PROCEDURE
 * chiar si atunci cand migrarile sunt la zi (caz in care DatabaseInitializer
 * sare peste proceduri).
 */
public final class RunProcedureSql {

    private RunProcedureSql() {}

    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "db/procedures/sp_auto_warn_users.sql";

        DatabaseConnectionPool.initialize();
        try {
            String sql = Files.readString(Path.of(file));
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                System.out.println("OK: " + file + " a fost executat cu succes.");
            }
        } catch (Exception e) {
            System.err.println("EROARE la executarea " + file + ": " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            DatabaseConnectionPool.shutdown();
        }
    }
}
