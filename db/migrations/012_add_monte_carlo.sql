-- Migrație 012: Tabel pentru cache-ul predicțiilor Monte Carlo probabilistice

CREATE TABLE IF NOT EXISTS monte_carlo_predictions (
    id BIGSERIAL PRIMARY KEY,
    city_id INT NOT NULL REFERENCES cities(id),
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    forecast_date DATE NOT NULL,
    horizon_day INT NOT NULL,
    temp_min_p50 DOUBLE PRECISION,
    temp_min_p10 DOUBLE PRECISION,
    temp_min_p90 DOUBLE PRECISION,
    temp_max_p50 DOUBLE PRECISION,
    temp_max_p10 DOUBLE PRECISION,
    temp_max_p90 DOUBLE PRECISION,
    wind_speed_p50 DOUBLE PRECISION,
    humidity_p50 DOUBLE PRECISION,
    precip_sum_p50 DOUBLE PRECISION,
    precip_prob DOUBLE PRECISION,
    storm_prob DOUBLE PRECISION,
    fog_prob DOUBLE PRECISION,
    heatwave_prob DOUBLE PRECISION,
    ensemble_spread DOUBLE PRECISION,
    UNIQUE (city_id, generated_at, forecast_date)
);

CREATE INDEX IF NOT EXISTS idx_mc_pred_latest ON monte_carlo_predictions (city_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_mc_pred_date ON monte_carlo_predictions (forecast_date);
