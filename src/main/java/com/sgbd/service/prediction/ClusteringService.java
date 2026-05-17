package com.sgbd.service.prediction;

import com.sgbd.util.DatabaseConnection;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Serviciu pentru clustering k-means al vectorilor meteo.
 * Grupeaza zilele in regimuri meteorologice si calculeaza metrici de validare.
 */
public class ClusteringService {

    private static final int DIMENSIONS = 39;
    private static final int MAX_ITERATIONS = 300;
    private static final String[] VECTOR_COLUMNS = {
        "temp_min", "temp_max", "temp_avg", "temp_amplitude", "temp_trend",
        "humidity_min", "humidity_max", "humidity_avg", "dew_point_min", "dew_point_spread",
        "wind_speed_avg", "wind_speed_max", "gust_factor", "wind_persistence",
        "sunshine_hours", "sunshine_fraction", "uv_index_max", "cloud_cover_proxy",
        "precipitation_sum", "precip_intensity", "precipitation_hours", "snow_depth",
        "pressure_mean", "pressure_trend", "pressure_range",
        "delta1_temp_avg", "delta2_temp_avg", "delta1_humidity_avg", "delta2_humidity_avg",
        "delta1_pressure_mean", "delta2_pressure_mean", "delta1_wind_speed_avg", "delta2_wind_speed_avg",
        "fog_score", "thunderstorm_score", "cyclone_score", "anticyclone_score", "heatwave_score", "inversion_score"
    };

    /**
     * Ruleaza algoritmul k-means pe toti vectorii meteo si salveaza rezultatele.
     *
     * @param k numarul de clustere (regimuri)
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void runKmeans(int k) throws SQLException {
        List<WeatherDataPoint> points = fetchAllVectors();
        if (points.size() < k) {
            throw new IllegalStateException("Prea putine date pentru k=" + k + ". Avem doar " + points.size() + " puncte.");
        }

        double[] means = new double[DIMENSIONS];
        double[] stds = new double[DIMENSIONS];
        computeStats(points, means, stds);
        standardize(points, means, stds);

        KMeansPlusPlusClusterer<WeatherDataPoint> clusterer =
            new KMeansPlusPlusClusterer<>(k, MAX_ITERATIONS, new EuclideanDistance());
        List<CentroidCluster<WeatherDataPoint>> clusters = clusterer.cluster(points);

        String climateZone = com.sgbd.util.ClimateZoneUtil.EUROPE_WIDE;
        saveCentroids(clusters, climateZone, means, stds);
        saveLabels(points, clusters, climateZone);
    }

    private List<WeatherDataPoint> fetchAllVectors() throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT city_id, date, ");
        for (int i = 0; i < VECTOR_COLUMNS.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append(VECTOR_COLUMNS[i]);
        }
        sql.append(" FROM weather_vectors ORDER BY city_id, date");

        List<WeatherDataPoint> points = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            while (rs.next()) {
                double[] vec = new double[DIMENSIONS];
                for (int i = 0; i < DIMENSIONS; i++) {
                    double val = rs.getDouble(VECTOR_COLUMNS[i]);
                    vec[i] = rs.wasNull() ? 0.0 : val;
                }
                int cityId = rs.getInt("city_id");
                LocalDate date = rs.getDate("date").toLocalDate();
                points.add(new WeatherDataPoint(cityId, date, vec));
            }
        }
        return points;
    }

    void computeStats(List<WeatherDataPoint> points, double[] means, double[] stds) {
        int n = points.size();
        Arrays.fill(means, 0.0);
        for (WeatherDataPoint p : points) {
            for (int i = 0; i < DIMENSIONS; i++) {
                means[i] += p.getPoint()[i];
            }
        }
        for (int i = 0; i < DIMENSIONS; i++) {
            means[i] /= n;
        }

        Arrays.fill(stds, 0.0);
        for (WeatherDataPoint p : points) {
            for (int i = 0; i < DIMENSIONS; i++) {
                double diff = p.getPoint()[i] - means[i];
                stds[i] += diff * diff;
            }
        }
        for (int i = 0; i < DIMENSIONS; i++) {
            stds[i] = Math.sqrt(stds[i] / n);
            if (stds[i] < 1e-9) stds[i] = 1.0;
        }
    }

    void standardize(List<WeatherDataPoint> points, double[] means, double[] stds) {
        for (WeatherDataPoint p : points) {
            double[] pt = p.getPoint();
            for (int i = 0; i < DIMENSIONS; i++) {
                pt[i] = (pt[i] - means[i]) / stds[i];
            }
        }
    }

    private void saveCentroids(List<CentroidCluster<WeatherDataPoint>> clusters, String climateZone,
                               double[] means, double[] stds) throws SQLException {
        String deleteSql = "DELETE FROM weather_regimes WHERE climate_zone = ?";
        String insertSql = "INSERT INTO weather_regimes (climate_zone, regime_id, centroid, covariance_flat, frequency, label_ro, description_ro) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement delStmt = conn.prepareStatement(deleteSql);
             PreparedStatement insStmt = conn.prepareStatement(insertSql)) {

            delStmt.setString(1, climateZone);
            delStmt.executeUpdate();

            int totalPoints = 0;
            for (CentroidCluster<WeatherDataPoint> c : clusters) {
                totalPoints += c.getPoints().size();
            }

            for (int r = 0; r < clusters.size(); r++) {
                CentroidCluster<WeatherDataPoint> cluster = clusters.get(r);
                double[] centroid = cluster.getCenter().getPoint();
                List<WeatherDataPoint> pts = cluster.getPoints();

                double[] variances = new double[DIMENSIONS];
                if (pts.size() > 1) {
                    for (int d = 0; d < DIMENSIONS; d++) {
                        double sum = 0.0;
                        for (WeatherDataPoint p : pts) {
                            double diff = p.getPoint()[d] - centroid[d];
                            sum += diff * diff;
                        }
                        variances[d] = sum / (pts.size() - 1);
                    }
                }

                Double[] covFlat = new Double[DIMENSIONS];
                for (int d = 0; d < DIMENSIONS; d++) {
                    covFlat[d] = variances[d];
                }

                Double[] centroidArr = new Double[DIMENSIONS];
                for (int d = 0; d < DIMENSIONS; d++) {
                    centroidArr[d] = centroid[d];
                }

                insStmt.setString(1, climateZone);
                insStmt.setInt(2, r);
                insStmt.setArray(3, conn.createArrayOf("DOUBLE PRECISION", centroidArr));
                insStmt.setArray(4, conn.createArrayOf("DOUBLE PRECISION", covFlat));
                insStmt.setDouble(5, pts.size() / (double) totalPoints);
                insStmt.setString(6, "Regim " + r);
                insStmt.setString(7, "Centroid " + r + " neetichetat");
                insStmt.addBatch();
            }
            insStmt.executeBatch();
        }

        updateGlobalFrequencies(climateZone);
    }

    private void updateGlobalFrequencies(String climateZone) throws SQLException {
        String sql = "UPDATE weather_regimes wr SET frequency = sub.cnt / sub.total " +
                     "FROM (SELECT regime_id, COUNT(*) AS cnt, SUM(COUNT(*)) OVER () AS total FROM daily_regimes WHERE climate_zone = ? GROUP BY regime_id) sub " +
                     "WHERE wr.climate_zone = ? AND wr.regime_id = sub.regime_id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, climateZone);
            stmt.setString(2, climateZone);
            stmt.executeUpdate();
        }
    }

    private void saveLabels(List<WeatherDataPoint> points, List<CentroidCluster<WeatherDataPoint>> clusters,
                            String climateZone) throws SQLException {
        String deleteSql = "DELETE FROM daily_regimes";
        String insertSql = "INSERT INTO daily_regimes (city_id, date, regime_id, climate_zone) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement delStmt = conn.createStatement();
             PreparedStatement insStmt = conn.prepareStatement(insertSql)) {

            delStmt.executeUpdate(deleteSql);

            for (WeatherDataPoint p : points) {
                int bestRegime = findNearestCluster(p, clusters);
                insStmt.setInt(1, p.getCityId());
                insStmt.setDate(2, java.sql.Date.valueOf(p.getDate()));
                insStmt.setInt(3, bestRegime);
                insStmt.setString(4, climateZone);
                insStmt.addBatch();
            }
            insStmt.executeBatch();
        }
    }

    int findNearestCluster(WeatherDataPoint p, List<CentroidCluster<WeatherDataPoint>> clusters) {
        double minDist = Double.MAX_VALUE;
        int best = 0;
        EuclideanDistance dist = new EuclideanDistance();
        for (int i = 0; i < clusters.size(); i++) {
            double d = dist.compute(p.getPoint(), clusters.get(i).getCenter().getPoint());
            if (d < minDist) {
                minDist = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Auto-eticheteaza regimurile pe baza valorilor centroizilor.
     *
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void labelRegimes() throws SQLException {
        String selectSql = "SELECT regime_id, centroid, climate_zone FROM weather_regimes";
        String updateSql = "UPDATE weather_regimes SET label_ro = ?, description_ro = ? WHERE regime_id = ? AND climate_zone = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql);
             PreparedStatement updStmt = conn.prepareStatement(updateSql)) {

            while (rs.next()) {
                int regimeId = rs.getInt("regime_id");
                String zone = rs.getString("climate_zone");
                Array arr = rs.getArray("centroid");
                Double[] centroid = (Double[]) arr.getArray();

                String label = inferLabel(centroid);
                String desc = buildDescription(centroid);

                updStmt.setString(1, label);
                updStmt.setString(2, desc);
                updStmt.setInt(3, regimeId);
                updStmt.setString(4, zone);
                updStmt.addBatch();
            }
            updStmt.executeBatch();
        }
    }

    String inferLabel(Double[] centroid) {
        double tempMax = centroid[1];
        double tempAvg = centroid[2];
        double wind = centroid[10];
        double humidity = centroid[8];
        double precip = centroid[18];
        double pressure = centroid[22];
        double fog = centroid[33];
        double storm = centroid[34];
        double heat = centroid[37];
        double cyclone = centroid[35];

        if (heat > 0.6 || tempMax > 1.0) return "Caniculă";
        if (storm > 0.5 && wind > 0.5) return "Furtună";
        if (fog > 0.5 && humidity > 0.5) return "Ceață densă";
        if (cyclone > 0.5 && pressure < -0.5) return "Ciclon";
        if (pressure > 0.5 && tempAvg < -0.3) return "Anticiclon rece";
        if (pressure > 0.5) return "Anticiclon";
        if (precip > 0.5 && tempAvg < 0) return "Ninsori";
        if (precip > 0.5) return "Ploaie";
        if (tempAvg < -1.0) return "Iarnă geroasă";
        if (wind > 1.0) return "Vânt puternic";
        return "Normal";
    }

    private String buildDescription(Double[] centroid) {
        return String.format("TempMaxZ=%.2f, UmidZ=%.2f, VantZ=%.2f, PresZ=%.2f, PloaieZ=%.2f",
            centroid[1], centroid[8], centroid[10], centroid[22], centroid[18]);
    }

    /**
     * Calculeaza scorul silhouette mediu pentru clusterizarea curenta.
     *
     * @return scorul silhouette mediu, intre -1 si 1
     * @throws SQLException daca apare o eroare la baza de date
     */
    public double computeSilhouetteScore() throws SQLException {
        List<WeatherDataPoint> points = fetchAllVectors();
        double[] means = new double[DIMENSIONS];
        double[] stds = new double[DIMENSIONS];
        computeStats(points, means, stds);
        standardize(points, means, stds);

        Map<String, Integer> labels = new HashMap<>();
        String sql = "SELECT city_id, date, regime_id FROM daily_regimes";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String key = rs.getInt("city_id") + "_" + rs.getDate("date");
                labels.put(key, rs.getInt("regime_id"));
            }
        }

        EuclideanDistance dist = new EuclideanDistance();
        double totalScore = 0.0;
        int n = points.size();

        for (int i = 0; i < n; i++) {
            WeatherDataPoint p = points.get(i);
            String key = p.getCityId() + "_" + java.sql.Date.valueOf(p.getDate());
            Integer clusterId = labels.get(key);
            if (clusterId == null) continue;

            double a = 0.0;
            double b = Double.MAX_VALUE;
            int countA = 0;

            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                WeatherDataPoint q = points.get(j);
                String qKey = q.getCityId() + "_" + java.sql.Date.valueOf(q.getDate());
                Integer qCluster = labels.get(qKey);
                if (qCluster == null) continue;

                double d = dist.compute(p.getPoint(), q.getPoint());
                if (qCluster.equals(clusterId)) {
                    a += d;
                    countA++;
                }
            }

            if (countA == 0) {
                totalScore += 0;
                continue;
            }
            a /= countA;

            for (int otherCluster = 0; otherCluster < 16; otherCluster++) {
                if (otherCluster == clusterId) continue;
                double avgDist = 0.0;
                int countB = 0;
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    WeatherDataPoint q = points.get(j);
                    String qKey = q.getCityId() + "_" + java.sql.Date.valueOf(q.getDate());
                    Integer qCluster = labels.get(qKey);
                    if (qCluster == null || qCluster != otherCluster) continue;
                    avgDist += dist.compute(p.getPoint(), q.getPoint());
                    countB++;
                }
                if (countB > 0) {
                    avgDist /= countB;
                    b = Math.min(b, avgDist);
                }
            }

            double s = (b - a) / Math.max(a, b);
            totalScore += s;
        }

        return n > 0 ? totalScore / n : 0.0;
    }

    static class WeatherDataPoint implements Clusterable {
        private final int cityId;
        private final LocalDate date;
        private final double[] point;

        WeatherDataPoint(int cityId, LocalDate date, double[] point) {
            this.cityId = cityId;
            this.date = date;
            this.point = point;
        }

        @Override
        public double[] getPoint() {
            return point;
        }

        public int getCityId() {
            return cityId;
        }

        public LocalDate getDate() {
            return date;
        }
    }
}
