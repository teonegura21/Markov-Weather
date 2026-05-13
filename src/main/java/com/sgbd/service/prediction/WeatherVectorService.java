package com.sgbd.service.prediction;

import com.sgbd.util.DatabaseConnection;

import java.sql.*;

/**
 * Serviciu pentru construirea și gestionarea vectorilor meteo 25D.
 * Fiecare zi pentru fiecare oraș este reprezentată de un vector de caracteristici
 * numerice folosit în motorul de predicție probabilistică.
 */
public class WeatherVectorService {

    /**
     * Construiește vectorul meteo 25D + derivate temporale pentru toate zilele unui oraș.
     *
     * @param cityId identificatorul orașului
     * @throws SQLException dacă apare o eroare la baza de date
     */
    public void buildWeatherVector(int cityId) throws SQLException {
        String sql = "SELECT sp_build_weather_vector(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.execute();
        }
    }

    /**
     * Construiește vectorii meteo pentru toate orașele din baza de date.
     *
     * @throws SQLException dacă apare o eroare la baza de date
     */
    public void buildVectorsForAllCities() throws SQLException {
        String sql = "SELECT id FROM cities";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int cityId = rs.getInt("id");
                buildWeatherVector(cityId);
            }
        }
    }

    /**
     * Verifică dacă există vectori meteo calculați pentru un oraș.
     *
     * @param cityId identificatorul orașului
     * @return true dacă există cel puțin un vector, false altfel
     * @throws SQLException dacă apare o eroare la baza de date
     */
    public boolean hasVectors(int cityId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM weather_vectors WHERE city_id = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Returnează numărul de zile pentru care s-au calculat vectori într-un oraș.
     *
     * @param cityId identificatorul orașului
     * @return numărul de vectori calculați
     * @throws SQLException dacă apare o eroare la baza de date
     */
    public int countVectors(int cityId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM weather_vectors WHERE city_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
