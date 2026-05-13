/*
 * fn_update_markov_weights
 *   Actualizează probabilitatea unei tranziții Markov specifice adăugând un delta,
 *   o limitează în intervalul [0.01, 0.99], apoi re-normalizează toate probabilitățile
 *   pentru același grup (climate_zone, season, r_prev, r_curr).
 *   Parametri:
 *     p_climate_zone - zona climatică
 *     p_season       - sezonul
 *     p_r_prev       - regimul anterior
 *     p_r_curr       - regimul curent
 *     p_r_next       - regimul următor
 *     p_delta        - variația de aplicat
 *   Returnează: noua probabilitate a tranziției (NULL dacă nu există)
 */
CREATE OR REPLACE FUNCTION fn_update_markov_weights(
    p_climate_zone VARCHAR,
    p_season VARCHAR,
    p_r_prev INT,
    p_r_curr INT,
    p_r_next INT,
    p_delta DOUBLE PRECISION
) RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
AS $$
DECLARE
    v_new_prob DOUBLE PRECISION;
BEGIN
    -- Aplică delta și limitează probabilitatea în [0.01, 0.99]
    UPDATE markov_transitions
    SET probability = GREATEST(0.01, LEAST(0.99, probability + p_delta))
    WHERE climate_zone = p_climate_zone
      AND season = p_season
      AND r_prev = p_r_prev
      AND r_curr = p_r_curr
      AND r_next = p_r_next
    RETURNING probability INTO v_new_prob;

    -- Dacă tranziția nu există, returnează NULL
    IF v_new_prob IS NULL THEN
        RETURN NULL;
    END IF;

    -- Re-normalizează întregul grup de tranziții
    PERFORM fn_normalize_markov(p_climate_zone, p_season, p_r_prev, p_r_curr);

    RETURN v_new_prob;
END;
$$;
