package com.sgbd.service.prediction;

import com.sgbd.model.City;
import com.sgbd.service.CityService;
import com.sgbd.service.WeatherApiService;
import com.sgbd.util.DatabaseConnection;
import com.sgbd.util.LoggerUtil;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Serviciu pentru calculul acuratetii predictiilor motorului Monte Carlo.
 * Compara predictiile cu datele meteo reale de la Open-Meteo si calculeaza metrici.
 */
public class AccuracyService {

    private static final Logger logger = LoggerUtil.getLogger(AccuracyService.class);
    private final WeatherApiService apiService = new WeatherApiService();
    private final PredictionEngineService predictionEngine = new PredictionEngineService();
    private final CityService cityService = new CityService();

    /**
     * Fetch date meteo reale pentru o data specifica de la Open-Meteo.
     *
     * @param cityId identificatorul orasului
     * @param date   data pentru care se doresc datele reale
     * @return ActualWeather sau null daca nu se poate obtine
     */
    public ActualWeather fetchActualWeather(int cityId, LocalDate date) {
        City city = getCityById(cityId);
        if (city == null) {
            logger.warning("Orasul cu ID " + cityId + " nu a fost gasit");
            return null;
        }

        try {
            List<WeatherApiService.DailyWeather> data = apiService.fetchHistorical(
                city.getLatitude(), city.getLongitude(), date, date);

            if (data == null || data.isEmpty()) {
                logger.warning("Nu s-au obtinut date reale pentru " + city.getName() + " la data " + date);
                return null;
            }

            WeatherApiService.DailyWeather dw = data.get(0);
            ActualWeather actual = new ActualWeather();
            actual.setDate(dw.date);
            actual.setTempMin(dw.tempMin);
            actual.setTempMax(dw.tempMax);
            actual.setWindSpeed(dw.windSpeed);
            actual.setHumidity(dw.humidity);
            actual.setPrecipSum(0); // nu avem precip sum in DailyWeather
            actual.setIconType(deriveIcon(dw.tempMin, dw.tempMax, dw.humidity, dw.windSpeed, dw.uvIndex));
            return actual;

        } catch (Exception e) {
            logger.warning("Eroare la fetch date reale: " + e.getMessage());
            return null;
        }
    }

    /**
     * Compara predictia Monte Carlo cu datele reale pentru o anumita data si orizont.
     *
     * @param cityId       identificatorul orasului
     * @param forecastDate data pentru care se face comparatia
     * @param horizonDay   orizontul de predictie (cate zile inainte a fost facuta predictia)
     * @return AccuracyMetrics sau null
     */
    public AccuracyMetrics compareWithPrediction(int cityId, LocalDate forecastDate, int horizonDay) {
        // Obtine predictia din cache (daca exista) sau genereaza una noua
        PredictionEngineService.MonteCarloResult predicted;
        try {
            predicted = predictionEngine.getProbabilisticForecast(cityId, forecastDate);
        } catch (Exception e) {
            logger.warning("Nu s-a putut obtine predictia: " + e.getMessage());
            return null;
        }

        if (predicted == null) {
            logger.warning("Nu exista predictie in cache pentru " + forecastDate);
            return null;
        }

        // Obtine datele reale
        ActualWeather actual = fetchActualWeather(cityId, forecastDate);
        if (actual == null) {
            return null;
        }

        // Calculeaza metrici
        AccuracyMetrics metrics = new AccuracyMetrics();
        metrics.setDate(forecastDate);
        metrics.setHorizonDay(horizonDay);

        metrics.setPredictedTempMaxP50(predicted.getTempMaxP50());
        metrics.setPredictedTempMinP50(predicted.getTempMinP50());
        metrics.setPredictedWindSpeedP50(predicted.getWindSpeedP50());
        metrics.setPredictedHumidityP50(predicted.getHumidityP50());

        metrics.setActualTempMax(actual.getTempMax());
        metrics.setActualTempMin(actual.getTempMin());
        metrics.setActualWindSpeed(actual.getWindSpeed());
        metrics.setActualHumidity(actual.getHumidity());

        double maeMax = Math.abs(predicted.getTempMaxP50() - actual.getTempMax());
        double maeMin = Math.abs(predicted.getTempMinP50() - actual.getTempMin());
        metrics.setMaeTempMax(maeMax);
        metrics.setMaeTempMin(maeMin);
        metrics.setRmseTempMax(Math.sqrt(maeMax * maeMax));
        metrics.setBiasTempMax(predicted.getTempMaxP50() - actual.getTempMax());
        metrics.setWindError(Math.abs(predicted.getWindSpeedP50() - actual.getWindSpeed()));
        metrics.setHumidityError(Math.abs(predicted.getHumidityP50() - actual.getHumidity()));

        // Hit rate pentru evenimente (simplificat)
        metrics.setStormHit(predicted.getStormProb() > 0.3 && actual.getWindSpeed() > 50);
        metrics.setHeatwaveHit(predicted.getHeatwaveProb() > 0.3 && actual.getTempMax() > 35);
        metrics.setPrecipHit(predicted.getPrecipProb() > 0.3);
        metrics.setFogHit(predicted.getFogProb() > 0.3);

        // Salveaza in baza de date
        storeAccuracyMetrics(cityId, metrics);

        return metrics;
    }

    /**
     * Ruleaza un backtest complet pentru ultimele N zile.
     *
     * @param cityId    identificatorul orasului
     * @param daysBack  cate zile in urma sa se compare
     * @param maxHorizon orizont maxim de predictie
     * @return lista de metrici pentru fiecare zi
     */
    public List<AccuracyMetrics> runBacktest(int cityId, int daysBack, int maxHorizon) {
        List<AccuracyMetrics> results = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int d = 1; d <= daysBack; d++) {
            LocalDate targetDate = today.minusDays(d);

            // Pentru simplitate, folosim orizont = 1 (predictie facuta cu o zi inainte)
            // In practica, ar trebui sa facem predictii pentru fiecare orizont
            AccuracyMetrics m = compareWithPrediction(cityId, targetDate, 1);
            if (m != null) {
                results.add(m);
            }

            // Delay mic pentru a nu supraincarca API-ul
            try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        return results;
    }

    /**
     * Returneaza un rezumat agregat al acuratetii.
     *
     * @param cityId   identificatorul orasului
     * @param daysBack cate zile in urma
     * @return AccuracySummary
     */
    public AccuracySummary getAccuracySummary(int cityId, int daysBack) {
        String sql = "SELECT * FROM sp_get_accuracy_summary(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setInt(2, daysBack);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new AccuracySummary(
                        rs.getDouble("overall_mae"),
                        rs.getDouble("overall_rmse"),
                        rs.getDouble("overall_bias"),
                        rs.getInt("total_predictions"),
                        rs.getDouble("avg_hit_rate")
                    );
                }
            }
        } catch (SQLException e) {
            logger.warning("Eroare la obtinerea rezumatului: " + e.getMessage());
        }
        return new AccuracySummary(0, 0, 0, 0, 0);
    }

    /**
     * Returneaza clasamentul orașelor dupa acuratete.
     *
     * @param daysBack cate zile in urma
     * @return lista de clasament
     */
    public List<CityAccuracyRanking> getCityAccuracyRanking(int daysBack) {
        List<CityAccuracyRanking> rankings = new ArrayList<>();
        String sql =
            "SELECT c.id, c.name, " +
            "COALESCE(AVG(pa.mae_temp), 0) AS mae, " +
            "COALESCE(AVG(pa.rmse_temp), 0) AS rmse, " +
            "COUNT(pa.id) AS total " +
            "FROM cities c " +
            "LEFT JOIN prediction_accuracy pa ON pa.city_id = c.id " +
            "AND pa.computed_at >= CURRENT_DATE - (? * INTERVAL '1 day') " +
            "GROUP BY c.id, c.name " +
            "ORDER BY mae ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, daysBack);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rankings.add(new CityAccuracyRanking(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("mae"),
                        rs.getDouble("rmse"),
                        rs.getInt("total")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("Eroare la clasament: " + e.getMessage());
        }
        return rankings;
    }

    private void storeAccuracyMetrics(int cityId, AccuracyMetrics m) {
        String sql = "CALL sp_store_accuracy_result(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(m.getDate()));
            stmt.setInt(3, m.getHorizonDay());
            stmt.setDouble(4, m.getPredictedTempMinP50());
            stmt.setDouble(5, m.getPredictedTempMaxP50());
            stmt.setDouble(6, m.getActualTempMin());
            stmt.setDouble(7, m.getActualTempMax());
            stmt.setDouble(8, m.getPredictedWindSpeedP50());
            stmt.setDouble(9, m.getActualWindSpeed());
            stmt.setInt(10, (int) m.getPredictedHumidityP50());
            stmt.setInt(11, (int) m.getActualHumidity());
            stmt.setDouble(12, m.getMaeTempMax());
            stmt.setDouble(13, m.getRmseTempMax());
            stmt.setDouble(14, m.getBiasTempMax());
            stmt.setString(15, null);
            stmt.setBoolean(16, m.isStormHit() || m.isHeatwaveHit() || m.isPrecipHit());
            stmt.execute();
        } catch (SQLException e) {
            logger.warning("Eroare la salvarea metricilor: " + e.getMessage());
        }
    }

    /**
     * Calculeaza metrici de acuratete pentru o singura valoare prezisa vs. reala.
     * Util pentru testare si validare rapida.
     */
    static AccuracyMetrics computeMetrics(double predictedTempMax, double actualTempMax) {
        AccuracyMetrics metrics = new AccuracyMetrics();
        double mae = Math.abs(predictedTempMax - actualTempMax);
        metrics.setMaeTempMax(mae);
        metrics.setRmseTempMax(Math.sqrt(mae * mae));
        metrics.setBiasTempMax(predictedTempMax - actualTempMax);
        return metrics;
    }

    private City getCityById(int cityId) {
        try {
            for (City c : cityService.getAllCities()) {
                if (c.getId() == cityId) return c;
            }
        } catch (SQLException e) {
            logger.warning("Eroare la citirea oraselor: " + e.getMessage());
        }
        return null;
    }

    private String deriveIcon(double tempMin, double tempMax, int humidity, double wind, int uv) {
        if (humidity > 85 && tempMin < 0) return "snow";
        if (humidity > 80 && wind > 50) return "storm";
        if (humidity > 70) return "rain";
        if (humidity > 50 && wind > 30) return "cloudy_windy";
        if (humidity > 50) return "cloudy";
        if (tempMax > 30 && uv > 7) return "sunny_hot";
        if (tempMax > 20) return "sunny";
        return "partly_cloudy";
    }
}
