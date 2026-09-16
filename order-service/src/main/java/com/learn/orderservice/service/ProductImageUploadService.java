package com.learn.orderservice.service;

import com.learn.orderservice.dto.ImageUploadUrlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

// The whole point of this class: the actual image bytes never pass through this service
// (or the Gateway, or any Spring Boot process at all). This only ever hands out a
// short-lived, one-time link the browser uses to talk to S3 directly -- see
// ImageUploadUrlResponse's comment for the two-step flow this backs.
@Service
public class ProductImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageUploadService.class);

    // Bounds both S3 storage per product and the size of the thumbnail-strip gallery the
    // frontend has to render -- ProductController checks this before minting an upload URL
    // and again before actually adding the image, since two admins (or two tabs) could both
    // pass the first check for the same product before either finishes uploading.
    public static final int MAX_IMAGES_PER_PRODUCT = 6;

    // Same bucket that already hosts the frontend (scripts/config.ps1's $FrontendBucket) --
    // reusing it means nothing new to provision, and its existing bucket policy already
    // lets CloudFront read anything written here, no extra configuration needed for that.
    @Value("${products.image-bucket}")
    private String bucket;

    @Value("${products.image-cdn-domain}")
    private String cdnDomain;

    // Only real image types -- this bucket also hosts this app's own frontend code, so
    // this is deliberately a narrow allowlist, not just "anything goes."
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    public ProductImageUploadService(S3Presigner s3Presigner, S3Client s3Client) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
    }

    public ImageUploadUrlResponse createUploadUrl(Long productId, String contentType) {
        String extension = EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException(
                    "Unsupported image type '%s' -- use image/jpeg, image/png, or image/webp".formatted(contentType));
        }

        // A random suffix, not just the productId, so re-uploading a new photo for the
        // same product doesn't silently overwrite (or get blocked by caching against) the
        // previous file at the exact same key -- each upload gets its own, genuinely new
        // object.
        String key = "product-images/%d-%s%s".formatted(productId, UUID.randomUUID(), extension);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        // 5 minutes: generous for an upload that starts immediately after this call
        // returns, short enough that a URL sitting in a browser history or a log line
        // isn't a standing risk once that window passes.
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new ImageUploadUrlResponse(presigned.url().toString(), "https://" + cdnDomain + "/" + key);
    }

    // Called whenever a ProductImage row stops pointing at a file -- an admin explicitly
    // removing one image (ProductController's DELETE endpoint) being the only caller now
    // that images are additive rather than replaced. Every upload gets a brand new random
    // key (see createUploadUrl's comment), so this is the only way an orphaned file in S3
    // ever gets cleaned up. Best-effort on purpose: a failure here (a transient S3 blip, or
    // the URL simply not being one of ours -- see the prefix check below) must never block
    // the actual deletion, which has already succeeded in the database by the time this
    // runs. Worst case if this fails, exactly one harmless orphaned file lingers -- the
    // same situation this method exists to reduce, not a correctness problem either way.
    public void deleteIfManaged(String imageUrl) {
        if (imageUrl == null) {
            return;
        }

        String urlPrefix = "https://" + cdnDomain + "/";
        if (!imageUrl.startsWith(urlPrefix + "product-images/")) {
            // Not one of our own uploads -- e.g. an admin set an external URL by hand.
            // Nothing in our bucket to clean up, and nothing we'd have permission to
            // delete outside product-images/ anyway (see ProductImageUploadPolicy).
            return;
        }

        String key = imageUrl.substring(urlPrefix.length());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("Could not delete product image {}: {}", imageUrl, e.getMessage());
        }
    }
}
