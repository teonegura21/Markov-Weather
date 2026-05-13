/*
 * sp_classify_similar_cities
 *   Clasifica orasele cu prognoze similare in ultimele N zile.
 *   Parametri: p_city_id (orasul de referinta), p_days (30 zile implicit)
 */
CREATE OR REPLACE FUNCTION sp_classify_similar_cities(
    p_city_id INTEGER,
    p_days INTEGER DEFAULT 30
) RETURNS TABLE(
    oras VARCHAR,
    tara VARCHAR,
    distanta_euclidiana DOUBLE PRECISION,
    temp_min_oras DOUBLE PRECISION,
    temp_max_oras DOUBLE PRECISION,
    umiditate_oras DOUBLE PRECISION,
    viteza_vant_oras DOUBLE PRECISION
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    WITH ref AS (
        SELECT AVG(temp_min) AS tmin, AVG(temp_max) AS tmax,
               AVG(humidity) AS hum, AVG(wind_speed) AS wind
        FROM forecasts
        WHERE city_id = p_city_id AND date >= CURRENT_DATE - p_days
    ),
    others AS (
        SELECT ci.id, ci.name::VARCHAR, co.name::VARCHAR AS tara,
               AVG(f.temp_min) AS tmin, AVG(f.temp_max) AS tmax,
               AVG(f.humidity) AS hum, AVG(f.wind_speed) AS wind
        FROM forecasts f
        JOIN cities ci ON f.city_id = ci.id
        JOIN countries co ON ci.country_id = co.id
        WHERE f.city_id != p_city_id AND f.date >= CURRENT_DATE - p_days
        GROUP BY ci.id, ci.name, co.name
    )
    SELECT
        o.name,
        o.tara,
        ROUND(SQRT(
            POWER(o.tmin - r.tmin, 2) +
            POWER(o.tmax - r.tmax, 2) +
            POWER(o.hum - r.hum, 2) +
            POWER(o.wind - r.wind, 2)
        )::numeric, 2),
        ROUND(o.tmin::numeric, 1),
        ROUND(o.tmax::numeric, 1),
        ROUND(o.hum::numeric, 1),
        ROUND(o.wind::numeric, 1)
    FROM others o, ref r
    ORDER BY SQRT(
        POWER(o.tmin - r.tmin, 2) +
        POWER(o.tmax - r.tmax, 2) +
        POWER(o.hum - r.hum, 2) +
        POWER(o.wind - r.wind, 2)
    );
END;
$$;
