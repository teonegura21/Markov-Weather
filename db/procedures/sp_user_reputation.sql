/*
 * sp_user_reputation
 *   Calculeaza reputatia unui utilizator pe baza istoricului de voturi si comentarii.
 *   Formula: (voturi corecte / total voturi) * (1 + log(1 + nr_comentarii))
 *   Parametri: p_user_id
 */
CREATE OR REPLACE FUNCTION sp_user_reputation(p_user_id INTEGER)
RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
AS $$
DECLARE
    v_total_votes INTEGER;
    v_agreed_votes INTEGER;
    v_comment_count INTEGER;
    v_accuracy DOUBLE PRECISION;
BEGIN
    SELECT COUNT(*), COUNT(*) FILTER (WHERE v.is_accurate = TRUE)
    INTO v_total_votes, v_agreed_votes
    FROM votes v
    WHERE v.user_id = p_user_id;

    SELECT COUNT(*) INTO v_comment_count
    FROM comments c
    WHERE c.user_id = p_user_id;

    IF v_total_votes = 0 THEN
        v_accuracy := 0;
    ELSE
        v_accuracy := v_agreed_votes::DOUBLE PRECISION / v_total_votes;
    END IF;

    RETURN ROUND((v_accuracy * (1 + LN(1 + v_comment_count)))::numeric, 3);
END;
$$;
