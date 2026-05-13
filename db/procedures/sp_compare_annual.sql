/*
 * sp_compare_annual
 *   Compara media anuala cu media tuturor anilor (temperaturile min, max, avg).
 */
CREATE OR REPLACE FUNCTION sp_compare_annual(p_city_id INTEGER, p_year INTEGER)
RETURNS TABLE(
    tip VARCHAR,
    medie_an_selectat_temp_min DOUBLE PRECISION,
    medie_an_selectat_temp_max DOUBLE PRECISION,
    medie_an_selectat_temp_avg DOUBLE PRECISION,
    medie_istorica_temp_min DOUBLE PRECISION,
    medie_istorica_temp_max DOUBLE PRECISION,
    medie_istorica_temp_avg DOUBLE PRECISION
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT 'anual'::VARCHAR,
           ROUND(AVG(f.temp_min)::numeric, 1),
           ROUND(AVG(f.temp_max)::numeric, 1),
           ROUND(AVG((f.temp_min + f.temp_max) / 2.0)::numeric, 1),
           ROUND(AVG(h.temp_min)::numeric, 1),
           ROUND(AVG(h.temp_max)::numeric, 1),
           ROUND(AVG((h.temp_min + h.temp_max) / 2.0)::numeric, 1)
    FROM forecasts f
    CROSS JOIN (
        SELECT temp_min, temp_max FROM forecasts
        WHERE city_id = p_city_id AND EXTRACT(YEAR FROM date) != p_year
    ) h
    WHERE f.city_id = p_city_id AND EXTRACT(YEAR FROM f.date) = p_year
    GROUP BY EXTRACT(YEAR FROM f.date);
END;
$$;
