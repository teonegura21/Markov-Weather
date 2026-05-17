/*
 * sp_daily_forecast_report
 *   Raport complet al prognozei pentru o data si un oras.
 *   Parametri: p_city_id, p_date
 */
CREATE OR REPLACE FUNCTION sp_daily_forecast_report(
    p_city_id INTEGER,
    p_date DATE
) RETURNS TABLE(
    tara VARCHAR, oras VARCHAR, data DATE,
    temp_min DOUBLE PRECISION, temp_max DOUBLE PRECISION,
    temp_avg DOUBLE PRECISION, viteza_vant DOUBLE PRECISION,
    pictograma VARCHAR, indice_uv INTEGER, umiditate INTEGER,
    avertizare TEXT, nr_voturi BIGINT, nr_acurat INTEGER,
    acuratete_procent DOUBLE PRECISION, nr_comentarii BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        co.name::VARCHAR,
        ci.name::VARCHAR,
        f.date,
        f.temp_min,
        f.temp_max,
        ROUND(((f.temp_min + f.temp_max) / 2.0)::numeric, 1)::DOUBLE PRECISION AS temp_avg,
        f.wind_speed,
        f.icon_type::VARCHAR,
        f.uv_index,
        f.humidity,
        f.warning_text,
        COUNT(v.id),
        COUNT(v.id) FILTER (WHERE v.is_accurate = TRUE)::INTEGER,
        CASE WHEN COUNT(v.id) > 0
            THEN ROUND((COUNT(v.id) FILTER (WHERE v.is_accurate = TRUE) * 100.0 / COUNT(v.id))::numeric, 1)::DOUBLE PRECISION
            ELSE 0::DOUBLE PRECISION
        END,
        COUNT(DISTINCT c.id)
    FROM forecasts f
    JOIN cities ci ON f.city_id = ci.id
    JOIN countries co ON ci.country_id = co.id
    LEFT JOIN votes v ON f.id = v.forecast_id
    LEFT JOIN comments c ON f.id = c.forecast_id
    WHERE f.city_id = p_city_id AND f.date = p_date
    GROUP BY co.name, ci.name, f.date, f.temp_min, f.temp_max, f.wind_speed,
             f.icon_type, f.uv_index, f.humidity, f.warning_text;
END;
$$;
