/*
 * fn_update_hmm_emission
 *   Actualizează probabilitatea de emisie HMM pentru un anumit regim,
 *   o limitează în [0.01, 0.99], apoi re-normalizează întregul array
 *   de probabilități de emisie pentru starea respectivă.
 *   Array-urile PostgreSQL sunt 1-indexate.
 *   Parametri:
 *     p_city_id      - ID-ul orașului
 *     p_state_id     - ID-ul stării ascunse
 *     p_regime_index - indexul regimului în array (1..16)
 *     p_delta        - variația de aplicat
 *   Returnează: noua probabilitate de emisie (NULL dacă starea nu există)
 */
CREATE OR REPLACE FUNCTION fn_update_hmm_emission(
    p_city_id INT,
    p_state_id INT,
    p_regime_index INT,
    p_delta DOUBLE PRECISION
) RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
AS $$
DECLARE
    v_probs DOUBLE PRECISION[];
    v_new_val DOUBLE PRECISION;
    v_sum DOUBLE PRECISION;
    v_i INT;
    v_len INT := 16;
BEGIN
    -- Validează indexul (1-indexat, 16 elemente)
    IF p_regime_index < 1 OR p_regime_index > v_len THEN
        RAISE EXCEPTION 'Indexul regimului trebuie să fie între 1 și 16, primit: %', p_regime_index;
    END IF;

    SELECT emission_probs INTO v_probs
    FROM hidden_states
    WHERE city_id = p_city_id
      AND state_id = p_state_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    -- Dacă array-ul este NULL sau gol, inițializează distribuția uniformă
    IF v_probs IS NULL OR array_length(v_probs, 1) IS NULL THEN
        v_probs := array_fill((1.0 / v_len)::DOUBLE PRECISION, ARRAY[v_len]);
    END IF;

    -- Aplică delta și limitează în [0.01, 0.99]
    v_new_val := GREATEST(0.01, LEAST(0.99, v_probs[p_regime_index] + p_delta));
    v_probs[p_regime_index] := v_new_val;

    -- Calculează suma array-ului
    SELECT COALESCE(SUM(val), 0.0) INTO v_sum
    FROM unnest(v_probs) AS val;

    -- Evită divizarea la zero
    IF v_sum = 0.0 THEN
        v_probs := array_fill((1.0 / v_len)::DOUBLE PRECISION, ARRAY[v_len]);
    ELSE
        FOR v_i IN 1..array_length(v_probs, 1) LOOP
            v_probs[v_i] := v_probs[v_i] / v_sum;
        END LOOP;
    END IF;

    UPDATE hidden_states
    SET emission_probs = v_probs
    WHERE city_id = p_city_id
      AND state_id = p_state_id;

    RETURN v_probs[p_regime_index];
END;
$$;
