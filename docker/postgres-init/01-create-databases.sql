-- Runs once, automatically, only against a brand-new postgres_data volume (the official
-- postgres image executes every .sql/.sh script under /docker-entrypoint-initdb.d/ on
-- first startup, in filename order). POSTGRES_USER/POSTGRES_PASSWORD (see
-- docker-compose.yml) already create the "postgres" superuser and its default database --
-- this just adds the three databases Order/Inventory/Payment Service actually connect
-- to, keeping their tables in separate namespaces from each other. Same reasoning as the
-- separate Oracle users this replaced, and the same "one instance, N databases" pattern
-- already used on RDS in production (see docker-compose.prod.yml / DEPLOYMENT.md) -- one
-- local container instead of three, without losing that isolation.
--
-- paymentdb was missing here for a while -- payment-service was never part of the
-- original Oracle-to-Postgres migration (it was Postgres-only from day one), so it got
-- overlooked when this file was first written. Found by actually trying to run all three
-- services locally, not by inspection.
CREATE DATABASE orderdb;
CREATE DATABASE inventorydb;
CREATE DATABASE paymentdb;
