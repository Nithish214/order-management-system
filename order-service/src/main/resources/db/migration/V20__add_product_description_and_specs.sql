-- Both nullable -- unlike V13's category backfill, there's no immediate UPDATE forcing
-- every existing row to have a value right away (that's V21, a separate migration:
-- schema here, content there, same split-by-concern V13/V14 already established). A
-- product with neither (the 300 bulk-generated ones from V16) is a normal, permanent
-- state, not a temporary one waiting to be backfilled -- so NOT NULL was never the
-- right constraint here the way it eventually was for category.
--
-- specs is plain TEXT holding a JSON object, not native Postgres JSONB -- see
-- SpecsConverter's own comment (order-service's entity package) for why: the same
-- "raw JSON in a text column, translated at the application layer" choice this
-- project's own outbox_event.payload column already makes, needing zero
-- Hibernate-version-specific JSONB configuration.
ALTER TABLE product ADD COLUMN description TEXT;
ALTER TABLE product ADD COLUMN specs TEXT;
