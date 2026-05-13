/*
 * sp_build_markov_tensor
 *   Construiește tensorul de tranziție Markov de ordin 2 condiționat sezonier.
 *   Numără tripleții de regimuri consecutive și normalizează în probabilități.
 *   Populează tabela markov_transitions.
 *   Parametri: p_climate_zone
 *   Returnează: VOID
 */
CREATE OR REPLACE FUNCTION sp_build_markov_tensor(
    p_climate_zone VARCHAR(50)
) RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    DELETE FROM markov_transitions WHERE climate_zone = p_climate_zone;

    INSERT INTO markov_transitions (climate_zone, season, r_prev, r_curr, r_next, count, probability)
    WITH triplets AS (
        SELECT
            fn_get_season(dr1.date) AS season,
            dr1.regime_id AS r_prev,
            dr2.regime_id AS r_curr,
            dr3.regime_id AS r_next,
            COUNT(*) AS cnt
        FROM daily_regimes dr1
        JOIN daily_regimes dr2
            ON dr2.city_id = dr1.city_id
            AND dr2.date = dr1.date + INTERVAL '1 day'
        JOIN daily_regimes dr3
            ON dr3.city_id = dr2.city_id
            AND dr3.date = dr2.date + INTERVAL '1 day'
        WHERE dr1.climate_zone = p_climate_zone
        GROUP BY fn_get_season(dr1.date), dr1.regime_id, dr2.regime_id, dr3.regime_id
    ),
    totals AS (
        SELECT
            season,
            r_prev,
            r_curr,
            SUM(cnt) AS total
        FROM triplets
        GROUP BY season, r_prev, r_curr
    )
    SELECT
        p_climate_zone,
        t.season,
        t.r_prev,
        t.r_curr,
        t.r_next,
        t.cnt,
        t.cnt::DOUBLE PRECISION / NULLIF(tt.total, 0)
    FROM triplets t
    JOIN totals tt
        ON tt.season = t.season
        AND tt.r_prev = t.r_prev
        AND tt.r_curr = t.r_curr;
END;
$$;
