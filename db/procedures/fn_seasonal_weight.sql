/*
 * fn_seasonal_weight
 *   Returnează o pondere sezonieră pentru o dată calendaristică.
 *   Parametri: p_date, p_winter, p_spring, p_summer, p_autumn
 *   Returnează: ponderea corespunzătoare sezonului
 */
CREATE OR REPLACE FUNCTION fn_seasonal_weight(
    p_date DATE,
    p_winter DOUBLE PRECISION,
    p_spring DOUBLE PRECISION,
    p_summer DOUBLE PRECISION,
    p_autumn DOUBLE PRECISION
) RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_month INTEGER;
BEGIN
    v_month := EXTRACT(MONTH FROM p_date);
    IF v_month IN (12, 1, 2) THEN
        RETURN p_winter;
    ELSIF v_month IN (3, 4, 5) THEN
        RETURN p_spring;
    ELSIF v_month IN (6, 7, 8) THEN
        RETURN p_summer;
    ELSE
        RETURN p_autumn;
    END IF;
END;
$$;
