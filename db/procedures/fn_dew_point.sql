/*
 * fn_dew_point
 *   Calculează punctul de rouă folosind formula de aproximare Magnus.
 *   Parametri: p_temp (temperatura în °C), p_humidity (umiditatea relativă în %)
 *   Returnează: punctul de rouă în °C
 */
CREATE OR REPLACE FUNCTION fn_dew_point(
    p_temp DOUBLE PRECISION,
    p_humidity DOUBLE PRECISION
) RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_gamma DOUBLE PRECISION;
BEGIN
    v_gamma := LN(p_humidity / 100.0) + (17.27 * p_temp) / (237.3 + p_temp);
    RETURN (237.3 * v_gamma) / (17.27 - v_gamma);
END;
$$;
