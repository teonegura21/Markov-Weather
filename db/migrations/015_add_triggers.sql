-- Migration 015: Add database triggers for automation
-- Requirement: at least 2 triggers for automation (professor requirement)

-- Trigger 1: Auto-update updated_at timestamp on forecasts when modified
CREATE OR REPLACE FUNCTION trg_fn_update_timestamp()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_forecasts_updated_at ON forecasts;
CREATE TRIGGER trg_forecasts_updated_at
    BEFORE UPDATE ON forecasts
    FOR EACH ROW
    EXECUTE FUNCTION trg_fn_update_timestamp();

-- Trigger 2: Auto-update user reputation when a new vote is inserted
CREATE OR REPLACE FUNCTION trg_fn_update_reputation_on_vote()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- Update user's reputation based on vote accuracy and comment count
    UPDATE users
    SET reputation = COALESCE(
        (SELECT ROUND(
            (
                (COUNT(*) FILTER (WHERE is_accurate = TRUE))::NUMERIC
                / NULLIF(COUNT(*), 0)::NUMERIC
            )
            * (1 + LN(1 + (SELECT COUNT(*) FROM comments WHERE user_id = NEW.user_id)))::NUMERIC
        , 3)
        FROM votes WHERE user_id = NEW.user_id),
        0
    )
    WHERE id = NEW.user_id;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_update_reputation_on_vote ON votes;
CREATE TRIGGER trg_update_reputation_on_vote
    AFTER INSERT ON votes
    FOR EACH ROW
    EXECUTE FUNCTION trg_fn_update_reputation_on_vote();
