-- Postgres-only feature: tsvector/GIN full-text search is a Postgres-native mechanism
-- with no equivalent syntax in Oracle (which has its own, completely different Oracle
-- Text/CONTAINS() system) -- this migration, and the GET /products/search endpoint it
-- backs, only exist in this Postgres tree. Local dev against Oracle simply doesn't have
-- product search; that's a deliberate scope decision given production runs on
-- Postgres/RDS, not an oversight.
--
-- GENERATED ALWAYS AS ... STORED keeps this column automatically in sync with name/sku
-- on every INSERT/UPDATE -- Postgres recomputes it itself; no application code or
-- trigger has to remember to update it when a product's name changes.
ALTER TABLE product ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('english', coalesce(name, '') || ' ' || coalesce(sku, ''))) STORED;

-- GIN (Generalized Inverted Index) is the index type built specifically for tsvector
-- columns -- this IS the actual "word -> list of matching rows" inverted index that
-- makes a keyword search fast regardless of catalog size, instead of scanning every
-- row's text on every single search the way a plain `name LIKE '%term%'` query would.
CREATE INDEX idx_product_search_vector ON product USING GIN(search_vector);
