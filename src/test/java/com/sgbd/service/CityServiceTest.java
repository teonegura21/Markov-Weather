package com.sgbd.service;

import com.sgbd.model.City;
import com.sgbd.model.Country;
import com.sgbd.util.DatabaseConnection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CityServiceTest {

    @Test
    void testGetAllCountries() throws SQLException {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);
            when(stmt.executeQuery(anyString())).thenReturn(rs);
            when(rs.next()).thenReturn(true, true, false);
            when(rs.getInt("id")).thenReturn(1, 2);
            when(rs.getString("name")).thenReturn("România", "Franța");
            when(rs.getString("code")).thenReturn("RO", "FR");

            CityService service = new CityService();
            List<Country> countries = service.getAllCountries();

            assertEquals(2, countries.size());
            assertEquals("România", countries.get(0).getName());
            assertEquals("RO", countries.get(0).getCode());
            assertEquals("Franța", countries.get(1).getName());
            assertEquals("FR", countries.get(1).getCode());
        }
    }

    @Test
    void testGetCitiesByCountry() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        try (MockedStatic<DatabaseConnection> mocked = mockStatic(DatabaseConnection.class)) {
            mocked.when(DatabaseConnection::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            when(rs.getInt("id")).thenReturn(1);
            when(rs.getString("name")).thenReturn("București");
            when(rs.getInt("country_id")).thenReturn(1);
            when(rs.getString("country_name")).thenReturn("România");
            when(rs.getDouble("latitude")).thenReturn(44.43);
            when(rs.getDouble("longitude")).thenReturn(26.10);
            when(rs.getBoolean("is_important")).thenReturn(true);

            CityService service = new CityService();
            List<City> cities = service.getCitiesByCountry(1);

            assertEquals(1, cities.size());
            assertEquals("București", cities.get(0).getName());
            assertEquals(44.43, cities.get(0).getLatitude(), 0.01);
            assertEquals(26.10, cities.get(0).getLongitude(), 0.01);
            assertTrue(cities.get(0).isImportant());
        }
    }

    @Test
    void testMapCity() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(10);
        when(rs.getString("name")).thenReturn("Timișoara");
        when(rs.getInt("country_id")).thenReturn(1);
        when(rs.getString("country_name")).thenReturn("România");
        when(rs.getDouble("latitude")).thenReturn(45.75);
        when(rs.getDouble("longitude")).thenReturn(21.23);
        when(rs.getBoolean("is_important")).thenReturn(false);

        CityService service = new CityService();
        City city = service.mapCity(rs);

        assertEquals(10, city.getId());
        assertEquals("Timișoara", city.getName());
        assertEquals(1, city.getCountryId());
        assertEquals("România", city.getCountryName());
        assertEquals(45.75, city.getLatitude(), 0.01);
        assertEquals(21.23, city.getLongitude(), 0.01);
        assertFalse(city.isImportant());
    }
}
