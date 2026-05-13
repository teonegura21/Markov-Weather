/*
 * sp_label_regimes
 *   Auto-eticheteaza regimurile meteo pe baza inspectiei centroizilor.
 *   Parametri: p_climate_zone
 *   Returneaza: VOID
 */
CREATE OR REPLACE FUNCTION sp_label_regimes(
    p_climate_zone VARCHAR(50)
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_rec RECORD;
    v_label VARCHAR(200);
    v_desc TEXT;
    v_temp_max DOUBLE PRECISION;
    v_heat DOUBLE PRECISION;
    v_storm DOUBLE PRECISION;
    v_fog DOUBLE PRECISION;
    v_cyclone DOUBLE PRECISION;
    v_pressure DOUBLE PRECISION;
    v_precip DOUBLE PRECISION;
BEGIN
    FOR v_rec IN
        SELECT regime_id, centroid
        FROM weather_regimes
        WHERE climate_zone = p_climate_zone
    LOOP
        v_temp_max := COALESCE(v_rec.centroid[2], 0);
        v_heat := COALESCE(v_rec.centroid[38], 0);
        v_storm := COALESCE(v_rec.centroid[35], 0);
        v_fog := COALESCE(v_rec.centroid[34], 0);
        v_cyclone := COALESCE(v_rec.centroid[36], 0);
        v_pressure := COALESCE(v_rec.centroid[23], 0);
        v_precip := COALESCE(v_rec.centroid[19], 0);

        IF v_heat > 0.6 OR v_temp_max > 1.0 THEN
            v_label := 'Caniculă';
            v_desc := 'Temperaturi extreme și presiune scăzută';
        ELSIF v_storm > 0.5 THEN
            v_label := 'Furtună';
            v_desc := 'Instabilitate atmosferică și vânt puternic';
        ELSIF v_fog > 0.5 THEN
            v_label := 'Ceață densă';
            v_desc := 'Umiditate ridicată și vizibilitate redusă';
        ELSIF v_cyclone > 0.5 AND v_pressure < -0.5 THEN
            v_label := 'Ciclon';
            v_desc := 'Depresiune cu precipitații și vânt';
        ELSIF v_pressure > 0.5 AND v_temp_max < -0.3 THEN
            v_label := 'Anticiclon rece';
            v_desc := 'Presiune ridicată, temperaturi scăzute';
        ELSIF v_pressure > 0.5 THEN
            v_label := 'Anticiclon';
            v_desc := 'Vreme stabilă și presiune ridicată';
        ELSIF v_precip > 0.5 AND v_temp_max < 0 THEN
            v_label := 'Ninsori';
            v_desc := 'Precipitații sub formă de zăpadă';
        ELSIF v_precip > 0.5 THEN
            v_label := 'Ploaie';
            v_desc := 'Precipitații lichide';
        ELSIF v_temp_max < -1.0 THEN
            v_label := 'Iarnă geroasă';
            v_desc := 'Temperaturi foarte scăzute';
        ELSE
            v_label := 'Normal';
            v_desc := 'Condiții meteorologice moderate';
        END IF;

        UPDATE weather_regimes
        SET label_ro = v_label, description_ro = v_desc
        WHERE climate_zone = p_climate_zone AND regime_id = v_rec.regime_id;
    END LOOP;
END;
$$;
