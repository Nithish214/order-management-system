package com.learn.orderservice.exception;

// A well-formed request that can't be applied to the current state of things -- "you already
// have the maximum number of saved addresses", "someone else changed your default address at
// the same moment, try again". Maps to 409 Conflict (see GlobalExceptionHandler), the same
// status InvalidOrderStateException uses for its own "valid request, wrong state" case; a
// separate class only because that one is specifically about an order's status.
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
