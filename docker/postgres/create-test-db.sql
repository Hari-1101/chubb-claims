-- Runs once, when the Postgres volume is first created (docker-entrypoint-initdb.d).
-- Separate database for the backend integration tests (profile "test"), so they never touch the demo data.
CREATE DATABASE claims_test OWNER claims;
