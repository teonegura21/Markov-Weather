-- Migrație 014: Tabel pentru logul procesului de reinforcement learning

CREATE TABLE IF NOT EXISTS reinforcement_log (
    id BIGSERIAL PRIMARY KEY,
    iteration INT NOT NULL,
    parameter_type VARCHAR(50) NOT NULL,
    parameter_key VARCHAR(200) NOT NULL,
    old_value DOUBLE PRECISION,
    new_value DOUBLE PRECISION,
    accuracy_before DOUBLE PRECISION,
    accuracy_after DOUBLE PRECISION,
    city_id INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reinforcement_log_cities FOREIGN KEY (city_id) REFERENCES cities(id)
);

CREATE INDEX idx_reinforcement_iteration ON reinforcement_log (iteration);
CREATE INDEX idx_reinforcement_param_type ON reinforcement_log (parameter_type);
CREATE INDEX idx_reinforcement_city ON reinforcement_log (city_id);
CREATE INDEX idx_reinforcement_created ON reinforcement_log (created_at);
