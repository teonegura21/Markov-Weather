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
     *
     * @return true daca initializarea a reusit, false altfel
     */
    public static boolean initialize() {
        logger.info("Se initializeaza baza de date...");
        try {
            // Verifica conexiunea
            try (Connection conn = DatabaseConnection.getConnection()) {
                logger.info("Conexiune la PostgreSQL reusita: " + conn.getMetaData().getURL());
            }

            // Ruleaza migratiile
            runMigrations();

            // Ruleaza seed-urile
            runSeeds();

            // Ruleaza procedurile stocate
            runProcedures();

            logger.info("Initializare baza de date completa.");
            return true;
        } catch (Exception e) {
            logger.severe("Eroare la initializarea bazei de date: " + e.getMessage());
            e.printStackTrace();
            return false;
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
                        logger.warning("Ignorat (exista deja): " + e.getMessage());
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
