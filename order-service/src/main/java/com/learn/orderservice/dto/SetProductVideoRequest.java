package com.learn.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetProductVideoRequest {

    // Called only after the browser's direct PUT to S3 (via the URL from
    // POST /products/{id}/video-upload-url) has already succeeded -- this endpoint just
    // records it against the product, it never itself touches file bytes.
    @NotBlank(message = "videoUrl is required")
    private String videoUrl;
}
