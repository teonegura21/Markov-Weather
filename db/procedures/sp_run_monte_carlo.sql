/*
 * sp_run_monte_carlo
 *   Ruleaza o simulare Monte Carlo simplificata direct in baza de date.
 *   Foloseste tensorul Markov si climatologia sezoniera.
 *   Parametri: p_city_id, p_start_date, p_days, p_trajectories
 *   Returneaza: VOID
 */
CREATE OR REPLACE FUNCTION sp_run_monte_carlo(
    p_city_id INTEGER,
    p_start_date DATE,
    p_days INTEGER,
    p_trajectories INTEGER DEFAULT 1000
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_prev INT := 0;
    v_curr INT := 0;
    v_next INT;
    v_season VARCHAR(10);
    v_rnd DOUBLE PRECISION;
    v_cum DOUBLE PRECISION;
    v_rec RECORD;
    v_t INT;
    v_temp_min DOUBLE PRECISION;
    v_temp_max DOUBLE PRECISION;
    v_wind DOUBLE PRECISION;
    v_humidity DOUBLE PRECISION;
BEGIN
    -- Initializare ultime 2 regimuri
    SELECT regime_id INTO v_curr
    FROM daily_regimes WHERE city_id = p_city_id AND date < p_start_date ORDER BY date DESC LIMIT 1;
    SELECT regime_id INTO v_prev
    FROM daily_regimes WHERE city_id = p_city_id AND date < p_start_date ORDER BY date DESC LIMIT 1 OFFSET 1;
    
    IF v_curr IS NULL THEN v_curr := 0; END IF;
    IF v_prev IS NULL THEN v_prev := 0; END IF;

    DELETE FROM monte_carlo_predictions
    WHERE city_id = p_city_id AND forecast_date >= p_start_date AND forecast_date < p_start_date + p_days;

    FOR v_t IN 1..p_days LOOP
        v_season := fn_get_season(p_start_date + v_t - 1);

        v_rnd := random();
        v_cum := 0;
        v_next := v_curr;

        FOR v_rec IN
            SELECT r_next, probability FROM markov_transitions
            WHERE climate_zone = 'temperate' AND season = v_season AND r_prev = v_prev AND r_curr = v_curr
            ORDER BY probability DESC
        LOOP
            v_cum := v_cum + v_rec.probability;
            IF v_rnd <= v_cum THEN
                v_next := v_rec.r_next;
                EXIT;
            END IF;
        END LOOP;

        SELECT 
            COALESCE(temp_min_mean, 0),
            COALESCE(temp_max_mean, 0),
            COALESCE(wind_speed_mean, 0),
            COALESCE(humidity_mean, 0)
        INTO v_temp_min, v_temp_max, v_wind, v_humidity
        FROM seasonal_climatology
        WHERE city_id = p_city_id AND day_of_year = EXTRACT(DOY FROM p_start_date + v_t - 1)
        LIMIT 1;

        INSERT INTO monte_carlo_predictions (
            city_id, forecast_date, horizon_day,
            temp_min_p50, temp_max_p50, wind_speed_p50, humidity_p50
        ) VALUES (
            p_city_id, p_start_date + v_t - 1, v_t,
            v_temp_min, v_temp_max, v_wind, v_humidity
        );

        v_prev := v_curr;
        v_curr := v_next;
    END LOOP;
END;
$$;
