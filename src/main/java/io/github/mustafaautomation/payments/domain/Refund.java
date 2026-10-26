package io.github.mustafaautomation.payments.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Refund(UUID id, UUID paymentId, long amountMinor, OffsetDateTime createdAt) {}
