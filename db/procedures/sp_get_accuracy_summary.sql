/*
 * sp_get_accuracy_summary
 *   Returnează un rezumat agregat al acurateței predicțiilor pentru un oraș
 *   în ultimele N zile (calculat după computed_at).
 *   Parametri:
 *     p_city_id - ID-ul orașului
 *     p_days    - numărul de zile în urmă (implicit 30)
 */
CREATE OR REPLACE FUNCTION sp_get_accuracy_summary(
    p_city_id INTEGER,
    p_days INTEGER DEFAULT 30
) RETURNS TABLE(
    overall_mae DOUBLE PRECISION,
    overall_rmse DOUBLE PRECISION,
    overall_bias DOUBLE PRECISION,
    total_predictions BIGINT,
    avg_hit_rate DOUBLE PRECISION
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        AVG(ABS(pa.predicted_temp_max - pa.actual_temp_max)),
        SQRT(AVG(POWER(pa.predicted_temp_max - pa.actual_temp_max, 2))),
        AVG(pa.predicted_temp_max - pa.actual_temp_max),
        COUNT(*),
        AVG(pa.hit_correct::INT)::DOUBLE PRECISION
    FROM prediction_accuracy pa
    WHERE pa.city_id = p_city_id
      AND pa.computed_at >= CURRENT_TIMESTAMP - (p_days || ' days')::INTERVAL;
END;
$$;
