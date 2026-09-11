-- Runs once, automatically, only against a brand-new oracle_data volume (the gvenzl
-- image executes every script under /container-entrypoint-initdb.d/startup after the
-- database and the primary APP_USER already exist). Gives Inventory Service its own
-- Oracle user/schema -- so its tables are owned and namespaced independently of Order
-- Service's app_user schema -- without needing a second Oracle container.
ALTER SESSION SET CONTAINER = XEPDB1;

CREATE USER inventory_user IDENTIFIED BY inventory_password;
GRANT CONNECT, RESOURCE TO inventory_user;
GRANT UNLIMITED TABLESPACE TO inventory_user;
