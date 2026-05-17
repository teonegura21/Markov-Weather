package com.sgbd.service.prediction;

import com.sgbd.util.ClimateZoneUtil;
import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator pentru intregul pipeline de predictie probabilistica.
 * Coordonneaza vectorii, regimurile, Markov, HMM si simularile Monte Carlo.
 */
public class PredictionEngineService {

    private final WeatherVectorService vectorService = new WeatherVectorService();
    private final RecipeDetectorService recipeService = new RecipeDetectorService();
    private final ClusteringService clusteringService = new ClusteringService();
    private final MarkovModelService markovService = new MarkovModelService();
    private final HmmTrainingService hmmService = new HmmTrainingService();
    private final MonteCarloEngine monteCarlo = new MonteCarloEngine();

    /**
     * Construieste intregul pipeline de predictie pentru un oras.
     *
     * @param cityId identificatorul orasului
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void buildFullPipeline(int cityId) throws SQLException {
        vectorService.buildWeatherVector(cityId);
        recipeService.computeRecipeScores(cityId);
        clusteringService.runKmeans(16);
        clusteringService.labelRegimes();

        String climateZone = getClimateZoneForCity(cityId);
        markovService.buildTransitionTensor(climateZone);
        markovService.addStructuralZeros();

        hmmService.trainHmm(cityId, 8);
        computeSeasonalClimatology(cityId);
    }

    /**
     * Returneaza predictia probabilistica pentru o data specifica.
     * Foloseste cache-ul daca exista, alt ruleaza o simulare.
     *
     * @param cityId identificatorul orasului
     * @param date   data pentru care se doreste predictia
     * @return rezultatul Monte Carlo, sau null daca nu exista
     * @throws SQLException daca apare o eroare la baza de date
     */
    public MonteCarloResult getProbabilisticForecast(int cityId, LocalDate date) throws SQLException {
        String sql = "SELECT * FROM monte_carlo_predictions WHERE city_id = ? AND forecast_date = ? ORDER BY generated_at DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResult(rs);
                }
            }
        }

        monteCarlo.runSimulation(cityId, date, 10, 5000);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResult(rs);
                }
            }
        }
        return null;
    }

    /**
     * Returneaza probabilitatea agregata de furtuna pentru urmatoarele zile.
     *
     * @param cityId identificatorul orasului
     * @param days   numarul de zile
     * @return probabilitatea medie de furtuna
     * @throws SQLException daca apare o eroare la baza de date
     */
    public double getStormProbability(int cityId, int days) throws SQLException {
        LocalDate start = LocalDate.now();
        monteCarlo.runSimulation(cityId, start, days, 5000);

        String sql = "SELECT AVG(storm_prob) AS prob FROM monte_carlo_predictions WHERE city_id = ? AND forecast_date >= ? AND forecast_date < ? AND generated_at = (SELECT MAX(generated_at) FROM monte_carlo_predictions WHERE city_id = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(start));
            stmt.setDate(3, Date.valueOf(start.plusDays(days)));
            stmt.setInt(4, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("prob");
                }
            }
        }
        return 0.0;
    }

    /**
     * Calculeaza riscul de canicula extrema (temperatura maxima > prag).
     *
     * @param cityId    identificatorul orasului
     * @param threshold pragul de temperatura in grade Celsius
     * @return probabilitatea aproximata de depasire a pragului
     * @throws SQLException daca apare o eroare la baza de date
     */
    public double getExtremeHeatRisk(int cityId, double threshold) throws SQLException {
        LocalDate start = LocalDate.now();
        int days = 10;
        monteCarlo.runSimulation(cityId, start, days, 5000);

        String sql = "SELECT MAX(temp_max_p90) AS max_p90 FROM monte_carlo_predictions WHERE city_id = ? AND forecast_date >= ? AND forecast_date < ? AND generated_at = (SELECT MAX(generated_at) FROM monte_carlo_predictions WHERE city_id = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(start));
            stmt.setDate(3, Date.valueOf(start.plusDays(days)));
            stmt.setInt(4, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double maxP90 = rs.getDouble("max_p90");
                    return 1.0 / (1.0 + Math.exp(-0.5 * (maxP90 - threshold)));
                }
            }
        }
        return 0.0;
    }

    /**
     * Returneaza predictia completa pentru un interval de zile.
     *
     * @param cityId    identificatorul orasului
     * @param startDate data de inceput
     * @param days      numarul de zile
     * @return lista cu rezultatele pentru fiecare zi
     * @throws SQLException daca apare o eroare la baza de date
     */
    public List<MonteCarloResult> getFullForecast(int cityId, LocalDate startDate, int days) throws SQLException {
        List<MonteCarloResult> results = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            MonteCarloResult r = getProbabilisticForecast(cityId, startDate.plusDays(d));
            if (r != null) results.add(r);
        }
        return results;
    }

    private String getClimateZoneForCity(int cityId) throws SQLException {
        String sql = "SELECT latitude, longitude FROM cities WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double lat = rs.getDouble("latitude");
                    double lon = rs.getDouble("longitude");
                    return ClimateZoneUtil.classify(lat, lon);
                }
            }
        }
        return ClimateZoneUtil.EUROPE_WIDE;
    }

    public void computeSeasonalClimatology(int cityId) throws SQLException {
        String sql = "SELECT sp_compute_seasonal_climatology(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.execute();
        }
    }

    private MonteCarloResult mapResult(ResultSet rs) throws SQLException {
        MonteCarloResult r = new MonteCarloResult();
        r.setForecastDate(rs.getDate("forecast_date").toLocalDate());
        r.setHorizonDay(rs.getInt("horizon_day"));
        r.setTempMinP10(rs.getDouble("temp_min_p10"));
        r.setTempMinP50(rs.getDouble("temp_min_p50"));
        r.setTempMinP90(rs.getDouble("temp_min_p90"));
        r.setTempMaxP10(rs.getDouble("temp_max_p10"));
        r.setTempMaxP50(rs.getDouble("temp_max_p50"));
        r.setTempMaxP90(rs.getDouble("temp_max_p90"));
        r.setWindSpeedP50(rs.getDouble("wind_speed_p50"));
        r.setHumidityP50(rs.getDouble("humidity_p50"));
        r.setPrecipSumP50(rs.getDouble("precip_sum_p50"));
        r.setPrecipProb(rs.getDouble("precip_prob"));
        r.setStormProb(rs.getDouble("storm_prob"));
        r.setFogProb(rs.getDouble("fog_prob"));
        r.setHeatwaveProb(rs.getDouble("heatwave_prob"));
        r.setEnsembleSpread(rs.getDouble("ensemble_spread"));
        return r;
    }

    /**
     * Clasa de rezultat pentru predictiile Monte Carlo.
     */
    public static class MonteCarloResult {
        private LocalDate forecastDate;
        private int horizonDay;
        private double tempMinP10, tempMinP50, tempMinP90;
        private double tempMaxP10, tempMaxP50, tempMaxP90;
        private double windSpeedP50, humidityP50, precipSumP50;
        private double precipProb, stormProb, fogProb, heatwaveProb, ensembleSpread;

        public LocalDate getForecastDate() { return forecastDate; }
        public void setForecastDate(LocalDate forecastDate) { this.forecastDate = forecastDate; }
        public int getHorizonDay() { return horizonDay; }
        public void setHorizonDay(int horizonDay) { this.horizonDay = horizonDay; }
        public double getTempMinP10() { return tempMinP10; }
        public void setTempMinP10(double tempMinP10) { this.tempMinP10 = tempMinP10; }
        public double getTempMinP50() { return tempMinP50; }
        public void setTempMinP50(double tempMinP50) { this.tempMinP50 = tempMinP50; }
        public double getTempMinP90() { return tempMinP90; }
        public void setTempMinP90(double tempMinP90) { this.tempMinP90 = tempMinP90; }
        public double getTempMaxP10() { return tempMaxP10; }
        public void setTempMaxP10(double tempMaxP10) { this.tempMaxP10 = tempMaxP10; }
        public double getTempMaxP50() { return tempMaxP50; }
        public void setTempMaxP50(double tempMaxP50) { this.tempMaxP50 = tempMaxP50; }
        public double getTempMaxP90() { return tempMaxP90; }
        public void setTempMaxP90(double tempMaxP90) { this.tempMaxP90 = tempMaxP90; }
        public double getWindSpeedP50() { return windSpeedP50; }
        public void setWindSpeedP50(double windSpeedP50) { this.windSpeedP50 = windSpeedP50; }
        public double getHumidityP50() { return humidityP50; }
        public void setHumidityP50(double humidityP50) { this.humidityP50 = humidityP50; }
        public double getPrecipSumP50() { return precipSumP50; }
        public void setPrecipSumP50(double precipSumP50) { this.precipSumP50 = precipSumP50; }
        public double getPrecipProb() { return precipProb; }
        public void setPrecipProb(double precipProb) { this.precipProb = precipProb; }
        public double getStormProb() { return stormProb; }
        public void setStormProb(double stormProb) { this.stormProb = stormProb; }
        public double getFogProb() { return fogProb; }
        public void setFogProb(double fogProb) { this.fogProb = fogProb; }
        public double getHeatwaveProb() { return heatwaveProb; }
        public void setHeatwaveProb(double heatwaveProb) { this.heatwaveProb = heatwaveProb; }
        public double getEnsembleSpread() { return ensembleSpread; }
        public void setEnsembleSpread(double ensembleSpread) { this.ensembleSpread = ensembleSpread; }
    }
}
