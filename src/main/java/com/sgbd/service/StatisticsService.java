package com.sgbd.service;

import com.sgbd.model.Anomaly;
import com.sgbd.model.CityRanking;
import com.sgbd.model.Forecast;
import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StatisticsService {

    public List<Anomaly> detectAnomalies(Integer cityId, Integer year) throws SQLException {
        List<Anomaly> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_detect_anomalies(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (cityId != null) stmt.setInt(1, cityId);
            else stmt.setNull(1, Types.INTEGER);
            if (year != null) stmt.setInt(2, year);
            else stmt.setNull(2, Types.INTEGER);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Anomaly a = new Anomaly();
                    a.setOras(rs.getString("oras"));
                    a.setTara(rs.getString("tara"));
                    a.setData(rs.getDate("data").toLocalDate());
                    a.setAnomalieTemperatura(rs.getBoolean("anomalie_temperatura"));
                    a.setAnomalieVant(rs.getBoolean("anomalie_vant"));
                    a.setAnomalieUmiditate(rs.getBoolean("anomalie_umiditate"));
                    a.setAnomalieUV(rs.getBoolean("anomalie_uv"));
                    a.setTempMin(rs.getDouble("temp_min"));
                    a.setTempMax(rs.getDouble("temp_max"));
                    a.setVitezaVant(rs.getDouble("viteza_vant"));
                    a.setUmiditate(rs.getInt("umiditate"));
                    a.setIndiceUV(rs.getInt("indice_uv"));
                    a.setDetaliiAnomalie(rs.getString("detalii_anomalie"));
                    list.add(a);
                }
            }
        }
        return list;
    }

    public List<Forecast> identifyErrorForecasts(Integer cityId, double threshold) throws SQLException {
        List<Forecast> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_identify_error_forecasts(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (cityId != null) stmt.setInt(1, cityId);
            else stmt.setNull(1, Types.INTEGER);
            stmt.setDouble(2, threshold);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Forecast f = new Forecast();
                    f.setCityName(rs.getString("oras"));
                    f.setDate(rs.getDate("data").toLocalDate());
                    f.setTempMin(rs.getDouble("temp_min"));
                    f.setTempMax(rs.getDouble("temp_max"));
                    f.setVoteCount(rs.getLong("nr_total_voturi"));
                    list.add(f);
                }
            }
        }
        return list;
    }

    public List<CityRanking> classifySimilarCities(int cityId, int days) throws SQLException {
        List<CityRanking> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_classify_similar_cities(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setInt(2, days);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CityRanking cr = new CityRanking();
                    cr.setOras(rs.getString("oras"));
                    cr.setTara(rs.getString("tara"));
                    cr.setValoare(rs.getDouble("distanta_euclidiana"));
                    cr.setUnitate("dist. euclidiana");
                    list.add(cr);
                }
            }
        }
        return list;
    }

    public List<CityRanking> getCityRankings(String criterion, int days) throws SQLException {
        List<CityRanking> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_city_rankings(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, criterion);
            stmt.setInt(2, days);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CityRanking cr = new CityRanking();
                    cr.setPozitie(rs.getLong("pozitie"));
                    cr.setOras(rs.getString("oras"));
                    cr.setTara(rs.getString("tara"));
                    cr.setValoare(rs.getDouble("valoare"));
                    cr.setUnitate(rs.getString("unitate"));
                    list.add(cr);
                }
            }
        }
        return list;
    }

    public double getForecastScore(int forecastId) throws SQLException {
        String sql = "SELECT sp_forecast_score(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, forecastId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0;
    }
}
