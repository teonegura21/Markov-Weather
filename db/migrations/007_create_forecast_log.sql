CREATE TABLE forecast_log (
    id SERIAL PRIMARY KEY,
    forecast_id INTEGER NOT NULL REFERENCES forecasts(id),
    change_type VARCHAR(20) NOT NULL,
    old_values JSONB,
    new_values JSONB,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_forecast_log_forecast ON forecast_log (forecast_id);
