package com.learn.orderservice.dto;

import com.learn.orderservice.entity.ProductImage;

// id is included (not just the URL) so the frontend has something to address a specific
// image by when deleting one -- see ProductController's DELETE /products/{id}/images/{imageId}.
public record ProductImageResponse(Long id, String imageUrl) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getImageUrl());
    }
}
