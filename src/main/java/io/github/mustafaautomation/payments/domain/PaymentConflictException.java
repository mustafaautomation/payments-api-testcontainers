package io.github.mustafaautomation.payments.domain;

/** The request is valid but not allowed in the payment's current state. Mapped to HTTP 409. */
public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String message) {
        super(message);
    }
}
