package io.github.mustafaautomation.payments.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Payment(
        UUID id,
        String merchantId,
        String idempotencyKey,
        String requestHash,
        long amountMinor,
        String currency,
        PaymentStatus status,
        long capturedMinor,
        long refundedMinor,
        OffsetDateTime createdAt) {}
