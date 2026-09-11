package com.learn.inventoryservice.dto;

import com.learn.inventoryservice.entity.ProductStock;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockResponse {

    private Long productId;
    private String sku;
    private Integer availableQuantity;

    public static StockResponse from(ProductStock stock) {
        StockResponse response = new StockResponse();
        response.setProductId(stock.getProductId());
        response.setSku(stock.getSku());
        response.setAvailableQuantity(stock.getAvailableQuantity());
        return response;
    }
}
