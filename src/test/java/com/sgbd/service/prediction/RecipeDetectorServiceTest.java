package com.sgbd.service.prediction;

import com.sgbd.util.DatabaseConnection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.*;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecipeDetectorServiceTest {

    @Test
    void testGetRecipeScoresReturnsValues() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true);
            when(rs.getDouble("fog_score")).thenReturn(0.25);
            when(rs.getDouble("thunderstorm_score")).thenReturn(0.75);
            when(rs.getDouble("cyclone_score")).thenReturn(0.10);
            when(rs.getDouble("anticyclone_score")).thenReturn(0.90);
            when(rs.getDouble("heatwave_score")).thenReturn(0.50);
            when(rs.getDouble("inversion_score")).thenReturn(0.30);

            RecipeDetectorService service = new RecipeDetectorService();
            RecipeDetectorService.RecipeScores scores = service.getRecipeScores(1, LocalDate.of(2024, 6, 15));

            assertNotNull(scores);
            assertEquals(0.25, scores.getFogScore(), 0.001);
            assertEquals(0.75, scores.getThunderstormScore(), 0.001);
            assertEquals(0.10, scores.getCycloneScore(), 0.001);
            assertEquals(0.90, scores.getAnticycloneScore(), 0.001);
            assertEquals(0.50, scores.getHeatwaveScore(), 0.001);
            assertEquals(0.30, scores.getInversionScore(), 0.001);
        }
    }

    @Test
    void testGetRecipeScoresReturnsNullWhenEmpty() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            RecipeDetectorService service = new RecipeDetectorService();
            RecipeDetectorService.RecipeScores scores = service.getRecipeScores(1, LocalDate.of(2024, 6, 15));

            assertNull(scores);
        }
    }

    @Test
    void testGetRecipeScoresZeroValues() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true);
            when(rs.getDouble(anyString())).thenReturn(0.0);

            RecipeDetectorService service = new RecipeDetectorService();
            RecipeDetectorService.RecipeScores scores = service.getRecipeScores(2, LocalDate.now());

            assertNotNull(scores);
            assertEquals(0.0, scores.getFogScore(), 0.001);
            assertEquals(0.0, scores.getThunderstormScore(), 0.001);
            assertEquals(0.0, scores.getCycloneScore(), 0.001);
        }
    }

    @Test
    void testComputeRecipeScores() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);

            RecipeDetectorService service = new RecipeDetectorService();
            service.computeRecipeScores(3);

            verify(stmt).setInt(1, 3);
            verify(stmt).execute();
        }
    }

    @Test
    void testComputeScoresForAllCities() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        PreparedStatement pstmt = mock(PreparedStatement.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);
            when(stmt.executeQuery(anyString())).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            when(rs.getInt("id")).thenReturn(7);
            when(conn.prepareStatement(anyString())).thenReturn(pstmt);

            RecipeDetectorService service = new RecipeDetectorService();
            service.computeScoresForAllCities();

            verify(pstmt, times(1)).execute();
        }
    }

    @Test
    void testRecipeScoresSettersAndGetters() {
        RecipeDetectorService.RecipeScores scores = new RecipeDetectorService.RecipeScores();
        scores.setFogScore(0.1);
        scores.setThunderstormScore(0.2);
        scores.setCycloneScore(0.3);
        scores.setAnticycloneScore(0.4);
        scores.setHeatwaveScore(0.5);
        scores.setInversionScore(0.6);

        assertEquals(0.1, scores.getFogScore(), 0.001);
        assertEquals(0.2, scores.getThunderstormScore(), 0.001);
        assertEquals(0.3, scores.getCycloneScore(), 0.001);
        assertEquals(0.4, scores.getAnticycloneScore(), 0.001);
        assertEquals(0.5, scores.getHeatwaveScore(), 0.001);
        assertEquals(0.6, scores.getInversionScore(), 0.001);
    }
}
