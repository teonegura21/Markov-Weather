package com.sgbd.service.prediction;

import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;

/**
 * Serviciu pentru detectarea fenomenelor meteorologice prin funcții fuzzy.
 * Calculează scoruri pentru: ceață, furtună, ciclon, anticiclon, caniculă, inversiune.
 */
public class RecipeDetectorService {

    /**
     * Calculează scorurile detectoarelor de fenomene pentru toate zilele unui oraș.
     *
     * @param cityId identificatorul orașului
     * @throws SQLException dacă apare o eroare la baza de date
     */
    public void computeRecipeScores(int cityId) throws SQLException {
        String sql = "SELECT sp_compute_recipe_scores(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.execute();
        }
    }

    /**
     * Calculează scorurile pentru toate orașele din baza de date.
     *
     * @throws SQLException dacă apare o eroare la baza de date
     */
    public void computeScoresForAllCities() throws SQLException {
        String sql = "SELECT id FROM cities";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int cityId = rs.getInt("id");
                computeRecipeScores(cityId);
            }
        }
    }

    /**
     * Returnează scorurile detectoarelor pentru o zi specifică.
     *
     * @param cityId identificatorul orașului
     * @param date   data pentru care se cer scorurile
     * @return un obiect RecipeScores cu cele 6 scoruri, sau null dacă nu există
     * @throws SQLException dacă apare o eroare la baza de date
     */
    public RecipeScores getRecipeScores(int cityId, LocalDate date) throws SQLException {
        String sql = "SELECT fog_score, thunderstorm_score, cyclone_score, " +
                     "anticyclone_score, heatwave_score, inversion_score " +
                     "FROM weather_vectors WHERE city_id = ? AND date = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    RecipeScores scores = new RecipeScores();
                    scores.setFogScore(rs.getDouble("fog_score"));
                    scores.setThunderstormScore(rs.getDouble("thunderstorm_score"));
                    scores.setCycloneScore(rs.getDouble("cyclone_score"));
                    scores.setAnticycloneScore(rs.getDouble("anticyclone_score"));
                    scores.setHeatwaveScore(rs.getDouble("heatwave_score"));
                    scores.setInversionScore(rs.getDouble("inversion_score"));
                    return scores;
                }
            }
        }
        return null;
    }

    /**
     * Clasă internă care reprezintă scorurile celor 6 detectoare de fenomene.
     */
    public static class RecipeScores {
        private double fogScore;
        private double thunderstormScore;
        private double cycloneScore;
        private double anticycloneScore;
        private double heatwaveScore;
        private double inversionScore;

        public double getFogScore() {
            return fogScore;
        }

        public void setFogScore(double fogScore) {
            this.fogScore = fogScore;
        }

        public double getThunderstormScore() {
            return thunderstormScore;
        }

        public void setThunderstormScore(double thunderstormScore) {
            this.thunderstormScore = thunderstormScore;
        }

        public double getCycloneScore() {
            return cycloneScore;
        }

        public void setCycloneScore(double cycloneScore) {
            this.cycloneScore = cycloneScore;
        }

        public double getAnticycloneScore() {
            return anticycloneScore;
        }

        public void setAnticycloneScore(double anticycloneScore) {
            this.anticycloneScore = anticycloneScore;
        }

        public double getHeatwaveScore() {
            return heatwaveScore;
        }

        public void setHeatwaveScore(double heatwaveScore) {
            this.heatwaveScore = heatwaveScore;
        }

        public double getInversionScore() {
            return inversionScore;
        }

        public void setInversionScore(double inversionScore) {
            this.inversionScore = inversionScore;
        }
    }
}
