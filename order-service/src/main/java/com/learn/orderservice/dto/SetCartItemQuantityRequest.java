package com.learn.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetCartItemQuantityRequest {

    // Deliberately no @Min here -- zero or negative means "remove this item" (see
    // CartController#setItemQuantity), the same convention the frontend's own cart
    // already used locally before this endpoint existed.
    @NotNull(message = "quantity is required")
    private Integer quantity;
}
