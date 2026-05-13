/*
 * sp_auto_warn_users
 *   Returneaza avertizarile active ce contin cuvinte cheie pentru un oras.
 *   Parametri: p_city_id (NULL = toate), p_keywords (array de cuvinte cheie)
 */
CREATE OR REPLACE FUNCTION sp_auto_warn_users(
    p_city_id INTEGER DEFAULT NULL,
    p_keywords TEXT[] DEFAULT ARRAY['furtuna', 'canicula', 'ger', 'ploaie', 'vant', 'avertizare']
) RETURNS TABLE(
    oras VARCHAR,
    tara VARCHAR,
    data DATE,
    avertizare TEXT,
    cuvinte_gasite TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_kw TEXT;
    v_found TEXT[];
BEGIN
    RETURN QUERY
    SELECT
        ci.name::VARCHAR,
        co.name::VARCHAR,
        f.date,
        f.warning_text,
        (
            SELECT STRING_AGG(kw, ', ')
            FROM UNNEST(p_keywords) AS kw
            WHERE LOWER(f.warning_text) ILIKE '%' || LOWER(kw) || '%'
        )::TEXT
    FROM forecasts f
    JOIN cities ci ON f.city_id = ci.id
    JOIN countries co ON ci.country_id = co.id
    WHERE f.warning_text IS NOT NULL
      AND (p_city_id IS NULL OR f.city_id = p_city_id)
      AND EXISTS (
        SELECT 1 FROM UNNEST(p_keywords) AS kw
        WHERE LOWER(f.warning_text) ILIKE '%' || LOWER(kw) || '%'
      )
    ORDER BY f.date DESC;
END;
$$;
