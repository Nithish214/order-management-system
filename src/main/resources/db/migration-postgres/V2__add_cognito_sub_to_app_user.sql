ALTER TABLE app_user ADD COLUMN cognito_sub VARCHAR(255) NULL;
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_cognito_sub UNIQUE (cognito_sub);
