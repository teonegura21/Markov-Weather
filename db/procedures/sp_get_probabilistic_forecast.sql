/*
 * sp_get_probabilistic_forecast
 *   Returneaza rezultatele cached ale simularii Monte Carlo pentru un oras si data.
 *   Parametri: p_city_id, p_date
 *   Returneaza: SETOF monte_carlo_predictions
 */
CREATE OR REPLACE FUNCTION sp_get_probabilistic_forecast(
    p_city_id INTEGER,
    p_date DATE
) RETURNS SETOF monte_carlo_predictions
LANGUAGE sql
STABLE
AS $$
    SELECT *
    FROM monte_carlo_predictions
    WHERE city_id = p_city_id AND forecast_date = p_date
    ORDER BY generated_at DESC
    LIMIT 1;
$$;
