-- Migration 016: Seed users, votes, comments, and structural_zeros
-- Ensures all core tables have at least 15 records for professor requirement.

-- ============================================================
-- Users (15 records)
-- ============================================================
INSERT INTO users (username, password_hash, reputation, created_at) VALUES
('ana_maria', 'hash1', 0.85, CURRENT_TIMESTAMP - INTERVAL '90 days'),
('bogdan_ion', 'hash2', 0.72, CURRENT_TIMESTAMP - INTERVAL '85 days'),
('cristina_pop', 'hash3', 0.91, CURRENT_TIMESTAMP - INTERVAL '80 days'),
('daniel_radu', 'hash4', 0.65, CURRENT_TIMESTAMP - INTERVAL '75 days'),
('elena_vasile', 'hash5', 0.78, CURRENT_TIMESTAMP - INTERVAL '70 days'),
('florin_matei', 'hash6', 0.88, CURRENT_TIMESTAMP - INTERVAL '65 days'),
('georgiana_dum', 'hash7', 0.55, CURRENT_TIMESTAMP - INTERVAL '60 days'),
('horia_stan', 'hash8', 0.93, CURRENT_TIMESTAMP - INTERVAL '55 days'),
('ioana_toma', 'hash9', 0.67, CURRENT_TIMESTAMP - INTERVAL '50 days'),
('julian_neagu', 'hash10', 0.81, CURRENT_TIMESTAMP - INTERVAL '45 days'),
('karina_luca', 'hash11', 0.74, CURRENT_TIMESTAMP - INTERVAL '40 days'),
('laurentiu_buc', 'hash12', 0.69, CURRENT_TIMESTAMP - INTERVAL '35 days'),
('mihaela_cris', 'hash13', 0.95, CURRENT_TIMESTAMP - INTERVAL '30 days'),
('nicolae_drag', 'hash14', 0.58, CURRENT_TIMESTAMP - INTERVAL '25 days'),
('olivia_marcu', 'hash15', 0.87, CURRENT_TIMESTAMP - INTERVAL '20 days')
ON CONFLICT (username) DO NOTHING;

-- ============================================================
-- Comments (15 records) — need at least one forecast to exist
-- ============================================================
DO $$
DECLARE
    v_forecast_id INTEGER;
    v_user_id INTEGER;
    v_contents TEXT[] := ARRAY[
        'Prognoza a fost foarte precisa astazi!',
        'A plouat exact cum s-a prezis. Bravo!',
        'Temperatura a fost usor subestimata.',
        'Vantul a fost mai puternic decat in prognoza.',
        'Excelenta acuratete pentru weekend.',
        'Prognoza s-a potrivit perfect cu realitatea.',
        'Avertizarea de furtuna a fost utila.',
        'Umiditatea a fost mai ridicata decat estimat.',
        'Soare toata ziua, exact cum s-a spus.',
        'Ninsoarea a venit cu o ora mai devreme.',
        'Indicele UV a fost corect estimat.',
        'Cer variabil, prognoza a fost in linii mari corecta.',
        'Ploaie usoara conform prognozei.',
        'Temperaturile maxime au fost respectate.',
        'O prognoza foarte buna pentru aceasta perioada.'
    ];
    v_idx INTEGER := 1;
BEGIN
    SELECT id INTO v_forecast_id FROM forecasts WHERE city_id = 1 ORDER BY date DESC LIMIT 1;

    IF v_forecast_id IS NOT NULL THEN
        FOR v_user_id IN SELECT id FROM users ORDER BY id LIMIT 15 LOOP
            INSERT INTO comments (user_id, forecast_id, comment_text, created_at)
            VALUES (v_user_id, v_forecast_id, v_contents[v_idx], CURRENT_TIMESTAMP - INTERVAL '1 day' * (v_idx % 30))
            ON CONFLICT DO NOTHING;
            v_idx := v_idx + 1;
        END LOOP;
    END IF;
END $$;

-- ============================================================
-- Votes (15 records) — users vote on forecasts
-- ============================================================
DO $$
DECLARE
    v_user_id INTEGER;
    v_forecast_id INTEGER;
    v_counter INTEGER := 0;
    v_forecast_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_forecast_count FROM forecasts;

    IF v_forecast_count = 0 THEN
        -- Nu exista prognoze inca; voturile vor fi create dupa import
        RETURN;
    END IF;

    FOR v_user_id IN SELECT id FROM users ORDER BY id LIMIT 15 LOOP
        -- Pick any available forecast, cycling through them
        SELECT id INTO v_forecast_id
        FROM forecasts
        ORDER BY id
        LIMIT 1 OFFSET (v_counter % v_forecast_count);

        IF v_forecast_id IS NOT NULL THEN
            INSERT INTO votes (user_id, forecast_id, is_accurate, created_at)
            VALUES (v_user_id, v_forecast_id, (v_user_id % 3 != 0), CURRENT_TIMESTAMP - INTERVAL '1 day' * (v_user_id % 30))
            ON CONFLICT (user_id, forecast_id) DO NOTHING;
            v_counter := v_counter + 1;
        END IF;
    END LOOP;
END $$;

-- ============================================================
-- Forecast log (15 records) — simulate forecast updates
-- ============================================================
DO $$
DECLARE
    v_forecast_id INTEGER;
    v_counter INTEGER := 0;
    v_old JSONB;
    v_new JSONB;
BEGIN
    FOR v_forecast_id IN SELECT id FROM forecasts WHERE city_id = 1 ORDER BY date DESC LIMIT 15 LOOP
        SELECT jsonb_build_object('temp_min', temp_min, 'temp_max', temp_max, 'icon_type', icon_type),
               jsonb_build_object('temp_min', temp_min - 1.5, 'temp_max', temp_max + 1.0, 'icon_type', 'cloudy')
        INTO v_old, v_new
        FROM forecasts WHERE id = v_forecast_id;

        INSERT INTO forecast_log (forecast_id, change_type, old_values, new_values, changed_at)
        VALUES (v_forecast_id, 'update', v_old, v_new, CURRENT_TIMESTAMP - INTERVAL '2 hours' * v_counter)
        ON CONFLICT DO NOTHING;
        v_counter := v_counter + 1;
    END LOOP;
END $$;

-- ============================================================
-- Structural zeros (15 records) — Markov model constraints
-- ============================================================
INSERT INTO structural_zeros (regime_from, regime_to, reason)
SELECT
    fr.r,
    tr.r,
    CASE
        WHEN fr.r = 1 AND tr.r = 16 THEN 'Iarna direct in canicula improbabil'
        WHEN fr.r = 16 AND tr.r = 1 THEN 'Canicula direct in ger improbabil'
        WHEN ABS(fr.r - tr.r) > 10 THEN 'Salt prea mare intre regimuri'
        ELSE 'Tranzitie structural improbabila'
    END
FROM generate_series(1, 5) AS fr(r)
CROSS JOIN generate_series(12, 16) AS tr(r)
WHERE NOT EXISTS (SELECT 1 FROM structural_zeros WHERE regime_from = fr.r AND regime_to = tr.r)
LIMIT 15;
