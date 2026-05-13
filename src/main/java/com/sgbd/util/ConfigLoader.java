package com.sgbd.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Incarcator de configuratie cu prioritizare:
 * 1. Variabile de mediu (System.getenv)
 * 2. Fisier .env din directorul proiectului
 * 3. Fisier application.properties din classpath
 * 4. Valoare implicita (default)
 *
 * Aceasta ierarhie permite suprascrierea configuratiei fara recompilare.
 */
public final class ConfigLoader {

    private static final Logger logger = LoggerUtil.getLogger(ConfigLoader.class);
    private static final Properties CONFIG = new Properties();
    private static final Properties ENV_OVERRIDES = new Properties();
    private static boolean loaded = false;

    private ConfigLoader() {}

    static {
        load();
    }

    private static synchronized void load() {
        if (loaded) return;

        // 1. Incarca application.properties din classpath
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                CONFIG.load(is);
                logger.fine("application.properties incarcat din classpath");
            }
        } catch (IOException e) {
            logger.warning("Nu s-a putut incarca application.properties: " + e.getMessage());
        }

        // 2. Incarca .env din directorul proiectului (daca exista)
        Path envPath = Paths.get(".env");
        if (Files.exists(envPath)) {
            try (InputStream is = Files.newInputStream(envPath)) {
                ENV_OVERRIDES.load(is);
                logger.info(".env incarcat din " + envPath.toAbsolutePath());
            } catch (IOException e) {
                logger.warning("Nu s-a putut incarca .env: " + e.getMessage());
            }
        }

        loaded = true;
    }

    /**
     * Returneaza valoarea unei chei, cautand in ordinea:
     * env var > .env > application.properties > default
     *
     * @param key          cheia de configuratie (ex: "db.url")
     * @param defaultValue valoare implicita daca nu se gaseste nimic
     * @return valoarea gasita sau defaultValue
     */
    public static String get(String key, String defaultValue) {
        String envKey = keyToEnvVar(key);
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        String dotEnvValue = ENV_OVERRIDES.getProperty(key);
        if (dotEnvValue != null && !dotEnvValue.isBlank()) {
            return dotEnvValue;
        }

        String propValue = CONFIG.getProperty(key);
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }

        return defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBool(String key, boolean defaultValue) {
        String val = get(key, String.valueOf(defaultValue)).toLowerCase();
        return val.equals("true") || val.equals("1") || val.equals("yes");
    }

    /**
     * Converteaza o cheie de proprietate in nume de variabila de mediu.
     * Ex: "db.url" -> "DB_URL", "db.pool.maxSize" -> "DB_POOL_MAX_SIZE"
     */
    private static String keyToEnvVar(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase();
    }
}
