/*
 * sp_compute_recipe_scores
 *   Calculează cele 6 scoruri fuzzy pentru detectoarele de fenomene meteorologice
 *   (ceață, furtună convectivă, ciclon, anticiclon, val de căldură, inversiune termică)
 *   și le salvează în tabela weather_vectors.
 *   Parametri: p_city_id
 *   Returnează: VOID
 */
CREATE OR REPLACE FUNCTION sp_compute_recipe_scores(
    p_city_id INTEGER
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_rec RECORD;
    v_fog DOUBLE PRECISION;
    v_thunder DOUBLE PRECISION;
    v_cyclone DOUBLE PRECISION;
    v_anti DOUBLE PRECISION;
    v_heat DOUBLE PRECISION;
    v_inversion DOUBLE PRECISION;
BEGIN
    FOR v_rec IN
        SELECT
            wv.city_id,
            wv.date,
            wv.humidity_avg,
            wv.wind_speed_avg,
            wv.dew_point_spread,
            wv.sunshine_fraction,
            wv.temp_max,
            wv.temp_amplitude,
            wv.gust_factor,
            wv.precip_intensity,
            wv.pressure_mean,
            wv.pressure_trend,
            wv.precipitation_hours,
            wv.temp_trend
        FROM weather_vectors wv
        WHERE wv.city_id = p_city_id
        ORDER BY wv.date
    LOOP
        v_fog := LEAST(
            fn_sigmoid(COALESCE(v_rec.humidity_avg, 60), 88, 5, 'up'),
            fn_rev_sigmoid(COALESCE(v_rec.wind_speed_avg, 10), 8, 3, 'down'),
            fn_rev_sigmoid(COALESCE(v_rec.dew_point_spread, 5), 2.5, 0.8, 'down'),
            fn_rev_sigmoid(COALESCE(v_rec.sunshine_fraction, 0.5), 0.3, 0.15, 'down'),
            fn_seasonal_weight(v_rec.date, 1.0, 0.5, 0.2, 0.9)
        );

        v_thunder := LEAST(
            fn_sigmoid(COALESCE(v_rec.temp_max, 20), 28, 3, 'up'),
            fn_sigmoid(COALESCE(v_rec.temp_amplitude, 8), 13, 3, 'up'),
            fn_sigmoid(COALESCE(v_rec.humidity_avg, 60), 45, 8, 'up'),
            fn_sigmoid(COALESCE(v_rec.gust_factor, 1.5), 2.5, 0.5, 'up'),
            fn_sigmoid(COALESCE(v_rec.precip_intensity, 0), 3, 1.5, 'up'),
            fn_seasonal_weight(v_rec.date, 0.05, 0.5, 1.0, 0.4)
        );

        v_cyclone := LEAST(
            fn_rev_sigmoid(COALESCE(v_rec.pressure_mean, 1013), 1005, 8, 'down'),
            fn_rev_sigmoid(COALESCE(v_rec.pressure_trend, 0), -2, 1.5, 'down'),
            fn_sigmoid(COALESCE(v_rec.wind_speed_avg, 10), 25, 8, 'up'),
            fn_sigmoid(COALESCE(v_rec.humidity_avg, 60), 75, 10, 'up'),
            fn_sigmoid(COALESCE(v_rec.precipitation_hours, 0), 6, 3, 'up'),
            fn_rev_sigmoid(COALESCE(v_rec.precip_intensity, 0), 5, 2, 'down')
        );

        v_anti := LEAST(
            fn_sigmoid(COALESCE(v_rec.pressure_mean, 1013), 1025, 5, 'up'),
            fn_sigmoid(COALESCE(v_rec.pressure_trend, 0), 0, 1.5, 'around_zero'),
            fn_rev_sigmoid(COALESCE(v_rec.wind_speed_avg, 10), 10, 5, 'down'),
            fn_rev_sigmoid(COALESCE(v_rec.humidity_avg, 60), 60, 10, 'down'),
            fn_sigmoid(COALESCE(v_rec.sunshine_fraction, 0.5), 0.7, 0.2, 'up')
        );

        v_heat := LEAST(
            fn_sigmoid(COALESCE(v_rec.temp_max, 20), 35, 2, 'up'),
            fn_sigmoid(COALESCE(v_rec.temp_trend, 0), 0, 1, 'around_zero'),
            fn_sigmoid(COALESCE(v_rec.sunshine_fraction, 0.5), 0.8, 0.1, 'up'),
            fn_sigmoid(COALESCE(v_rec.temp_amplitude, 8), 14, 2, 'up'),
            fn_seasonal_weight(v_rec.date, 0.0, 0.2, 1.0, 0.3)
        );

        v_inversion := LEAST(
            fn_rev_sigmoid(COALESCE(v_rec.temp_amplitude, 8), 4, 1.5, 'down'),
            fn_rev_sigmoid(COALESCE(v_rec.wind_speed_avg, 10), 5, 3, 'down'),
            fn_rev_sigmoid(COALESCE(v_rec.sunshine_fraction, 0.5), 0.15, 0.1, 'down'),
            fn_sigmoid(COALESCE(v_rec.humidity_avg, 60), 82, 8, 'up'),
            fn_seasonal_weight(v_rec.date, 1.0, 0.3, 0.05, 0.8)
        );

        UPDATE weather_vectors
        SET
            fog_score = v_fog,
            thunderstorm_score = v_thunder,
            cyclone_score = v_cyclone,
            anticyclone_score = v_anti,
            heatwave_score = v_heat,
            inversion_score = v_inversion
        WHERE city_id = v_rec.city_id AND date = v_rec.date;
    END LOOP;
END;
$$;
