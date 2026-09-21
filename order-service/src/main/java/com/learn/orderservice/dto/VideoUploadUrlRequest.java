package com.learn.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VideoUploadUrlRequest {

    // Constrains what the presigned URL will actually accept (see
    // ProductVideoUploadService's own content-type allowlist) -- only real, web-playable
    // video types, not an open door to storing arbitrary files in the bucket that also
    // hosts this app's own frontend.
    @NotBlank(message = "contentType is required")
    private String contentType;
}
