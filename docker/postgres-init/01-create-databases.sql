-- Runs once, automatically, only against a brand-new postgres_data volume (the official
-- postgres image executes every .sql/.sh script under /docker-entrypoint-initdb.d/ on
-- first startup, in filename order). POSTGRES_USER/POSTGRES_PASSWORD (see
-- docker-compose.yml) already create the "postgres" superuser and its default database --
-- this just adds the two databases Order Service and Inventory Service actually connect
-- to, keeping their tables in separate namespaces from each other. Same reasoning as the
-- separate Oracle users this replaced, and the same "one instance, two databases" pattern
-- already used on RDS in production (see docker-compose.prod.yml / DEPLOYMENT.md) -- one
-- local container instead of two, without losing that isolation.
CREATE DATABASE orderdb;
CREATE DATABASE inventorydb;
