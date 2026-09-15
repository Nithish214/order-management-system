package com.learn.orderservice.dto;

import com.learn.orderservice.entity.Product;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductResponse {

    private Long id;
    private String sku;
    private String name;
    private BigDecimal unitPrice;
    // Null for any product without an uploaded image yet -- the frontend shows a plain
    // placeholder in that case rather than a broken image icon.
    private String imageUrl;

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setUnitPrice(product.getUnitPrice());
        response.setImageUrl(product.getImageUrl());
        return response;
    }
}
