/*
 * sp_compute_accuracy
 *   Calculează metricile de acuratețe (MAE, RMSE, BIAS) pentru temperatura maximă
 *   pentru fiecare predicție din intervalul specificat.
 *   Parametri:
 *     p_city_id    - ID-ul orașului
 *     p_start_date - data de început a intervalului
 *     p_end_date   - data de sfârșit a intervalului
 */
CREATE OR REPLACE FUNCTION sp_compute_accuracy(
    p_city_id INTEGER,
    p_start_date DATE,
    p_end_date DATE
) RETURNS TABLE(
    forecast_date DATE,
    horizon_day INT,
    mae DOUBLE PRECISION,
    rmse DOUBLE PRECISION,
    bias DOUBLE PRECISION,
    predicted_temp_max DOUBLE PRECISION,
    actual_temp_max DOUBLE PRECISION
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        pa.forecast_date,
        pa.horizon_day,
        ABS(pa.predicted_temp_max - pa.actual_temp_max) AS mae,
        SQRT(POWER(pa.predicted_temp_max - pa.actual_temp_max, 2)) AS rmse,
        (pa.predicted_temp_max - pa.actual_temp_max) AS bias,
        pa.predicted_temp_max,
        pa.actual_temp_max
    FROM prediction_accuracy pa
    WHERE pa.city_id = p_city_id
      AND pa.forecast_date BETWEEN p_start_date AND p_end_date
    ORDER BY pa.forecast_date, pa.horizon_day;
END;
$$;
