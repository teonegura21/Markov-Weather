/*
 * sp_get_map_data
 *   Returneaza temperaturile min si max pentru orasele importante dintr-o tara, pentru o data.
 *   Parametri: p_country_id, p_date
 */
CREATE OR REPLACE FUNCTION sp_get_map_data(
    p_country_id INTEGER DEFAULT NULL,
    p_date DATE DEFAULT CURRENT_DATE
) RETURNS TABLE(
    tara VARCHAR,
    cod_tara VARCHAR,
    oras VARCHAR,
    latitudine DOUBLE PRECISION,
    longitudine DOUBLE PRECISION,
    temp_min DOUBLE PRECISION,
    temp_max DOUBLE PRECISION,
    pictograma VARCHAR
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        co.name::VARCHAR,
        co.code::VARCHAR,
        ci.name::VARCHAR,
        ci.latitude,
        ci.longitude,
        f.temp_min,
        f.temp_max,
        f.icon_type::VARCHAR
    FROM forecasts f
    JOIN cities ci ON f.city_id = ci.id
    JOIN countries co ON ci.country_id = co.id
    WHERE f.date = p_date
      AND (p_country_id IS NULL OR co.id = p_country_id)
    ORDER BY co.name, ci.is_important DESC, ci.name;
END;
$$;
