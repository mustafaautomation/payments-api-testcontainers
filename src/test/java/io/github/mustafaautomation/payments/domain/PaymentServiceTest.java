package io.github.mustafaautomation.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.mustafaautomation.payments.store.PaymentStore;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/** Service rules with the store mocked. The same rules are re-checked against PostgreSQL in the ITs. */
class PaymentServiceTest {

    private PaymentStore store;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        store = mock(PaymentStore.class);
        service = new PaymentService(store, Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC));
    }

    private static Payment payment(PaymentStatus status, long amount, long captured, long refunded) {
        return new Payment(UUID.randomUUID(), "m1", "key-00000001", RequestFingerprint.of("m1", amount, "USD"),
                amount, "USD", status, captured, refunded, OffsetDateTime.parse("2026-10-26T10:00:00Z"));
    }

    @Test
    void createsAnAuthorisedPayment() {
        when(store.findByIdempotencyKey("m1", "key-00000001")).thenReturn(Optional.empty());

        var result = service.create("m1", "key-00000001", "10.00", "USD");

        assertThat(result.replayed()).isFalse();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(result.payment().amountMinor()).isEqualTo(1000);
        verify(store).insert(any());
    }

    @Test
    void replaysWhenTheKeyAndRequestMatch() {
        Payment existing = payment(PaymentStatus.AUTHORIZED, 1000, 0, 0);
        when(store.findByIdempotencyKey("m1", "key-00000001")).thenReturn(Optional.of(existing));

        var result = service.create("m1", "key-00000001", "10", "USD");

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment()).isSameAs(existing);
        verify(store, never()).insert(any());
    }

    @Test
    void rejectsKeyReuseWithADifferentAmount() {
        when(store.findByIdempotencyKey("m1", "key-00000001"))
                .thenReturn(Optional.of(payment(PaymentStatus.AUTHORIZED, 1000, 0, 0)));

        assertThatThrownBy(() -> service.create("m1", "key-00000001", "10.01", "USD"))
                .isInstanceOf(IdempotencyMismatchException.class);
    }

    @Test
    void losingAnInsertRaceReturnsTheWinnersPayment() {
        Payment winner = payment(PaymentStatus.AUTHORIZED, 1000, 0, 0);
        when(store.findByIdempotencyKey("m1", "key-00000001"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        doThrow(new DuplicateKeyException("uq_payments_merchant_idempotency")).when(store).insert(any());

        var result = service.create("m1", "key-00000001", "10.00", "USD");

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment()).isSameAs(winner);
    }

    @Test
    void refundCannotExceedTheRefundableBalance() {
        Payment p = payment(PaymentStatus.PARTIALLY_REFUNDED, 1000, 1000, 700);
        when(store.findByIdForUpdate(p.id())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.refund(p.id(), "3.01"))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("exceeds the refundable balance of 3.00");
        verify(store, never()).insertRefund(any());
    }

    @Test
    void refundingTheRemainderMarksThePaymentRefunded() {
        Payment p = payment(PaymentStatus.PARTIALLY_REFUNDED, 1000, 1000, 700);
        when(store.findByIdForUpdate(p.id())).thenReturn(Optional.of(p));

        service.refund(p.id(), "3.00");

        verify(store).updateState(p.id(), PaymentStatus.REFUNDED, 1000, 1000);
    }

    @Test
    void cannotCaptureAVoidedPayment() {
        Payment p = payment(PaymentStatus.VOIDED, 1000, 0, 0);
        when(store.findByIdForUpdate(p.id())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.capture(p.id())).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void unknownPaymentIsNotFound() {
        UUID id = UUID.randomUUID();
        when(store.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(PaymentNotFoundException.class);
    }
}
