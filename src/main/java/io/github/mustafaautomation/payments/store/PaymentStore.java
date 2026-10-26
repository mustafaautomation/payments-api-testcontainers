package io.github.mustafaautomation.payments.store;

import io.github.mustafaautomation.payments.domain.Payment;
import io.github.mustafaautomation.payments.domain.PaymentStatus;
import io.github.mustafaautomation.payments.domain.Refund;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL through JdbcClient, so the queries and locks are visible. */
@Repository
public class PaymentStore {

    private final JdbcClient jdbc;

    public PaymentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Payment p) {
        jdbc.sql("""
                insert into payments (id, merchant_id, idempotency_key, request_hash, amount_minor,
                                      currency, status, captured_minor, refunded_minor, created_at)
                values (:id, :merchant, :key, :hash, :amount, :currency, :status, 0, 0, :createdAt)
                """)
                .param("id", p.id())
                .param("merchant", p.merchantId())
                .param("key", p.idempotencyKey())
                .param("hash", p.requestHash())
                .param("amount", p.amountMinor())
                .param("currency", p.currency())
                .param("status", p.status().name())
                .param("createdAt", p.createdAt())
                .update();
    }

    public Optional<Payment> findById(UUID id) {
        return jdbc.sql("select * from payments where id = :id").param("id", id).query(PaymentStore::map).optional();
    }

    /** Row lock for the rest of the transaction, so concurrent refunds are applied one at a time. */
    public Optional<Payment> findByIdForUpdate(UUID id) {
        return jdbc.sql("select * from payments where id = :id for update")
                .param("id", id)
                .query(PaymentStore::map)
                .optional();
    }

    public Optional<Payment> findByIdempotencyKey(String merchantId, String key) {
        return jdbc.sql("select * from payments where merchant_id = :merchant and idempotency_key = :key")
                .param("merchant", merchantId)
                .param("key", key)
                .query(PaymentStore::map)
                .optional();
    }

    public void updateState(UUID id, PaymentStatus status, long capturedMinor, long refundedMinor) {
        jdbc.sql("""
                update payments set status = :status, captured_minor = :captured, refunded_minor = :refunded
                where id = :id
                """)
                .param("status", status.name())
                .param("captured", capturedMinor)
                .param("refunded", refundedMinor)
                .param("id", id)
                .update();
    }

    public void insertRefund(Refund r) {
        jdbc.sql("insert into refunds (id, payment_id, amount_minor, created_at) values (:id, :pid, :amount, :at)")
                .param("id", r.id())
                .param("pid", r.paymentId())
                .param("amount", r.amountMinor())
                .param("at", r.createdAt())
                .update();
    }

    public List<Refund> refundsFor(UUID paymentId) {
        return jdbc.sql("select * from refunds where payment_id = :pid order by created_at")
                .param("pid", paymentId)
                .query((rs, n) -> new Refund(
                        rs.getObject("id", UUID.class),
                        rs.getObject("payment_id", UUID.class),
                        rs.getLong("amount_minor"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    private static Payment map(ResultSet rs, int rowNum) throws SQLException {
        return new Payment(
                rs.getObject("id", UUID.class),
                rs.getString("merchant_id"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getLong("captured_minor"),
                rs.getLong("refunded_minor"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
