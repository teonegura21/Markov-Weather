package com.sgbd.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manager de stare persistentă pentru aplicație.
 * Salvează selecția orașului, data, dimensiunea ferestrei și preferințele
 * într-un fișier JSON în directorul home al utilizatorului.
 */
public class AppState {

    private static final Path STATE_DIR = Paths.get(System.getProperty("user.home"), ".prognoza-meteo");
    private static final Path STATE_FILE = STATE_DIR.resolve("state.json");
    private static final ObjectMapper mapper = new ObjectMapper();

    private static AppState instance;

    private int lastSelectedCityId = -1;
    private String lastSelectedDate = null;
    private int lastLoggedInUserId = -1;
    private double windowWidth = 1200;
    private double windowHeight = 850;
    private boolean celsiusUnit = true;
    private String windSpeedUnit = "km/h";
    private boolean autoSyncOnStartup = true;

    private AppState() {
        load();
    }

    public static synchronized AppState getInstance() {
        if (instance == null) {
            instance = new AppState();
        }
        return instance;
    }

    public void load() {
        if (!Files.exists(STATE_FILE)) {
            return;
        }
        try {
            ObjectNode node = (ObjectNode) mapper.readTree(STATE_FILE.toFile());
            if (node.has("lastSelectedCityId")) {
                this.lastSelectedCityId = node.get("lastSelectedCityId").asInt(-1);
            }
            if (node.has("lastSelectedDate")) {
                this.lastSelectedDate = node.get("lastSelectedDate").asText(null);
            }
            if (node.has("windowWidth")) {
                this.windowWidth = node.get("windowWidth").asDouble(1200);
            }
            if (node.has("windowHeight")) {
                this.windowHeight = node.get("windowHeight").asDouble(850);
            }
            if (node.has("celsiusUnit")) {
                this.celsiusUnit = node.get("celsiusUnit").asBoolean(true);
            }
            if (node.has("windSpeedUnit")) {
                this.windSpeedUnit = node.get("windSpeedUnit").asText("km/h");
            }
            if (node.has("autoSyncOnStartup")) {
                this.autoSyncOnStartup = node.get("autoSyncOnStartup").asBoolean(true);
            }
            if (node.has("lastLoggedInUserId")) {
                this.lastLoggedInUserId = node.get("lastLoggedInUserId").asInt(-1);
            }
        } catch (IOException e) {
            LoggerUtil.getLogger(AppState.class).warning("Eroare la încărcarea stării: " + e.getMessage());
        }
    }

    public void save() {
        try {
            if (!Files.exists(STATE_DIR)) {
                Files.createDirectories(STATE_DIR);
            }
            ObjectNode node = mapper.createObjectNode();
            node.put("lastSelectedCityId", lastSelectedCityId);
            node.put("lastSelectedDate", lastSelectedDate);
            node.put("windowWidth", windowWidth);
            node.put("windowHeight", windowHeight);
            node.put("celsiusUnit", celsiusUnit);
            node.put("windSpeedUnit", windSpeedUnit);
            node.put("autoSyncOnStartup", autoSyncOnStartup);
            node.put("lastLoggedInUserId", lastLoggedInUserId);
            mapper.writerWithDefaultPrettyPrinter().writeValue(STATE_FILE.toFile(), node);
        } catch (IOException e) {
            LoggerUtil.getLogger(AppState.class).warning("Eroare la salvarea stării: " + e.getMessage());
        }
    }

    public int getLastSelectedCityId() {
        return lastSelectedCityId;
    }

    public void setLastSelectedCityId(int lastSelectedCityId) {
        this.lastSelectedCityId = lastSelectedCityId;
    }

    public String getLastSelectedDate() {
        return lastSelectedDate;
    }

    public void setLastSelectedDate(String lastSelectedDate) {
        this.lastSelectedDate = lastSelectedDate;
    }

    public double getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(double windowWidth) {
        this.windowWidth = windowWidth;
    }

    public double getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(double windowHeight) {
        this.windowHeight = windowHeight;
    }

    public boolean isCelsiusUnit() {
        return celsiusUnit;
    }

    public void setCelsiusUnit(boolean celsiusUnit) {
        this.celsiusUnit = celsiusUnit;
    }

    public String getWindSpeedUnit() {
        return windSpeedUnit;
    }

    public void setWindSpeedUnit(String windSpeedUnit) {
        this.windSpeedUnit = windSpeedUnit;
    }

    public boolean isAutoSyncOnStartup() {
        return autoSyncOnStartup;
    }

    public void setAutoSyncOnStartup(boolean autoSyncOnStartup) {
        this.autoSyncOnStartup = autoSyncOnStartup;
    }

    public int getLastLoggedInUserId() {
        return lastLoggedInUserId;
    }

    public void setLastLoggedInUserId(int lastLoggedInUserId) {
        this.lastLoggedInUserId = lastLoggedInUserId;
    }
}
