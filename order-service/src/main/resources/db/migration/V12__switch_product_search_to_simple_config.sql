-- Found live: searching "we" never matched "HD Webcam", even after V11's prefix-matching
-- fix. Root cause is different from the earlier "web" bug -- Postgres's 'english' text
-- search configuration treats "we" as a STOPWORD (the same category as "the", "a", "is")
-- and strips it out of both indexed content and queries entirely, regardless of any
-- prefix matching, since stopwords are assumed too common to carry useful search signal.
-- Confirmed directly: `SELECT to_tsquery('english', 'we:*')` returns an empty query and
-- Postgres itself logs "text-search query contains only stop words... ignored".
--
-- Switched to the 'simple' configuration: no stemming, no stopword list, just lowercasing
-- and tokenizing. For an e-commerce product search (short names, not natural-language
-- sentences), this is actually the better fit than 'english' was -- the earlier "mous"
-- matching "mouse" case still works fine under 'simple' too (it's a literal character
-- prefix regardless of stemming), and now "we" matching "webcam" works as well.
--
-- A GENERATED ALWAYS AS column's expression can't be altered in place (no ALTER COLUMN
-- ... SET EXPRESSION in Postgres) -- has to be dropped and recreated. The GIN index goes
-- with it, since it's built on the column being dropped, and gets rebuilt after.
DROP INDEX idx_product_search_vector;
ALTER TABLE product DROP COLUMN search_vector;

ALTER TABLE product ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(sku, ''))) STORED;

CREATE INDEX idx_product_search_vector ON product USING GIN(search_vector);
