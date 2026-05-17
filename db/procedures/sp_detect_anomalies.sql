/*
 * sp_detect_anomalies
 *   Detecteaza anomalii extreme: temperaturi, vant, umiditate, UV.
 *   Parametri: p_city_id (NULL = toate orasele), p_year
 */
CREATE OR REPLACE FUNCTION sp_detect_anomalies(
    p_city_id INTEGER DEFAULT NULL,
    p_year INTEGER DEFAULT NULL
) RETURNS TABLE(
    oras VARCHAR,
    tara VARCHAR,
    data DATE,
    anomalie_temperatura BOOLEAN,
    anomalie_vant BOOLEAN,
    anomalie_umiditate BOOLEAN,
    anomalie_uv BOOLEAN,
    out_temp_min DOUBLE PRECISION,
    out_temp_max DOUBLE PRECISION,
    out_viteza_vant DOUBLE PRECISION,
    out_umiditate INTEGER,
    out_indice_uv INTEGER,
    detalii_anomalie TEXT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        ci.name::VARCHAR,
        co.name::VARCHAR,
        f.date,
        (f.temp_max > avgs.tmax_avg + 2 * avgs.tmax_stddev
         OR f.temp_min < avgs.tmin_avg - 2 * avgs.tmin_stddev),
        f.wind_speed > avgs.wind_avg + 2 * avgs.wind_stddev,
        f.humidity > avgs.hum_avg + 2 * avgs.hum_stddev,
        f.uv_index > avgs.uv_avg + 2 * avgs.uv_stddev,
        f.temp_min, f.temp_max,
        f.wind_speed, f.humidity, f.uv_index,
        CASE
            WHEN f.temp_max > avgs.tmax_avg + 2 * avgs.tmax_stddev
                 OR f.temp_min < avgs.tmin_avg - 2 * avgs.tmin_stddev
            THEN 'Anomalie de temperatura la ' || ci.name || ' pe ' || f.date::TEXT || ': min=' || f.temp_min::TEXT || '°C, max=' || f.temp_max::TEXT || '°C (media lunara: ' || ROUND(avgs.tmin_avg::numeric, 1)::TEXT || '°C / ' || ROUND(avgs.tmax_avg::numeric, 1)::TEXT || '°C)'
            WHEN f.wind_speed > avgs.wind_avg + 2 * avgs.wind_stddev
            THEN 'Anomalie de vant la ' || ci.name || ' - ' || f.wind_speed::TEXT || ' km/h (media: ' || ROUND(avgs.wind_avg::numeric, 1)::TEXT || ')'
            WHEN f.humidity > avgs.hum_avg + 2 * avgs.hum_stddev
            THEN 'Anomalie de umiditate la ' || ci.name || ' - ' || f.humidity::TEXT || '% (media: ' || ROUND(avgs.hum_avg::numeric, 1)::TEXT || '%)'
            WHEN f.uv_index > avgs.uv_avg + 2 * avgs.uv_stddev
            THEN 'Anomalie de indice UV la ' || ci.name || ' - ' || f.uv_index::TEXT || ' (media: ' || ROUND(avgs.uv_avg::numeric, 1)::TEXT || ')'
            ELSE NULL
        END
    FROM forecasts f
    JOIN cities ci ON f.city_id = ci.id
    JOIN countries co ON ci.country_id = co.id
    JOIN LATERAL (
        SELECT AVG(inner_f.temp_min), STDDEV(inner_f.temp_min),
               AVG(inner_f.temp_max), STDDEV(inner_f.temp_max),
               AVG(inner_f.wind_speed), STDDEV(inner_f.wind_speed),
               AVG(inner_f.humidity), STDDEV(inner_f.humidity),
               AVG(inner_f.uv_index), STDDEV(inner_f.uv_index)
        FROM forecasts AS inner_f
        WHERE inner_f.city_id = f.city_id
          AND EXTRACT(MONTH FROM inner_f.date) = EXTRACT(MONTH FROM f.date)
          AND inner_f.date != f.date
    ) avgs(tmin_avg, tmin_stddev, tmax_avg, tmax_stddev,
           wind_avg, wind_stddev, hum_avg, hum_stddev, uv_avg, uv_stddev) ON TRUE
    WHERE (p_city_id IS NULL OR f.city_id = p_city_id)
      AND (p_year IS NULL OR EXTRACT(YEAR FROM f.date) = p_year)
      AND (
        f.temp_max > avgs.tmax_avg + 2 * avgs.tmax_stddev
        OR f.temp_min < avgs.tmin_avg - 2 * avgs.tmin_stddev
        OR f.wind_speed > avgs.wind_avg + 2 * avgs.wind_stddev
        OR f.humidity > avgs.hum_avg + 2 * avgs.hum_stddev
        OR f.uv_index > avgs.uv_avg + 2 * avgs.uv_stddev
      )
    ORDER BY f.date DESC;
END;
$$;
