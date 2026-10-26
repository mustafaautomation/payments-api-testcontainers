package io.github.mustafaautomation.payments.api;

import io.github.mustafaautomation.payments.domain.Money;
import io.github.mustafaautomation.payments.domain.Payment;
import io.github.mustafaautomation.payments.domain.Refund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class Dtos {

    private Dtos() {}

    public record CreatePaymentRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String merchantId,
            @NotBlank String amount,
            @NotBlank String currency) {}

    public record RefundRequest(@NotBlank String amount) {}

    public record PaymentResponse(
            UUID id,
            String merchantId,
            String amount,
            String currency,
            String status,
            String capturedAmount,
            String refundedAmount,
            OffsetDateTime createdAt) {

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(
                    p.id(), p.merchantId(),
                    Money.format(p.amountMinor(), p.currency()), p.currency(), p.status().name(),
                    Money.format(p.capturedMinor(), p.currency()), Money.format(p.refundedMinor(), p.currency()),
                    p.createdAt());
        }
    }

    public record RefundResponse(UUID id, UUID paymentId, String amount, String currency, OffsetDateTime createdAt) {

        static RefundResponse from(Refund r, String currency) {
            return new RefundResponse(r.id(), r.paymentId(), Money.format(r.amountMinor(), currency), currency,
                    r.createdAt());
        }
    }
}
