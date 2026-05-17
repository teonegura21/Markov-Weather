package com.sgbd.service.prediction;

import com.sgbd.util.DatabaseConnection;
import com.sgbd.util.LoggerUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * Serviciu de reinforcement learning pentru ajustarea ponderilor Markov si emisiilor HMM
 * pe baza erorilor de predictie observate in tabela prediction_accuracy.
 */
public class ReinforcementService {

    private static final Logger logger = LoggerUtil.getLogger(ReinforcementService.class);

    private static final double MAE_THRESHOLD = 2.0;
    private static final double HIGH_ACCURACY = 0.80;
    private static final double LOW_ACCURACY = 0.50;
    private static final double MARKOV_DELTA = 0.02;
    private static final double HMM_DELTA = 0.01;
    private static final double MIN_PROB = 0.01;
    private static final double MAX_PROB = 0.99;

    private final HmmTrainingService hmmService = new HmmTrainingService();
    private final ThreadLocal<Integer> currentIteration = new ThreadLocal<>();

    /**
     * Ruleaza o iteratie completa de invatare pentru un oras.
     * Ajusteaza ponderile Markov la nivel de zona climatica si emisiile HMM la nivel de oras,
     * pe baza acuratetii predictiilor din ultimele 30 de zile.
     *
     * @param cityId identificatorul orasului
     */
    public void runLearningIteration(int cityId) {
        logger.info("Porneste iteratia de invatare pentru orasul " + cityId);
        try {
            int iteration = getNextIteration();
            currentIteration.set(iteration);
            try {
                double accuracyBefore = computeOverallAccuracy(cityId, 30);
                adjustMarkovWeights(com.sgbd.util.ClimateZoneUtil.EUROPE_WIDE);
                adjustHMMEmissions(cityId);
                double accuracyAfter = computeOverallAccuracy(cityId, 30);
                logger.info("Iteratia " + iteration + " completa: acuratete inainte="
                        + accuracyBefore + ", dupa=" + accuracyAfter);
            } finally {
                currentIteration.remove();
            }
        } catch (SQLException e) {
            logger.warning("Eroare SQL in runLearningIteration: " + e.getMessage());
        }
    }

    /**
     * Ajusteaza ponderile tranzitiilor Markov pentru o zona climatica.
     * Pentru fiecare tranzitie (sezon, r_prev, r_curr, r_next) se verifica acuratetea
     * predictiilor asociate in ultimele 30 de zile. Daca acuratetea > 80% creste probabilitatea
     * cu +0.02; daca < 50% scade cu -0.02. Probabilitatile sunt limitate intre 0.01 si 0.99,
     * apoi re-normalizate astfel incat suma pe fiecare grup (sezon, r_prev, r_curr) = 1.0.
     *
     * @param climateZone zona climatica (ex: "romania")
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void adjustMarkovWeights(String climateZone) throws SQLException {
        logger.info("Ajusteaza ponderile Markov pentru zona climatica: " + climateZone);

        Map<String, List<TransitionEntry>> groups = new HashMap<>();
        String selectSql = "SELECT id, season, r_prev, r_curr, r_next, probability FROM markov_transitions WHERE climate_zone = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, climateZone);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TransitionEntry te = new TransitionEntry(
                            rs.getLong("id"),
                            rs.getString("season"),
                            rs.getInt("r_prev"),
                            rs.getInt("r_curr"),
                            rs.getInt("r_next"),
                            rs.getDouble("probability")
                    );
                    String key = te.season + "|" + te.rPrev + "|" + te.rCurr;
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(te);
                }
            }
        }

        Map<String, TransitionAccuracy> accuracyMap = computeTransitionAccuracies(climateZone);
        int iteration = getNextIteration();

        String updateSql = "UPDATE markov_transitions SET probability = ? WHERE id = ?";
        String logSql = "INSERT INTO reinforcement_log (iteration, parameter_type, parameter_key, old_value, new_value, accuracy_before, accuracy_after, city_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement updStmt = conn.prepareStatement(updateSql);
             PreparedStatement logStmt = conn.prepareStatement(logSql)) {

            for (List<TransitionEntry> transitions : groups.values()) {
                boolean anyAdjusted = false;

                for (TransitionEntry t : transitions) {
                    String accKey = t.season + "|" + t.rPrev + "|" + t.rCurr + "|" + t.rNext;
                    TransitionAccuracy ta = accuracyMap.get(accKey);
                    if (ta == null || ta.total == 0) {
                        continue;
                    }

                    double rate = (double) ta.accurate / ta.total;
                    if (rate >= HIGH_ACCURACY) {
                        t.probability += MARKOV_DELTA;
                    } else if (rate < LOW_ACCURACY) {
                        t.probability -= MARKOV_DELTA;
                    }

                    t.probability = Math.max(MIN_PROB, Math.min(MAX_PROB, t.probability));
                    anyAdjusted = true;
                }

                if (!anyAdjusted) {
                    continue;
                }

                double sum = transitions.stream().mapToDouble(t -> t.probability).sum();
                if (sum > 0) {
                    for (TransitionEntry t : transitions) {
                        t.probability /= sum;
                    }
                }

                for (TransitionEntry t : transitions) {
                    if (Math.abs(t.probability - t.originalProbability) < 1e-9) {
                        continue;
                    }

                    updStmt.setDouble(1, t.probability);
                    updStmt.setLong(2, t.id);
                    updStmt.addBatch();

                    String paramKey = climateZone + "|" + t.season + "|" + t.rPrev + "|" + t.rCurr + "|" + t.rNext;
                    String accKey = t.season + "|" + t.rPrev + "|" + t.rCurr + "|" + t.rNext;
                    TransitionAccuracy ta = accuracyMap.get(accKey);
                    double rate = (ta != null && ta.total > 0) ? (double) ta.accurate / ta.total : 0.0;

                    logStmt.setInt(1, iteration);
                    logStmt.setString(2, "markov_weight");
                    logStmt.setString(3, paramKey);
                    logStmt.setDouble(4, t.originalProbability);
                    logStmt.setDouble(5, t.probability);
                    logStmt.setDouble(6, rate);
                    logStmt.setDouble(7, rate);
                    logStmt.setNull(8, Types.INTEGER);
                    logStmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
                    logStmt.addBatch();
                }
            }

            updStmt.executeBatch();
            logStmt.executeBatch();
        }

        logger.info("Ajustare Markov completa pentru zona: " + climateZone);
    }

    /**
     * Ajusteaza matricea de emisie HMM pentru un oras specific.
     * Decodeaza secventa cea mai probabila de stari ascunse (Viterbi) si compara
     * regimurile observate cu predictiile. Daca o stare ascunsa emite frecvent un regim
     * corect, creste probabilitatea cu +0.01; daca emite un regim eronat, scade cu -0.01.
     * Fiecare rand al matricii B este re-normalizat la suma 1.0.
     *
     * @param cityId identificatorul orasului
     * @throws SQLException daca apare o eroare la baza de date
     */
    public void adjustHMMEmissions(int cityId) throws SQLException {
        logger.info("Ajusteaza emisiile HMM pentru orasul " + cityId);

        int[] hiddenSequence;
        try {
            hiddenSequence = hmmService.decodeMostLikelyStateSequence(cityId);
        } catch (SQLException e) {
            logger.warning("Nu s-a putut decoda secventa HMM: " + e.getMessage());
            return;
        }

        if (hiddenSequence.length == 0) {
            logger.info("Secventa HMM vida pentru orasul " + cityId);
            return;
        }

        List<DailyRegime> observations = new ArrayList<>();
        String obsSql = "SELECT date, regime_id FROM daily_regimes WHERE city_id = ? ORDER BY date";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(obsSql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    observations.add(new DailyRegime(cityId, rs.getDate("date").toLocalDate(), rs.getInt("regime_id")));
                }
            }
        }

        if (observations.size() != hiddenSequence.length) {
            logger.warning("Lungime diferita intre observatii (" + observations.size()
                    + ") si stari ascunse (" + hiddenSequence.length + ")");
            return;
        }

        LocalDate cutoff = LocalDate.now().minusDays(30);
        Map<LocalDate, Double> maeMap = new HashMap<>();
        String accSql = "SELECT forecast_date, mae_temp FROM prediction_accuracy WHERE city_id = ? AND forecast_date >= ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(accSql)) {
            stmt.setInt(1, cityId);
            stmt.setDate(2, java.sql.Date.valueOf(cutoff));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double mae = rs.getDouble("mae_temp");
                    if (rs.wasNull()) {
                        continue;
                    }
                    maeMap.put(rs.getDate("forecast_date").toLocalDate(), mae);
                }
            }
        }

        Map<String, EmissionStats> stats = new HashMap<>();
        for (int t = 0; t < observations.size(); t++) {
            DailyRegime obs = observations.get(t);
            int hState = hiddenSequence[t];
            Double mae = maeMap.get(obs.date);
            if (mae == null) {
                continue;
            }

            String key = hState + "|" + obs.regimeId;
            EmissionStats es = stats.computeIfAbsent(key, k -> new EmissionStats());
            es.total++;
            if (mae <= MAE_THRESHOLD) {
                es.accurate++;
            }
        }

        Map<Integer, double[]> oldEmissions = loadEmissions(cityId);
        if (oldEmissions.isEmpty()) {
            logger.warning("Matrice de emisie vida pentru orasul " + cityId);
            return;
        }

        Map<Integer, double[]> newEmissions = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : oldEmissions.entrySet()) {
            newEmissions.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }

        for (Map.Entry<Integer, double[]> entry : newEmissions.entrySet()) {
            int stateId = entry.getKey();
            double[] row = entry.getValue();

            for (int regime = 0; regime < row.length; regime++) {
                String key = stateId + "|" + regime;
                EmissionStats es = stats.get(key);
                if (es == null || es.total == 0) {
                    continue;
                }

                double rate = (double) es.accurate / es.total;
                if (rate >= HIGH_ACCURACY) {
                    row[regime] += HMM_DELTA;
                } else if (rate < LOW_ACCURACY) {
                    row[regime] -= HMM_DELTA;
                }

                row[regime] = Math.max(MIN_PROB, Math.min(MAX_PROB, row[regime]));
            }

            double sum = Arrays.stream(row).sum();
            if (sum > 0) {
                for (int i = 0; i < row.length; i++) {
                    row[i] /= sum;
                }
            }
        }

        int iteration = getNextIteration();
        String updateSql = "UPDATE hidden_states SET emission_probs = ? WHERE city_id = ? AND state_id = ?";
        String logSql = "INSERT INTO reinforcement_log (iteration, parameter_type, parameter_key, old_value, new_value, accuracy_before, accuracy_after, city_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement updStmt = conn.prepareStatement(updateSql);
             PreparedStatement logStmt = conn.prepareStatement(logSql)) {

            for (Map.Entry<Integer, double[]> entry : newEmissions.entrySet()) {
                int stateId = entry.getKey();
                double[] newRow = entry.getValue();
                double[] oldRow = oldEmissions.get(stateId);

                boolean changed = false;
                for (int i = 0; i < newRow.length; i++) {
                    if (Math.abs(newRow[i] - oldRow[i]) > 1e-9) {
                        changed = true;
                        break;
                    }
                }

                if (!changed) {
                    continue;
                }

                Double[] arr = new Double[newRow.length];
                for (int i = 0; i < newRow.length; i++) {
                    arr[i] = newRow[i];
                }
                updStmt.setArray(1, conn.createArrayOf("DOUBLE PRECISION", arr));
                updStmt.setInt(2, cityId);
                updStmt.setInt(3, stateId);
                updStmt.addBatch();

                for (int regime = 0; regime < newRow.length; regime++) {
                    if (Math.abs(newRow[regime] - oldRow[regime]) > 1e-9) {
                        String paramKey = stateId + "|" + regime;
                        EmissionStats es = stats.get(paramKey);
                        double rate = (es != null && es.total > 0) ? (double) es.accurate / es.total : 0.0;

                        logStmt.setInt(1, iteration);
                        logStmt.setString(2, "hmm_emission");
                        logStmt.setString(3, paramKey);
                        logStmt.setDouble(4, oldRow[regime]);
                        logStmt.setDouble(5, newRow[regime]);
                        logStmt.setDouble(6, rate);
                        logStmt.setDouble(7, rate);
                        logStmt.setInt(8, cityId);
                        logStmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
                        logStmt.addBatch();
                    }
                }
            }

            updStmt.executeBatch();
            logStmt.executeBatch();
        }

        logger.info("Ajustare HMM completa pentru orasul " + cityId);
    }

    /**
     * Returneaza cel mai recent log de invatare pentru un oras.
     *
     * @param cityId identificatorul orasului
     * @return ultimul {@link ReinforcementLog} sau null daca nu exista
     */
    public ReinforcementLog getLastLearningLog(int cityId) {
        String sql = "SELECT id, iteration, parameter_type, parameter_key, old_value, new_value, accuracy_before, accuracy_after, city_id, created_at FROM reinforcement_log WHERE city_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapLog(rs);
                }
            }
        } catch (SQLException e) {
            logger.warning("Eroare la citirea logului: " + e.getMessage());
        }
        return null;
    }

    /**
     * Anuleaza ultima ajustare efectuata pentru un oras.
     * Citeste ultimul log, inverseaza valorile old/new si actualizeaza tabela corespunzatoare.
     * Inseraza un nou log de tip "undo".
     *
     * @param cityId identificatorul orasului
     */
    public void undoLastAdjustment(int cityId) {
        logger.info("Anuleaza ultima ajustare pentru orasul " + cityId);
        ReinforcementLog log = getLastLearningLog(cityId);
        if (log == null) {
            logger.warning("Nu exista log de ajustare pentru orasul " + cityId);
            return;
        }

        try {
            if ("markov_weight".equals(log.getParameterType())) {
                undoMarkovAdjustment(log);
            } else if ("hmm_emission".equals(log.getParameterType())) {
                undoHMMEmissionAdjustment(log);
            } else {
                logger.warning("Tip de parametru necunoscut pentru undo: " + log.getParameterType());
            }
        } catch (SQLException e) {
            logger.warning("Eroare SQL la undo: " + e.getMessage());
        }
    }

    // ================================================================================
    // Metode private auxiliare
    // ================================================================================

    private int getNextIteration() throws SQLException {
        Integer cached = currentIteration.get();
        if (cached != null) {
            return cached;
        }
        String sql = "SELECT COALESCE(MAX(iteration), 0) + 1 FROM reinforcement_log";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1;
    }

    private double computeOverallAccuracy(int cityId, int daysBack) throws SQLException {
        LocalDate cutoff = LocalDate.now().minusDays(daysBack);
        String sql = "SELECT COUNT(*) AS total, COUNT(CASE WHEN mae_temp <= ? THEN 1 END) AS accurate FROM prediction_accuracy WHERE city_id = ? AND forecast_date >= ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, MAE_THRESHOLD);
            stmt.setInt(2, cityId);
            stmt.setDate(3, java.sql.Date.valueOf(cutoff));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int accurate = rs.getInt("accurate");
                    return total > 0 ? (double) accurate / total : 0.0;
                }
            }
        }
        return 0.0;
    }

    private Map<String, TransitionAccuracy> computeTransitionAccuracies(String climateZone) throws SQLException {
        Map<String, TransitionAccuracy> result = new HashMap<>();
        LocalDate cutoff = LocalDate.now().minusDays(30);

        Map<Integer, List<DailyRegime>> cityRegimes = new HashMap<>();
        String sql = "SELECT city_id, date, regime_id FROM daily_regimes WHERE climate_zone = ? AND date >= ? ORDER BY city_id, date";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, climateZone);
            stmt.setDate(2, java.sql.Date.valueOf(cutoff));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int cid = rs.getInt("city_id");
                    LocalDate d = rs.getDate("date").toLocalDate();
                    int rid = rs.getInt("regime_id");
                    cityRegimes.computeIfAbsent(cid, k -> new ArrayList<>()).add(new DailyRegime(cid, d, rid));
                }
            }
        }

        if (cityRegimes.isEmpty()) {
            return result;
        }

        Map<String, Double> maeMap = new HashMap<>();
        String accSql = "SELECT city_id, forecast_date, mae_temp FROM prediction_accuracy WHERE city_id = ANY(?) AND forecast_date >= ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(accSql)) {
            Array arr = conn.createArrayOf("INTEGER", cityRegimes.keySet().toArray());
            stmt.setArray(1, arr);
            stmt.setDate(2, java.sql.Date.valueOf(cutoff));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double mae = rs.getDouble("mae_temp");
                    if (rs.wasNull()) {
                        continue;
                    }
                    int cid = rs.getInt("city_id");
                    LocalDate fd = rs.getDate("forecast_date").toLocalDate();
                    maeMap.put(cid + "_" + fd.toString(), mae);
                }
            }
        }

        for (List<DailyRegime> list : cityRegimes.values()) {
            for (int i = 1; i < list.size() - 1; i++) {
                DailyRegime prev = list.get(i - 1);
                DailyRegime curr = list.get(i);
                DailyRegime next = list.get(i + 1);
                if (!curr.date.equals(prev.date.plusDays(1)) || !next.date.equals(curr.date.plusDays(1))) {
                    continue;
                }

                Double mae = maeMap.get(curr.cityId + "_" + next.date.toString());
                if (mae == null) {
                    continue;
                }

                String season = getSeason(curr.date);
                String key = season + "|" + prev.regimeId + "|" + curr.regimeId + "|" + next.regimeId;

                TransitionAccuracy ta = result.computeIfAbsent(key, k -> new TransitionAccuracy());
                ta.total++;
                if (mae <= MAE_THRESHOLD) {
                    ta.accurate++;
                }
            }
        }

        return result;
    }

    private String getSeason(LocalDate date) {
        int month = date.getMonthValue();
        if (month == 12 || month <= 2) {
            return "winter";
        }
        if (month <= 5) {
            return "spring";
        }
        if (month <= 8) {
            return "summer";
        }
        return "autumn";
    }

    private Map<Integer, double[]> loadEmissions(int cityId) throws SQLException {
        Map<Integer, double[]> map = new HashMap<>();
        String sql = "SELECT state_id, emission_probs FROM hidden_states WHERE city_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int sid = rs.getInt("state_id");
                    Array arr = rs.getArray("emission_probs");
                    if (arr == null) continue;
                    Double[] vals = (Double[]) arr.getArray();
                    double[] vd = new double[vals.length];
                    for (int i = 0; i < vals.length; i++) {
                        vd[i] = vals[i];
                    }
                    map.put(sid, vd);
                }
            }
        }
        return map;
    }

    /**
     * Limiteaza o probabilitate in intervalul [MIN_PROB, MAX_PROB].
     */
    static double clampProbability(double value) {
        return Math.max(MIN_PROB, Math.min(MAX_PROB, value));
    }

    private void undoMarkovAdjustment(ReinforcementLog log) throws SQLException {
        String[] parts = log.getParameterKey().split("\\|");
        if (parts.length != 5) {
            logger.warning("Format invalid pentru cheia Markov: " + log.getParameterKey());
            return;
        }
        String climateZone = parts[0];
        String season = parts[1];
        int rPrev = Integer.parseInt(parts[2]);
        int rCurr = Integer.parseInt(parts[3]);
        int rNext = Integer.parseInt(parts[4]);

        String updateSql = "UPDATE markov_transitions SET probability = ? WHERE climate_zone = ? AND season = ? AND r_prev = ? AND r_curr = ? AND r_next = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setDouble(1, log.getOldValue());
            stmt.setString(2, climateZone);
            stmt.setString(3, season);
            stmt.setInt(4, rPrev);
            stmt.setInt(5, rCurr);
            stmt.setInt(6, rNext);
            stmt.executeUpdate();
        }

        renormalizeMarkovGroup(climateZone, season, rPrev, rCurr);
        insertUndoLog(log);
    }

    private void undoHMMEmissionAdjustment(ReinforcementLog log) throws SQLException {
        String[] parts = log.getParameterKey().split("\\|");
        if (parts.length != 2) {
            logger.warning("Format invalid pentru cheia HMM: " + log.getParameterKey());
            return;
        }
        int stateId = Integer.parseInt(parts[0]);
        int regimeId = Integer.parseInt(parts[1]);
        Integer cityId = log.getCityId();
        if (cityId == null) {
            logger.warning("CityId lipsa pentru log HMM");
            return;
        }

        Map<Integer, double[]> emissions = loadEmissions(cityId);
        double[] row = emissions.get(stateId);
        if (row == null) {
            logger.warning("Stare HMM inexistenta: " + stateId);
            return;
        }

        row[regimeId] = log.getOldValue();
        double sum = Arrays.stream(row).sum();
        if (sum > 0) {
            for (int i = 0; i < row.length; i++) {
                row[i] /= sum;
            }
        }

        String updateSql = "UPDATE hidden_states SET emission_probs = ? WHERE city_id = ? AND state_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            Double[] arr = new Double[row.length];
            for (int i = 0; i < row.length; i++) {
                arr[i] = row[i];
            }
            stmt.setArray(1, conn.createArrayOf("DOUBLE PRECISION", arr));
            stmt.setInt(2, cityId);
            stmt.setInt(3, stateId);
            stmt.executeUpdate();
        }

        insertUndoLog(log);
    }

    private void renormalizeMarkovGroup(String climateZone, String season, int rPrev, int rCurr) throws SQLException {
        String selectSql = "SELECT id, probability FROM markov_transitions WHERE climate_zone = ? AND season = ? AND r_prev = ? AND r_curr = ?";
        List<Long> ids = new ArrayList<>();
        List<Double> probs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, climateZone);
            stmt.setString(2, season);
            stmt.setInt(3, rPrev);
            stmt.setInt(4, rCurr);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                    probs.add(rs.getDouble("probability"));
                }
            }
        }

        double sum = probs.stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            String updateSql = "UPDATE markov_transitions SET probability = ? WHERE id = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                for (int i = 0; i < ids.size(); i++) {
                    stmt.setDouble(1, probs.get(i) / sum);
                    stmt.setLong(2, ids.get(i));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
    }

    private void insertUndoLog(ReinforcementLog original) throws SQLException {
        String sql = "INSERT INTO reinforcement_log (iteration, parameter_type, parameter_key, old_value, new_value, accuracy_before, accuracy_after, city_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, original.getIteration());
            stmt.setString(2, "undo_" + original.getParameterType());
            stmt.setString(3, original.getParameterKey());
            stmt.setDouble(4, original.getNewValue());
            stmt.setDouble(5, original.getOldValue());
            stmt.setDouble(6, original.getAccuracyBefore());
            stmt.setDouble(7, original.getAccuracyAfter());
            stmt.setObject(8, original.getCityId());
            stmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
    }

    private ReinforcementLog mapLog(ResultSet rs) throws SQLException {
        Integer cityId = rs.getObject("city_id") != null ? rs.getInt("city_id") : null;
        return new ReinforcementLog(
                rs.getLong("id"),
                rs.getInt("iteration"),
                rs.getString("parameter_type"),
                rs.getString("parameter_key"),
                rs.getDouble("old_value"),
                rs.getDouble("new_value"),
                rs.getDouble("accuracy_before"),
                rs.getDouble("accuracy_after"),
                cityId,
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    // ================================================================================
    // Clase interne auxiliare
    // ================================================================================

    private static class TransitionEntry {
        final long id;
        final String season;
        final int rPrev;
        final int rCurr;
        final int rNext;
        final double originalProbability;
        double probability;

        TransitionEntry(long id, String season, int rPrev, int rCurr, int rNext, double probability) {
            this.id = id;
            this.season = season;
            this.rPrev = rPrev;
            this.rCurr = rCurr;
            this.rNext = rNext;
            this.originalProbability = probability;
            this.probability = probability;
        }
    }

    private static class TransitionAccuracy {
        int total;
        int accurate;
    }

    private static class DailyRegime {
        final int cityId;
        final LocalDate date;
        final int regimeId;

        DailyRegime(int cityId, LocalDate date, int regimeId) {
            this.cityId = cityId;
            this.date = date;
            this.regimeId = regimeId;
        }
    }

    private static class EmissionStats {
        int total;
        int accurate;
    }
}
