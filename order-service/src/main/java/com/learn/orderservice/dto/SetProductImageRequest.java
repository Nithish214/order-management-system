package com.learn.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetProductImageRequest {

    // Called only after the browser's direct PUT to S3 (via the URL from
    // POST /products/{id}/image-upload-url) has already succeeded -- this endpoint just
    // records where it ended up, it never itself touches file bytes.
    @NotBlank(message = "imageUrl is required")
    private String imageUrl;
}
