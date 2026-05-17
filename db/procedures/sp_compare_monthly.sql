/*
 * sp_compare_monthly
 *   Compara prognozele unei luni cu media lunara istorica.
 */
CREATE OR REPLACE FUNCTION sp_compare_monthly(
    p_city_id INTEGER,
    p_year INTEGER,
    p_month INTEGER
) RETURNS TABLE(
    zi INTEGER,
    temp_min_actuala DOUBLE PRECISION,
    temp_max_actuala DOUBLE PRECISION,
    temp_min_medie_istorica DOUBLE PRECISION,
    temp_max_medie_istorica DOUBLE PRECISION
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        EXTRACT(DAY FROM f.date)::INTEGER,
        f.temp_min,
        f.temp_max,
        ROUND(AVG(h.temp_min)::numeric, 1)::DOUBLE PRECISION,
        ROUND(AVG(h.temp_max)::numeric, 1)::DOUBLE PRECISION
    FROM forecasts f
    LEFT JOIN forecasts h ON h.city_id = p_city_id
        AND EXTRACT(MONTH FROM h.date) = p_month
        AND EXTRACT(DAY FROM h.date) = EXTRACT(DAY FROM f.date)
        AND EXTRACT(YEAR FROM h.date) != p_year
    WHERE f.city_id = p_city_id
      AND EXTRACT(YEAR FROM f.date) = p_year
      AND EXTRACT(MONTH FROM f.date) = p_month
    GROUP BY f.date, f.temp_min, f.temp_max
    ORDER BY f.date;
END;
$$;
