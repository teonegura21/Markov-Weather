/*
 * sp_identify_error_forecasts
 *   Identifica prognozele cu erori mari - cele cu vot negativ > 50%.
 *   Parametri: p_city_id (NULL = toate orasele), p_threshold (default 50)
 */
CREATE OR REPLACE FUNCTION sp_identify_error_forecasts(
    p_city_id INTEGER DEFAULT NULL,
    p_threshold DOUBLE PRECISION DEFAULT 50.0
) RETURNS TABLE(
    oras VARCHAR,
    data DATE,
    temp_min DOUBLE PRECISION,
    temp_max DOUBLE PRECISION,
    nr_total_voturi BIGINT,
    nr_voturi_negative BIGINT,
    procent_eronat DOUBLE PRECISION,
    avertizare TEXT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        ci.name::VARCHAR,
        f.date,
        f.temp_min,
        f.temp_max,
        COUNT(v.id),
        COUNT(v.id) FILTER (WHERE v.is_accurate = FALSE),
        CASE WHEN COUNT(v.id) > 0
            THEN ROUND((COUNT(v.id) FILTER (WHERE v.is_accurate = FALSE) * 100.0 / COUNT(v.id))::numeric, 1)
            ELSE 0
        END,
        f.warning_text
    FROM forecasts f
    JOIN cities ci ON f.city_id = ci.id
    LEFT JOIN votes v ON f.id = v.forecast_id
    WHERE (p_city_id IS NULL OR f.city_id = p_city_id)
    GROUP BY ci.name, f.date, f.temp_min, f.temp_max, f.warning_text
    HAVING COUNT(v.id) > 0
       AND COUNT(v.id) FILTER (WHERE v.is_accurate = FALSE) * 100.0 / COUNT(v.id) > p_threshold
    ORDER BY COUNT(v.id) FILTER (WHERE v.is_accurate = FALSE) * 100.0 / COUNT(v.id) DESC;
END;
$$;
