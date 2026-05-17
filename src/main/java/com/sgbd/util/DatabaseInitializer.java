package com.sgbd.util;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Initializator pentru baza de date.
 * Ruleaza migratiile, seed-urile si procedurile stocate la pornirea aplicatiei.
 * Se asigura ca schema este creata si populata corect.
 */
public final class DatabaseInitializer {

    private static final Logger logger = LoggerUtil.getLogger(DatabaseInitializer.class);
    private static final String MIGRATIONS_DIR = "db/migrations";
    private static final String SEEDS_DIR = "db/seeds";
    private static final String PROCEDURES_DIR = "db/procedures";

    private DatabaseInitializer() {}

    /**
     * Ruleaza intregul proces de initializare: migratii, seeds, proceduri.
     * Daca schema este deja la zi, sare peste migratii pentru a reduce zgomotul in log.
     *
     * @return true daca initializarea a reusit, false altfel
     */
    public static boolean initialize() {
        try {
            try (Connection conn = DatabaseConnection.getConnection()) {
                String url = conn.getMetaData().getURL();

                if (isSchemaCurrent(conn)) {
                    logger.info("Baza de date este deja initializata — se sare peste migratii.");
                    return true;
                }

                logger.info("Se initializeaza baza de date...");
                logger.info("Conexiune la PostgreSQL reusita: " + url);

                runMigrations();

                if (!isDatabasePopulated()) {
                    runSeeds();
                }

                runProcedures();
                recordSchemaVersion(conn);

                logger.info("Initializare baza de date completa.");
                return true;
            }
        } catch (Exception e) {
            logger.severe("Eroare la initializarea bazei de date: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean isSchemaCurrent(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = '_schema_version')")) {
            if (!rs.next() || !rs.getBoolean(1)) {
                return false;
            }
        }
        String currentHash = computeMigrationsHash();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT version_hash FROM _schema_version WHERE id = 1")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return currentHash.equals(rs.getString(1));
            }
        }
        return false;
    }

    private static void recordSchemaVersion(Connection conn) throws SQLException {
        String hash = computeMigrationsHash();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS _schema_version (id INTEGER PRIMARY KEY, version_hash VARCHAR(64), applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO _schema_version (id, version_hash) VALUES (1, ?) ON CONFLICT (id) DO UPDATE SET version_hash = EXCLUDED.version_hash, applied_at = CURRENT_TIMESTAMP")) {
            stmt.setString(1, hash);
            stmt.executeUpdate();
        }
    }

    private static String computeMigrationsHash() {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            List<Path> files = Files.list(Path.of(MIGRATIONS_DIR))
                .filter(p -> p.toString().endsWith(".sql"))
                .sorted()
                .collect(Collectors.toList());
            for (Path f : files) {
                md.update(Files.readString(f).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void runMigrations() throws IOException, SQLException {
        List<Path> files = Files.list(Path.of(MIGRATIONS_DIR))
            .filter(p -> p.toString().endsWith(".sql"))
            .sorted()
            .collect(Collectors.toList());

        logger.info("Se ruleaza " + files.size() + " migratii...");
        for (Path f : files) {
            executeSqlFile(f);
            logger.info("Migratie aplicata: " + f.getFileName());
        }
    }

    private static void runSeeds() throws IOException, SQLException {
        List<Path> files = Files.list(Path.of(SEEDS_DIR))
            .filter(p -> p.toString().endsWith(".sql"))
            .sorted()
            .collect(Collectors.toList());

        logger.info("Se ruleaza " + files.size() + " seed-uri...");
        for (Path f : files) {
            executeSqlFile(f);
            logger.info("Seed aplicat: " + f.getFileName());
        }
    }

    private static void runProcedures() throws IOException, SQLException {
        List<Path> files = Files.list(Path.of(PROCEDURES_DIR))
            .filter(p -> p.toString().endsWith(".sql"))
            .sorted()
            .collect(Collectors.toList());

        logger.info("Se ruleaza " + files.size() + " proceduri stocate...");
        for (Path f : files) {
            executeSqlFile(f);
            logger.info("Procedura creata: " + f.getFileName());
        }
    }

    private static void executeSqlFile(Path path) throws IOException, SQLException {
        String sql = Files.readString(path);
        // Imparte continutul pe comenzi separate (delimitate de ";" si newline)
        // Dar ignora ";" din interiorul blocurilor $$ ... $$
        String[] commands = splitCommands(sql);

        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String cmd : commands) {
                String trimmed = cmd.trim();
                if (trimmed.isEmpty()) continue;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(trimmed);
                } catch (SQLException e) {
                    // Ignora erorile de tip "already exists" pentru idempotenta
                    String msg = e.getMessage().toLowerCase();
                    if (msg.contains("already exists") || msg.contains("duplicate") || msg.contains("conflict")) {
                        // Expected during idempotent migrations — no need to log
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Imparte un bloc SQL in comenzi individuale,
     * respectand blocurile PL/pgSQL delimitate de $$.
     */
    private static String[] splitCommands(String sql) {
        List<String> commands = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDollarBlock = false;
        boolean inBlockComment = false;
        String[] lines = sql.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (!inDollarBlock && !inBlockComment && trimmed.startsWith("--")) {
                continue;
            }
            if (!inDollarBlock && trimmed.startsWith("/*")) {
                inBlockComment = true;
            }
            if (inBlockComment && trimmed.endsWith("*/")) {
                inBlockComment = false;
                continue;
            }
            if (inBlockComment) {
                continue;
            }
            if (trimmed.contains("$$")) {
                inDollarBlock = !inDollarBlock;
            }
            current.append(line).append("\n");
            if (!inDollarBlock && trimmed.endsWith(";")) {
                commands.add(current.toString());
                current = new StringBuilder();
            }
        }
        if (!current.toString().trim().isEmpty()) {
            commands.add(current.toString());
        }
        return commands.toArray(new String[0]);
    }

    /**
     * Verifica daca baza de date are deja date (tabele populate).
     */
    public static boolean isDatabasePopulated() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM cities")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }
}
