/*
 * sp_store_accuracy_result
 *   Procedură de inserare sau actualizare a unui rezultat de acuratețe
 *   în tabela prediction_accuracy. Dacă există deja o înregistrare
 *   pentru aceeași combinație (city_id, forecast_date, horizon_day),
 *   se face actualizarea valorilor.
 */
CREATE OR REPLACE PROCEDURE sp_store_accuracy_result(
    p_city_id INTEGER,
    p_forecast_date DATE,
    p_horizon_day INT,
    p_predicted_temp_min DOUBLE PRECISION,
    p_predicted_temp_max DOUBLE PRECISION,
    p_actual_temp_min DOUBLE PRECISION,
    p_actual_temp_max DOUBLE PRECISION,
    p_predicted_wind_speed DOUBLE PRECISION,
    p_actual_wind_speed DOUBLE PRECISION,
    p_predicted_humidity INT,
    p_actual_humidity INT,
    p_mae_temp DOUBLE PRECISION,
    p_rmse_temp DOUBLE PRECISION,
    p_bias_temp DOUBLE PRECISION,
    p_hit_event VARCHAR(50),
    p_hit_correct BOOLEAN
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO prediction_accuracy (
        city_id, forecast_date, horizon_day,
        predicted_temp_min, predicted_temp_max,
        actual_temp_min, actual_temp_max,
        predicted_wind_speed, actual_wind_speed,
        predicted_humidity, actual_humidity,
        mae_temp, rmse_temp, bias_temp,
        hit_event, hit_correct
    ) VALUES (
        p_city_id, p_forecast_date, p_horizon_day,
        p_predicted_temp_min, p_predicted_temp_max,
        p_actual_temp_min, p_actual_temp_max,
        p_predicted_wind_speed, p_actual_wind_speed,
        p_predicted_humidity, p_actual_humidity,
        p_mae_temp, p_rmse_temp, p_bias_temp,
        p_hit_event, p_hit_correct
    )
    ON CONFLICT (city_id, forecast_date, horizon_day) DO UPDATE SET
        predicted_temp_min = EXCLUDED.predicted_temp_min,
        predicted_temp_max = EXCLUDED.predicted_temp_max,
        actual_temp_min = EXCLUDED.actual_temp_min,
        actual_temp_max = EXCLUDED.actual_temp_max,
        predicted_wind_speed = EXCLUDED.predicted_wind_speed,
        actual_wind_speed = EXCLUDED.actual_wind_speed,
        predicted_humidity = EXCLUDED.predicted_humidity,
        actual_humidity = EXCLUDED.actual_humidity,
        mae_temp = EXCLUDED.mae_temp,
        rmse_temp = EXCLUDED.rmse_temp,
        bias_temp = EXCLUDED.bias_temp,
        hit_event = EXCLUDED.hit_event,
        hit_correct = EXCLUDED.hit_correct,
        computed_at = CURRENT_TIMESTAMP;
END;
$$;
