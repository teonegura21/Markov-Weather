/*
 * sp_city_weather_evolution
 *   Evolutia meteo a unui oras pe o perioada (grafic).
 *   Parametri: p_city_id, p_start_date, p_end_date
 */
CREATE OR REPLACE FUNCTION sp_city_weather_evolution(
    p_city_id INTEGER,
    p_start_date DATE,
    p_end_date DATE
) RETURNS TABLE(
    data DATE,
    temp_min DOUBLE PRECISION,
    temp_max DOUBLE PRECISION,
    temp_avg DOUBLE PRECISION,
    viteza_vant DOUBLE PRECISION,
    umiditate INTEGER,
    indice_uv INTEGER,
    pictograma VARCHAR
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        f.date,
        f.temp_min,
        f.temp_max,
        ROUND(((f.temp_min + f.temp_max) / 2.0)::numeric, 1)::DOUBLE PRECISION,
        f.wind_speed,
        f.humidity,
        f.uv_index,
        f.icon_type::VARCHAR
    FROM forecasts f
    WHERE f.city_id = p_city_id
      AND f.date BETWEEN p_start_date AND p_end_date
    ORDER BY f.date;
END;
$$;
