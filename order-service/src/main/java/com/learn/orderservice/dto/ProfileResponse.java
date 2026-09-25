package com.learn.orderservice.dto;

import com.learn.orderservice.entity.AppUser;

// A separate shape from UserResponse on purpose. GET /users and GET /users/{id} return
// UserResponse for ANY user to ANY logged-in caller; a phone number added there would be
// readable by every account. This is only ever returned for the caller's own row
// (/users/me), so it can safely carry the private fields.
public record ProfileResponse(String email, String name, String phoneNumber) {
    public static ProfileResponse from(AppUser user) {
        return new ProfileResponse(user.getEmail(), user.getName(), user.getPhoneNumber());
    }
}
