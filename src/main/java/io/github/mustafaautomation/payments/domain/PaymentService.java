package io.github.mustafaautomation.payments.domain;

import io.github.mustafaautomation.payments.store.PaymentStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    /** Result of a create call: the payment and whether it was replayed from an earlier request. */
    public record CreateResult(Payment payment, boolean replayed) {}

    private final PaymentStore store;
    private final Clock clock;

    @Autowired
    public PaymentService(PaymentStore store) {
        this(store, Clock.systemUTC());
    }

    PaymentService(PaymentStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * Creates an authorised payment. The same Idempotency-Key with the same request returns the
     * original payment; with a different request it is rejected. Two concurrent requests with one
     * key race on the unique constraint, and the loser reads the winner's row.
     */
    public CreateResult create(String merchantId, String idempotencyKey, String amount, String currencyCode) {
        long amountMinor = Money.toMinor(amount, currencyCode);
        String hash = RequestFingerprint.of(merchantId, amountMinor, currencyCode);

        var existing = store.findByIdempotencyKey(merchantId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash, idempotencyKey);
        }

        Payment payment = new Payment(
                UUID.randomUUID(), merchantId, idempotencyKey, hash, amountMinor, currencyCode,
                PaymentStatus.AUTHORIZED, 0, 0, OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
        try {
            store.insert(payment);
            return new CreateResult(payment, false);
        } catch (DuplicateKeyException race) {
            Payment winner = store.findByIdempotencyKey(merchantId, idempotencyKey).orElseThrow(() -> race);
            return replay(winner, hash, idempotencyKey);
        }
    }

    private static CreateResult replay(Payment existing, String hash, String key) {
        if (!existing.requestHash().equals(hash)) {
            throw new IdempotencyMismatchException(key);
        }
        return new CreateResult(existing, true);
    }

    public Payment get(UUID id) {
        return store.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public java.util.List<Refund> refunds(UUID id) {
        get(id);
        return store.refundsFor(id);
    }

    @Transactional
    public Payment capture(UUID id) {
        Payment p = store.findByIdForUpdate(id).orElseThrow(() -> new PaymentNotFoundException(id));
        if (!p.status().canCapture()) {
            throw new PaymentConflictException("cannot capture a payment in status " + p.status());
        }
        store.updateState(id, PaymentStatus.CAPTURED, p.amountMinor(), 0);
        return get(id);
    }

    @Transactional
    public Payment voidPayment(UUID id) {
        Payment p = store.findByIdForUpdate(id).orElseThrow(() -> new PaymentNotFoundException(id));
        if (!p.status().canVoid()) {
            throw new PaymentConflictException("cannot void a payment in status " + p.status());
        }
        store.updateState(id, PaymentStatus.VOIDED, 0, 0);
        return get(id);
    }

    /** Refunds part or all of the captured amount. The row lock serialises concurrent refunds. */
    @Transactional
    public Refund refund(UUID id, String amount) {
        Payment p = store.findByIdForUpdate(id).orElseThrow(() -> new PaymentNotFoundException(id));
        if (!p.status().canRefund()) {
            throw new PaymentConflictException("cannot refund a payment in status " + p.status());
        }
        long amountMinor = Money.toMinor(amount, p.currency());
        long remaining = p.capturedMinor() - p.refundedMinor();
        if (amountMinor > remaining) {
            throw new PaymentConflictException("refund of " + Money.format(amountMinor, p.currency())
                    + " exceeds the refundable balance of " + Money.format(remaining, p.currency()));
        }
        long refunded = p.refundedMinor() + amountMinor;
        store.updateState(id, PaymentStatus.afterRefund(p.capturedMinor(), refunded), p.capturedMinor(), refunded);
        Refund refund = new Refund(UUID.randomUUID(), id, amountMinor,
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
        store.insertRefund(refund);
        return refund;
    }
}
