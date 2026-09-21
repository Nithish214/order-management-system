package com.learn.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learn.orderservice.entity.Product;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

// NON_NULL, specifically for sku below: when a caller isn't allowed to see it, the field
// is omitted from the JSON entirely ("this was never sent") rather than present as
// `"sku": null` ("here's an empty value") -- a clearer signal of "redacted" than a null,
// and consistent with the frontend already treating it as absent (ProductDetailPage's
// isAdmin-gated render).
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class ProductResponse {

    private Long id;
    private String sku;
    private String name;
    private BigDecimal unitPrice;
    private String category;
    // Empty for any product without an uploaded image yet -- the frontend shows a plain
    // placeholder in that case rather than a broken image icon. Ordered oldest-first, so
    // images.get(0) (when present) is this product's cover/thumbnail image everywhere a
    // single image is shown (the product list row, cart) -- see Product entity's comment.
    private List<ProductImageResponse> images;
    // Null for any product without an uploaded video -- omitted from the JSON entirely
    // (see the class's NON_NULL setting above), same "absent means none" convention as
    // sku being redacted, rather than a literal "videoUrl": null.
    private String videoUrl;

    // Includes sku -- safe default for callers that are already guaranteed admin-only by
    // SecurityConfig (addProductImage, deleteProductImage), unlike the three read
    // endpoints below, which any authenticated user can reach and need the caller's
    // actual role (see the other overload).
    public static ProductResponse from(Product product) {
        return from(product, true);
    }

    // sku is internal catalog bookkeeping, not something a regular customer needs --
    // same reasoning ProductDetailPage's frontend-only redaction already applied, now
    // enforced here too so a non-admin genuinely can't see it (e.g. via the network
    // tab), not merely isn't shown it. ProductController passes the caller's real role
    // (from the Gateway's X-User-Is-Admin header) for GET /products, GET /products/{id},
    // and GET /products/search -- the only three endpoints a non-admin can reach.
    public static ProductResponse from(Product product, boolean includeSku) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(includeSku ? product.getSku() : null);
        response.setName(product.getName());
        response.setUnitPrice(product.getUnitPrice());
        response.setCategory(product.getCategory());
        response.setImages(product.getImages().stream().map(ProductImageResponse::from).toList());
        response.setVideoUrl(product.getVideoUrl());
        return response;
    }
}
