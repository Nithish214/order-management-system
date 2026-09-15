-- Nullable: existing products simply have no image until one is uploaded. Points at the
-- final CloudFront URL the image resolves to (e.g. https://<distribution>/product-images/
-- <file>) -- the actual file lives in S3, not in this database.
ALTER TABLE product ADD image_url VARCHAR(500) NULL;
