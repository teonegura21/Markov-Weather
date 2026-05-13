/*
 * fn_rev_sigmoid
 *   Complementul funcției sigmoid: 1 - fn_sigmoid(x).
 *   Parametri: p_x, p_center, p_width, p_direction
 *   Returnează: scor fuzzy în [0, 1]
 */
CREATE OR REPLACE FUNCTION fn_rev_sigmoid(
    p_x DOUBLE PRECISION,
    p_center DOUBLE PRECISION,
    p_width DOUBLE PRECISION,
    p_direction VARCHAR(20) DEFAULT 'up'
) RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    RETURN 1.0 - fn_sigmoid(p_x, p_center, p_width, p_direction);
END;
$$;
