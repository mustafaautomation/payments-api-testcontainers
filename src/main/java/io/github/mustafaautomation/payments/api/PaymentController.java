package io.github.mustafaautomation.payments.api;

import io.github.mustafaautomation.payments.api.Dtos.CreatePaymentRequest;
import io.github.mustafaautomation.payments.api.Dtos.PaymentResponse;
import io.github.mustafaautomation.payments.api.Dtos.RefundRequest;
import io.github.mustafaautomation.payments.api.Dtos.RefundResponse;
import io.github.mustafaautomation.payments.domain.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest body) {
        var result = service.create(body.merchantId(), idempotencyKey, body.amount(), body.currency());
        var response = PaymentResponse.from(result.payment());
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(response);
        }
        return ResponseEntity.created(URI.create("/api/v1/payments/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(service.get(id));
    }

    @PostMapping("/{id}/capture")
    public PaymentResponse capture(@PathVariable UUID id) {
        return PaymentResponse.from(service.capture(id));
    }

    @PostMapping("/{id}/void")
    public PaymentResponse voidPayment(@PathVariable UUID id) {
        return PaymentResponse.from(service.voidPayment(id));
    }

    @PostMapping("/{id}/refunds")
    public ResponseEntity<RefundResponse> refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest body) {
        var refund = service.refund(id, body.amount());
        var currency = service.get(id).currency();
        return ResponseEntity.status(HttpStatus.CREATED).body(RefundResponse.from(refund, currency));
    }

    @GetMapping("/{id}/refunds")
    public List<RefundResponse> refunds(@PathVariable UUID id) {
        var payment = service.get(id);
        return service.refunds(id).stream().map(r -> RefundResponse.from(r, payment.currency())).toList();
    }
}
