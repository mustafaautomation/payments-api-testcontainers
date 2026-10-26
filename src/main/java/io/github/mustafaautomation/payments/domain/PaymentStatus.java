package io.github.mustafaautomation.payments.domain;

/** Payment lifecycle. Allowed moves are encoded here so the rules live in one place. */
public enum PaymentStatus {
    AUTHORIZED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    VOIDED;

    public boolean canCapture() {
        return this == AUTHORIZED;
    }

    public boolean canVoid() {
        return this == AUTHORIZED;
    }

    public boolean canRefund() {
        return this == CAPTURED || this == PARTIALLY_REFUNDED;
    }

    /** Status after refunding so that {@code refundedMinor} of {@code capturedMinor} has been returned. */
    public static PaymentStatus afterRefund(long capturedMinor, long refundedMinor) {
        if (refundedMinor <= 0 || refundedMinor > capturedMinor) {
            throw new IllegalArgumentException("refunded total must be between 1 and the captured amount");
        }
        return refundedMinor == capturedMinor ? REFUNDED : PARTIALLY_REFUNDED;
    }
}
