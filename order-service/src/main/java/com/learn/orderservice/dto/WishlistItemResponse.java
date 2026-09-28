package com.learn.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learn.orderservice.entity.WishlistItem;

import java.math.BigDecimal;
import java.time.Instant;

// Shaped like CartItemResponse on purpose -- same "read the product live, never snapshot
// it" reasoning (see WishlistItem's own comment), plus savedAt for "recently saved" sorting
// on the frontend. NON_NULL / includeSku for the same admin-only sku redaction as
// CartItemResponse and ProductResponse.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WishlistItemResponse(Long productId, String sku, String name, BigDecimal unitPrice, String imageUrl, Instant savedAt) {

    public static WishlistItemResponse from(WishlistItem item, boolean includeSku) {
        var product = item.getProduct();
        String imageUrl = product.getImages().isEmpty() ? null : product.getImages().get(0).getImageUrl();
        return new WishlistItemResponse(
                product.getId(),
                includeSku ? product.getSku() : null,
                product.getName(),
                product.getUnitPrice(),
                imageUrl,
                item.getCreatedAt()
        );
    }
}
