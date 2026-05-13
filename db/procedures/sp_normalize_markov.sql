/*
 * fn_normalize_markov
 *   Normalizează probabilitățile tranzițiilor Markov pentru un grup specific
 *   (climate_zone, season, r_prev, r_curr) astfel încât suma lor să fie 1.0.
 *   Gestionează cazul de divizare la zero sau grup gol.
 *   Parametri:
 *     p_climate_zone - zona climatică
 *     p_season       - sezonul
 *     p_r_prev       - regimul anterior
 *     p_r_curr       - regimul curent
 *   Returnează: VOID
 */
CREATE OR REPLACE FUNCTION fn_normalize_markov(
    p_climate_zone VARCHAR,
    p_season VARCHAR,
    p_r_prev INT,
    p_r_curr INT
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_total DOUBLE PRECISION;
    v_count INT;
BEGIN
    SELECT COALESCE(SUM(probability), 0.0), COUNT(*)
    INTO v_total, v_count
    FROM markov_transitions
    WHERE climate_zone = p_climate_zone
      AND season = p_season
      AND r_prev = p_r_prev
      AND r_curr = p_r_curr;

    -- Evită divizarea la zero sau grupul gol
    IF v_total = 0.0 OR v_count = 0 THEN
        RETURN;
    END IF;

    UPDATE markov_transitions
    SET probability = probability / v_total
    WHERE climate_zone = p_climate_zone
      AND season = p_season
      AND r_prev = p_r_prev
      AND r_curr = p_r_curr;
END;
$$;
