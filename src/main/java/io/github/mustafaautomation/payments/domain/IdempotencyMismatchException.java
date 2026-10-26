package io.github.mustafaautomation.payments.domain;

/** The idempotency key was already used with a different request body. Mapped to HTTP 422. */
public class IdempotencyMismatchException extends RuntimeException {
    public IdempotencyMismatchException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request");
    }
}
