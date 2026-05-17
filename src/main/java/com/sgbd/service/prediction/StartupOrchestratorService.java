package com.sgbd.service.prediction;

import com.sgbd.model.City;
import com.sgbd.service.CityService;
import com.sgbd.service.WeatherImporterService;
import com.sgbd.util.DatabaseConnection;
import com.sgbd.util.LoggerUtil;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/**
 * Orchestrator pentru mentenanța automatizată la pornirea aplicației.
 * Detectează zilele istorice lipsă, reîmprospătează prognoza,
 * reconstruiește pipeline-ul ML și rulează RL — totul în fundal.
 *
 * Scenariu: dacă utilizatorul nu a mai pornit aplicația de 3 zile,
 * la următorul startup se vor importa automat cele 3 zile lipsă,
 * se va regenera prognoza pentru următoarele 10 zile,
 * se vor recalcula vectorii, regimurile și predicțiile Monte Carlo,
 * iar motorul RL va ajusta ponderile pe baza noilor date.
 */
public class StartupOrchestratorService {

    private static final Logger logger = LoggerUtil.getLogger(StartupOrchestratorService.class);

    private final WeatherImporterService importer = new WeatherImporterService();
    private final CityService cityService = new CityService();
    private final WeatherVectorService vectorService = new WeatherVectorService();
    private final RecipeDetectorService recipeService = new RecipeDetectorService();
    private final ClusteringService clusteringService = new ClusteringService();
    private final MarkovModelService markovService = new MarkovModelService();
    private final HmmTrainingService hmmService = new HmmTrainingService();
    private final PredictionEngineService predictionEngine = new PredictionEngineService();
    private final MonteCarloEngine monteCarlo = new MonteCarloEngine();
    private final AccuracyService accuracyService = new AccuracyService();
    private final ReinforcementService rlService = new ReinforcementService();

    /**
     * Rezultatul operațiunii de mentenanță la startup.
     */
    public static class StartupResult {
        public int gapDaysImported;
        public int forecastDaysImported;
        public int citiesProcessed;
        public int citiesWithMlPipeline;
        public int predictionsGenerated;
        public int rlIterations;
        public boolean mlRebuildFull;
        public String message;

        @Override
        public String toString() {
            return String.format(
                "Startup: gap=%d zile, prognoza=%d zile, orase=%d, ML=%s, predictii=%d, RL=%d",
                gapDaysImported, forecastDaysImported, citiesProcessed,
                mlRebuildFull ? "rebuild complet" : "incremental",
                predictionsGenerated, rlIterations);
        }
    }

    /**
     * Rulează fluxul complet de mentenanță la pornire.
     *
     * @return rezumatul operațiunilor
     * @throws SQLException dacă apare o eroare gravă la baza de date
     */
    public StartupResult runStartupMaintenance() throws SQLException {
        StartupResult result = new StartupResult();
        logger.info("=== PORNESC MENTENANTA STARTUP ===");

        // VERIFICARE RAPIDA: daca datele sunt deja la zi, nu mai facem nimic
        if (isDataFresh()) {
            result.message = "Datele sunt deja la zi (prognoza și predicțiile există pentru azi). Sar peste mentenanță.";
            logger.info(result.message);
            return result;
        }

        // 1. Detectează și importă gap-ul istoric
        LocalDate lastHistorical = getLastHistoricalDate();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        if (lastHistorical != null && lastHistorical.isBefore(yesterday)) {
            LocalDate gapStart = lastHistorical.plusDays(1);
            logger.info("Gap istoric detectat: " + gapStart + " -> " + yesterday);
            WeatherImporterService.ImportResult gapResult = importer.importHistoricalForAllCities(gapStart, yesterday);
            result.gapDaysImported = gapResult.imported;
            logger.info("Gap importat: " + gapResult.imported + " zile, " + gapResult.errors + " erori");
        } else if (lastHistorical == null) {
            logger.info("Nu există date istorice. Se va face import complet la prima pornire.");
        } else {
            logger.info("Date istorice la zi (ultima: " + lastHistorical + ")");
        }

        // 2. Reîmprospătează prognoza (șterge vechiul forecast, importă nou)
        logger.info("Reîmprospătez prognoza...");
        importer.cleanupOldForecasts();
        WeatherImporterService.ImportResult forecastResult = importer.importForecastForAllCities(10);
        result.forecastDaysImported = forecastResult.imported;
        logger.info("Prognoza importată: " + forecastResult.imported + " zile, " + forecastResult.errors + " erori");

        // 3. Reconstruiește pipeline-ul ML
        List<City> cities = cityService.getAllCities();
        result.citiesProcessed = cities.size();
        boolean hasExistingMl = hasExistingMlPipeline();

        if (!hasExistingMl || result.gapDaysImported > 30) {
            // Rebuild complet dacă nu există ML sau dacă gap-ul e mare (>30 zile)
            result.mlRebuildFull = true;
            logger.info("Reconstrucție ML COMPLETA (motiv: " + (hasExistingMl ? "gap mare" : "date lipsa") + ")");
            rebuildMlPipelineFull(cities);
        } else {
            // Rebuild incremental — doar vectori și predicții
            result.mlRebuildFull = false;
            logger.info("Reconstrucție ML INCREMENTALA");
            rebuildMlPipelineIncremental(cities);
        }
        result.citiesWithMlPipeline = cities.size();

        // 4. Generează predicții Monte Carlo pentru următoarele 10 zile
        logger.info("Generez predicții Monte Carlo...");
        LocalDate today = LocalDate.now();
        int predCount = 0;
        for (City city : cities) {
            try {
                monteCarlo.runSimulation(city.getId(), today, 10, 5000);
                predCount += 10;
            } catch (Exception e) {
                logger.warning("Eroare Monte Carlo pentru orașul " + city.getId() + ": " + e.getMessage());
            }
        }
        result.predictionsGenerated = predCount;
        logger.info("Predicții generate: " + predCount + " zile-oras");

        // 5. Rulează backtest + RL pentru fiecare oraș
        logger.info("Rulez backtest și RL...");
        int rlCount = 0;
        for (City city : cities) {
            try {
                // Backtest pe ultimele 14 zile (doar dacă există date)
                accuracyService.runBacktest(city.getId(), 14, 1);
                // RL — o iterație per oraș
                rlService.runLearningIteration(city.getId());
                rlCount++;
            } catch (Exception e) {
                logger.warning("Eroare RL pentru orașul " + city.getId() + ": " + e.getMessage());
            }
        }
        result.rlIterations = rlCount;
        logger.info("RL complet: " + rlCount + " orașe");

        result.message = result.toString();
        logger.info("=== MENTENANTA STARTUP COMPLETA ===");
        return result;
    }

    /**
     * Verifică dacă datele sunt suficient de proaspete încât să putem sărim peste mentenanță.
     * Condiții: există prognoză forecast importată azi și există predicții Monte Carlo generate azi.
     */
    private boolean isDataFresh() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            // Verifică dacă există prognoză forecast cu fetched_at = azi
            try (ResultSet rs = stmt.executeQuery(
                "SELECT EXISTS(SELECT 1 FROM forecasts WHERE data_source = 'forecast_api' AND fetched_at::date = CURRENT_DATE LIMIT 1)")) {
                if (!rs.next() || !rs.getBoolean(1)) return false;
            }
            // Verifică dacă există predicții Monte Carlo generate azi
            try (ResultSet rs = stmt.executeQuery(
                "SELECT EXISTS(SELECT 1 FROM monte_carlo_predictions WHERE generated_at::date = CURRENT_DATE LIMIT 1)")) {
                if (!rs.next() || !rs.getBoolean(1)) return false;
            }
            return true;
        } catch (SQLException e) {
            logger.warning("Eroare la verificarea prospețimii datelor: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returnează ultima dată istorică disponibilă în baza de date.
     */
    private LocalDate getLastHistoricalDate() {
        String sql = "SELECT MAX(date) FROM forecasts WHERE data_source = 'historical_api'";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                Date d = rs.getDate(1);
                return d != null ? d.toLocalDate() : null;
            }
        } catch (SQLException e) {
            logger.warning("Eroare la citirea ultimei date istorice: " + e.getMessage());
        }
        return null;
    }

    /**
     * Verifică dacă există deja un pipeline ML construit (vectori, regimuri, Markov).
     */
    private boolean hasExistingMlPipeline() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            // Verifică weather_vectors
            try (ResultSet rs = stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM weather_vectors LIMIT 1)")) {
                if (!rs.next() || !rs.getBoolean(1)) return false;
            }
            // Verifică daily_regimes
            try (ResultSet rs = stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM daily_regimes LIMIT 1)")) {
                if (!rs.next() || !rs.getBoolean(1)) return false;
            }
            // Verifică markov_transitions
            try (ResultSet rs = stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM markov_transitions LIMIT 1)")) {
                if (!rs.next() || !rs.getBoolean(1)) return false;
            }
            return true;
        } catch (SQLException e) {
            logger.warning("Eroare la verificarea pipeline-ului ML: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reconstrucție completă a pipeline-ului ML pentru toate orașele.
     */
    private void rebuildMlPipelineFull(List<City> cities) throws SQLException {
        // Vectori + recipe pentru fiecare oraș
        for (City city : cities) {
            try {
                vectorService.buildWeatherVector(city.getId());
                recipeService.computeRecipeScores(city.getId());
            } catch (Exception e) {
                logger.warning("Eroare vectori/recipe pentru orașul " + city.getId() + ": " + e.getMessage());
            }
        }

        // Clustering global (o singură dată pentru toată zona climatică)
        try {
            clusteringService.runKmeans(16);
            clusteringService.labelRegimes();
        } catch (Exception e) {
            logger.warning("Eroare clustering: " + e.getMessage());
        }

        // Markov (per zonă climatică — întreaga Europă într-o singură zonă pentru clustering global)
        String climateZone = com.sgbd.util.ClimateZoneUtil.EUROPE_WIDE;
        try {
            markovService.buildTransitionTensor(climateZone);
            markovService.addStructuralZeros();
        } catch (Exception e) {
            logger.warning("Eroare Markov: " + e.getMessage());
        }

        // HMM + climatologie pentru fiecare oraș
        for (City city : cities) {
            try {
                hmmService.trainHmm(city.getId(), 8);
                predictionEngine.computeSeasonalClimatology(city.getId());
            } catch (Exception e) {
                logger.warning("Eroare HMM pentru orașul " + city.getId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Reconstrucție incrementală a pipeline-ului ML.
     * Doar recalculează vectorii pentru orașe și păstrează regimurile/Markov/HMM existente.
     */
    private void rebuildMlPipelineIncremental(List<City> cities) {
        for (City city : cities) {
            try {
                vectorService.buildWeatherVector(city.getId());
                recipeService.computeRecipeScores(city.getId());
            } catch (Exception e) {
                logger.warning("Eroare vectori incremental pentru orașul " + city.getId() + ": " + e.getMessage());
            }
        }
        // Rulăm k-means și labelRegimes pentru a include noile zile în daily_regimes
        try {
            clusteringService.runKmeans(16);
            clusteringService.labelRegimes();
        } catch (Exception e) {
            logger.warning("Eroare clustering incremental: " + e.getMessage());
        }
    }
}
