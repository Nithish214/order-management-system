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

    // One of the CALLER's saved addresses -- required, since an order with nowhere to send it
    // isn't a complete order. Only an id crosses the wire: the address's actual fields are read
    // from the database and copied onto the order server-side (see OrderCreationService), never
    // taken from the client, and an id that isn't the caller's own is rejected outright.
    @NotNull(message = "addressId is required")
    private Long addressId;

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
