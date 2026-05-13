/*
 * sp_update_regime_accuracy
 *   Incrementează contoarele de predicții corecte și totale pentru un regim climatic
 *   și recalculează rata de acuratețe. Dacă regimul nu există, se creează.
 *   Parametri:
 *     p_climate_zone - zona climatică
 *     p_regime_id    - ID-ul regimului
 *     p_is_correct   - TRUE dacă predicția a fost corectă
 */
CREATE OR REPLACE PROCEDURE sp_update_regime_accuracy(
    p_climate_zone VARCHAR(50),
    p_regime_id INT,
    p_is_correct BOOLEAN
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO regime_accuracy (climate_zone, regime_id, correct_predictions, total_predictions, accuracy_rate)
    VALUES (
        p_climate_zone,
        p_regime_id,
        CASE WHEN p_is_correct THEN 1 ELSE 0 END,
        1,
        CASE WHEN p_is_correct THEN 1.0 ELSE 0.0 END
    )
    ON CONFLICT (climate_zone, regime_id) DO UPDATE SET
        correct_predictions = regime_accuracy.correct_predictions + CASE WHEN p_is_correct THEN 1 ELSE 0 END,
        total_predictions = regime_accuracy.total_predictions + 1,
        accuracy_rate = (
            regime_accuracy.correct_predictions + CASE WHEN p_is_correct THEN 1 ELSE 0 END
        )::DOUBLE PRECISION / (regime_accuracy.total_predictions + 1),
        last_updated = CURRENT_TIMESTAMP;
END;
$$;
