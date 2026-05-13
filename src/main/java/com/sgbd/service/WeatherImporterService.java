package com.sgbd.service;

import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

import com.sgbd.util.LoggerUtil;

public class WeatherImporterService {
    private static final Logger logger = LoggerUtil.getLogger(WeatherImporterService.class);
    private static final int API_DELAY_MS = 250;
    private static final int MAX_API_CALLS_PER_RUN = 50;
    private static final int STALE_THRESHOLD_HOURS = 24;

    private final WeatherApiService apiService = new WeatherApiService();
    private int apiCalls = 0;

    public static class ImportResult {
        public int imported;
        public int skipped;
        public int errors;
    }

    public static class FreshnessStatus {
        public boolean needsRefresh;
        public LocalDateTime lastFetched;
        public long hoursSinceLastFetch;
        public int staleForecastCount;
        public int totalForecastCount;
    }

    public ImportResult importHistoricalForCity(int cityId, double lat, double lon,
                                                 LocalDate start, LocalDate end) {
        List<WeatherApiService.DailyWeather> data = apiService.fetchHistorical(lat, lon, start, end);
        return upsertForecasts(cityId, data, "historical_api");
    }

    public ImportResult importForecastForCity(int cityId, double lat, double lon, int days) {
        List<WeatherApiService.DailyWeather> data = apiService.fetchForecast(lat, lon, days);
        return upsertForecasts(cityId, data, "forecast_api");
    }

    public ImportResult importHistoricalForAllCities(LocalDate start, LocalDate end) {
        ImportResult total = new ImportResult();
        apiCalls = 0;

        String sql = "SELECT id, latitude, longitude FROM cities";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                if (apiCalls >= MAX_API_CALLS_PER_RUN) break;

                int cityId = rs.getInt("id");
                double lat = rs.getDouble("latitude");
                double lon = rs.getDouble("longitude");

                try {
                    apiCalls++;
                    List<WeatherApiService.DailyWeather> data = apiService.fetchHistorical(lat, lon, start, end);
                    ImportResult r = upsertForecasts(cityId, data, "historical_api");
                    total.imported += r.imported;
                    total.skipped += r.skipped;
                    total.errors += r.errors;
                } catch (Exception e) {
                    logger.severe("Eroare import istoric pentru orașul " + cityId + ": " + e.getMessage());
                    total.errors++;
                }

                throttle();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }

        generateWarningsForPeriod(start, end);
        return total;
    }

    public ImportResult importForecastForAllCities(int days) {
        ImportResult total = new ImportResult();
        apiCalls = 0;
        LocalDate today = LocalDate.now();

        String sql = "SELECT id, latitude, longitude FROM cities";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                if (apiCalls >= MAX_API_CALLS_PER_RUN) break;

                int cityId = rs.getInt("id");
                double lat = rs.getDouble("latitude");
                double lon = rs.getDouble("longitude");

                try {
                    apiCalls++;
                    List<WeatherApiService.DailyWeather> data = apiService.fetchForecast(lat, lon, days);
                    ImportResult r = upsertForecasts(cityId, data, "forecast_api");
                    total.imported += r.imported;
                    total.skipped += r.skipped;
                    total.errors += r.errors;
                } catch (Exception e) {
                    logger.severe("Eroare import prognoză pentru orașul " + cityId + ": " + e.getMessage());
                    total.errors++;
                }

                throttle();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }

        generateWarningsForPeriod(today, today.plusDays(days));
        return total;
    }

    public FreshnessStatus checkFreshness() {
        FreshnessStatus status = new FreshnessStatus();
        String sql = "SELECT " +
                     "COUNT(*) AS total, " +
                     "COUNT(*) FILTER (WHERE data_source = 'forecast_api') AS forecast_count, " +
                     "COUNT(*) FILTER (WHERE data_source = 'forecast_api' AND (fetched_at IS NULL OR fetched_at < ?)) AS stale_count, " +
                     "MAX(fetched_at) FILTER (WHERE data_source = 'forecast_api') AS last_fetched " +
                     "FROM forecasts";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusHours(STALE_THRESHOLD_HOURS)));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    status.totalForecastCount = rs.getInt("total");
                    int forecastCount = rs.getInt("forecast_count");
                    status.staleForecastCount = rs.getInt("stale_count");
                    Timestamp ts = rs.getTimestamp("last_fetched");
                    if (ts != null) {
                        status.lastFetched = ts.toLocalDateTime();
                        status.hoursSinceLastFetch = ChronoUnit.HOURS.between(status.lastFetched, LocalDateTime.now());
                    }
                    status.needsRefresh = forecastCount == 0 || status.staleForecastCount > 0;
                }
            }
        } catch (SQLException e) {
            logger.severe("Eroare verificare prospețime date: " + e.getMessage());
        }
        return status;
    }

    public void refreshStaleForecasts() {
        FreshnessStatus status = checkFreshness();
        if (!status.needsRefresh) return;

        LocalDate today = LocalDate.now();
        apiCalls = 0;

        String sql = "SELECT id, latitude, longitude FROM cities";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                if (apiCalls >= MAX_API_CALLS_PER_RUN) break;

                int cityId = rs.getInt("id");
                double lat = rs.getDouble("latitude");
                double lon = rs.getDouble("longitude");

                try {
                    apiCalls++;
                    List<WeatherApiService.DailyWeather> data = apiService.fetchForecast(lat, lon, 10);
                    upsertForecasts(cityId, data, "forecast_api");
                } catch (Exception e) {
                    logger.severe("Eroare reîmprospătare pentru orașul " + cityId + ": " + e.getMessage());
                }

                throttle();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }

        generateWarningsForPeriod(today, today.plusDays(10));
    }

    public void cleanupOldForecasts() {
        String sql = "DELETE FROM forecasts WHERE data_source = 'forecast_api' AND date < CURRENT_DATE - INTERVAL '2 days'";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            logger.info("S-au șters " + deleted + " intrări prognoză vechi");
        } catch (SQLException e) {
            logger.severe("Eroare curățare prognoze vechi: " + e.getMessage());
        }
    }

    private ImportResult upsertForecasts(int cityId, List<WeatherApiService.DailyWeather> data, String source) {
        ImportResult result = new ImportResult();

        String sql = "INSERT INTO forecasts (city_id, date, temp_min, temp_max, wind_speed, icon_type, uv_index, humidity, warning_text, data_source, fetched_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (city_id, date) DO UPDATE SET " +
                     "temp_min = EXCLUDED.temp_min, temp_max = EXCLUDED.temp_max, " +
                     "wind_speed = EXCLUDED.wind_speed, icon_type = EXCLUDED.icon_type, " +
                     "uv_index = EXCLUDED.uv_index, humidity = EXCLUDED.humidity, " +
                     "data_source = EXCLUDED.data_source, fetched_at = EXCLUDED.fetched_at, " +
                     "updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (WeatherApiService.DailyWeather dw : data) {
                String icon = deriveIcon(dw.tempMin, dw.tempMax, dw.humidity, dw.windSpeed, dw.uvIndex);
                stmt.setInt(1, cityId);
                stmt.setDate(2, Date.valueOf(dw.date));
                stmt.setDouble(3, round(dw.tempMin));
                stmt.setDouble(4, round(dw.tempMax));
                stmt.setDouble(5, round(dw.windSpeed));
                stmt.setString(6, icon);
                stmt.setInt(7, dw.uvIndex);
                stmt.setInt(8, dw.humidity);
                stmt.setString(9, source);
                stmt.addBatch();
                result.imported++;
            }

            stmt.executeBatch();

        } catch (SQLException e) {
            logger.severe("Eroare upsert pentru orașul " + cityId + ": " + e.getMessage());
            result.errors = result.imported;
            result.imported = 0;
        }

        return result;
    }

    private void generateWarningsForPeriod(LocalDate start, LocalDate end) {
        String sql = "UPDATE forecasts SET warning_text = NULL WHERE date BETWEEN ? AND ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(start));
            stmt.setDate(2, Date.valueOf(end));
            stmt.executeUpdate();

            sql = "UPDATE forecasts SET warning_text = " +
                  "CASE " +
                  "  WHEN humidity > 85 AND wind_speed > 60 THEN 'AVERTIZARE FURTUNA: umiditate foarte mare si vant puternic. Recomandam a se sta in casa.' " +
                  "  WHEN temp_max > 38 THEN 'AVERTIZARE CANICULA: temperaturi extreme. Evitati expunerea la soare si hidratati-va corespunzator.' " +
                  "  WHEN temp_min < -15 THEN 'AVERTIZARE GER: temperaturi extrem de scazute. Imbracaminte groasa si evitati deplasarile lungi.' " +
                  "  WHEN humidity > 90 THEN 'AVERTIZARE PLOTOIE TORENTIALA: umiditate foarte mare. Luati umbrela si evitati zonele inundabile.' " +
                  "  WHEN wind_speed > 70 THEN 'AVERTIZARE VANT PUTERNIC: vant peste 70 km/h. Evitati deplasarile si parcati in siguranta.' " +
                  "  ELSE NULL " +
                  "END, updated_at = CURRENT_TIMESTAMP " +
                  "WHERE date BETWEEN ? AND ?";
            try (PreparedStatement stmt2 = conn.prepareStatement(sql)) {
                stmt2.setDate(1, Date.valueOf(start));
                stmt2.setDate(2, Date.valueOf(end));
                stmt2.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Eroare generare avertizări: " + e.getMessage());
        }
    }

    private void throttle() {
        try { Thread.sleep(API_DELAY_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static String deriveIcon(double tempMin, double tempMax, int humidity, double wind, int uv) {
        if (humidity > 85 && tempMin < 0) return "snow";
        if (humidity > 80 && wind > 50) return "storm";
        if (humidity > 70) return "rain";
        if (humidity > 50 && wind > 30) return "cloudy_windy";
        if (humidity > 50) return "cloudy";
        if (tempMax > 30 && uv > 7) return "sunny_hot";
        if (tempMax > 20) return "sunny";
        return "partly_cloudy";
    }

    static double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
