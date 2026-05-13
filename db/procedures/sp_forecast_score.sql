/*
 * sp_forecast_score
 *   Scorul unei prognoze pe baza voturilor cu ponderare in functie de reputatia userului.
 *   Parametri: p_forecast_id
 */
CREATE OR REPLACE FUNCTION sp_forecast_score(p_forecast_id INTEGER)
RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
AS $$
DECLARE
    v_score DOUBLE PRECISION;
    v_total_weight DOUBLE PRECISION;
BEGIN
    SELECT COALESCE(SUM(weight), 0), COALESCE(SUM(ABS(weight)), 0)
    INTO v_score, v_total_weight
    FROM (
        SELECT
            CASE WHEN v.is_accurate THEN 1.0 ELSE -1.0 END
            * (1.0 + COALESCE(u.reputation, 0.0)) AS weight
        FROM votes v
        JOIN users u ON v.user_id = u.id
        WHERE v.forecast_id = p_forecast_id
    ) w;

    IF v_total_weight = 0 THEN
        RETURN 0;
    END IF;

    RETURN ROUND((v_score / v_total_weight)::numeric, 3);
END;
$$;
