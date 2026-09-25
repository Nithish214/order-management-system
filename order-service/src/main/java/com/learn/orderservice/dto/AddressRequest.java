package com.learn.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body for both creating and updating a saved address -- same fields either way, and a
// PUT here replaces all of them (there's no partial-update form to get half-wrong).
//
// isDefault is a Boolean (not boolean) on purpose: absent means "no opinion", which is
// different from an explicit false. See AddressService for exactly how it's interpreted.
// @JsonProperty pins the JSON key to "isDefault" -- without it, some Jackson versions read
// a record component named isDefault as a getter for a property called "default".
public record AddressRequest(
        @NotBlank(message = "label is required") @Size(max = 50, message = "label must be at most 50 characters")
        String label,
        @NotBlank(message = "line1 is required") @Size(max = 200, message = "line1 must be at most 200 characters")
        String line1,
        @Size(max = 200, message = "line2 must be at most 200 characters")
        String line2,
        @NotBlank(message = "city is required") @Size(max = 100, message = "city must be at most 100 characters")
        String city,
        @NotBlank(message = "state is required") @Size(max = 100, message = "state must be at most 100 characters")
        String state,
        @NotBlank(message = "postalCode is required") @Size(max = 20, message = "postalCode must be at most 20 characters")
        String postalCode,
        @NotBlank(message = "country is required") @Size(max = 100, message = "country must be at most 100 characters")
        String country,
        @JsonProperty("isDefault") Boolean isDefault
) {
}
