package com.sgbd.service;

import com.sgbd.model.City;
import com.sgbd.model.Country;
import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CityService {

    public List<Country> getAllCountries() throws SQLException {
        List<Country> list = new ArrayList<>();
        String sql = "SELECT id, name, code FROM countries ORDER BY name";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Country(rs.getInt("id"), rs.getString("name"), rs.getString("code")));
            }
        }
        return list;
    }

    public List<City> getCitiesByCountry(int countryId) throws SQLException {
        List<City> list = new ArrayList<>();
        String sql = "SELECT c.id, c.name, c.country_id, co.name AS country_name, " +
                     "c.latitude, c.longitude, c.is_important " +
                     "FROM cities c JOIN countries co ON c.country_id = co.id " +
                     "WHERE c.country_id = ? ORDER BY c.is_important DESC, c.name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, countryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapCity(rs));
                }
            }
        }
        return list;
    }

    public List<City> getAllCities() throws SQLException {
        List<City> list = new ArrayList<>();
        String sql = "SELECT c.id, c.name, c.country_id, co.name AS country_name, " +
                     "c.latitude, c.longitude, c.is_important " +
                     "FROM cities c JOIN countries co ON c.country_id = co.id " +
                     "ORDER BY co.name, c.is_important DESC, c.name";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapCity(rs));
            }
        }
        return list;
    }

    City mapCity(ResultSet rs) throws SQLException {
        City c = new City();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setCountryId(rs.getInt("country_id"));
        c.setCountryName(rs.getString("country_name"));
        c.setLatitude(rs.getDouble("latitude"));
        c.setLongitude(rs.getDouble("longitude"));
        c.setImportant(rs.getBoolean("is_important"));
        return c;
    }
}
