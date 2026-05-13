/*
 * sp_city_rankings
 *   Clasamente ale oraselor: cele mai calde, reci, umede, cu vant, cu avertizari.
 *   Parametri: p_criterion ('hottest', 'coldest', 'windiest', 'most_humid', 'most_warnings', 'most_extreme')
 */
CREATE OR REPLACE FUNCTION sp_city_rankings(
    p_criterion VARCHAR DEFAULT 'hottest',
    p_days INTEGER DEFAULT 30
) RETURNS TABLE(
    pozitie BIGINT,
    oras VARCHAR,
    tara VARCHAR,
    valoare DOUBLE PRECISION,
    unitate VARCHAR
)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_criterion = 'hottest' THEN
        RETURN QUERY
        SELECT ROW_NUMBER() OVER (ORDER BY AVG(f.temp_max) DESC),
               ci.name::VARCHAR, co.name::VARCHAR,
               ROUND(AVG(f.temp_max)::numeric, 1), '°C'::VARCHAR
        FROM forecasts f
        JOIN cities ci ON f.city_id = ci.id
        JOIN countries co ON ci.country_id = co.id
        WHERE f.date >= CURRENT_DATE - p_days
        GROUP BY ci.id, ci.name, co.name
        ORDER BY AVG(f.temp_max) DESC;

    ELSIF p_criterion = 'coldest' THEN
        RETURN QUERY
        SELECT ROW_NUMBER() OVER (ORDER BY AVG(f.temp_min)),
               ci.name::VARCHAR, co.name::VARCHAR,
               ROUND(AVG(f.temp_min)::numeric, 1), '°C'::VARCHAR
        FROM forecasts f
        JOIN cities ci ON f.city_id = ci.id
        JOIN countries co ON ci.country_id = co.id
        WHERE f.date >= CURRENT_DATE - p_days
        GROUP BY ci.id, ci.name, co.name
        ORDER BY AVG(f.temp_min);

    ELSIF p_criterion = 'windiest' THEN
        RETURN QUERY
        SELECT ROW_NUMBER() OVER (ORDER BY AVG(f.wind_speed) DESC),
               ci.name::VARCHAR, co.name::VARCHAR,
               ROUND(AVG(f.wind_speed)::numeric, 1), 'km/h'::VARCHAR
        FROM forecasts f
        JOIN cities ci ON f.city_id = ci.id
        JOIN countries co ON ci.country_id = co.id
        WHERE f.date >= CURRENT_DATE - p_days
        GROUP BY ci.id, ci.name, co.name
        ORDER BY AVG(f.wind_speed) DESC;

    ELSIF p_criterion = 'most_humid' THEN
        RETURN QUERY
        SELECT ROW_NUMBER() OVER (ORDER BY AVG(f.humidity) DESC),
               ci.name::VARCHAR, co.name::VARCHAR,
               ROUND(AVG(f.humidity)::numeric, 1), '%'::VARCHAR
        FROM forecasts f
        JOIN cities ci ON f.city_id = ci.id
        JOIN countries co ON ci.country_id = co.id
        WHERE f.date >= CURRENT_DATE - p_days
        GROUP BY ci.id, ci.name, co.name
        ORDER BY AVG(f.humidity) DESC;

    ELSIF p_criterion = 'most_warnings' THEN
        RETURN QUERY
        SELECT ROW_NUMBER() OVER (ORDER BY COUNT(f.id) FILTER (WHERE f.warning_text IS NOT NULL) DESC),
               ci.name::VARCHAR, co.name::VARCHAR,
               COUNT(f.id) FILTER (WHERE f.warning_text IS NOT NULL)::DOUBLE PRECISION,
               'avertizari'::VARCHAR
        FROM forecasts f
        JOIN cities ci ON f.city_id = ci.id
        JOIN countries co ON ci.country_id = co.id
        WHERE f.date >= CURRENT_DATE - p_days
        GROUP BY ci.id, ci.name, co.name
        ORDER BY COUNT(f.id) FILTER (WHERE f.warning_text IS NOT NULL) DESC;

    ELSIF p_criterion = 'most_extreme' THEN
        RETURN QUERY
        SELECT ROW_NUMBER() OVER (ORDER BY (MAX(f.temp_max) - MIN(f.temp_min)) DESC),
               ci.name::VARCHAR, co.name::VARCHAR,
               ROUND((MAX(f.temp_max) - MIN(f.temp_min))::numeric, 1),
               '°C amplitudine'::VARCHAR
        FROM forecasts f
        JOIN cities ci ON f.city_id = ci.id
        JOIN countries co ON ci.country_id = co.id
        WHERE f.date >= CURRENT_DATE - p_days
        GROUP BY ci.id, ci.name, co.name
        ORDER BY (MAX(f.temp_max) - MIN(f.temp_min)) DESC;
    END IF;
END;
$$;
