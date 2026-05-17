-- Migration 019: Add default login user
INSERT INTO users (username, password_hash, reputation, created_at)
VALUES ('user', '04f8996da763b7a969b1028ee3007569eaf3a635486ddab211d512c85b9df8fb', 0.0, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;
