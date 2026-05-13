/*
 * sp_log_reinforcement
 *   Înregistrează un eveniment de învățare prin recompensare în jurnalul
 *   reinforcement_log.
 *   Parametri: toate coloanele tabelei reinforcement_log (fără id care este auto-generat).
 */
CREATE OR REPLACE PROCEDURE sp_log_reinforcement(
    p_iteration INT,
    p_parameter_type VARCHAR,
    p_parameter_key VARCHAR,
    p_old_value DOUBLE PRECISION,
    p_new_value DOUBLE PRECISION,
    p_accuracy_before DOUBLE PRECISION,
    p_accuracy_after DOUBLE PRECISION,
    p_city_id INT,
    p_created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO reinforcement_log (
        iteration, parameter_type, parameter_key, old_value, new_value,
        accuracy_before, accuracy_after, city_id, created_at
    ) VALUES (
        p_iteration, p_parameter_type, p_parameter_key, p_old_value, p_new_value,
        p_accuracy_before, p_accuracy_after, p_city_id, p_created_at
    );
END;
$$;
