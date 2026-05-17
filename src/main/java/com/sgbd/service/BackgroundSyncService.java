package com.sgbd.service;

import com.sgbd.service.prediction.StartupOrchestratorService;
import com.sgbd.util.LoggerUtil;

import javafx.application.Platform;

import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Serviciu de sincronizare în fundal.
 * Rulează mentenanța completă la pornire și permite refresh manual.
 * Nu rulează periodic — doar la startup și la cerere.
 */
public class BackgroundSyncService {

    private static final Logger logger = LoggerUtil.getLogger(BackgroundSyncService.class);

    private final StartupOrchestratorService orchestrator = new StartupOrchestratorService();
    private final ForecastService forecastService = new ForecastService();
    private Consumer<String> statusCallback;
    private boolean running = false;

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    private void updateStatus(String message) {
        logger.info(message);
        if (statusCallback != null) {
            Platform.runLater(() -> statusCallback.accept(message));
        }
    }

    /**
     * Rulează sincronizarea completă la pornirea aplicației.
     */
    public void runStartupSync() {
        if (running) {
            logger.warning("Sincronizare deja în desfășurare...");
            return;
        }
        running = true;

        new Thread(() -> {
            try {
                updateStatus("🔄 Verific date...");
                Thread.sleep(500);

                StartupOrchestratorService.StartupResult result = orchestrator.runStartupMaintenance();
                updateStatus("✅ " + result.message);

                try {
                    forecastService.callAutoWarnUsers();
                } catch (SQLException e) {
                    logger.warning("Eroare la avertizarea automată a utilizatorilor: " + e.getMessage());
                }
            } catch (SQLException e) {
                logger.severe("Eroare SQL la sincronizare: " + e.getMessage());
                updateStatus("❌ Eroare sincronizare: " + e.getMessage());
            } catch (Exception e) {
                logger.severe("Eroare la sincronizare: " + e.getMessage());
                updateStatus("❌ Eroare: " + e.getMessage());
            } finally {
                running = false;
            }
        }, "bg-sync").start();
    }

    /**
     * Forțează un refresh manual al datelor.
     */
    public void refreshNow() {
        runStartupSync();
    }

    public boolean isRunning() {
        return running;
    }
}
