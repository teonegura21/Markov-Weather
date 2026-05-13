-- Migrație 011: Tabele pentru lanț Markov de ordin 2, zerouri structurale și Hidden Markov Model

CREATE TABLE IF NOT EXISTS markov_transitions (
    id BIGSERIAL PRIMARY KEY,
    climate_zone VARCHAR(50) NOT NULL,
    season VARCHAR(10) NOT NULL,
    r_prev INT NOT NULL,
    r_curr INT NOT NULL,
    r_next INT NOT NULL,
    count INT NOT NULL,
    probability DOUBLE PRECISION NOT NULL,
    UNIQUE (climate_zone, season, r_prev, r_curr, r_next)
);

CREATE INDEX IF NOT EXISTS idx_markov_zone_season ON markov_transitions (climate_zone, season);
CREATE INDEX IF NOT EXISTS idx_markov_lookup ON markov_transitions (climate_zone, season, r_prev, r_curr);

CREATE TABLE IF NOT EXISTS structural_zeros (
    id SERIAL PRIMARY KEY,
    regime_from INT NOT NULL,
    regime_to INT NOT NULL,
    reason TEXT NOT NULL,
    CONSTRAINT uq_structural_zero UNIQUE (regime_from, regime_to)
);

CREATE TABLE IF NOT EXISTS hidden_states (
    id SERIAL PRIMARY KEY,
    city_id INT NOT NULL REFERENCES cities(id),
    state_id INT NOT NULL,
    label_ro VARCHAR(200),
    emission_probs DOUBLE PRECISION[] NOT NULL,
    UNIQUE (city_id, state_id)
);

CREATE INDEX IF NOT EXISTS idx_hidden_states_city ON hidden_states (city_id);

CREATE TABLE IF NOT EXISTS hidden_transitions (
    id SERIAL PRIMARY KEY,
    city_id INT NOT NULL REFERENCES cities(id),
    state_from INT NOT NULL,
    state_to INT NOT NULL,
    duration_bucket INT NOT NULL,
    probability DOUBLE PRECISION NOT NULL,
    UNIQUE (city_id, state_from, state_to, duration_bucket)
);

CREATE INDEX IF NOT EXISTS idx_hidden_transitions_city ON hidden_transitions (city_id);
