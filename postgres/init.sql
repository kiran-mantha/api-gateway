-- postgres/init.sql

CREATE TABLE IF NOT EXISTS api_keys (
    id          SERIAL PRIMARY KEY,
    key_value   VARCHAR(255) NOT NULL UNIQUE,
    client_name VARCHAR(100) NOT NULL,
    is_active   BOOLEAN DEFAULT TRUE,
    rate_limit  INT DEFAULT 100,         -- requests per minute
    created_at  TIMESTAMP DEFAULT NOW()
);

-- Seed one test key for development
INSERT INTO api_keys (key_value, client_name, rate_limit)
VALUES ('test-api-key-12345', 'dev-client', 50)
ON CONFLICT DO NOTHING;