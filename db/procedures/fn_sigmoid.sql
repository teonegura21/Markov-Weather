/*
 * fn_sigmoid
 *   Funcție de apartenență fuzzy sigmoidă.
 *   Parametri: p_x (valoare de intrare), p_center (centru), p_width (lățime),
 *              p_direction ('up', 'down', 'around_zero')
 *   Returnează: scor fuzzy în [0, 1]
 */
CREATE OR REPLACE FUNCTION fn_sigmoid(
    p_x DOUBLE PRECISION,
    p_center DOUBLE PRECISION,
    p_width DOUBLE PRECISION,
    p_direction VARCHAR(20) DEFAULT 'up'
) RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_z DOUBLE PRECISION;
BEGIN
    IF p_width = 0 THEN
        RETURN CASE
            WHEN p_direction = 'up' AND p_x >= p_center THEN 1.0
            WHEN p_direction = 'up' AND p_x < p_center THEN 0.0
            WHEN p_direction = 'down' AND p_x >= p_center THEN 0.0
            WHEN p_direction = 'down' AND p_x < p_center THEN 1.0
            WHEN p_direction = 'around_zero' AND p_x = p_center THEN 1.0
            ELSE 0.0
        END;
    END IF;

    IF p_direction = 'around_zero' THEN
        RETURN EXP(-(POWER(p_x - p_center, 2)) / (2.0 * p_width * p_width));
    END IF;

    v_z := (p_x - p_center) / p_width;

    IF p_direction = 'down' THEN
        RETURN 1.0 / (1.0 + EXP(v_z));
    ELSE
        RETURN 1.0 / (1.0 + EXP(-v_z));
    END IF;
END;
$$;
