package com.sgbd.service;

import com.sgbd.model.City;
import com.sgbd.service.prediction.*;
import com.sgbd.util.DatabaseConnection;
import com.sgbd.util.LoggerUtil;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Serviciu pentru popularea bazei de date cu date meteo reale.
 * Importa date istorice si prognoze de la Open-Meteo pentru toate orasele.
 */
public class DataPopulationService {

    private static final Logger logger = LoggerUtil.getLogger(DataPopulationService.class);
    private final WeatherImporterService importer = new WeatherImporterService();
    private final CityService cityService = new CityService();

    /**
     * Populeaza baza de date cu date istorice pentru ultimele N ani.
     *
     * @param years numarul de ani in urma de la care se importa
     * @return rezultatul agregat al importului
     */
    public WeatherImporterService.ImportResult populateHistoricalData(int years) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusYears(years);
        logger.info("Se importa date istorice de la " + start + " pana la " + end);
        return importer.importHistoricalForAllCities(start, end);
    }

    /**
     * Importa prognoza actuala pentru toate orasele.
     *
     * @param days numarul de zile de prognoza
     * @return rezultatul agregat al importului
     */
    public WeatherImporterService.ImportResult populateForecastData(int days) {
        logger.info("Se importa prognoza meteo pentru " + days + " zile");
        return importer.importForecastForAllCities(days);
    }

    /**
     * Importa date istorice pentru un singur oras.
     *
     * @param cityId identificatorul orasului
     * @param years  numarul de ani
     * @return rezultatul importului
     */
    public WeatherImporterService.ImportResult populateHistoricalForCity(int cityId, int years) {
        City city = getCityById(cityId);
        if (city == null) {
            logger.warning("Orasul cu ID " + cityId + " nu a fost gasit");
            return new WeatherImporterService.ImportResult();
        }
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusYears(years);
        logger.info("Se importa date istorice pentru " + city.getName());
        return importer.importHistoricalForCity(cityId, city.getLatitude(), city.getLongitude(), start, end);
    }

    /**
     * Importa prognoza orara pentru toate orasele.
     *
     * @param days numarul de zile de prognoza orara
     * @return rezultatul agregat al importului
     */
    public WeatherImporterService.ImportResult populateHourlyForecastData(int days) {
        WeatherImporterService.ImportResult total = new WeatherImporterService.ImportResult();
        List<City> cities = new ArrayList<>();
        try {
            cities = cityService.getAllCities();
        } catch (SQLException e) {
            logger.warning("Eroare la citirea oraselor pentru prognoza orara: " + e.getMessage());
            return total;
        }

        for (City city : cities) {
            try {
                WeatherImporterService.ImportResult r = importer.importHourlyForecastForCity(
                    city.getId(), city.getLatitude(), city.getLongitude(), days);
                total.imported += r.imported;
                total.errors += r.errors;
            } catch (Exception e) {
                logger.warning("Eroare import orar pentru orasul " + city.getName() + ": " + e.getMessage());
                total.errors++;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return total;
    }

    /**
     * Importa prognoza pentru un singur oras.
     *
     * @param cityId identificatorul orasului
     * @param days   numarul de zile
     * @return rezultatul importului
     */
    public WeatherImporterService.ImportResult populateForecastForCity(int cityId, int days) {
        City city = getCityById(cityId);
        if (city == null) {
            logger.warning("Orasul cu ID " + cityId + " nu a fost gasit");
            return new WeatherImporterService.ImportResult();
        }
        logger.info("Se importa prognoza pentru " + city.getName());
        return importer.importForecastForCity(cityId, city.getLatitude(), city.getLongitude(), days);
    }

    /**
     * Populare completa: istoric + prognoza pentru toate orasele.
     * Acesta este fluxul principal de initializare a datelor.
     *
     * @param historicalYears ani de istoric
     * @param forecastDays    zile de prognoza
     * @return rezumatul operatiunilor
     */
    public PopulationSummary populateAll(int historicalYears, int forecastDays) {
        PopulationSummary summary = new PopulationSummary();

        logger.info("=== INCEP POPULAREA BAZEI DE DATE ===");

        // 1. Date istorice
        WeatherImporterService.ImportResult hist = populateHistoricalData(historicalYears);
        summary.historicalImported = hist.imported;
        summary.historicalErrors = hist.errors;
        logger.info("Istoric importat: " + hist.imported + " zile, " + hist.errors + " erori");

        // 2. Prognoza actuala (zilnica)
        WeatherImporterService.ImportResult fore = populateForecastData(forecastDays);
        summary.forecastImported = fore.imported;
        summary.forecastErrors = fore.errors;
        logger.info("Prognoza importata: " + fore.imported + " zile, " + fore.errors + " erori");

        // 2b. Prognoza orara pentru toate orasele
        try {
            WeatherImporterService.ImportResult hourly = populateHourlyForecastData(forecastDays);
            logger.info("Prognoza orara importata: " + hourly.imported + " ore, " + hourly.errors + " erori");
        } catch (Exception e) {
            logger.warning("Eroare la importul prognozei orare: " + e.getMessage());
        }

        // 3. Genereaza avertizari automate
        try {
            generateWarnings();
            logger.info("Avertizari generate cu succes");
        } catch (SQLException e) {
            logger.warning("Eroare la generarea avertizarilor: " + e.getMessage());
        }

        // 4. Insereaza date demo (voturi, comentarii, log) pentru a satisface cerinta de 15+ inregistrari per tabela
        try {
            seedDemoData();
            logger.info("Date demo inserate cu succes");
        } catch (SQLException e) {
            logger.warning("Eroare la inserarea datelor demo: " + e.getMessage());
        }

        // 5. Ruleaza pipeline-ul ML pentru a popula tabelele de predictie
        try {
            runMlPipeline();
            logger.info("Pipeline ML completat cu succes");
        } catch (Exception e) {
            logger.warning("Eroare la rularea pipeline-ului ML: " + e.getMessage());
        }

        logger.info("=== POPULARE COMPLETA ===");
        return summary;
    }

    /**
     * Insereaza date demo (voturi, comentarii, forecast_log) dupa importul prognozelor.
     * Necesar pentru a satisface cerinta profesorului de minim 15 inregistrari per tabela.
     */
    public void seedDemoData() throws SQLException {
        String seedVotes =
            "DO $$ " +
            "DECLARE v_user_id INTEGER; v_forecast_id INTEGER; v_counter INTEGER := 0; v_fc INTEGER; " +
            "BEGIN " +
            "  SELECT COUNT(*) INTO v_fc FROM forecasts; " +
            "  IF v_fc = 0 THEN RETURN; END IF; " +
            "  FOR v_user_id IN SELECT id FROM users ORDER BY id LIMIT 15 LOOP " +
            "    SELECT id INTO v_forecast_id FROM forecasts ORDER BY id LIMIT 1 OFFSET (v_counter % v_fc); " +
            "    INSERT INTO votes (user_id, forecast_id, is_accurate, created_at) " +
            "    VALUES (v_user_id, v_forecast_id, (v_user_id % 3 <> 0), CURRENT_TIMESTAMP - INTERVAL '1 day' * (v_user_id % 30)) " +
            "    ON CONFLICT (user_id, forecast_id) DO NOTHING; " +
            "    v_counter := v_counter + 1; " +
            "  END LOOP; " +
            "END $$;";

        String seedComments =
            "DO $$ " +
            "DECLARE v_forecast_id INTEGER; v_user_id INTEGER; " +
            "  v_contents TEXT[] := ARRAY[ " +
            "    'Prognoza a fost foarte precisa astazi!', " +
            "    'A plouat exact cum s-a prezis. Bravo!', " +
            "    'Temperatura a fost usor subestimata.', " +
            "    'Vantul a fost mai puternic decat in prognoza.', " +
            "    'Excelenta acuratete pentru weekend.', " +
            "    'Prognoza s-a potrivit perfect cu realitatea.', " +
            "    'Avertizarea de furtuna a fost utila.', " +
            "    'Umiditatea a fost mai ridicata decat estimat.', " +
            "    'Soare toata ziua, exact cum s-a spus.', " +
            "    'Ninsoarea a venit cu o ora mai devreme.', " +
            "    'Indicele UV a fost corect estimat.', " +
            "    'Cer variabil, prognoza a fost in linii mari corecta.', " +
            "    'Ploaie usoara conform prognozei.', " +
            "    'Temperaturile maxime au fost respectate.', " +
            "    'O prognoza foarte buna pentru aceasta perioada.' " +
            "  ]; v_idx INTEGER := 1; " +
            "BEGIN " +
            "  SELECT id INTO v_forecast_id FROM forecasts WHERE city_id = 1 ORDER BY date DESC LIMIT 1; " +
            "  IF v_forecast_id IS NULL THEN RETURN; END IF; " +
            "  FOR v_user_id IN SELECT id FROM users ORDER BY id LIMIT 15 LOOP " +
            "    INSERT INTO comments (user_id, forecast_id, comment_text, created_at) " +
            "    VALUES (v_user_id, v_forecast_id, v_contents[v_idx], CURRENT_TIMESTAMP - INTERVAL '1 day' * (v_idx % 30)) " +
            "    ON CONFLICT DO NOTHING; " +
            "    v_idx := v_idx + 1; " +
            "  END LOOP; " +
            "END $$;";

        String seedForecastLog =
            "DO $$ " +
            "DECLARE v_forecast_id INTEGER; v_counter INTEGER := 0; v_old JSONB; v_new JSONB; " +
            "BEGIN " +
            "  FOR v_forecast_id IN SELECT id FROM forecasts WHERE city_id = 1 ORDER BY date DESC LIMIT 15 LOOP " +
            "    SELECT jsonb_build_object('temp_min', temp_min, 'temp_max', temp_max, 'icon_type', icon_type), " +
            "           jsonb_build_object('temp_min', temp_min - 1.5, 'temp_max', temp_max + 1.0, 'icon_type', 'cloudy') " +
            "    INTO v_old, v_new FROM forecasts WHERE id = v_forecast_id; " +
            "    INSERT INTO forecast_log (forecast_id, change_type, old_values, new_values, changed_at) " +
            "    VALUES (v_forecast_id, 'update', v_old, v_new, CURRENT_TIMESTAMP - INTERVAL '2 hours' * v_counter) " +
            "    ON CONFLICT DO NOTHING; " +
            "    v_counter := v_counter + 1; " +
            "  END LOOP; " +
            "END $$;";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(seedVotes);
            stmt.execute(seedComments);
            stmt.execute(seedForecastLog);
        }
    }

    /**
     * Ruleaza pipeline-ul ML complet pentru toate orasele.
     * Populeaza tabelele: weather_vectors, daily_regimes, weather_regimes,
     * markov_transitions, hidden_states, hidden_transitions, seasonal_climatology,
     * monte_carlo_predictions, prediction_accuracy, regime_accuracy,
     * markov_weight_adjustments, reinforcement_log.
     */
    public void runMlPipeline() throws Exception {
        List<City> cities = cityService.getAllCities();
        if (cities.isEmpty()) {
            logger.warning("Nu exista orase pentru pipeline ML");
            return;
        }

        WeatherVectorService vectorService = new WeatherVectorService();
        RecipeDetectorService recipeService = new RecipeDetectorService();
        ClusteringService clusteringService = new ClusteringService();
        MarkovModelService markovService = new MarkovModelService();
        HmmTrainingService hmmService = new HmmTrainingService();
        PredictionEngineService predictionEngine = new PredictionEngineService();
        MonteCarloEngine monteCarlo = new MonteCarloEngine();

        logger.info("=== PORNESC PIPELINE ML ===");

        // 1. Vectori + recipe pentru fiecare oras
        for (City city : cities) {
            try {
                vectorService.buildWeatherVector(city.getId());
                recipeService.computeRecipeScores(city.getId());
            } catch (Exception e) {
                logger.warning("Eroare vectori/recipe pentru orasul " + city.getId() + ": " + e.getMessage());
            }
        }
        logger.info("Vectori meteo construiti");

        // 2. Clustering global
        try {
            clusteringService.runKmeans(16);
            clusteringService.labelRegimes();
        } catch (Exception e) {
            logger.warning("Eroare clustering: " + e.getMessage());
        }
        logger.info("Clustering complet");

        // 3. Markov
        String climateZone = com.sgbd.util.ClimateZoneUtil.EUROPE_WIDE;
        try {
            markovService.buildTransitionTensor(climateZone);
            markovService.addStructuralZeros();
        } catch (Exception e) {
            logger.warning("Eroare Markov: " + e.getMessage());
        }
        logger.info("Tensor Markov construit");

        // 4. HMM + climatologie + Monte Carlo pentru fiecare oras
        LocalDate today = LocalDate.now();
        for (City city : cities) {
            try {
                hmmService.trainHmm(city.getId(), 8);
                predictionEngine.computeSeasonalClimatology(city.getId());
                monteCarlo.runSimulation(city.getId(), today, 10, 100);
            } catch (Exception e) {
                logger.warning("Eroare HMM/MC pentru orasul " + city.getId() + ": " + e.getMessage());
            }
        }
        logger.info("HMM, climatologie si Monte Carlo completate");

        // 5. Seed demo pentru tabelele de acuratete si RL
        seedMlDemoData();

        logger.info("=== PIPELINE ML COMPLET ===");
    }

    /**
     * Insereaza date demo in tabelele ML de acuratete si reinforcement.
     */
    private void seedMlDemoData() throws SQLException {
        String seedPredictionAccuracy =
            "DO $$ " +
            "DECLARE v_city_id INTEGER; v_date DATE; v_counter INTEGER := 0; " +
            "BEGIN " +
            "  FOR v_city_id IN SELECT id FROM cities ORDER BY id LIMIT 5 LOOP " +
            "    FOR v_date IN SELECT date FROM forecasts WHERE city_id = v_city_id ORDER BY date DESC LIMIT 3 LOOP " +
            "      INSERT INTO prediction_accuracy (city_id, forecast_date, horizon_day, predicted_temp_min, predicted_temp_max, actual_temp_min, actual_temp_max, predicted_wind_speed, actual_wind_speed, predicted_humidity, actual_humidity, mae_temp, rmse_temp, bias_temp, hit_event, hit_correct, computed_at) " +
            "      VALUES (v_city_id, v_date, 1, v_counter % 20 + 10, v_counter % 25 + 15, v_counter % 20 + 11, v_counter % 25 + 14, 10 + (v_counter % 10), 11 + (v_counter % 8), 50 + (v_counter % 30), 52 + (v_counter % 25), 0.5 + (v_counter % 3), 0.7 + (v_counter % 2), 0.2, 'temp', (v_counter % 2 = 0), CURRENT_TIMESTAMP - INTERVAL '1 day' * v_counter) " +
            "      ON CONFLICT (city_id, forecast_date, horizon_day) DO NOTHING; " +
            "      v_counter := v_counter + 1; " +
            "    END LOOP; " +
            "  END LOOP; " +
            "END $$;";

        String seedRegimeAccuracy =
            "DO $$ " +
            "DECLARE v_regime INTEGER; " +
            "BEGIN " +
            "  FOR v_regime IN 0..15 LOOP " +
            "    INSERT INTO regime_accuracy (climate_zone, regime_id, correct_predictions, total_predictions, accuracy_rate, last_updated) " +
            "    VALUES ('romania', v_regime, 10 + v_regime, 15 + v_regime, (10.0 + v_regime) / NULLIF(15 + v_regime, 0), CURRENT_TIMESTAMP) " +
            "    ON CONFLICT (climate_zone, regime_id) DO NOTHING; " +
            "  END LOOP; " +
            "END $$;";

        String seedMarkovAdjustments =
            "DO $$ " +
            "DECLARE v_i INTEGER; v_seasons TEXT[] := ARRAY['iarna', 'primavara', 'vara', 'toamna']; " +
            "BEGIN " +
            "  FOR v_i IN 1..15 LOOP " +
            "    INSERT INTO markov_weight_adjustments (climate_zone, season, r_prev, r_curr, r_next, adjustment_delta, reason, applied_at) " +
            "    VALUES ('romania', v_seasons[1 + (v_i % 4)], v_i % 16, (v_i + 1) % 16, (v_i + 2) % 16, 0.01 * (v_i % 5 - 2), 'Ajustare demo', CURRENT_TIMESTAMP - INTERVAL '1 hour' * v_i) " +
            "    ON CONFLICT DO NOTHING; " +
            "  END LOOP; " +
            "END $$;";

        String seedReinforcementLog =
            "DO $$ " +
            "DECLARE v_i INTEGER; v_city_id INTEGER; " +
            "BEGIN " +
            "  SELECT id INTO v_city_id FROM cities ORDER BY id LIMIT 1; " +
            "  IF v_city_id IS NULL THEN RETURN; END IF; " +
            "  FOR v_i IN 1..15 LOOP " +
            "    INSERT INTO reinforcement_log (iteration, parameter_type, parameter_key, old_value, new_value, accuracy_before, accuracy_after, city_id, created_at) " +
            "    VALUES (v_i, 'markov_weight', 'romania|iarna|0|0|1', 0.1, 0.12, 0.7, 0.75, v_city_id, CURRENT_TIMESTAMP - INTERVAL '30 minutes' * v_i) " +
            "    ON CONFLICT DO NOTHING; " +
            "  END LOOP; " +
            "END $$;";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(seedPredictionAccuracy);
            stmt.execute(seedRegimeAccuracy);
            stmt.execute(seedMarkovAdjustments);
            stmt.execute(seedReinforcementLog);
        }
    }

    /**
     * Genereaza avertizari meteo automat pentru toate prognozele.
     */
    public void generateWarnings() throws SQLException {
        String sql = "CALL sp_update_all_warnings(EXTRACT(YEAR FROM CURRENT_DATE)::INT)";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Returneaza numarul total de prognoze din baza de date.
     */
    public int getTotalForecastCount() {
        String sql = "SELECT COUNT(*) FROM forecasts";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            logger.warning("Eroare la numarare: " + e.getMessage());
            return 0;
        }
    }

    private City getCityById(int cityId) {
        try {
            List<City> cities = cityService.getAllCities();
            for (City c : cities) {
                if (c.getId() == cityId) return c;
            }
        } catch (SQLException e) {
            logger.warning("Eroare la citirea oraselor: " + e.getMessage());
        }
        return null;
    }

    /**
     * Rezumat al popularii bazei de date.
     */
    public static class PopulationSummary {
        public int historicalImported;
        public int historicalErrors;
        public int forecastImported;
        public int forecastErrors;

        @Override
        public String toString() {
            return String.format(
                "Populare DB: Istoric=%d zile (erori=%d), Prognoza=%d zile (erori=%d)",
                historicalImported, historicalErrors, forecastImported, forecastErrors);
        }
    }
}
