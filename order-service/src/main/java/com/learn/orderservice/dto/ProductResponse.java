package com.learn.orderservice.dto;

import com.learn.orderservice.entity.Product;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProductResponse {

    private Long id;
    private String sku;
    private String name;
    private BigDecimal unitPrice;
    // Empty for any product without an uploaded image yet -- the frontend shows a plain
    // placeholder in that case rather than a broken image icon. Ordered oldest-first, so
    // images.get(0) (when present) is this product's cover/thumbnail image everywhere a
    // single image is shown (the product list row, cart) -- see Product entity's comment.
    private List<ProductImageResponse> images;

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setUnitPrice(product.getUnitPrice());
        response.setImages(product.getImages().stream().map(ProductImageResponse::from).toList());
        return response;
    }
}
