-- Migrație 009: Extindere schema cu vectori meteo 25D
-- Adaugă coloane brute lipsă în forecasts și creează tabela weather_vectors

ALTER TABLE forecasts
    ADD COLUMN IF NOT EXISTS pressure_mean DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pressure_trend DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS precipitation_sum DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS precipitation_hours DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS sunshine_hours DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS dew_point DOUBLE PRECISION;

CREATE TABLE IF NOT EXISTS weather_vectors (
    id BIGSERIAL PRIMARY KEY,
    city_id INTEGER NOT NULL REFERENCES cities(id),
    date DATE NOT NULL,

    -- Layer A: Termic (5 dimensiuni)
    temp_min DOUBLE PRECISION,
    temp_max DOUBLE PRECISION,
    temp_avg DOUBLE PRECISION,
    temp_amplitude DOUBLE PRECISION,
    temp_trend DOUBLE PRECISION,

    -- Layer B: Umiditate (5 dimensiuni)
    humidity_min DOUBLE PRECISION,
    humidity_max DOUBLE PRECISION,
    humidity_avg DOUBLE PRECISION,
    dew_point_min DOUBLE PRECISION,
    dew_point_spread DOUBLE PRECISION,

    -- Layer C: Vânt (4 dimensiuni)
    wind_speed_avg DOUBLE PRECISION,
    wind_speed_max DOUBLE PRECISION,
    gust_factor DOUBLE PRECISION,
    wind_persistence DOUBLE PRECISION,

    -- Layer D: Radiație și Nori (4 dimensiuni)
    sunshine_hours DOUBLE PRECISION,
    sunshine_fraction DOUBLE PRECISION,
    uv_index_max DOUBLE PRECISION,
    cloud_cover_proxy DOUBLE PRECISION,

    -- Layer E: Precipitații (4 dimensiuni)
    precipitation_sum DOUBLE PRECISION,
    precip_intensity DOUBLE PRECISION,
    precipitation_hours DOUBLE PRECISION,
    snow_depth DOUBLE PRECISION,

    -- Layer F: Presiune (3 dimensiuni)
    pressure_mean DOUBLE PRECISION,
    pressure_trend DOUBLE PRECISION,
    pressure_range DOUBLE PRECISION,

    -- Derivate temporale (8 dimensiuni adiționale)
    delta1_temp_avg DOUBLE PRECISION,
    delta2_temp_avg DOUBLE PRECISION,
    delta1_humidity_avg DOUBLE PRECISION,
    delta2_humidity_avg DOUBLE PRECISION,
    delta1_pressure_mean DOUBLE PRECISION,
    delta2_pressure_mean DOUBLE PRECISION,
    delta1_wind_speed_avg DOUBLE PRECISION,
    delta2_wind_speed_avg DOUBLE PRECISION,

    -- Scoruri detectoare de fenomene (6 dimensiuni)
    fog_score DOUBLE PRECISION,
    thunderstorm_score DOUBLE PRECISION,
    cyclone_score DOUBLE PRECISION,
    anticyclone_score DOUBLE PRECISION,
    heatwave_score DOUBLE PRECISION,
    inversion_score DOUBLE PRECISION,

    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (city_id, date)
);

CREATE INDEX IF NOT EXISTS idx_weather_vectors_city_date ON weather_vectors (city_id, date);
CREATE INDEX IF NOT EXISTS idx_weather_vectors_date ON weather_vectors (date);
