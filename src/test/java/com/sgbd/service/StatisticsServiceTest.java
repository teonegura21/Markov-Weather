package com.sgbd.service;

import com.sgbd.model.Anomaly;
import com.sgbd.model.CityRanking;
import com.sgbd.util.DatabaseConnection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsServiceTest {

    @Test
    void testGetCityRankings() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, true, false);
            when(rs.getLong("pozitie")).thenReturn(1L, 2L);
            when(rs.getString("oras")).thenReturn("Bucuresti", "Cluj-Napoca");
            when(rs.getString("tara")).thenReturn("Romania", "Romania");
            when(rs.getDouble("valoare")).thenReturn(35.0, 28.0);
            when(rs.getString("unitate")).thenReturn("°C", "°C");

            StatisticsService service = new StatisticsService();
            List<CityRanking> rankings = service.getCityRankings("hottest", 5);

            assertEquals(2, rankings.size());
            assertEquals("Bucuresti", rankings.get(0).getOras());
            assertEquals(35.0, rankings.get(0).getValoare(), 0.01);
        }
    }

    @Test
    void testDetectAnomalies() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            when(rs.getString("oras")).thenReturn("Bucuresti");
            when(rs.getString("tara")).thenReturn("Romania");
            when(rs.getDate("data")).thenReturn(Date.valueOf(LocalDate.of(2024, 7, 15)));
            when(rs.getBoolean("anomalie_temperatura")).thenReturn(true);
            when(rs.getBoolean("anomalie_vant")).thenReturn(false);
            when(rs.getBoolean("anomalie_umiditate")).thenReturn(false);
            when(rs.getBoolean("anomalie_uv")).thenReturn(true);
            when(rs.getDouble("temp_min")).thenReturn(15.0);
            when(rs.getDouble("temp_max")).thenReturn(42.0);
            when(rs.getDouble("viteza_vant")).thenReturn(10.0);
            when(rs.getInt("umiditate")).thenReturn(50);
            when(rs.getInt("indice_uv")).thenReturn(9);
            when(rs.getString("detalii_anomalie")).thenReturn("Canicula extrema");

            StatisticsService service = new StatisticsService();
            List<Anomaly> anomalies = service.detectAnomalies(1, 2024);

            assertEquals(1, anomalies.size());
            assertTrue(anomalies.get(0).isAnomalieTemperatura());
            assertTrue(anomalies.get(0).isAnomalieUV());
            assertEquals("Canicula extrema", anomalies.get(0).getDetaliiAnomalie());
        }
    }

    @Test
    void testClassifySimilarCities() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            when(rs.getString("oras")).thenReturn("Ploiesti");
            when(rs.getString("tara")).thenReturn("Romania");
            when(rs.getDouble("distanta_euclidiana")).thenReturn(2.5);

            StatisticsService service = new StatisticsService();
            List<CityRanking> similar = service.classifySimilarCities(1, 30);

            assertEquals(1, similar.size());
            assertEquals("Ploiesti", similar.get(0).getOras());
            assertEquals(2.5, similar.get(0).getValoare(), 0.01);
        }
    }

    @Test
    void testGetForecastScore() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true);
            when(rs.getDouble(1)).thenReturn(3.75);

            StatisticsService service = new StatisticsService();
            double score = service.getForecastScore(42);

            assertEquals(3.75, score, 0.01);
        }
    }
}
