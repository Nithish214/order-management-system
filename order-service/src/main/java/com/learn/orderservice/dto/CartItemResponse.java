package com.learn.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learn.orderservice.entity.CartItem;

import java.math.BigDecimal;

// unitPrice/name/sku are read live from the linked Product here, never stored on
// CartItem itself -- see that entity's own comment for why. NON_NULL for the same
// redaction reasoning as ProductResponse: sku is omitted entirely for a non-admin caller,
// not present as a literal null.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartItemResponse(Long productId, String sku, String name, BigDecimal unitPrice, int quantity, String imageUrl) {

    // sku included only for callers already guaranteed admin (mirrors
    // ProductResponse#from(Product)'s same-shaped overload below).
    public static CartItemResponse from(CartItem item) {
        return from(item, true);
    }

    public static CartItemResponse from(CartItem item, boolean includeSku) {
        var product = item.getProduct();
        String imageUrl = product.getImages().isEmpty() ? null : product.getImages().get(0).getImageUrl();
        return new CartItemResponse(
                product.getId(),
                includeSku ? product.getSku() : null,
                product.getName(),
                product.getUnitPrice(),
                item.getQuantity(),
                imageUrl
        );
    }
}
