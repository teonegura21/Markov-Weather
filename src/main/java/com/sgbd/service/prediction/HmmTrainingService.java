package com.sgbd.service.prediction;

import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.util.*;

/**
 * Serviciu pentru antrenarea Hidden Markov Model folosind algoritmul Baum-Welch.
 * Modeleaza stari ascunse (8) care emit regimuri meteorologice observabile (16).
 */
public class HmmTrainingService {

    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 1e-5;
    private static final int NUM_REGIMES = 16;
    private static final double EPS = 1e-100;

    /**
     * Antreneaza un HMM pentru un oras specific folosind secventa de regimuri observate.
     *
     * @param cityId       identificatorul orasului
     * @param hiddenStates numarul de stari ascunse (de obicei 8)
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void trainHmm(int cityId, int hiddenStates) throws SQLException {
        int[] observations = fetchObservationSequence(cityId);
        if (observations.length < hiddenStates * 2) {
            throw new IllegalStateException("Secvența de observații este prea scurtă pentru antrenare HMM.");
        }

        int[] O = observations;
        int N = hiddenStates;
        int T = O.length;

        double[][] A = initializeMatrix(N, N);
        double[][] B = initializeMatrix(N, NUM_REGIMES);
        double[] pi = initializeVector(N);

        normalizeRows(A);
        normalizeRows(B);
        normalizeVector(pi);

        double prevLogLikelihood = Double.NEGATIVE_INFINITY;

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double[][] alpha = forwardAlgorithm(O, A, B, pi);
            double[][] beta = backwardAlgorithm(O, A, B);
            double logLikelihood = computeLogLikelihood(alpha);

            if (Math.abs(logLikelihood - prevLogLikelihood) < CONVERGENCE_THRESHOLD) {
                break;
            }
            prevLogLikelihood = logLikelihood;

            Object[] result = baumWelchStep(O, A, B, pi, alpha, beta);
            A = (double[][]) result[0];
            B = (double[][]) result[1];
            pi = (double[]) result[2];
        }

        saveHmm(cityId, A, B);
    }

    private int[] fetchObservationSequence(int cityId) throws SQLException {
        String sql = "SELECT regime_id FROM daily_regimes WHERE city_id = ? ORDER BY date";
        List<Integer> seq = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    seq.add(rs.getInt("regime_id"));
                }
            }
        }
        int[] arr = new int[seq.size()];
        for (int i = 0; i < seq.size(); i++) arr[i] = seq.get(i);
        return arr;
    }

    private double[][] initializeMatrix(int rows, int cols) {
        double[][] m = new double[rows][cols];
        Random rand = new Random(42);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                m[i][j] = rand.nextDouble() * 0.1 + 0.05;
            }
        }
        return m;
    }

    private double[] initializeVector(int size) {
        double[] v = new double[size];
        Random rand = new Random(42);
        for (int i = 0; i < size; i++) {
            v[i] = rand.nextDouble() * 0.1 + 0.05;
        }
        return v;
    }

    private void normalizeRows(double[][] m) {
        for (int i = 0; i < m.length; i++) {
            double sum = 0.0;
            for (int j = 0; j < m[i].length; j++) sum += m[i][j];
            if (sum > 0) {
                for (int j = 0; j < m[i].length; j++) m[i][j] /= sum;
            }
        }
    }

    private void normalizeVector(double[] v) {
        double sum = 0.0;
        for (double val : v) sum += val;
        if (sum > 0) {
            for (int i = 0; i < v.length; i++) v[i] /= sum;
        }
    }

    /**
     * Algoritmul forward pentru HMM.
     *
     * @param O  secventa de observatii
     * @param A  matrice de tranzitie
     * @param B  matrice de emisie
     * @param pi distributie initiala
     * @return matricea alpha
     */
    public double[][] forwardAlgorithm(int[] O, double[][] A, double[][] B, double[] pi) {
        int N = A.length;
        int T = O.length;
        double[][] alpha = new double[T][N];

        for (int i = 0; i < N; i++) {
            alpha[0][i] = pi[i] * B[i][O[0]];
        }

        for (int t = 1; t < T; t++) {
            for (int i = 0; i < N; i++) {
                double sum = 0.0;
                for (int j = 0; j < N; j++) {
                    sum += alpha[t - 1][j] * A[j][i];
                }
                alpha[t][i] = sum * B[i][O[t]];
            }
        }
        return alpha;
    }

    /**
     * Algoritmul backward pentru HMM.
     *
     * @param O secventa de observatii
     * @param A matrice de tranzitie
     * @param B matrice de emisie
     * @return matricea beta
     */
    public double[][] backwardAlgorithm(int[] O, double[][] A, double[][] B) {
        int N = A.length;
        int T = O.length;
        double[][] beta = new double[T][N];

        for (int i = 0; i < N; i++) {
            beta[T - 1][i] = 1.0;
        }

        for (int t = T - 2; t >= 0; t--) {
            for (int i = 0; i < N; i++) {
                double sum = 0.0;
                for (int j = 0; j < N; j++) {
                    sum += A[i][j] * B[j][O[t + 1]] * beta[t + 1][j];
                }
                beta[t][i] = sum;
            }
        }
        return beta;
    }

    private double computeLogLikelihood(double[][] alpha) {
        int T = alpha.length;
        double likelihood = 0.0;
        for (int i = 0; i < alpha[T - 1].length; i++) {
            likelihood += alpha[T - 1][i];
        }
        return Math.log(likelihood + EPS);
    }

    /**
     * Executa un pas de re-estimare Baum-Welch.
     *
     * @param O      secventa de observatii
     * @param A      matrice de tranzitie curenta
     * @param B      matrice de emisie curenta
     * @param pi     distributie initiala curenta
     * @param alpha  matricea forward
     * @param beta   matricea backward
     * @return obiect cu noile matrici A, B si pi
     */
    public Object[] baumWelchStep(int[] O, double[][] A, double[][] B, double[] pi, double[][] alpha, double[][] beta) {
        int N = A.length;
        int T = O.length;
        int M = B[0].length;

        double[][][] xi = new double[T - 1][N][N];
        double[][] gamma = new double[T][N];

        double likelihood = 0.0;
        for (int i = 0; i < N; i++) likelihood += alpha[T - 1][i];
        double denomL = likelihood + EPS;

        for (int t = 0; t < T; t++) {
            for (int i = 0; i < N; i++) {
                gamma[t][i] = (alpha[t][i] * beta[t][i]) / denomL;
            }
        }

        for (int t = 0; t < T - 1; t++) {
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    xi[t][i][j] = (alpha[t][i] * A[i][j] * B[j][O[t + 1]] * beta[t + 1][j]) / denomL;
                }
            }
        }

        double[] piNew = new double[N];
        for (int i = 0; i < N; i++) {
            piNew[i] = gamma[0][i];
        }

        double[][] ANew = new double[N][N];
        for (int i = 0; i < N; i++) {
            double sumGamma = 0.0;
            for (int t = 0; t < T - 1; t++) sumGamma += gamma[t][i];
            sumGamma += EPS;
            for (int j = 0; j < N; j++) {
                double sumXi = 0.0;
                for (int t = 0; t < T - 1; t++) sumXi += xi[t][i][j];
                ANew[i][j] = sumXi / sumGamma;
            }
        }

        double[][] BNew = new double[N][M];
        for (int i = 0; i < N; i++) {
            double sumGamma = 0.0;
            for (int t = 0; t < T; t++) sumGamma += gamma[t][i];
            sumGamma += EPS;
            for (int k = 0; k < M; k++) {
                double sumObs = 0.0;
                for (int t = 0; t < T; t++) {
                    if (O[t] == k) sumObs += gamma[t][i];
                }
                BNew[i][k] = sumObs / sumGamma;
            }
        }

        normalizeRows(ANew);
        normalizeRows(BNew);
        normalizeVector(piNew);

        return new Object[]{ANew, BNew, piNew};
    }

    private void saveHmm(int cityId, double[][] A, double[][] B) throws SQLException {
        int N = A.length;

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM hidden_states WHERE city_id = ?")) {
                stmt.setInt(1, cityId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM hidden_transitions WHERE city_id = ?")) {
                stmt.setInt(1, cityId);
                stmt.executeUpdate();
            }

            String insB = "INSERT INTO hidden_states (city_id, state_id, emission_probs) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insB)) {
                for (int i = 0; i < N; i++) {
                    Double[] arr = new Double[NUM_REGIMES];
                    for (int j = 0; j < NUM_REGIMES; j++) {
                        arr[j] = B[i][j];
                    }
                    stmt.setInt(1, cityId);
                    stmt.setInt(2, i);
                    stmt.setArray(3, conn.createArrayOf("DOUBLE PRECISION", arr));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            String insA = "INSERT INTO hidden_transitions (city_id, state_from, state_to, duration_bucket, probability) VALUES (?, ?, ?, 1, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insA)) {
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        stmt.setInt(1, cityId);
                        stmt.setInt(2, i);
                        stmt.setInt(3, j);
                        stmt.setDouble(4, A[i][j]);
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }
        }
    }

    /**
     * Decodeaza cea mai probabila secventa de stari ascunse folosind algoritmul Viterbi.
     *
     * @param cityId identificatorul orasului
     * @return vector cu stari ascunse pentru fiecare zi
     * @throws SQLException daca apare o eroare la baza de date
     */
    public int[] decodeMostLikelyStateSequence(int cityId) throws SQLException {
        int[] O = fetchObservationSequence(cityId);
        if (O.length == 0) return new int[0];

        double[][] A = loadTransitions(cityId);
        double[][] B = loadEmissions(cityId);
        int N = A.length;
        int T = O.length;

        double[][] delta = new double[T][N];
        int[][] psi = new int[T][N];

        for (int i = 0; i < N; i++) {
            delta[0][i] = Math.log(B[i][O[0]] + EPS);
            psi[0][i] = 0;
        }

        for (int t = 1; t < T; t++) {
            for (int i = 0; i < N; i++) {
                double maxVal = Double.NEGATIVE_INFINITY;
                int maxState = 0;
                for (int j = 0; j < N; j++) {
                    double val = delta[t - 1][j] + Math.log(A[j][i] + EPS);
                    if (val > maxVal) {
                        maxVal = val;
                        maxState = j;
                    }
                }
                delta[t][i] = maxVal + Math.log(B[i][O[t]] + EPS);
                psi[t][i] = maxState;
            }
        }

        double maxFinal = Double.NEGATIVE_INFINITY;
        int qT = 0;
        for (int i = 0; i < N; i++) {
            if (delta[T - 1][i] > maxFinal) {
                maxFinal = delta[T - 1][i];
                qT = i;
            }
        }

        int[] path = new int[T];
        path[T - 1] = qT;
        for (int t = T - 2; t >= 0; t--) {
            path[t] = psi[t + 1][path[t + 1]];
        }
        return path;
    }

    private double[][] loadTransitions(int cityId) throws SQLException {
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

    private double[][] loadEmissions(int cityId) throws SQLException {
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
                    Array arr = rs.getArray("emission_probs");
                    Double[] vals = (Double[]) arr.getArray();
                    double[] vd = new double[vals.length];
                    for (int i = 0; i < vals.length; i++) vd[i] = vals[i];
                    map.put(sid, vd);
                    maxState = Math.max(maxState, sid);
                    M = vals.length;
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
}
