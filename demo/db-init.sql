-- Runs once, when the demo database is first created (before the gateway's migrations).
-- The scorer's read-only login, as in production: the migrations grant it the scoring views when it exists.
CREATE ROLE fairness_scorer LOGIN PASSWORD 'demo-scorer-password';
ALTER ROLE fairness_scorer SET default_transaction_read_only = on;
GRANT CONNECT ON DATABASE fairness TO fairness_scorer;
GRANT USAGE ON SCHEMA public TO fairness_scorer;
