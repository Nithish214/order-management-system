package com.learn.orderservice.exception;

// Deliberately not org.springframework.security.access.AccessDeniedException -- Order
// Service has no Spring Security dependency at all and shouldn't need one just to signal
// "not yours" (same reasoning as elsewhere: this service stays unaware of the auth
// framework, it just enforces a plain ownership check on data it already has).
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
