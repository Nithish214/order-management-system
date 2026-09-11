-- Links a Cognito identity (its "sub" claim -- the OIDC-standard permanent, immutable,
-- unique-per-user identifier) to an app_user row. Nullable because existing seeded rows
-- (Alice, Bob) were never created through Cognito and have no sub to link -- only
-- newly auto-created app_user rows (see OrderController) ever populate this.
ALTER TABLE app_user ADD cognito_sub VARCHAR2(255 CHAR) NULL;
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_cognito_sub UNIQUE (cognito_sub);
