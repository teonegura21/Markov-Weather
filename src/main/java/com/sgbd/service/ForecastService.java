package com.sgbd.service;

import com.sgbd.model.*;
import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ForecastService {
    private final WeatherImporterService importerService = new WeatherImporterService();

    public WeatherImporterService.ImportResult importHistorical(int cityId, double lat, double lon,
                                                                  LocalDate start, LocalDate end) {
        return importerService.importHistoricalForCity(cityId, lat, lon, start, end);
    }

    public WeatherImporterService.ImportResult importForecast(int cityId, double lat, double lon, int days) {
        return importerService.importForecastForCity(cityId, lat, lon, days);
    }

    public WeatherImporterService.ImportResult importHistoricalAll(LocalDate start, LocalDate end) {
        return importerService.importHistoricalForAllCities(start, end);
    }

    public WeatherImporterService.ImportResult importForecastAll(int days) {
        return importerService.importForecastForAllCities(days);
    }

    public WeatherImporterService.FreshnessStatus checkDataFreshness() {
        return importerService.checkFreshness();
    }

    public void autoRefreshIfStale() {
        importerService.refreshStaleForecasts();
    }

    public void callAutoWarnUsers() throws SQLException {
        String sql = "CALL sp_auto_warn_users()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    public void cleanupOldForecasts() {
        importerService.cleanupOldForecasts();
    }

    public LocalDateTime getLastForecastFetchTime() throws SQLException {
        String sql = "SELECT MAX(fetched_at) FROM forecasts WHERE data_source = 'forecast_api'";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp(1);
                return ts != null ? ts.toLocalDateTime() : null;
            }
        }
        return null;
    }

    public boolean hasHistoricalData() throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM forecasts WHERE data_source = 'historical_api')";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    public boolean hasForecastData() throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM forecasts WHERE data_source = 'forecast_api')";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    public Forecast getDailyReport(int cityId, LocalDate date) throws SQLException {
        String sql = "SELECT * FROM sp_daily_forecast_report(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Forecast f = new Forecast();
                    f.setCityName(rs.getString("oras"));
                    f.setCountryName(rs.getString("tara"));
                    f.setDate(rs.getDate("data").toLocalDate());
                    f.setTempMin(rs.getDouble("temp_min"));
                    f.setTempMax(rs.getDouble("temp_max"));
                    f.setWindSpeed(rs.getDouble("viteza_vant"));
                    f.setIconType(rs.getString("pictograma"));
                    f.setUvIndex(rs.getInt("indice_uv"));
                    f.setHumidity(rs.getInt("umiditate"));
                    f.setWarningText(rs.getString("avertizare"));
                    f.setVoteCount(rs.getLong("nr_voturi"));
                    f.setAccurateVotes(rs.getLong("nr_acurat"));
                    f.setAccuracyPercent(rs.getDouble("acuratete_procent"));
                    f.setCommentCount(rs.getLong("nr_comentarii"));
                    f.setId(getForecastId(cityId, date));
                    return f;
                }
            }
        }
        return null;
    }

    private int getForecastId(int cityId, LocalDate date) throws SQLException {
        String sql = "SELECT id FROM forecasts WHERE city_id = ? AND date = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        return 0;
    }

    public List<Forecast> getForecastsByCity(int cityId, LocalDate from, LocalDate to) throws SQLException {
        List<Forecast> list = new ArrayList<>();
        String sql = "SELECT f.id, f.city_id, ci.name AS city_name, co.name AS country_name, " +
                     "f.date, f.temp_min, f.temp_max, f.wind_speed, f.icon_type, f.uv_index, " +
                     "f.humidity, f.warning_text FROM forecasts f " +
                     "JOIN cities ci ON f.city_id = ci.id " +
                     "JOIN countries co ON ci.country_id = co.id " +
                     "WHERE f.city_id = ? AND f.date BETWEEN ? AND ? " +
                     "ORDER BY f.date";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(from));
            stmt.setDate(3, Date.valueOf(to));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapForecast(rs));
                }
            }
        }
        return list;
    }

    public List<ComparisonResult> compareSameDay(int cityId, LocalDate date) throws SQLException {
        List<ComparisonResult> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_comparison_same_day(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ComparisonResult cr = new ComparisonResult();
                    cr.setTipComparatie(rs.getString("tip_comparatie"));
                    cr.setTempMinActuala(rs.getDouble("temp_min_actuala"));
                    cr.setTempMaxActuala(rs.getDouble("temp_max_actuala"));
                    cr.setTempMinMedie(rs.getDouble("temp_min_medie"));
                    cr.setTempMaxMedie(rs.getDouble("temp_max_medie"));
                    cr.setTempAvgActuala(rs.getDouble("temp_avg_actuala"));
                    cr.setTempAvgMedie(rs.getDouble("temp_avg_medie"));
                    cr.setDiferentaTempMin(rs.getDouble("diferenta_temp_min"));
                    cr.setDiferentaTempMax(rs.getDouble("diferenta_temp_max"));
                    list.add(cr);
                }
            }
        }
        return list;
    }

    public List<ComparisonResult> compareMonthly(int cityId, int year, int month) throws SQLException {
        List<ComparisonResult> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_compare_monthly(?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setInt(2, year);
            stmt.setInt(3, month);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ComparisonResult cr = new ComparisonResult();
                    cr.setTipComparatie("lunar");
                    cr.setTempMinActuala(rs.getDouble("temp_min_actuala"));
                    cr.setTempMaxActuala(rs.getDouble("temp_max_actuala"));
                    cr.setTempMinMedie(rs.getDouble("temp_min_medie_istorica"));
                    cr.setTempMaxMedie(rs.getDouble("temp_max_medie_istorica"));
                    list.add(cr);
                }
            }
        }
        return list;
    }

    public List<ComparisonResult> compareAnnual(int cityId, int year) throws SQLException {
        List<ComparisonResult> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_compare_annual(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setInt(2, year);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ComparisonResult cr = new ComparisonResult();
                    cr.setTipComparatie(rs.getString("tip"));
                    cr.setMedieAnSelectatTempMin(rs.getDouble("medie_an_selectat_temp_min"));
                    cr.setMedieAnSelectatTempMax(rs.getDouble("medie_an_selectat_temp_max"));
                    cr.setMedieAnSelectatTempAvg(rs.getDouble("medie_an_selectat_temp_avg"));
                    cr.setMedieIstoricaTempMin(rs.getDouble("medie_istorica_temp_min"));
                    cr.setMedieIstoricaTempMax(rs.getDouble("medie_istorica_temp_max"));
                    cr.setMedieIstoricaTempAvg(rs.getDouble("medie_istorica_temp_avg"));
                    list.add(cr);
                }
            }
        }
        return list;
    }

    public List<Forecast> predictWeek(int cityId, LocalDate startDate, int days) throws SQLException {
        List<Forecast> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_predict_week(?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(startDate));
            stmt.setInt(3, days);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Forecast f = new Forecast();
                    f.setDate(rs.getDate("zi").toLocalDate());
                    f.setTempMin(rs.getDouble("temp_min_prezis"));
                    f.setTempMax(rs.getDouble("temp_max_prezis"));
                    f.setWindSpeed(rs.getDouble("viteza_vant_prezisa"));
                    f.setHumidity(rs.getInt("umiditate_prezisa"));
                    f.setUvIndex(rs.getInt("indice_uv_prezis"));
                    f.setIconType(rs.getString("pictograma_prezisa"));
                    list.add(f);
                }
            }
        } catch (SQLException e) {
            throw translateException(e);
        }
        return list;
    }

    /**
     * Traduce excepțiile SQL în tipuri distincte pe baza SQLState.
     * Profesorul cere ca aplicația client să prindă cel puțin 2 excepții
     * PL/SQL distincte (nu doar SQLException generic).
     */
    private SQLException translateException(SQLException e) {
        String state = e.getSQLState();
        if ("P0001".equals(state)) {
            // RAISE EXCEPTION din PL/pgSQL (ex: validare eșuată în sp_predict_week)
            return new SQLException("Eroare validare: " + e.getMessage(), "PLSQL_RAISE", e);
        }
        if ("23505".equals(state)) {
            // unique_violation — ex: duplicat în forecasts (city_id, date)
            return new SQLException("Înregistrare duplicată: această combinație oraș-dată există deja.", "UNIQUE_VIOLATION", e);
        }
        if ("23503".equals(state)) {
            // foreign_key_violation — ex: city_id inexistent
            return new SQLException("Referință invalidă: orașul specificat nu există în baza de date.", "FK_VIOLATION", e);
        }
        return e;
    }

    public List<Forecast> getCityWeatherEvolution(int cityId, LocalDate from, LocalDate to) throws SQLException {
        List<Forecast> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_city_weather_evolution(?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(from));
            stmt.setDate(3, Date.valueOf(to));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Forecast f = new Forecast();
                    f.setDate(rs.getDate("data").toLocalDate());
                    f.setTempMin(rs.getDouble("temp_min"));
                    f.setTempMax(rs.getDouble("temp_max"));
                    f.setWindSpeed(rs.getDouble("viteza_vant"));
                    f.setHumidity(rs.getInt("umiditate"));
                    f.setUvIndex(rs.getInt("indice_uv"));
                    f.setIconType(rs.getString("pictograma"));
                    list.add(f);
                }
            }
        }
        return list;
    }

    public List<HourlyForecast> getHourlyForecasts(int cityId, LocalDate date) throws SQLException {
        List<HourlyForecast> list = new ArrayList<>();
        String sql = "SELECT city_id, forecast_date, hour, temperature, humidity, wind_speed, " +
                     "precipitation_probability, weather_code, icon_type " +
                     "FROM hourly_forecasts WHERE city_id = ? AND forecast_date = ? ORDER BY hour";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    HourlyForecast h = new HourlyForecast();
                    h.setCityId(rs.getInt("city_id"));
                    h.setForecastDate(rs.getDate("forecast_date").toLocalDate());
                    h.setHour(rs.getInt("hour"));
                    h.setTemperature(rs.getDouble("temperature"));
                    h.setHumidity(rs.getInt("humidity"));
                    h.setWindSpeed(rs.getDouble("wind_speed"));
                    h.setPrecipProbability(rs.getInt("precipitation_probability"));
                    h.setWeatherCode(rs.getInt("weather_code"));
                    h.setIconType(rs.getString("icon_type"));
                    list.add(h);
                }
            }
        }
        return list;
    }

    Forecast mapForecast(ResultSet rs) throws SQLException {
        Forecast f = new Forecast();
        try { f.setId(rs.getInt("id")); } catch (SQLException e) { }
        try { f.setCityId(rs.getInt("city_id")); } catch (SQLException e) { }
        try { f.setCityName(rs.getString("city_name")); } catch (SQLException e) { }
        try { f.setCountryName(rs.getString("country_name")); } catch (SQLException e) { }
        f.setDate(rs.getDate("date").toLocalDate());
        f.setTempMin(rs.getDouble("temp_min"));
        f.setTempMax(rs.getDouble("temp_max"));
        f.setWindSpeed(rs.getDouble("wind_speed"));
        f.setIconType(rs.getString("icon_type"));
        f.setUvIndex(rs.getInt("uv_index"));
        f.setHumidity(rs.getInt("humidity"));
        try { f.setWarningText(rs.getString("warning_text")); } catch (SQLException e) { }
        return f;
    }
}
