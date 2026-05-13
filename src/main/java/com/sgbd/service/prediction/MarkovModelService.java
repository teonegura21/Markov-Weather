package com.sgbd.service.prediction;

import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviciu pentru construirea si gestionarea modelului Markov de ordin 2.
 * Gestioneaza tensorul de tranzitie si zerourile structurale.
 */
public class MarkovModelService {

    /**
     * Construieste tensorul de tranzitie Markov apeland procedura stocata.
     *
     * @param climateZone zona climatica pentru care se construieste tensorul
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void buildTransitionTensor(String climateZone) throws SQLException {
        String sql = "SELECT sp_build_markov_tensor(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, climateZone);
            stmt.execute();
        }
    }

    /**
     * Identifica tranzitii fizic imposibile intre regimuri si le salveaza in structural_zeros.
     *
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void addStructuralZeros() throws SQLException {
        String selectSql = "SELECT regime_id, centroid, climate_zone FROM weather_regimes";
        String insertSql = "INSERT INTO structural_zeros (regime_from, regime_to, reason) VALUES (?, ?, ?) ON CONFLICT (regime_from, regime_to) DO NOTHING";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql);
             PreparedStatement insStmt = conn.prepareStatement(insertSql)) {

            class RegimeInfo {
                int id;
                double tempMax, heatScore, snowDepth, stormScore, fogScore, pressure, tempAvg;
                String zone;
                RegimeInfo(int id, double[] c, String zone) {
                    this.id = id;
                    this.zone = zone;
                    this.tempMax = c[1];
                    this.tempAvg = c[2];
                    this.heatScore = c[37];
                    this.snowDepth = c[21];
                    this.stormScore = c[34];
                    this.fogScore = c[33];
                    this.pressure = c[22];
                }
            }

            List<RegimeInfo> regimes = new ArrayList<>();
            while (rs.next()) {
                int rid = rs.getInt("regime_id");
                String zone = rs.getString("climate_zone");
                Array arr = rs.getArray("centroid");
                Double[] c = (Double[]) arr.getArray();
                double[] cd = new double[c.length];
                for (int i = 0; i < c.length; i++) cd[i] = c[i];
                regimes.add(new RegimeInfo(rid, cd, zone));
            }

            for (RegimeInfo from : regimes) {
                for (RegimeInfo to : regimes) {
                    if (from.id == to.id) continue;
                    String reason = null;

                    if ((from.heatScore > 0.5 || from.tempMax > 1.0) && to.snowDepth > 0.1) {
                        reason = "Tranziție imposibilă: caniculă → zăpadă într-o singură zi";
                    } else if (from.snowDepth > 0.1 && (to.heatScore > 0.5 || to.tempMax > 1.0)) {
                        reason = "Tranziție imposibilă: zăpadă → caniculă într-o singură zi";
                    } else if (Math.abs(to.pressure - from.pressure) > 3.0 && Math.abs(from.tempMax - to.tempMax) > 2.5) {
                        reason = "Schimbare bruscă de presiune și temperatură fizic improbabilă";
                    } else if (from.stormScore > 0.6 && to.fogScore > 0.6 && from.tempMax > to.tempMax + 1.0) {
                        reason = "Furtună → ceață densă cu răcire bruscă: zero structural";
                    }

                    if (reason != null) {
                        insStmt.setInt(1, from.id);
                        insStmt.setInt(2, to.id);
                        insStmt.setString(3, reason);
                        insStmt.addBatch();
                    }
                }
            }
            insStmt.executeBatch();
        }
    }

    /**
     * Returneaza probabilitatea unei tranzitii specifice din tensorul Markov.
     *
     * @param season sezonul
     * @param rPrev  regimul anterior
     * @param rCurr  regimul curent
     * @param rNext  regimul urmator
     * @return probabilitatea tranzitiei, sau 0.0 daca nu exista
     * @throws SQLException daca apare o eroare la baza de date
     */
    public double getTransitionProbability(String season, int rPrev, int rCurr, int rNext) throws SQLException {
        String sql = "SELECT probability FROM markov_transitions WHERE season = ? AND r_prev = ? AND r_curr = ? AND r_next = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, season);
            stmt.setInt(2, rPrev);
            stmt.setInt(3, rCurr);
            stmt.setInt(4, rNext);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("probability");
                }
            }
        }
        return 0.0;
    }

    /**
     * Returneaza distributia completa a probabilitatilor pentru urmatorul regim.
     *
     * @param season      sezonul
     * @param rPrev       regimul anterior
     * @param rCurr       regimul curent
     * @param numRegimes  numarul total de regimuri
     * @return vector de probabilitati indexat dupa regime_id
     * @throws SQLException daca apare o eroare la baza de date
     */
    public double[] getTransitionDistribution(String season, int rPrev, int rCurr, int numRegimes) throws SQLException {
        double[] probs = new double[numRegimes];
        String sql = "SELECT r_next, probability FROM markov_transitions WHERE season = ? AND r_prev = ? AND r_curr = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, season);
            stmt.setInt(2, rPrev);
            stmt.setInt(3, rCurr);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int next = rs.getInt("r_next");
                    double p = rs.getDouble("probability");
                    if (next >= 0 && next < numRegimes) {
                        probs[next] = p;
                    }
                }
            }
        }
        return probs;
    }
}
