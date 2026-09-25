-- ---- 1. Contact number -------------------------------------------------------------
-- A plain attribute of the account (one per user), so it lives on app_user itself rather
-- than in a table of its own. Nullable: existing users don't have one, and it isn't
-- required to browse or buy.
ALTER TABLE app_user ADD COLUMN phone_number VARCHAR(20);

-- ---- 2. Address book ---------------------------------------------------------------
-- The user's saved addresses: mutable, editable, deletable. Orders do NOT reference this
-- table (see section 3) -- that's the whole point of the split.
CREATE TABLE shipping_address (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    label VARCHAR(50) NOT NULL,
    line1 VARCHAR(200) NOT NULL,
    line2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_shipping_address PRIMARY KEY (id),
    CONSTRAINT fk_shipping_address_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE SEQUENCE shipping_address_seq START WITH 1 INCREMENT BY 1;

-- Every lookup is "this user's addresses".
CREATE INDEX idx_shipping_address_user ON shipping_address(user_id);

-- "At most one default per user", enforced by the DATABASE, not just application code.
-- A partial unique index: only rows WHERE is_default is true are indexed, so it's a
-- uniqueness rule over user_id for default rows alone -- any number of non-default rows per
-- user are fine, but a second default for the same user is rejected outright. Application
-- logic alone (clear the old default, then set the new one) is a check-then-act: two
-- requests promoting different addresses at the same moment can both pass it and leave two
-- defaults. The index makes that race impossible -- the slower request fails with a
-- constraint violation instead of silently corrupting the invariant (AddressService turns
-- that into a clean 409). Postgres-only syntax, which is fine: Postgres is this project's
-- only database now.
CREATE UNIQUE INDEX uq_shipping_address_one_default ON shipping_address(user_id) WHERE is_default;

-- ---- 3. Address snapshot on each order ---------------------------------------------
-- Same principle as order_item.unit_price: an order records what was TRUE when it was placed,
-- not a live pointer to something that can change later. Edit or delete a saved address
-- tomorrow and last month's orders must still say where they were actually sent.
--
-- Flattened columns on orders (ship_*), NOT a separate order_shipping_address table -- see
-- ShippingAddressSnapshot for the tradeoff. There is deliberately NO foreign key to
-- shipping_address: a FK would either block deleting an address that was ever used, or
-- force ON DELETE SET NULL and lose the record -- both defeat the snapshot.
--
-- All nullable: every order placed before this migration has no address, and none is
-- invented for them. New orders always get one (POST /orders requires addressId).
ALTER TABLE orders
    ADD COLUMN ship_line1 VARCHAR(200),
    ADD COLUMN ship_line2 VARCHAR(200),
    ADD COLUMN ship_city VARCHAR(100),
    ADD COLUMN ship_state VARCHAR(100),
    ADD COLUMN ship_postal_code VARCHAR(20),
    ADD COLUMN ship_country VARCHAR(100);

-- An order has a whole address or none -- never a half-written one. line2 is legitimately
-- optional, so it's excluded from the "all present" side.
ALTER TABLE orders ADD CONSTRAINT chk_orders_ship_all_or_none CHECK (
    (ship_line1 IS NULL AND ship_city IS NULL AND ship_state IS NULL
        AND ship_postal_code IS NULL AND ship_country IS NULL)
    OR
    (ship_line1 IS NOT NULL AND ship_city IS NOT NULL AND ship_state IS NOT NULL
        AND ship_postal_code IS NOT NULL AND ship_country IS NOT NULL)
);
