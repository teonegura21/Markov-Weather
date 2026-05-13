package com.sgbd.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Pool de conexiuni la baza de date folosind HikariCP.
 * Inlocuieste DriverManager brute-force cu un pool eficient
 * care reutilizeaza conexiunile TCP catre PostgreSQL.
 *
 * La pornire, daca PostgreSQL nu e inca gata (ex: container Docker),
 * se incearca reconectarea cu exponential backoff.
 */
public final class DatabaseConnectionPool {

    private static final Logger logger = LoggerUtil.getLogger(DatabaseConnectionPool.class);
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_RETRY_MS = 1000;

    private static HikariDataSource dataSource;

    private DatabaseConnectionPool() {}

    /**
     * Initializeaza pool-ul de conexiuni.
     * Daca esueaza, reincearca cu backoff exponential pana la MAX_RETRIES.
     *
     * @return true daca initializarea a reusit
     */
    public static synchronized boolean initialize() {
        if (dataSource != null && !dataSource.isClosed()) {
            return true;
        }

        String url = ConfigLoader.get("db.url", "jdbc:postgresql://localhost:5433/prognoza_meteo");
        String user = ConfigLoader.get("db.user", "postgres");
        String password = ConfigLoader.get("db.password", "postgres");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(Integer.parseInt(ConfigLoader.get("db.pool.maxSize", "10")));
        config.setMinimumIdle(Integer.parseInt(ConfigLoader.get("db.pool.minIdle", "2")));
        config.setConnectionTimeout(Long.parseLong(ConfigLoader.get("db.pool.connectionTimeout", "5000")));
        config.setIdleTimeout(Long.parseLong(ConfigLoader.get("db.pool.idleTimeout", "600000")));
        config.setMaxLifetime(Long.parseLong(ConfigLoader.get("db.pool.maxLifetime", "1800000")));
        config.setPoolName("PrognozaMeteoPool");
        config.setAutoCommit(true);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        long retryDelay = INITIAL_RETRY_MS;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                dataSource = new HikariDataSource(config);
                // Verifică conexiunea cu un health check
                try (Connection conn = dataSource.getConnection()) {
                    conn.prepareStatement("SELECT 1").executeQuery();
                }
                logger.info("Pool HikariCP initializat cu succes la " + url);
                return true;
            } catch (Exception e) {
                logger.warning("Eroare initializare pool (incercarea " + attempt + "/" + MAX_RETRIES + "): " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    logger.info("Se reincearca in " + retryDelay + " ms...");
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    retryDelay *= 2;
                }
            }
        }
        logger.severe("Nu s-a putut initializa pool-ul de conexiuni dupa " + MAX_RETRIES + " incercari.");
        return false;
    }

    /**
     * Returneaza o conexiune din pool.
     * Daca pool-ul nu e initializat, il initializeaza automat.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            if (!initialize()) {
                throw new SQLException("Pool-ul de conexiuni nu este disponibil");
            }
        }
        return dataSource.getConnection();
    }

    /**
     * Verifica daca baza de date raspunde la un query simplu.
     */
    public static boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("SELECT 1").executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Inchide pool-ul si elibereaza resursele.
     */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Pool HikariCP inchis.");
        }
    }
}
