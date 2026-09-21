-- One optional video per product (a demo/showcase clip), not a collection like
-- ProductImage -- nullable, and simply overwritten (old file cleaned up in S3 by
-- ProductVideoUploadService) rather than accumulating like images do.
ALTER TABLE product ADD COLUMN video_url VARCHAR(500);
