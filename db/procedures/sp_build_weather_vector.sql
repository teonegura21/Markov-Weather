/*
 * sp_build_weather_vector
 *   Construiește vectorul meteo 25D + derivate temporale pentru toate zilele unui oraș.
 *   Populează tabela weather_vectors pornind de la datele brute din forecasts.
 *   Parametri: p_city_id
 *   Returnează: VOID
 */
CREATE OR REPLACE FUNCTION sp_build_weather_vector(
    p_city_id INTEGER
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_rec RECORD;
    v_prev1 RECORD;
    v_prev2 RECORD;
    v_temp_avg DOUBLE PRECISION;
    v_dew_point DOUBLE PRECISION;
    v_pressure_mean DOUBLE PRECISION;
BEGIN
    DELETE FROM weather_vectors WHERE city_id = p_city_id;

    FOR v_rec IN
        SELECT
            f.city_id,
            f.date,
            f.temp_min,
            f.temp_max,
            f.humidity,
            f.wind_speed,
            f.uv_index,
            f.pressure_mean,
            f.pressure_trend,
            f.precipitation_sum,
            f.precipitation_hours,
            f.sunshine_hours,
            f.dew_point
        FROM forecasts f
        WHERE f.city_id = p_city_id
        ORDER BY f.date
    LOOP
        v_temp_avg := (v_rec.temp_min + v_rec.temp_max) / 2.0;
        v_dew_point := COALESCE(v_rec.dew_point, fn_dew_point(v_rec.temp_min, v_rec.humidity::DOUBLE PRECISION));
        v_pressure_mean := COALESCE(v_rec.pressure_mean, 1013.25);

        SELECT * INTO v_prev1
        FROM weather_vectors
        WHERE city_id = p_city_id AND date = v_rec.date - INTERVAL '1 day';

        SELECT * INTO v_prev2
        FROM weather_vectors
        WHERE city_id = p_city_id AND date = v_rec.date - INTERVAL '2 days';

        INSERT INTO weather_vectors (
            city_id, date,
            temp_min, temp_max, temp_avg, temp_amplitude, temp_trend,
            humidity_min, humidity_max, humidity_avg, dew_point_min, dew_point_spread,
            wind_speed_avg, wind_speed_max, gust_factor, wind_persistence,
            sunshine_hours, sunshine_fraction, uv_index_max, cloud_cover_proxy,
            precipitation_sum, precip_intensity, precipitation_hours, snow_depth,
            pressure_mean, pressure_trend, pressure_range,
            delta1_temp_avg, delta2_temp_avg,
            delta1_humidity_avg, delta2_humidity_avg,
            delta1_pressure_mean, delta2_pressure_mean,
            delta1_wind_speed_avg, delta2_wind_speed_avg
        ) VALUES (
            v_rec.city_id, v_rec.date,
            v_rec.temp_min, v_rec.temp_max, v_temp_avg,
            v_rec.temp_max - v_rec.temp_min,
            COALESCE(v_temp_avg - v_prev1.temp_avg, 0),
            v_rec.humidity, v_rec.humidity, v_rec.humidity,
            v_dew_point,
            v_rec.temp_min - v_dew_point,
            v_rec.wind_speed, v_rec.wind_speed, 1.0, 0,
            COALESCE(v_rec.sunshine_hours, 0),
            CASE WHEN v_rec.sunshine_hours IS NOT NULL THEN LEAST(1.0, GREATEST(0.0, v_rec.sunshine_hours / 12.0)) ELSE NULL END,
            v_rec.uv_index,
            CASE WHEN v_rec.sunshine_hours IS NOT NULL THEN 1.0 - LEAST(1.0, GREATEST(0.0, v_rec.sunshine_hours / 12.0)) ELSE NULL END,
            COALESCE(v_rec.precipitation_sum, 0),
            CASE WHEN COALESCE(v_rec.precipitation_hours, 0) > 0 THEN COALESCE(v_rec.precipitation_sum, 0) / v_rec.precipitation_hours ELSE 0 END,
            COALESCE(v_rec.precipitation_hours, 0),
            CASE WHEN v_rec.temp_max < 0 THEN COALESCE(v_rec.precipitation_sum, 0) * 10 ELSE 0 END,
            v_pressure_mean,
            COALESCE(v_rec.pressure_trend, COALESCE(v_pressure_mean - v_prev1.pressure_mean, 0)),
            0,
            CASE WHEN v_prev1.temp_avg IS NOT NULL THEN v_temp_avg - v_prev1.temp_avg ELSE NULL END,
            CASE WHEN v_prev2.temp_avg IS NOT NULL THEN v_temp_avg - v_prev2.temp_avg ELSE NULL END,
            CASE WHEN v_prev1.humidity_avg IS NOT NULL THEN v_rec.humidity - v_prev1.humidity_avg ELSE NULL END,
            CASE WHEN v_prev2.humidity_avg IS NOT NULL THEN v_rec.humidity - v_prev2.humidity_avg ELSE NULL END,
            CASE WHEN v_prev1.pressure_mean IS NOT NULL THEN v_pressure_mean - v_prev1.pressure_mean ELSE NULL END,
            CASE WHEN v_prev2.pressure_mean IS NOT NULL THEN v_pressure_mean - v_prev2.pressure_mean ELSE NULL END,
            CASE WHEN v_prev1.wind_speed_avg IS NOT NULL THEN v_rec.wind_speed - v_prev1.wind_speed_avg ELSE NULL END,
            CASE WHEN v_prev2.wind_speed_avg IS NOT NULL THEN v_rec.wind_speed - v_prev2.wind_speed_avg ELSE NULL END
        );
    END LOOP;
END;
$$;
