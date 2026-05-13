package com.sgbd.service.prediction;

import com.sgbd.util.DatabaseConnection;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Motor de simulare Monte Carlo pentru predictia probabilistica a vremii.
 * Genereaza traiectorii multiple folosind Markov + HMM si extrage percentile.
 */
public class MonteCarloEngine {

    private static final int NUM_REGIMES = 16;
    private static final double TAU = 5.0;
    private static final double MARKOV_WEIGHT = 0.6;
    private static final double HMM_WEIGHT = 0.4;
    private static final double EVENT_THRESHOLD = 0.5;

    private final MarkovModelService markovService = new MarkovModelService();
    private final HmmTrainingService hmmService = new HmmTrainingService();
    private final Set<String> structuralZeroCache = new HashSet<>();

    /**
     * Ruleaza simularea Monte Carlo completa si salveaza rezultatele in cache.
     *
     * @param cityId      identificatorul orasului
     * @param startDate   data de inceput a predictiei
     * @param days        numarul de zile de predictie
     * @param trajectories numarul de traiectorii (de obicei 5000)
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void runSimulation(int cityId, LocalDate startDate, int days, int trajectories) throws SQLException {
        int[] history = fetchLastTwoRegimes(cityId, startDate);
        int rPrev = history.length > 1 ? history[1] : 0;
        int rCurr = history.length > 0 ? history[0] : 0;

        int[] hiddenSeq = hmmService.decodeMostLikelyStateSequence(cityId);
        int currentHiddenState = hiddenSeq.length > 0 ? hiddenSeq[hiddenSeq.length - 1] : 0;

        double[][] A = loadHmmTransitions(cityId);
        double[][] B = loadHmmEmissions(cityId);
        Map<Integer, RegimeModel> regimes = loadRegimeModels();
        loadStructuralZeros();

        // Pre-încarcă distribuțiile Markov și climatologia sezonieră pentru a evita query-uri repetitive
        Map<String, double[]> markovCache = new HashMap<>();
        Map<LocalDate, double[]> seasonalCache = new HashMap<>();

        List<TrajectoryDay> allDays = new ArrayList<>();
        Random rand = new Random();

        for (int tr = 0; tr < trajectories; tr++) {
            int prev = rPrev;
            int curr = rCurr;
            int hidden = currentHiddenState;

            for (int d = 1; d <= days; d++) {
                String season = getSeason(startDate.plusDays(d - 1));
                LocalDate targetDate = startDate.plusDays(d - 1);

                // TransitionSampler: blend Markov + HMM (cu cache)
                final int fPrev = prev;
                final int fCurr = curr;
                String markovKey = season + "|" + fPrev + "|" + fCurr;
                double[] markovProbs = markovCache.computeIfAbsent(markovKey,
                    k -> {
                        try {
                            return markovService.getTransitionDistribution(season, fPrev, fCurr, NUM_REGIMES);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
                double[] hmmProbs = new double[NUM_REGIMES];
                if (B != null && hidden >= 0 && hidden < B.length) {
                    hmmProbs = B[hidden].clone();
                }

                double[] blended = new double[NUM_REGIMES];
                double sum = 0.0;
                for (int r = 0; r < NUM_REGIMES; r++) {
                    blended[r] = MARKOV_WEIGHT * markovProbs[r] + HMM_WEIGHT * hmmProbs[r];
                    sum += blended[r];
                }
                if (sum > 0) {
                    for (int r = 0; r < NUM_REGIMES; r++) blended[r] /= sum;
                }

                // StructuralZeroFilter
                int next = sampleRegime(blended, rand);
                boolean isZero = isStructuralZeroCached(curr, next);
                int attempts = 0;
                while (isZero && attempts < 50) {
                    next = sampleRegime(blended, rand);
                    isZero = isStructuralZeroCached(curr, next);
                    attempts++;
                }

                // WeatherVectorSampler
                RegimeModel rm = regimes.get(next);
                double[] vector = sampleVector(rm, rand);

                // SeasonalBlender (cu cache)
                double[] seasonal = seasonalCache.computeIfAbsent(targetDate,
                    date -> {
                        try {
                            return loadSeasonalClimatology(cityId, date);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
                double[] blendedVector = blendWithSeasonal(vector, seasonal, d);

                // Actualizeaza stare ascunsa
                if (A != null && hidden >= 0 && hidden < A.length) {
                    hidden = sampleHiddenState(A[hidden], rand);
                }

                allDays.add(new TrajectoryDay(d, startDate.plusDays(d - 1), blendedVector, next));

                prev = curr;
                curr = next;
            }
        }

        extractAndSave(cityId, startDate, days, allDays);
    }

    private int[] fetchLastTwoRegimes(int cityId, LocalDate startDate) throws SQLException {
        String sql = "SELECT regime_id FROM daily_regimes WHERE city_id = ? AND date < ? ORDER BY date DESC LIMIT 2";
        List<Integer> regs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, java.sql.Date.valueOf(startDate));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    regs.add(rs.getInt("regime_id"));
                }
            }
        }
        int[] arr = new int[regs.size()];
        for (int i = 0; i < regs.size(); i++) arr[i] = regs.get(i);
        return arr;
    }

    private String getSeason(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= 3 && month <= 5) return "primavara";
        if (month >= 6 && month <= 8) return "vara";
        if (month >= 9 && month <= 11) return "toamna";
        return "iarna";
    }

    private int sampleRegime(double[] probs, Random rand) {
        double u = rand.nextDouble();
        double cum = 0.0;
        for (int i = 0; i < probs.length; i++) {
            cum += probs[i];
            if (u <= cum) return i;
        }
        return probs.length - 1;
    }

    private int sampleHiddenState(double[] probs, Random rand) {
        return sampleRegime(probs, rand);
    }

    private void loadStructuralZeros() throws SQLException {
        structuralZeroCache.clear();
        String sql = "SELECT regime_from, regime_to FROM structural_zeros";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                structuralZeroCache.add(rs.getInt("regime_from") + "_" + rs.getInt("regime_to"));
            }
        }
    }

    private boolean isStructuralZeroCached(int from, int to) {
        return structuralZeroCache.contains(from + "_" + to);
    }

    private double[] sampleVector(RegimeModel rm, Random rand) {
        if (rm == null) return new double[39];
        double[] mean = rm.centroid.clone();
        double[] covDiag = rm.covariance;
        int dim = mean.length;
        double[] sample = new double[dim];

        if (covDiag.length == dim * dim) {
            try {
                double[][] cov = new double[dim][dim];
                for (int i = 0; i < dim; i++) {
                    for (int j = 0; j < dim; j++) {
                        cov[i][j] = covDiag[i * dim + j];
                    }
                }
                RealMatrix covMatrix = new Array2DRowRealMatrix(cov);
                CholeskyDecomposition chol = new CholeskyDecomposition(covMatrix);
                RealMatrix L = chol.getL();

                double[] z = new double[dim];
                for (int i = 0; i < dim; i++) {
                    z[i] = rand.nextGaussian();
                }

                double[] lz = L.operate(z);
                for (int i = 0; i < dim; i++) {
                    sample[i] = mean[i] + lz[i];
                }
                return sample;
            } catch (Exception e) {
                // Fallback la diagonala
            }
        }

        for (int i = 0; i < dim; i++) {
            double std = Math.sqrt(Math.max(0, covDiag[i % covDiag.length]));
            sample[i] = mean[i] + std * rand.nextGaussian();
        }
        return sample;
    }

    private double[] loadSeasonalClimatology(int cityId, LocalDate date) throws SQLException {
        String sql = "SELECT temp_min_mean, temp_max_mean, wind_speed_mean, humidity_mean, precip_sum_mean " +
                     "FROM seasonal_climatology WHERE city_id = ? AND day_of_year = ?";
        double[] vec = new double[5];
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            stmt.setInt(2, date.getDayOfYear());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    vec[0] = rs.getDouble("temp_min_mean");
                    vec[1] = rs.getDouble("temp_max_mean");
                    vec[2] = rs.getDouble("wind_speed_mean");
                    vec[3] = rs.getDouble("humidity_mean");
                    vec[4] = rs.getDouble("precip_sum_mean");
                }
            }
        }
        return vec;
    }

    private double[] blendWithSeasonal(double[] vector, double[] seasonal, int day) {
        double w = Math.exp(-day / TAU);
        int[] seasonalIdx = {0, 1, 10, 8, 18};
        double[] result = vector.clone();
        for (int i = 0; i < seasonalIdx.length && i < seasonal.length; i++) {
            int idx = seasonalIdx[i];
            if (!Double.isNaN(seasonal[i])) {
                result[idx] = w * vector[idx] + (1.0 - w) * seasonal[i];
            }
        }
        return result;
    }

    private void extractAndSave(int cityId, LocalDate startDate, int days, List<TrajectoryDay> allDays) throws SQLException {
        String deleteSql = "DELETE FROM monte_carlo_predictions WHERE city_id = ? AND forecast_date >= ? AND forecast_date <= ?";
        String insertSql = "INSERT INTO monte_carlo_predictions (" +
            "city_id, generated_at, forecast_date, horizon_day, " +
            "temp_min_p50, temp_min_p10, temp_min_p90, " +
            "temp_max_p50, temp_max_p10, temp_max_p90, " +
            "wind_speed_p50, humidity_p50, precip_sum_p50, " +
            "precip_prob, storm_prob, fog_prob, heatwave_prob, ensemble_spread) " +
            "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement delStmt = conn.prepareStatement(deleteSql);
             PreparedStatement insStmt = conn.prepareStatement(insertSql)) {

            delStmt.setInt(1, cityId);
            delStmt.setDate(2, java.sql.Date.valueOf(startDate));
            delStmt.setDate(3, java.sql.Date.valueOf(startDate.plusDays(days - 1)));
            delStmt.executeUpdate();

            for (int d = 1; d <= days; d++) {
                final int horizon = d;
                List<TrajectoryDay> dayData = allDays.stream()
                    .filter(td -> td.horizon == horizon)
                    .toList();

                if (dayData.isEmpty()) continue;

                double[] tempMin = dayData.stream().mapToDouble(td -> td.vector[0]).sorted().toArray();
                double[] tempMax = dayData.stream().mapToDouble(td -> td.vector[1]).sorted().toArray();
                double[] wind = dayData.stream().mapToDouble(td -> td.vector[10]).toArray();
                double[] humidity = dayData.stream().mapToDouble(td -> td.vector[8]).toArray();
                double[] precip = dayData.stream().mapToDouble(td -> td.vector[18]).toArray();
                double[] storm = dayData.stream().mapToDouble(td -> td.vector[34]).toArray();
                double[] fog = dayData.stream().mapToDouble(td -> td.vector[33]).toArray();
                double[] heat = dayData.stream().mapToDouble(td -> td.vector[37]).toArray();

                double p10TempMin = percentile(tempMin, 0.10);
                double p50TempMin = percentile(tempMin, 0.50);
                double p90TempMin = percentile(tempMin, 0.90);
                double p10TempMax = percentile(tempMax, 0.10);
                double p50TempMax = percentile(tempMax, 0.50);
                double p90TempMax = percentile(tempMax, 0.90);
                double p50Wind = median(wind);
                double p50Humidity = median(humidity);
                double p50Precip = median(precip);

                double precipProb = probGreaterThan(precip, 0.1);
                double stormProb = probGreaterThan(storm, EVENT_THRESHOLD);
                double fogProb = probGreaterThan(fog, EVENT_THRESHOLD);
                double heatProb = probGreaterThan(heat, EVENT_THRESHOLD);

                double spread = tempMax[tempMax.length - 1] - tempMax[0];

                insStmt.setInt(1, cityId);
                insStmt.setDate(2, java.sql.Date.valueOf(startDate.plusDays(d - 1)));
                insStmt.setInt(3, d);
                insStmt.setDouble(4, p50TempMin);
                insStmt.setDouble(5, p10TempMin);
                insStmt.setDouble(6, p90TempMin);
                insStmt.setDouble(7, p50TempMax);
                insStmt.setDouble(8, p10TempMax);
                insStmt.setDouble(9, p90TempMax);
                insStmt.setDouble(10, p50Wind);
                insStmt.setDouble(11, p50Humidity);
                insStmt.setDouble(12, p50Precip);
                insStmt.setDouble(13, precipProb);
                insStmt.setDouble(14, stormProb);
                insStmt.setDouble(15, fogProb);
                insStmt.setDouble(16, heatProb);
                insStmt.setDouble(17, spread);
                insStmt.addBatch();
            }
            insStmt.executeBatch();
        }
    }

    private double percentile(double[] sorted, double p) {
        int n = sorted.length;
        if (n == 0) return 0.0;
        double pos = p * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        double frac = pos - lo;
        if (lo == hi) return sorted[lo];
        return sorted[lo] * (1 - frac) + sorted[hi] * frac;
    }

    private double median(double[] vals) {
        double[] copy = vals.clone();
        Arrays.sort(copy);
        return percentile(copy, 0.5);
    }

    private double probGreaterThan(double[] vals, double threshold) {
        int count = 0;
        for (double v : vals) {
            if (v > threshold) count++;
        }
        return (double) count / vals.length;
    }

    private Map<Integer, RegimeModel> loadRegimeModels() throws SQLException {
        String sql = "SELECT regime_id, centroid, covariance_flat FROM weather_regimes";
        Map<Integer, RegimeModel> map = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int rid = rs.getInt("regime_id");
                Double[] c = (Double[]) rs.getArray("centroid").getArray();
                Double[] cov = (Double[]) rs.getArray("covariance_flat").getArray();
                double[] cd = new double[c.length];
                double[] covd = new double[cov.length];
                for (int i = 0; i < c.length; i++) cd[i] = c[i];
                for (int i = 0; i < cov.length; i++) covd[i] = cov[i];
                map.put(rid, new RegimeModel(cd, covd));
            }
        }
        return map;
    }

    private double[][] loadHmmTransitions(int cityId) throws SQLException {
        String sql = "SELECT state_from, state_to, probability FROM hidden_transitions WHERE city_id = ? AND duration_bucket = 1";
        Map<Integer, Map<Integer, Double>> map = new HashMap<>();
        int maxState = 0;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int from = rs.getInt("state_from");
                    int to = rs.getInt("state_to");
                    double p = rs.getDouble("probability");
                    map.computeIfAbsent(from, k -> new HashMap<>()).put(to, p);
                    maxState = Math.max(maxState, Math.max(from, to));
                }
            }
        }
        int N = maxState + 1;
        double[][] A = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                A[i][j] = map.getOrDefault(i, Collections.emptyMap()).getOrDefault(j, 0.0);
            }
        }
        return A;
    }

    private double[][] loadHmmEmissions(int cityId) throws SQLException {
        String sql = "SELECT state_id, emission_probs FROM hidden_states WHERE city_id = ?";
        Map<Integer, double[]> map = new HashMap<>();
        int maxState = 0;
        int M = NUM_REGIMES;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int sid = rs.getInt("state_id");
                    Double[] arr = (Double[]) rs.getArray("emission_probs").getArray();
                    double[] vd = new double[arr.length];
                    for (int i = 0; i < arr.length; i++) vd[i] = arr[i];
                    map.put(sid, vd);
                    maxState = Math.max(maxState, sid);
                    M = arr.length;
                }
            }
        }
        int N = maxState + 1;
        double[][] B = new double[N][M];
        for (int i = 0; i < N; i++) {
            B[i] = map.getOrDefault(i, new double[M]);
        }
        return B;
    }

    private static class RegimeModel {
        final double[] centroid;
        final double[] covariance;
        RegimeModel(double[] centroid, double[] covariance) {
            this.centroid = centroid;
            this.covariance = covariance;
        }
    }

    private static class TrajectoryDay {
        final int horizon;
        final LocalDate date;
        final double[] vector;
        final int regime;
        TrajectoryDay(int horizon, LocalDate date, double[] vector, int regime) {
            this.horizon = horizon;
            this.date = date;
            this.vector = vector;
            this.regime = regime;
        }
    }
}
