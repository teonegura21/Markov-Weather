-- Migration 017: Tabel pentru prognoze orare
-- Stocheaza date meteo la nivel de ora pentru fiecare zi si oras.

CREATE TABLE IF NOT EXISTS hourly_forecasts (
    id BIGSERIAL PRIMARY KEY,
    city_id INTEGER NOT NULL REFERENCES cities(id),
    forecast_date DATE NOT NULL,
    hour INTEGER NOT NULL CHECK (hour BETWEEN 0 AND 23),
    temperature DOUBLE PRECISION NOT NULL,
    humidity INTEGER CHECK (humidity BETWEEN 0 AND 100),
    wind_speed DOUBLE PRECISION,
    precipitation_probability INTEGER CHECK (precipitation_probability BETWEEN 0 AND 100),
    weather_code INTEGER,
    icon_type VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (city_id, forecast_date, hour)
);

CREATE INDEX idx_hourly_city_date_hour ON hourly_forecasts (city_id, forecast_date, hour);
CREATE INDEX idx_hourly_forecast_date ON hourly_forecasts (forecast_date);
