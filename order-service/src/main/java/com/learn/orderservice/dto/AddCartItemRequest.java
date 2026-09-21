package com.learn.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddCartItemRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    // Defaults to 1 if omitted -- matches the frontend's existing addItem(product,
    // quantity = 1) default, and every plain "Add to cart" button that doesn't pass one.
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity = 1;
}
