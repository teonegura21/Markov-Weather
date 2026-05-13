CREATE TABLE votes (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    forecast_id INTEGER NOT NULL REFERENCES forecasts(id),
    is_accurate BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, forecast_id)
);

CREATE INDEX idx_votes_forecast ON votes (forecast_id);
CREATE INDEX idx_votes_user ON votes (user_id);
