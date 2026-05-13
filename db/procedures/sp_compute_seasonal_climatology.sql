/*
 * sp_compute_seasonal_climatology
 *   Calculează mediile și deviațiile standard per zi calendaristică pentru un oraș.
 *   Populează tabela seasonal_climatology cu statistici agregate pe regim și global.
 *   Parametri: p_city_id
 *   Returnează: VOID
 */
CREATE OR REPLACE FUNCTION sp_compute_seasonal_climatology(
    p_city_id INTEGER
) RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    DELETE FROM seasonal_climatology WHERE city_id = p_city_id;

    INSERT INTO seasonal_climatology (
        city_id, day_of_year, regime_id,
        temp_min_mean, temp_min_std,
        temp_max_mean, temp_max_std,
        wind_speed_mean, humidity_mean, precip_sum_mean,
        sample_count
    )
    SELECT
        p_city_id,
        EXTRACT(DOY FROM f.date)::INT,
        NULL,
        AVG(f.temp_min),
        COALESCE(STDDEV(f.temp_min), 0),
        AVG(f.temp_max),
        COALESCE(STDDEV(f.temp_max), 0),
        AVG(f.wind_speed),
        AVG(f.humidity),
        AVG(COALESCE(f.precipitation_sum, 0)),
        COUNT(*)
    FROM forecasts f
    WHERE f.city_id = p_city_id
    GROUP BY EXTRACT(DOY FROM f.date);
END;
$$;
