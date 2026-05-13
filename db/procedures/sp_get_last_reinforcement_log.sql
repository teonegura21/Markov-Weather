/*
 * fn_get_last_reinforcement_log
 *   Returnează cel mai recent eveniment de reinforcement learning
 *   înregistrat pentru un oraș dat.
 *   Parametri:
 *     p_city_id - ID-ul orașului
 *   Returnează: o singură înregistrare din reinforcement_log
 */
CREATE OR REPLACE FUNCTION fn_get_last_reinforcement_log(
    p_city_id INT
) RETURNS TABLE (
    id BIGINT,
    iteration INT,
    parameter_type VARCHAR,
    parameter_key VARCHAR,
    old_value DOUBLE PRECISION,
    new_value DOUBLE PRECISION,
    accuracy_before DOUBLE PRECISION,
    accuracy_after DOUBLE PRECISION,
    city_id INT,
    created_at TIMESTAMP
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        rl.id,
        rl.iteration,
        rl.parameter_type,
        rl.parameter_key,
        rl.old_value,
        rl.new_value,
        rl.accuracy_before,
        rl.accuracy_after,
        rl.city_id,
        rl.created_at
    FROM reinforcement_log rl
    WHERE rl.city_id = p_city_id
    ORDER BY rl.created_at DESC, rl.id DESC
    LIMIT 1;
END;
$$;
