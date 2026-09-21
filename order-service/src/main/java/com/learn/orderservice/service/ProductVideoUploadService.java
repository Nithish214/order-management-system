package com.learn.orderservice.service;

import com.learn.orderservice.dto.VideoUploadUrlResponse;
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

// Same shape and reasoning as ProductImageUploadService (presign a direct-to-S3 PUT, best-
// effort cleanup of the file an admin replaces or removes) -- kept as its own class rather
// than folded into that one, since videos differ in every place that actually matters: a
// single field, not a capped collection (see Product#videoUrl), a completely different
// content-type allowlist and S3 prefix, and no per-product count check to duplicate.
@Service
public class ProductVideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(ProductVideoUploadService.class);

    // Same bucket the frontend and product images already live in -- nothing new to
    // provision, same CloudFront read access already covers this prefix too.
    @Value("${products.image-bucket}")
    private String bucket;

    @Value("${products.image-cdn-domain}")
    private String cdnDomain;

    // Narrow on purpose, same reasoning as ProductImageUploadService's own allowlist --
    // just the two formats every modern browser can play natively with a plain <video>
    // tag, no transcoding pipeline involved.
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "video/mp4", ".mp4",
            "video/webm", ".webm"
    );

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    public ProductVideoUploadService(S3Presigner s3Presigner, S3Client s3Client) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
    }

    public VideoUploadUrlResponse createUploadUrl(Long productId, String contentType) {
        String extension = EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException(
                    "Unsupported video type '%s' -- use video/mp4 or video/webm".formatted(contentType));
        }

        // Random suffix, not just the productId, for the same reason as product images:
        // re-uploading a replacement video shouldn't collide with (or get blocked by
        // caching against) the file it's replacing.
        String key = "product-videos/%d-%s%s".formatted(productId, UUID.randomUUID(), extension);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        // Same 5-minute window as image uploads -- generous for an upload that starts
        // immediately, short enough that a stray URL in browser history isn't a standing
        // risk once it passes.
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new VideoUploadUrlResponse(presigned.url().toString(), "https://" + cdnDomain + "/" + key);
    }

    // Called whenever a product's videoUrl stops pointing at a file -- replaced with a
    // new upload, or removed outright (see ProductController's PUT/DELETE .../video).
    // Best-effort, same reasoning as ProductImageUploadService#deleteIfManaged: a failure
    // here must never block the database change, which has already succeeded by the time
    // this runs. Worst case, exactly one harmless orphaned file lingers in S3.
    public void deleteIfManaged(String videoUrl) {
        if (videoUrl == null) {
            return;
        }

        String urlPrefix = "https://" + cdnDomain + "/";
        if (!videoUrl.startsWith(urlPrefix + "product-videos/")) {
            // Not one of our own uploads -- nothing to clean up, and nothing we'd have
            // permission to delete outside product-videos/ anyway.
            return;
        }

        String key = videoUrl.substring(urlPrefix.length());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("Could not delete product video {}: {}", videoUrl, e.getMessage());
        }
    }
}
