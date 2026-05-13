-- Migrație 013: Tabele pentru urmărirea acurateței predicțiilor și ajustări Markov

CREATE TABLE IF NOT EXISTS prediction_accuracy (
    id BIGSERIAL PRIMARY KEY,
    city_id INTEGER NOT NULL,
    forecast_date DATE NOT NULL,
    horizon_day INT NOT NULL CHECK (horizon_day BETWEEN 1 AND 10),
    predicted_temp_min DOUBLE PRECISION,
    predicted_temp_max DOUBLE PRECISION,
    actual_temp_min DOUBLE PRECISION,
    actual_temp_max DOUBLE PRECISION,
    predicted_wind_speed DOUBLE PRECISION,
    actual_wind_speed DOUBLE PRECISION,
    predicted_humidity INT,
    actual_humidity INT,
    mae_temp DOUBLE PRECISION,
    rmse_temp DOUBLE PRECISION,
    bias_temp DOUBLE PRECISION,
    hit_event VARCHAR(50),
    hit_correct BOOLEAN,
    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_prediction_accuracy UNIQUE (city_id, forecast_date, horizon_day),
    CONSTRAINT fk_prediction_accuracy_cities FOREIGN KEY (city_id) REFERENCES cities(id)
);

CREATE INDEX idx_prediction_accuracy_city ON prediction_accuracy (city_id);
CREATE INDEX idx_prediction_accuracy_date ON prediction_accuracy (forecast_date);
CREATE INDEX idx_prediction_accuracy_horizon ON prediction_accuracy (horizon_day);
CREATE INDEX idx_prediction_accuracy_city_date ON prediction_accuracy (city_id, forecast_date);
CREATE INDEX idx_prediction_accuracy_computed ON prediction_accuracy (computed_at);

CREATE TABLE IF NOT EXISTS regime_accuracy (
    id SERIAL PRIMARY KEY,
    climate_zone VARCHAR(50),
    regime_id INT NOT NULL,
    correct_predictions INT NOT NULL DEFAULT 0,
    total_predictions INT NOT NULL DEFAULT 0,
    accuracy_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_regime_accuracy UNIQUE (climate_zone, regime_id)
);

CREATE INDEX idx_regime_accuracy_zone ON regime_accuracy (climate_zone);
CREATE INDEX idx_regime_accuracy_regime ON regime_accuracy (regime_id);
CREATE INDEX idx_regime_accuracy_rate ON regime_accuracy (accuracy_rate DESC);

CREATE TABLE IF NOT EXISTS markov_weight_adjustments (
    id BIGSERIAL PRIMARY KEY,
    climate_zone VARCHAR(50),
    season VARCHAR(10),
    r_prev INT,
    r_curr INT,
    r_next INT,
    adjustment_delta DOUBLE PRECISION,
    reason TEXT,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_markov_adj_zone ON markov_weight_adjustments (climate_zone);
CREATE INDEX idx_markov_adj_season ON markov_weight_adjustments (season);
CREATE INDEX idx_markov_adj_transition ON markov_weight_adjustments (r_prev, r_curr, r_next);
