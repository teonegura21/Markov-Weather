package com.sgbd.service;

import com.sgbd.model.ComparisonResult;
import com.sgbd.model.Forecast;
import com.sgbd.util.DatabaseConnection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForecastServiceTest {

    @Test
    void testMapForecastComplete() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(42);
        when(rs.getInt("city_id")).thenReturn(5);
        when(rs.getString("city_name")).thenReturn("Cluj-Napoca");
        when(rs.getString("country_name")).thenReturn("România");
        when(rs.getDate("date")).thenReturn(Date.valueOf(LocalDate.of(2024, 6, 15)));
        when(rs.getDouble("temp_min")).thenReturn(15.5);
        when(rs.getDouble("temp_max")).thenReturn(28.3);
        when(rs.getDouble("wind_speed")).thenReturn(12.0);
        when(rs.getString("icon_type")).thenReturn("sunny");
        when(rs.getInt("uv_index")).thenReturn(7);
        when(rs.getInt("humidity")).thenReturn(45);
        when(rs.getString("warning_text")).thenReturn("AVERTIZARE CANICULA");

        ForecastService service = new ForecastService();
        Forecast f = service.mapForecast(rs);

        assertEquals(42, f.getId());
        assertEquals(5, f.getCityId());
        assertEquals("Cluj-Napoca", f.getCityName());
        assertEquals("România", f.getCountryName());
        assertEquals(LocalDate.of(2024, 6, 15), f.getDate());
        assertEquals(15.5, f.getTempMin(), 0.01);
        assertEquals(28.3, f.getTempMax(), 0.01);
        assertEquals(12.0, f.getWindSpeed(), 0.01);
        assertEquals("sunny", f.getIconType());
        assertEquals(7, f.getUvIndex());
        assertEquals(45, f.getHumidity());
        assertEquals("AVERTIZARE CANICULA", f.getWarningText());
    }

    @Test
    void testMapForecastIgnoresMissingColumns() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(1);
        when(rs.getInt("city_id")).thenReturn(1);
        when(rs.getString("city_name")).thenThrow(new SQLException("Column not found"));
        when(rs.getString("country_name")).thenThrow(new SQLException("Column not found"));
        when(rs.getDate("date")).thenReturn(Date.valueOf(LocalDate.now()));
        when(rs.getDouble("temp_min")).thenReturn(10.0);
        when(rs.getDouble("temp_max")).thenReturn(20.0);
        when(rs.getDouble("wind_speed")).thenReturn(5.0);
        when(rs.getString("icon_type")).thenReturn("cloudy");
        when(rs.getInt("uv_index")).thenReturn(3);
        when(rs.getInt("humidity")).thenReturn(60);
        when(rs.getString("warning_text")).thenThrow(new SQLException("Column not found"));

        ForecastService service = new ForecastService();
        Forecast f = service.mapForecast(rs);

        assertEquals(1, f.getId());
        assertEquals(1, f.getCityId());
        assertNull(f.getCityName());
        assertNull(f.getCountryName());
        assertNull(f.getWarningText());
        assertEquals("cloudy", f.getIconType());
        assertEquals(10.0, f.getTempMin(), 0.01);
    }

    @Test
    void testMapForecastMinimalColumns() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenThrow(new SQLException("Column not found"));
        when(rs.getInt("city_id")).thenThrow(new SQLException("Column not found"));
        when(rs.getString("city_name")).thenThrow(new SQLException("Column not found"));
        when(rs.getString("country_name")).thenThrow(new SQLException("Column not found"));
        when(rs.getDate("date")).thenReturn(Date.valueOf(LocalDate.of(2024, 1, 1)));
        when(rs.getDouble("temp_min")).thenReturn(-5.0);
        when(rs.getDouble("temp_max")).thenReturn(5.0);
        when(rs.getDouble("wind_speed")).thenReturn(20.0);
        when(rs.getString("icon_type")).thenReturn("snow");
        when(rs.getInt("uv_index")).thenReturn(1);
        when(rs.getInt("humidity")).thenReturn(80);
        when(rs.getString("warning_text")).thenThrow(new SQLException("Column not found"));

        ForecastService service = new ForecastService();
        Forecast f = service.mapForecast(rs);

        assertEquals(0, f.getId());
        assertEquals(0, f.getCityId());
        assertNull(f.getCityName());
        assertEquals(-5.0, f.getTempMin(), 0.01);
        assertEquals(5.0, f.getTempMax(), 0.01);
        assertEquals("snow", f.getIconType());
    }

    @Test
    void testCompareMonthly() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            when(rs.getDouble("temp_min_actuala")).thenReturn(15.0);
            when(rs.getDouble("temp_max_actuala")).thenReturn(28.0);
            when(rs.getDouble("temp_min_medie_istorica")).thenReturn(14.0);
            when(rs.getDouble("temp_max_medie_istorica")).thenReturn(26.0);

            ForecastService service = new ForecastService();
            List<ComparisonResult> results = service.compareMonthly(1, 2024, 6);

            assertEquals(1, results.size());
            assertEquals(15.0, results.get(0).getTempMinActuala(), 0.01);
        }
    }

    @Test
    void testCompareAnnual() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            when(rs.getString("tip")).thenReturn("anual");
            when(rs.getDouble("medie_an_selectat_temp_min")).thenReturn(5.0);
            when(rs.getDouble("medie_an_selectat_temp_max")).thenReturn(18.0);
            when(rs.getDouble("medie_istorica_temp_min")).thenReturn(4.5);
            when(rs.getDouble("medie_istorica_temp_max")).thenReturn(17.5);

            ForecastService service = new ForecastService();
            List<ComparisonResult> results = service.compareAnnual(1, 2024);

            assertEquals(1, results.size());
            assertEquals("anual", results.get(0).getTipComparatie());
        }
    }

    @Test
    void testPredictWeek() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            when(rs.getDate("zi")).thenReturn(Date.valueOf(LocalDate.of(2024, 6, 20)));
            when(rs.getDouble("temp_min_prezis")).thenReturn(15.0);
            when(rs.getDouble("temp_max_prezis")).thenReturn(25.0);
            when(rs.getDouble("viteza_vant_prezisa")).thenReturn(10.0);
            when(rs.getInt("umiditate_prezisa")).thenReturn(55);
            when(rs.getInt("indice_uv_prezis")).thenReturn(6);
            when(rs.getString("pictograma_prezisa")).thenReturn("sunny");

            ForecastService service = new ForecastService();
            List<Forecast> results = service.predictWeek(1, LocalDate.of(2024, 6, 20), 7);

            assertEquals(1, results.size());
            assertEquals("sunny", results.get(0).getIconType());
        }
    }
}
