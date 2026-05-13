-- Migrație 010: Tabele pentru regimuri meteo, clasificare zilnică și climatologie sezonieră

CREATE TABLE IF NOT EXISTS weather_regimes (
    id SERIAL PRIMARY KEY,
    climate_zone VARCHAR(50) NOT NULL,
    regime_id INT NOT NULL,
    centroid DOUBLE PRECISION[] NOT NULL,
    covariance_flat DOUBLE PRECISION[] NOT NULL,
    frequency DOUBLE PRECISION NOT NULL,
    label_ro VARCHAR(200),
    description_ro TEXT,
    UNIQUE (climate_zone, regime_id)
);

CREATE INDEX IF NOT EXISTS idx_weather_regimes_zone ON weather_regimes (climate_zone);

CREATE TABLE IF NOT EXISTS daily_regimes (
    id BIGSERIAL PRIMARY KEY,
    city_id INT NOT NULL REFERENCES cities(id),
    date DATE NOT NULL,
    regime_id INT NOT NULL,
    climate_zone VARCHAR(50) NOT NULL,
    fog_score DOUBLE PRECISION,
    thunderstorm_score DOUBLE PRECISION,
    cyclone_score DOUBLE PRECISION,
    anticyclone_score DOUBLE PRECISION,
    heatwave_score DOUBLE PRECISION,
    inversion_score DOUBLE PRECISION,
    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (city_id, date)
);

CREATE INDEX IF NOT EXISTS idx_daily_regimes_city_date ON daily_regimes (city_id, date);
CREATE INDEX IF NOT EXISTS idx_daily_regimes_regime ON daily_regimes (regime_id);

CREATE TABLE IF NOT EXISTS seasonal_climatology (
    id SERIAL PRIMARY KEY,
    city_id INT NOT NULL REFERENCES cities(id),
    day_of_year INT NOT NULL,
    regime_id INT,
    temp_min_mean DOUBLE PRECISION,
    temp_min_std DOUBLE PRECISION,
    temp_max_mean DOUBLE PRECISION,
    temp_max_std DOUBLE PRECISION,
    wind_speed_mean DOUBLE PRECISION,
    humidity_mean DOUBLE PRECISION,
    precip_sum_mean DOUBLE PRECISION,
    sample_count INT NOT NULL,
    UNIQUE (city_id, day_of_year, regime_id)
);

CREATE INDEX IF NOT EXISTS idx_seasonal_clim_city_doy ON seasonal_climatology (city_id, day_of_year);
