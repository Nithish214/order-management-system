package com.learn.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learn.orderservice.entity.Product;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
    // Null (omitted from the JSON) for a product that's never had one written -- see
    // Product entity's own comment on which products currently do.
    private String description;
    // Empty, not null, for a product with no specs -- an empty JSON object ({}) is a
    // simpler thing for the frontend to render (map over zero entries) than a field
    // that's sometimes absent and sometimes an object.
    private Map<String, String> specs;
    // Null (omitted from the JSON, via this class's NON_NULL setting) for a product with
    // zero reviews -- distinct from a literal 0, which would misleadingly read as "rated
    // zero stars" rather than "not yet rated at all". Only ever populated by
    // ProductController's single-product GET (see its own comment) -- the list/search
    // endpoints deliberately leave this null rather than pay for a bulk aggregate query
    // across a whole page of products for what's a nice-to-have there, not the core of
    // this feature.
    private Double averageRating;
    // 0, not null, even when averageRating is null -- "reviewCount": 0 is exactly true and
    // needs no special-casing on the frontend, unlike averageRating's "no average exists"
    // case above.
    private int reviewCount;

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
        response.setDescription(product.getDescription());
        response.setSpecs(product.getSpecs());
        return response;
    }
}
