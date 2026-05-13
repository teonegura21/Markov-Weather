/*
 * sp_update_all_warnings
 *   Genereaza automata avertizarile meteo pe baza pragurilor:
 *     - furtuna: umiditate > 85% si vant > 60 km/h
 *     - canicula: temperatura > 38°C
 *     - ger: temperatura < -15°C
 *     - ploaie torentiala: umiditate > 90%
 *     - vant puternic: vant > 70 km/h
 *   Parametri: p_year INTEGER - anul pentru care se actualizeaza
 */
CREATE OR REPLACE PROCEDURE sp_update_all_warnings(p_year INTEGER)
LANGUAGE plpgsql
AS $$
DECLARE
    v_f RECORD;
    v_warning TEXT;
BEGIN
    FOR v_f IN
        SELECT id, temp_min, temp_max, wind_speed, humidity, uv_index
        FROM forecasts
        WHERE EXTRACT(YEAR FROM date) = p_year
    LOOP
        v_warning := NULL;

        IF v_f.humidity > 85 AND v_f.wind_speed > 60 THEN
            v_warning := 'AVERTIZARE FURTUNA: umiditate foarte mare si vant puternic. Recomandam a se sta in casa.';
        ELSIF v_f.temp_max > 38 THEN
            v_warning := 'AVERTIZARE CANICULA: temperaturi extreme. Evitati expunerea la soare si hidratati-va corespunzator.';
        ELSIF v_f.temp_min < -15 THEN
            v_warning := 'AVERTIZARE GER: temperaturi extrem de scazute. Imbracaminte groasa si evitati deplasarile lungi.';
        ELSIF v_f.humidity > 90 THEN
            v_warning := 'AVERTIZARE PLOTOIE TORENTIALA: umiditate foarte mare. Luati umbrela si evitati zonele inundabile.';
        ELSIF v_f.wind_speed > 70 THEN
            v_warning := 'AVERTIZARE VANT PUTERNIC: vant peste 70 km/h. Evitati deplasarile si parcati in siguranta.';
        END IF;

        UPDATE forecasts SET warning_text = v_warning, updated_at = CURRENT_TIMESTAMP
        WHERE id = v_f.id;
    END LOOP;
END;
$$;
