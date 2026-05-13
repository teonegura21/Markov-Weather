package com.sgbd.service;

import com.sgbd.model.City;
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

        // 2. Prognoza actuala
        WeatherImporterService.ImportResult fore = populateForecastData(forecastDays);
        summary.forecastImported = fore.imported;
        summary.forecastErrors = fore.errors;
        logger.info("Prognoza importata: " + fore.imported + " zile, " + fore.errors + " erori");

        // 3. Genereaza avertizari automate
        try {
            generateWarnings();
            logger.info("Avertizari generate cu succes");
        } catch (SQLException e) {
            logger.warning("Eroare la generarea avertizarilor: " + e.getMessage());
        }

        logger.info("=== POPULARE COMPLETA ===");
        return summary;
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
