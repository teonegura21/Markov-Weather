/*
 * sp_comparison_same_day
 *   Compara prognoza curenta cu media aceleiasi zile din alti ani + media sezoniera.
 */
CREATE OR REPLACE FUNCTION sp_comparison_same_day(
    p_city_id INTEGER,
    p_date DATE
) RETURNS TABLE(
    tip_comparatie VARCHAR,
    temp_min_actuala DOUBLE PRECISION,
    temp_max_actuala DOUBLE PRECISION,
    temp_min_medie DOUBLE PRECISION,
    temp_max_medie DOUBLE PRECISION,
    temp_avg_actuala DOUBLE PRECISION,
    temp_avg_medie DOUBLE PRECISION,
    diferenta_temp_min DOUBLE PRECISION,
    diferenta_temp_max DOUBLE PRECISION
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_season_start DATE;
    v_season_end DATE;
    v_month INTEGER := EXTRACT(MONTH FROM p_date);
    v_day INTEGER := EXTRACT(DAY FROM p_date);
BEGIN
    -- same day across years
    RETURN QUERY
    SELECT 'aceeasi_zi_ani_diferiti'::VARCHAR,
           f.temp_min, f.temp_max,
           ROUND(AVG(h.temp_min)::numeric, 1)::DOUBLE PRECISION,
           ROUND(AVG(h.temp_max)::numeric, 1)::DOUBLE PRECISION,
           ROUND(((f.temp_min + f.temp_max) / 2.0)::numeric, 1)::DOUBLE PRECISION,
           ROUND((AVG((h.temp_min + h.temp_max) / 2.0))::numeric, 1)::DOUBLE PRECISION,
           ROUND((f.temp_min - AVG(h.temp_min))::numeric, 1)::DOUBLE PRECISION,
           ROUND((f.temp_max - AVG(h.temp_max))::numeric, 1)::DOUBLE PRECISION
    FROM forecasts f
    CROSS JOIN (
        SELECT temp_min, temp_max FROM forecasts
        WHERE city_id = p_city_id
          AND EXTRACT(MONTH FROM date) = v_month
          AND EXTRACT(DAY FROM date) = v_day
          AND date != p_date
    ) h
    WHERE f.city_id = p_city_id AND f.date = p_date
    GROUP BY f.temp_min, f.temp_max;

    -- seasonal
    SELECT CASE
        WHEN v_month IN (12, 1, 2) THEN make_date(EXTRACT(YEAR FROM p_date)::INTEGER, 12, 1)
        WHEN v_month IN (3, 4, 5) THEN make_date(EXTRACT(YEAR FROM p_DATE)::INTEGER, 3, 1)
        WHEN v_month IN (6, 7, 8) THEN make_date(EXTRACT(YEAR FROM p_DATE)::INTEGER, 6, 1)
        ELSE make_date(EXTRACT(YEAR FROM p_DATE)::INTEGER, 9, 1)
    END INTO v_season_start;
    v_season_end := v_season_start + INTERVAL '2 months' + INTERVAL '29 days';

    RETURN QUERY
    SELECT 'sezonier'::VARCHAR,
           f.temp_min, f.temp_max,
           ROUND(AVG(h.temp_min)::numeric, 1)::DOUBLE PRECISION,
           ROUND(AVG(h.temp_max)::numeric, 1)::DOUBLE PRECISION,
           ROUND(((f.temp_min + f.temp_max) / 2.0)::numeric, 1)::DOUBLE PRECISION,
           ROUND((AVG((h.temp_min + h.temp_max) / 2.0))::numeric, 1)::DOUBLE PRECISION,
           ROUND((f.temp_min - AVG(h.temp_min))::numeric, 1)::DOUBLE PRECISION,
           ROUND((f.temp_max - AVG(h.temp_max))::numeric, 1)::DOUBLE PRECISION
    FROM forecasts f
    CROSS JOIN (
        SELECT temp_min, temp_max FROM forecasts
        WHERE city_id = p_city_id
          AND date BETWEEN v_season_start AND v_season_end
          AND date != p_date
    ) h
    WHERE f.city_id = p_city_id AND f.date = p_date
    GROUP BY f.temp_min, f.temp_max;
END;
$$;
