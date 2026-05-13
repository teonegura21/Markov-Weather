/*
 * fn_get_season
 *   Determină sezonul calendaristic pentru o dată dată.
 *   Parametri: p_date
 *   Returnează: 'winter', 'spring', 'summer', 'autumn'
 */
CREATE OR REPLACE FUNCTION fn_get_season(
    p_date DATE
) RETURNS VARCHAR(10)
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_month INTEGER;
BEGIN
    v_month := EXTRACT(MONTH FROM p_date);
    IF v_month IN (12, 1, 2) THEN
        RETURN 'winter';
    ELSIF v_month IN (3, 4, 5) THEN
        RETURN 'spring';
    ELSIF v_month IN (6, 7, 8) THEN
        RETURN 'summer';
    ELSE
        RETURN 'autumn';
    END IF;
END;
$$;
