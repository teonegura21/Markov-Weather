/*
 * sp_predict_week
 *   Prezice prognoza pentru urmatoarele 7-10 zile pe baza mediei aceleiasi zile
 *   din anii trecuti + tendinta ultimelor 7 zile.
 *   Daca nu exista date istorice, foloseste doar ultimele 7 zile si extrapoleaza.
 */
CREATE OR REPLACE FUNCTION sp_predict_week(
    p_city_id INTEGER,
    p_start_date DATE,
    p_days INTEGER DEFAULT 7
) RETURNS TABLE(
    zi DATE,
    temp_min_prezis DOUBLE PRECISION,
    temp_max_prezis DOUBLE PRECISION,
    temp_avg_prezis DOUBLE PRECISION,
    viteza_vant_prezisa DOUBLE PRECISION,
    umiditate_prezisa INTEGER,
    indice_uv_prezis INTEGER,
    pictograma_prezisa VARCHAR,
    incredere DOUBLE PRECISION
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_i INTEGER;
    v_target DATE;
    v_month INTEGER;
    v_day INTEGER;
    v_years_count INTEGER;
    v_last_7_tmin DOUBLE PRECISION;
    v_last_7_tmax DOUBLE PRECISION;
    v_last_7_wind DOUBLE PRECISION;
    v_last_7_hum DOUBLE PRECISION;
    v_last_7_uv DOUBLE PRECISION;
    v_hist_tmin DOUBLE PRECISION;
    v_hist_tmax DOUBLE PRECISION;
    v_hist_wind DOUBLE PRECISION;
    v_hist_hum DOUBLE PRECISION;
    v_hist_uv DOUBLE PRECISION;
    v_has_historical BOOLEAN;
    v_total_days INTEGER;
BEGIN
    SELECT COUNT(*) > 0 INTO v_has_historical
    FROM forecasts
    WHERE city_id = p_city_id AND date < p_start_date;

    SELECT AVG(temp_min), AVG(temp_max), AVG(wind_speed),
           AVG(humidity), AVG(uv_index)
    INTO v_last_7_tmin, v_last_7_tmax, v_last_7_wind, v_last_7_hum, v_last_7_uv
    FROM (
        SELECT temp_min, temp_max, wind_speed, humidity, uv_index
        FROM forecasts
        WHERE city_id = p_city_id AND date < p_start_date
        ORDER BY date DESC
        LIMIT 7
    ) recent;

    IF v_last_7_tmin IS NULL THEN
        v_last_7_tmin := 5;  v_last_7_tmax := 15;
        v_last_7_wind := 10; v_last_7_hum := 60; v_last_7_uv := 3;
    END IF;

    SELECT COUNT(*) INTO v_total_days
    FROM forecasts WHERE city_id = p_city_id;

    FOR v_i IN 1..p_days LOOP
        v_target := p_start_date + (v_i - 1);
        v_month := EXTRACT(MONTH FROM v_target);
        v_day := EXTRACT(DAY FROM v_target);

        SELECT COUNT(DISTINCT EXTRACT(YEAR FROM date))
        INTO v_years_count
        FROM forecasts
        WHERE city_id = p_city_id
          AND EXTRACT(MONTH FROM date) = v_month
          AND EXTRACT(DAY FROM date) = v_day
          AND date < p_start_date;

        SELECT
            COALESCE(AVG(temp_min), v_last_7_tmin),
            COALESCE(AVG(temp_max), v_last_7_tmax),
            COALESCE(AVG(wind_speed), v_last_7_wind),
            COALESCE(AVG(humidity), v_last_7_hum),
            COALESCE(AVG(uv_index), v_last_7_uv)
        INTO v_hist_tmin, v_hist_tmax, v_hist_wind, v_hist_hum, v_hist_uv
        FROM forecasts
        WHERE city_id = p_city_id
          AND EXTRACT(MONTH FROM date) = v_month
          AND EXTRACT(DAY FROM date) = v_day
          AND date < p_start_date;

        RETURN QUERY
        SELECT v_target,
               ROUND(v_hist_tmin::numeric, 1),
               ROUND(v_hist_tmax::numeric, 1),
               ROUND(((v_hist_tmin + v_hist_tmax) / 2.0)::numeric, 1),
               ROUND(v_hist_wind::numeric, 1),
               ROUND(v_hist_hum)::INTEGER,
               ROUND(v_hist_uv)::INTEGER,
               sp_generate_icon(v_hist_tmax, ROUND(v_hist_hum)::INTEGER, v_hist_wind, ROUND(v_hist_uv)::INTEGER)::VARCHAR,
               CASE
                   WHEN v_total_days > 365 THEN 0.9
                   WHEN v_total_days > 30 THEN 0.5
                   ELSE 0.2
               END;
    END LOOP;
END;
$$;
