package com.learn.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.learn.orderservice.entity.ShippingAddress;

public record AddressResponse(
        Long id,
        String label,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        @JsonProperty("isDefault") boolean isDefault
) {
    public static AddressResponse from(ShippingAddress address) {
        return new AddressResponse(
                address.getId(), address.getLabel(), address.getLine1(), address.getLine2(),
                address.getCity(), address.getState(), address.getPostalCode(), address.getCountry(),
                address.isDefaultAddress());
    }
}
