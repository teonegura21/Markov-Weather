CREATE TABLE forecasts (
    id SERIAL PRIMARY KEY,
    city_id INTEGER NOT NULL REFERENCES cities(id),
    date DATE NOT NULL,
    temp_min DOUBLE PRECISION NOT NULL,
    temp_max DOUBLE PRECISION NOT NULL,
    wind_speed DOUBLE PRECISION NOT NULL,
    icon_type VARCHAR(30) NOT NULL,
    uv_index INTEGER NOT NULL,
    humidity INTEGER NOT NULL CHECK (humidity BETWEEN 0 AND 100),
    warning_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (city_id, date)
);

CREATE INDEX idx_forecasts_city ON forecasts (city_id);
CREATE INDEX idx_forecasts_date ON forecasts (date);
CREATE INDEX idx_forecasts_city_date ON forecasts (city_id, date);
