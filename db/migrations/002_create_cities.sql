CREATE TABLE cities (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country_id INTEGER NOT NULL REFERENCES countries(id),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    is_important BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (name, country_id)
);

CREATE INDEX idx_cities_country ON cities (country_id);
