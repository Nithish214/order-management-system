package com.learn.orderservice.dto;

// Same two-step-upload shape as ImageUploadUrlResponse, just named for what it actually
// carries here: uploadUrl is the short-lived, one-time S3 PUT link; videoUrl is where
// that file will be reachable afterward via the CDN, which the frontend sends back to
// PUT /products/{id}/video once the direct-to-S3 upload succeeds.
public record VideoUploadUrlResponse(String uploadUrl, String videoUrl) {
}
