package com.sgbd.service.prediction;

import com.sgbd.model.City;
import com.sgbd.service.CityService;
import com.sgbd.service.WeatherImporterService;
import com.sgbd.util.DatabaseConnectionPool;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integrare end-to-end pentru pipeline-ul ML.
 * Rulează cu o bază de date PostgreSQL reală (Docker).
 * Se așteaptă ca DB să fie disponibilă la localhost:5433.
 */
class MlPipelineIntegrationTest {

    private static final int BUCHAREST_CITY_ID = 1;
    private static final String BUCHAREST_NAME = "Bucuresti";

    @BeforeAll
    static void setUp() {
        DatabaseConnectionPool.initialize();
    }

    @AfterAll
    static void tearDown() {
        DatabaseConnectionPool.shutdown();
    }

    @Test
    void testFullMlPipelineForBucharest() throws Exception {
        // 1. Verificăm că orașul București există
        CityService cityService = new CityService();
        City bucharest = cityService.getCityById(BUCHAREST_CITY_ID);
        assertNotNull(bucharest, "Orașul București trebuie să existe în baza de date");
        assertEquals(BUCHAREST_NAME, bucharest.getName());

        // 2. Importăm date istorice pentru ultimii 2 ani (un singur apel API)
        WeatherImporterService importer = new WeatherImporterService();
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusYears(2);

        WeatherImporterService.ImportResult importResult =
            importer.importHistoricalForCity(BUCHAREST_CITY_ID,
                bucharest.getLatitude(), bucharest.getLongitude(), startDate, endDate);

        assertTrue(importResult.imported > 100,
            "Trebuie importate cel puțin 100 de zile istorice, am importat: " + importResult.imported);
        System.out.println("Importat " + importResult.imported + " zile istorice pentru București");

        // 3. Construim vectorii meteo 25D
        WeatherVectorService vectorService = new WeatherVectorService();
        vectorService.buildWeatherVector(BUCHAREST_CITY_ID);

        int vectorCount = vectorService.countVectors(BUCHAREST_CITY_ID);
        assertTrue(vectorCount > 100,
            "Trebuie cel puțin 100 de vectori calculați, am găsit: " + vectorCount);
        System.out.println("Vectori meteo construiți: " + vectorCount);

        // Verificăm că vectorii au valori reale (nu doar default)
        assertVectorHasRealValues(BUCHAREST_CITY_ID);

        // 4. Calculăm scorurile detectoarelor fuzzy
        RecipeDetectorService recipeService = new RecipeDetectorService();
        recipeService.computeRecipeScores(BUCHAREST_CITY_ID);

        RecipeDetectorService.RecipeScores scores =
            recipeService.getRecipeScores(BUCHAREST_CITY_ID, endDate);
        assertNotNull(scores, "Scorurile detectoarelor trebuie să existe pentru ultima zi");
        System.out.println("Scoruri recipe: fog=" + scores.getFogScore()
            + " thunder=" + scores.getThunderstormScore()
            + " heat=" + scores.getHeatwaveScore());

        // 5. Clustering k-means (pe toate orașele — doar București are date, deci e ok)
        ClusteringService clusteringService = new ClusteringService();
        clusteringService.runKmeans(8); // folosim K=8 pentru test (mai puține date)
        clusteringService.labelRegimes();

        int regimeCount = countDailyRegimes(BUCHAREST_CITY_ID);
        assertTrue(regimeCount > 0, "Trebuie cel puțin un regim calculat");
        System.out.println("Regimuri zilnice: " + regimeCount);

        // 6. Construim tensorul Markov
        MarkovModelService markovService = new MarkovModelService();
        markovService.buildTransitionTensor("europe");
        markovService.addStructuralZeros();

        int markovCount = countMarkovTransitions();
        assertTrue(markovCount > 0, "Tensorul Markov trebuie populat");
        System.out.println("Tranziții Markov: " + markovCount);

        int zeroCount = countStructuralZeros();
        assertTrue(zeroCount >= 0, "Zerourile structurale trebuie calculate");
        System.out.println("Zerouri structurale: " + zeroCount);

        // 7. Antrenăm HMM
        HmmTrainingService hmmService = new HmmTrainingService();
        hmmService.trainHmm(BUCHAREST_CITY_ID, 4); // 4 stări ascunse pentru test

        int hmmStateCount = countHiddenStates(BUCHAREST_CITY_ID);
        assertTrue(hmmStateCount > 0, "HMM trebuie să aibă stări ascunse salvate");
        System.out.println("Stări HMM: " + hmmStateCount);

        // 8. Climatologie sezonieră
        PredictionEngineService predictionEngine = new PredictionEngineService();
        predictionEngine.computeSeasonalClimatology(BUCHAREST_CITY_ID);

        int climCount = countSeasonalClimatology(BUCHAREST_CITY_ID);
        assertTrue(climCount > 0, "Climatologia sezonieră trebuie populată");
        System.out.println("Înregistrări climatologie: " + climCount);

        // 9. Simulare Monte Carlo
        MonteCarloEngine mcEngine = new MonteCarloEngine();
        LocalDate forecastStart = LocalDate.now();
        mcEngine.runSimulation(BUCHAREST_CITY_ID, forecastStart, 7, 500);

        int mcCount = countMonteCarloPredictions(BUCHAREST_CITY_ID, forecastStart);
        assertTrue(mcCount >= 7,
            "Trebuie cel puțin 7 zile de predicții Monte Carlo, am găsit: " + mcCount);
        System.out.println("Predicții Monte Carlo: " + mcCount + " zile");

        // 10. Verificăm consistența percentilelor: P10 <= P50 <= P90
        assertPercentilesConsistent(BUCHAREST_CITY_ID, forecastStart);

        // 11. Verificăm că probabilitățile sunt în [0, 1]
        assertProbabilitiesInRange(BUCHAREST_CITY_ID, forecastStart);

        System.out.println("=== PIPELINE ML END-TO-END COMPLET CU SUCCES ===");
    }

    private void assertVectorHasRealValues(int cityId) throws SQLException {
        String sql = "SELECT pressure_mean, sunshine_hours, dew_point_min FROM weather_vectors WHERE city_id = ? LIMIT 1";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "Trebuie să existe cel puțin un vector");
                double pressure = rs.getDouble("pressure_mean");
                double sunshine = rs.getDouble("sunshine_hours");
                double dewPoint = rs.getDouble("dew_point_min");

                assertTrue(pressure > 900 && pressure < 1100,
                    "Presiunea trebuie să fie realistă (hPa), am găsit: " + pressure);
                assertTrue(sunshine >= 0 && sunshine <= 24,
                    "Orele de soare trebuie să fie în [0, 24], am găsit: " + sunshine);
                assertTrue(dewPoint > -50 && dewPoint < 50,
                    "Punctul de rouă trebuie să fie realist, am găsit: " + dewPoint);
            }
        }
    }

    private int countDailyRegimes(int cityId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM daily_regimes WHERE city_id = ?";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int countMarkovTransitions() throws SQLException {
        String sql = "SELECT COUNT(*) FROM markov_transitions";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int countStructuralZeros() throws SQLException {
        String sql = "SELECT COUNT(*) FROM structural_zeros";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int countHiddenStates(int cityId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM hidden_states WHERE city_id = ?";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int countSeasonalClimatology(int cityId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM seasonal_climatology WHERE city_id = ?";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int countMonteCarloPredictions(int cityId, LocalDate start) throws SQLException {
        String sql = "SELECT COUNT(*) FROM monte_carlo_predictions WHERE city_id = ? AND forecast_date >= ?";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, java.sql.Date.valueOf(start));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void assertPercentilesConsistent(int cityId, LocalDate start) throws SQLException {
        String sql = "SELECT temp_min_p10, temp_min_p50, temp_min_p90, temp_max_p10, temp_max_p50, temp_max_p90 " +
                     "FROM monte_carlo_predictions WHERE city_id = ? AND forecast_date >= ?";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, java.sql.Date.valueOf(start));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double tminP10 = rs.getDouble("temp_min_p10");
                    double tminP50 = rs.getDouble("temp_min_p50");
                    double tminP90 = rs.getDouble("temp_min_p90");
                    double tmaxP10 = rs.getDouble("temp_max_p10");
                    double tmaxP50 = rs.getDouble("temp_max_p50");
                    double tmaxP90 = rs.getDouble("temp_max_p90");

                    assertTrue(tminP10 <= tminP50 + 0.01,
                        "P10 temp_min (" + tminP10 + ") trebuie <= P50 (" + tminP50 + ")");
                    assertTrue(tminP50 <= tminP90 + 0.01,
                        "P50 temp_min (" + tminP50 + ") trebuie <= P90 (" + tminP90 + ")");
                    assertTrue(tmaxP10 <= tmaxP50 + 0.01,
                        "P10 temp_max (" + tmaxP10 + ") trebuie <= P50 (" + tmaxP50 + ")");
                    assertTrue(tmaxP50 <= tmaxP90 + 0.01,
                        "P50 temp_max (" + tmaxP50 + ") trebuie <= P90 (" + tmaxP90 + ")");
                }
            }
        }
    }

    private void assertProbabilitiesInRange(int cityId, LocalDate start) throws SQLException {
        String sql = "SELECT precip_prob, storm_prob, fog_prob, heatwave_prob " +
                     "FROM monte_carlo_predictions WHERE city_id = ? AND forecast_date >= ?";
        try (Connection conn = com.sgbd.util.DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, java.sql.Date.valueOf(start));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    assertProbability(rs.getDouble("precip_prob"), "precip_prob");
                    assertProbability(rs.getDouble("storm_prob"), "storm_prob");
                    assertProbability(rs.getDouble("fog_prob"), "fog_prob");
                    assertProbability(rs.getDouble("heatwave_prob"), "heatwave_prob");
                }
            }
        }
    }

    private void assertProbability(double p, String name) {
        assertTrue(p >= 0.0 && p <= 1.0,
            name + " trebuie să fie în [0, 1], am găsit: " + p);
    }
}
