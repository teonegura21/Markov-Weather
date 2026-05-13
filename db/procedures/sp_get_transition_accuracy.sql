/*
 * fn_get_transition_accuracy
 *   Calculează acuratețea medie (1 - MAE/20) pentru predicțiile care au folosit
 *   o anumită tranziție Markov (r_prev -> r_curr -> r_next) într-un sezon
 *   și zonă climatică date, în ultimele p_days_back zile.
 *   Parametri:
 *     p_climate_zone - zona climatică
 *     p_season       - sezonul
 *     p_r_prev       - regimul anterior
 *     p_r_curr       - regimul curent
 *     p_r_next       - regimul următor
 *     p_days_back    - numărul de zile în urmă
 *   Returnează: acuratețea medie (DOUBLE PRECISION); 0.0 dacă nu există date
 */
CREATE OR REPLACE FUNCTION fn_get_transition_accuracy(
    p_climate_zone VARCHAR,
    p_season VARCHAR,
    p_r_prev INT,
    p_r_curr INT,
    p_r_next INT,
    p_days_back INT
) RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
AS $$
DECLARE
    v_avg_accuracy DOUBLE PRECISION;
BEGIN
    WITH transitions AS (
        SELECT
            dr.city_id,
            dr.date AS regime_date,
            LAG(dr.regime_id) OVER (PARTITION BY dr.city_id ORDER BY dr.date) AS prev_regime,
            dr.regime_id AS curr_regime,
            LEAD(dr.regime_id) OVER (PARTITION BY dr.city_id ORDER BY dr.date) AS next_regime,
            fn_get_season(dr.date) AS season
        FROM daily_regimes dr
        WHERE dr.climate_zone = p_climate_zone
          AND dr.date >= CURRENT_DATE - p_days_back
    )
    SELECT COALESCE(AVG(GREATEST(0.0, 1.0 - (pa.mae_temp / 20.0))), 0.0)
    INTO v_avg_accuracy
    FROM transitions t
    JOIN prediction_accuracy pa
        ON pa.city_id = t.city_id
       AND pa.forecast_date = t.regime_date
    WHERE t.prev_regime = p_r_prev
      AND t.curr_regime = p_r_curr
      AND t.next_regime = p_r_next
      AND t.season = p_season;

    RETURN v_avg_accuracy;
END;
$$;
