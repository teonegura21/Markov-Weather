package com.sgbd.util;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DbDiagnostic {
    public static void main(String[] args) {
        String countCitiesSql = "SELECT COUNT(*) FROM cities";
        String countForecastsSql = "SELECT COUNT(*) FROM forecasts";
        String cityForecastSql = "SELECT c.id, c.name, COUNT(f.id) as forecast_count " +
                                 "FROM cities c LEFT JOIN forecasts f ON c.id = f.city_id " +
                                 "GROUP BY c.id, c.name ORDER BY c.id";

        try (Connection conn = DatabaseConnection.getConnection()) {
            int totalCities = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countCitiesSql)) {
                if (rs.next()) {
                    totalCities = rs.getInt(1);
                }
            }

            int totalForecasts = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countForecastsSql)) {
                if (rs.next()) {
                    totalForecasts = rs.getInt(1);
                }
            }

            List<String> zeroCities = new ArrayList<>();
            int minCount = Integer.MAX_VALUE;
            int maxCount = 0;
            int sumCount = 0;

            System.out.println("=== Diagnostic Prognoze ===");
            System.out.println("Total orașe: " + totalCities);
            System.out.println("Total prognoze: " + totalForecasts);
            System.out.println();
            System.out.printf("%-4s %-30s %s%n", "ID", "Nume", "Prognoze");
            System.out.println("--------------------------------------------------");

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(cityForecastSql)) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    int count = rs.getInt("forecast_count");
                    System.out.printf("%-4d %-30s %d%n", id, name, count);
                    if (count == 0) {
                        zeroCities.add(name);
                    }
                    if (count < minCount) {
                        minCount = count;
                    }
                    if (count > maxCount) {
                        maxCount = count;
                    }
                    sumCount += count;
                }
            }

            System.out.println();
            System.out.println("Orașe fără prognoze: " + zeroCities.size());
            for (String name : zeroCities) {
                System.out.println("  - " + name);
            }
            System.out.println();
            System.out.println("Min prognoze/oraș: " + minCount);
            System.out.println("Max prognoze/oraș: " + maxCount);
            System.out.println("Medie prognoze/oraș: "
                    + (totalCities > 0
                            ? String.format("%.2f", (double) sumCount / totalCities)
                            : "N/A"));

        } catch (SQLException e) {
            System.err.println("Eroare SQL: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            DatabaseConnectionPool.shutdown();
        }
    }
}
