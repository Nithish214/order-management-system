package com.learn.orderservice.exception;

// Thrown when a request conflicts with the order's current state -- right now that's just
// "trying to cancel an order that's already CONFIRMED/CANCELLED/REJECTED beyond the point
// where cancelling it means anything." Maps to 409 Conflict (see GlobalExceptionHandler),
// the correct status for "this request is otherwise well-formed, but the resource isn't in
// a state that allows it" -- distinct from 400 (the request itself is malformed) or 404
// (the resource doesn't exist at all).
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
