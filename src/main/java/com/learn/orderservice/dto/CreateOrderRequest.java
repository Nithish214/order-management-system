package com.learn.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateOrderRequest {

    // No userId field, deliberately: who's placing the order comes from the X-User-Sub
    // header the Gateway sets from the caller's own validated JWT (see OrderController),
    // never from client-supplied JSON -- otherwise any authenticated caller could place
    // an order as anyone else just by changing this field.

    @NotNull(message = "items are required")
    @Valid
    private List<OrderItemRequest> items;

    @Getter
    @Setter
    public static class OrderItemRequest {

        @NotNull(message = "productId is required")
        private Long productId;

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be positive")
        private Integer quantity;
    }
}
