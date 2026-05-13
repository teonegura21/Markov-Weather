ALTER TABLE forecasts ADD COLUMN data_source VARCHAR(30) NOT NULL DEFAULT 'generated';
ALTER TABLE forecasts ADD COLUMN fetched_at TIMESTAMP;

CREATE INDEX idx_forecasts_source ON forecasts (data_source);
CREATE INDEX idx_forecasts_fetched ON forecasts (fetched_at);
