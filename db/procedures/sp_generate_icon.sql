/*
 * sp_generate_icon
 *   Genereaza tipul de pictograma pe baza parametrilor meteo.
 *   Parametri: p_temp, p_humidity, p_wind_speed, p_uv_index
 *   Returneaza: VARCHAR - tipul pictogramei
 */
CREATE OR REPLACE FUNCTION sp_generate_icon(
    p_temp DOUBLE PRECISION,
    p_humidity INTEGER,
    p_wind_speed DOUBLE PRECISION,
    p_uv_index INTEGER
) RETURNS VARCHAR
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_humidity > 85 AND p_temp < 0 THEN
        RETURN 'snow';
    ELSIF p_humidity > 80 AND p_wind_speed > 50 THEN
        RETURN 'storm';
    ELSIF p_humidity > 70 THEN
        RETURN 'rain';
    ELSIF p_humidity > 50 AND p_wind_speed > 30 THEN
        RETURN 'cloudy_windy';
    ELSIF p_humidity > 50 THEN
        RETURN 'cloudy';
    ELSIF p_temp > 30 AND p_uv_index > 7 THEN
        RETURN 'sunny_hot';
    ELSIF p_temp > 20 THEN
        RETURN 'sunny';
    ELSE
        RETURN 'partly_cloudy';
    END IF;
END;
$$;
