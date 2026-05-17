/*
 * sp_generate_forecasts
 *   Genereaza prognoze aleatorii pentru toate orasele pentru un an calendaristic.
 *   Parametri: p_year INTEGER - anul pentru care se genereaza
 */
CREATE OR REPLACE PROCEDURE sp_generate_forecasts(p_year INTEGER)
LANGUAGE plpgsql
AS $$
DECLARE
    v_city RECORD;
    v_day DATE;
    v_temp_min DOUBLE PRECISION;
    v_temp_max DOUBLE PRECISION;
    v_wind DOUBLE PRECISION;
    v_humidity INTEGER;
    v_uv INTEGER;
    v_icon VARCHAR(30);
    v_month INTEGER;
BEGIN
    IF p_year IS NULL OR p_year < 1900 OR p_year > 2100 THEN
        RAISE EXCEPTION 'Anul trebuie să fie între 1900 și 2100, primit: %', p_year;
    END IF;

    FOR v_city IN SELECT id FROM cities LOOP
        v_day := make_date(p_year, 1, 1);
        WHILE EXTRACT(YEAR FROM v_day) = p_year LOOP
            v_month := EXTRACT(MONTH FROM v_day);

            v_temp_min := CASE
                WHEN v_month IN (12, 1, 2) THEN random() * 10 - 10
                WHEN v_month IN (6, 7, 8) THEN random() * 10 + 15
                ELSE random() * 15
            END;

            v_temp_max := v_temp_min + random() * 12 + 3;

            v_wind := random() * 80;

            v_humidity := 30 + floor(random() * 70)::INTEGER;

            v_uv := CASE
                WHEN v_month IN (6, 7, 8) THEN 5 + floor(random() * 6)::INTEGER
                ELSE floor(random() * 6)::INTEGER
            END;

            v_icon := CASE
                WHEN v_humidity > 80 AND v_temp_min < 0 THEN 'snow'
                WHEN v_humidity > 80 AND v_temp_min > 0 THEN 'rain'
                WHEN v_humidity > 60 THEN 'cloudy'
                WHEN v_temp_max > 30 THEN 'sunny'
                ELSE 'partly_cloudy'
            END;

            INSERT INTO forecasts (city_id, date, temp_min, temp_max, wind_speed, icon_type, uv_index, humidity, warning_text)
            VALUES (v_city.id, v_day, ROUND(v_temp_min::numeric, 1), ROUND(v_temp_max::numeric, 1),
                    ROUND(v_wind::numeric, 1), v_icon, v_uv, v_humidity, NULL)
            ON CONFLICT (city_id, date) DO NOTHING;

            v_day := v_day + INTERVAL '1 day';
        END LOOP;
    END LOOP;

    PERFORM sp_update_all_warnings(p_year);
    COMMIT;
END;
$$;
