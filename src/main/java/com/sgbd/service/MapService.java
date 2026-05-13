package com.sgbd.service;

import com.sgbd.model.City;
import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MapService {

    public static class MapData {
        private String tara;
        private String codTara;
        private String oras;
        private double latitudine;
        private double longitudine;
        private double tempMin;
        private double tempMax;
        private double vitezaVant;
        private int umiditate;
        private String pictograma;

        public String getTara() { return tara; }
        public void setTara(String tara) { this.tara = tara; }
        public String getCodTara() { return codTara; }
        public void setCodTara(String codTara) { this.codTara = codTara; }
        public String getOras() { return oras; }
        public void setOras(String oras) { this.oras = oras; }
        public double getLatitudine() { return latitudine; }
        public void setLatitudine(double latitudine) { this.latitudine = latitudine; }
        public double getLongitudine() { return longitudine; }
        public void setLongitudine(double longitudine) { this.longitudine = longitudine; }
        public double getTempMin() { return tempMin; }
        public void setTempMin(double tempMin) { this.tempMin = tempMin; }
        public double getTempMax() { return tempMax; }
        public void setTempMax(double tempMax) { this.tempMax = tempMax; }
        public double getVitezaVant() { return vitezaVant; }
        public void setVitezaVant(double vitezaVant) { this.vitezaVant = vitezaVant; }
        public int getUmiditate() { return umiditate; }
        public void setUmiditate(int umiditate) { this.umiditate = umiditate; }
        public String getPictograma() { return pictograma; }
        public void setPictograma(String pictograma) { this.pictograma = pictograma; }
    }

    public List<MapData> getMapData(Integer countryId, LocalDate date) throws SQLException {
        List<MapData> list = new ArrayList<>();
        String sql = "SELECT * FROM sp_get_map_data(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (countryId != null) stmt.setInt(1, countryId);
            else stmt.setNull(1, Types.INTEGER);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    MapData md = new MapData();
                    md.setTara(rs.getString("tara"));
                    md.setCodTara(rs.getString("cod_tara"));
                    md.setOras(rs.getString("oras"));
                    md.setLatitudine(rs.getDouble("latitudine"));
                    md.setLongitudine(rs.getDouble("longitudine"));
                    md.setTempMin(rs.getDouble("temp_min"));
                    md.setTempMax(rs.getDouble("temp_max"));
                    md.setVitezaVant(rs.getDouble("viteza_vant"));
                    md.setUmiditate(rs.getInt("umiditate"));
                    md.setPictograma(rs.getString("pictograma"));
                    list.add(md);
                }
            }
        }
        return list;
    }
}
