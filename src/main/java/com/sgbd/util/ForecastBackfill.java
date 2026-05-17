package com.sgbd.util;

import com.sgbd.service.WeatherImporterService;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ForecastBackfill {

    private static final int THRESHOLD = 30;
    private static final int FORECAST_DAYS = 16;
    private static final int HISTORICAL_DAYS = 14;
    private static final int API_DELAY_MS = 200;

    public static void main(String[] args) {
        WeatherImporterService importer = new WeatherImporterService();

        String sql = "SELECT c.id, c.name, c.latitude, c.longitude, COUNT(f.id) as cnt " +
                     "FROM cities c LEFT JOIN forecasts f ON c.id = f.city_id " +
                     "GROUP BY c.id, c.name, c.latitude, c.longitude " +
                     "HAVING COUNT(f.id) < ? " +
                     "ORDER BY c.id";

        List<CityNeed> needed = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, THRESHOLD);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    needed.add(new CityNeed(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"),
                            rs.getInt("cnt")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Eroare la citirea orașelor: " + e.getMessage(), e);
        }

        System.out.println("Orașe cu mai puțin de " + THRESHOLD + " prognoze: " + needed.size());
        if (needed.isEmpty()) {
            System.out.println("Toate orașele au suficiente date.");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate histStart = today.minusDays(HISTORICAL_DAYS);
        LocalDate histEnd = today.minusDays(1);

        int totalImportedForecast = 0;
        int totalImportedHistorical = 0;
        int totalErrors = 0;

        for (CityNeed city : needed) {
            System.out.println("Procesez orașul " + city.name
                    + " (id=" + city.id + ", existent=" + city.existing + ")...");

            try {
                WeatherImporterService.ImportResult r1 = importer.importForecastForCity(
                        city.id, city.lat, city.lon, FORECAST_DAYS);
                totalImportedForecast += r1.imported;
                totalErrors += r1.errors;
                System.out.println("  Forecast: importate " + r1.imported
                        + ", erori " + r1.errors);
                Thread.sleep(API_DELAY_MS);
            } catch (Exception e) {
                System.err.println("  Eroare forecast pentru " + city.name + ": " + e.getMessage());
                totalErrors++;
            }

            try {
                WeatherImporterService.ImportResult r2 = importer.importHistoricalForCity(
                        city.id, city.lat, city.lon, histStart, histEnd);
                totalImportedHistorical += r2.imported;
                totalErrors += r2.errors;
                System.out.println("  Historical: importate " + r2.imported
                        + ", erori " + r2.errors);
                Thread.sleep(API_DELAY_MS);
            } catch (Exception e) {
                System.err.println("  Eroare historical pentru " + city.name + ": " + e.getMessage());
                totalErrors++;
            }
        }

        System.out.println();
        System.out.println("=== Rezumat backfill ===");
        System.out.println("Orașe procesate: " + needed.size());
        System.out.println("Prognoze importate (forecast): " + totalImportedForecast);
        System.out.println("Prognoze importate (historical): " + totalImportedHistorical);
        System.out.println("Erori: " + totalErrors);
    }

    private static class CityNeed {
        final int id;
        final String name;
        final double lat;
        final double lon;
        final int existing;

        CityNeed(int id, String name, double lat, double lon, int existing) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.existing = existing;
        }
    }
}
