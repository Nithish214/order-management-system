package com.learn.orderservice.service;

import com.learn.orderservice.dto.ImageUploadUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    public ProductImageUploadService(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
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
}
