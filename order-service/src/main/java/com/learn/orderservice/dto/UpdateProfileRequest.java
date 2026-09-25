package com.learn.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Email is deliberately not here: it's tied to the Cognito identity (the login), so changing
// it in this database alone would just be overwritten by the next login sync.
//
// phoneNumber: null or blank clears it. Otherwise digits with the usual separators -- an
// optional leading +, must start and end on a digit ("+44 20 7946 0958", "(555) 123-4567").
// Deliberately permissive about format: it's contact info for a courier, not something to
// reject a real number over because of how someone likes to type it.
public record UpdateProfileRequest(
        @NotBlank(message = "name is required") @Size(max = 255, message = "name must be at most 255 characters")
        String name,
        @Size(max = 20, message = "phoneNumber must be at most 20 characters")
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()\\-]{5,18}[0-9]$", message = "phoneNumber must be a valid phone number")
        String phoneNumber
) {
}
