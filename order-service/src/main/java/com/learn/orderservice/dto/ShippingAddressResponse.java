package com.learn.orderservice.dto;

import com.learn.orderservice.entity.ShippingAddressSnapshot;

// The address as it was when the order was placed -- no id and no label, because there is
// nothing to look up or edit: it's a record of where that order went, not a saved address.
public record ShippingAddressResponse(
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country
) {
    public static ShippingAddressResponse from(ShippingAddressSnapshot snapshot) {
        return new ShippingAddressResponse(
                snapshot.getLine1(), snapshot.getLine2(), snapshot.getCity(),
                snapshot.getState(), snapshot.getPostalCode(), snapshot.getCountry());
    }
}
