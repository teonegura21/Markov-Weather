-- Migration 018: Trigger auto-populare forecast_log la UPDATE pe forecasts
-- Requirement: trigger pentru audit automat al modificarilor de prognoza

CREATE OR REPLACE FUNCTION trg_fn_log_forecast_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.temp_min IS DISTINCT FROM NEW.temp_min
       OR OLD.temp_max IS DISTINCT FROM NEW.temp_max
       OR OLD.wind_speed IS DISTINCT FROM NEW.wind_speed
       OR OLD.icon_type IS DISTINCT FROM NEW.icon_type
       OR OLD.humidity IS DISTINCT FROM NEW.humidity
       OR OLD.uv_index IS DISTINCT FROM NEW.uv_index THEN

        INSERT INTO forecast_log (forecast_id, change_type, old_values, new_values, changed_at)
        VALUES (
            NEW.id,
            'update',
            jsonb_build_object(
                'temp_min', OLD.temp_min,
                'temp_max', OLD.temp_max,
                'wind_speed', OLD.wind_speed,
                'icon_type', OLD.icon_type,
                'humidity', OLD.humidity,
                'uv_index', OLD.uv_index
            ),
            jsonb_build_object(
                'temp_min', NEW.temp_min,
                'temp_max', NEW.temp_max,
                'wind_speed', NEW.wind_speed,
                'icon_type', NEW.icon_type,
                'humidity', NEW.humidity,
                'uv_index', NEW.uv_index
            ),
            CURRENT_TIMESTAMP
        );
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_log_forecast_change ON forecasts;
CREATE TRIGGER trg_log_forecast_change
    AFTER UPDATE ON forecasts
    FOR EACH ROW
    EXECUTE FUNCTION trg_fn_log_forecast_change();
