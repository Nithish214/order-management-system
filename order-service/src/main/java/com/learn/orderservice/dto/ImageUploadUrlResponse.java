package com.learn.orderservice.dto;

// uploadUrl: a short-lived, one-time link the browser PUTs the actual file bytes to,
// directly to S3 -- this never passes through Order Service or the Gateway at all.
// imageUrl: where that same file will be reachable afterward, via the CDN -- this is what
// the frontend later sends back to PUT /products/{id}/image once the upload succeeds.
public record ImageUploadUrlResponse(String uploadUrl, String imageUrl) {
}
