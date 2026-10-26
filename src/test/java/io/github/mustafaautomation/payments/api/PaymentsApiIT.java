package io.github.mustafaautomation.payments.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Black-box API tests: the real Spring Boot app on a random port, a real PostgreSQL in Docker
 * (Testcontainers), and REST Assured as the client. Nothing in the data layer is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresContainerConfig.class)
class PaymentsApiIT {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/payments";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private static String newKey() {
        return "key-" + UUID.randomUUID();
    }

    private static Response create(String key, String merchant, String amount, String currency) {
        return given().contentType(ContentType.JSON)
                .header("Idempotency-Key", key)
                .body(Map.of("merchantId", merchant, "amount", amount, "currency", currency))
                .post();
    }

    private static String createdId(String amount, String currency) {
        return create(newKey(), "merchant-1", amount, currency).then().statusCode(201).extract().path("id");
    }

    private static String capturedId(String amount) {
        String id = createdId(amount, "USD");
        given().post("/{id}/capture", id).then().statusCode(200);
        return id;
    }

    private static Response refund(String id, String amount) {
        return given().contentType(ContentType.JSON).body(Map.of("amount", amount)).post("/{id}/refunds", id);
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        void createsAnAuthorisedPaymentWithLocation() {
            create(newKey(), "merchant-1", "12.3", "USD").then()
                    .statusCode(201)
                    .header("Location", matchesPattern("^/api/v1/payments/[0-9a-f-]{36}$"))
                    .body("status", equalTo("AUTHORIZED"))
                    .body("amount", equalTo("12.30"))
                    .body("currency", equalTo("USD"))
                    .body("capturedAmount", equalTo("0.00"))
                    .body("createdAt", notNullValue());
        }

        @Test
        void usesTheCurrencyMinorUnit() {
            create(newKey(), "merchant-1", "500", "JPY").then().statusCode(201).body("amount", equalTo("500"));
            create(newKey(), "merchant-1", "1.250", "KWD").then().statusCode(201).body("amount", equalTo("1.250"));
        }

        @Test
        void rejectsTooManyDecimalsAsProblemDetail() {
            create(newKey(), "merchant-1", "10.001", "USD").then()
                    .statusCode(400)
                    .contentType("application/problem+json")
                    .body("type", equalTo("urn:problem:invalid-amount"))
                    .body("detail", equalTo("amount has more than 2 decimal places for USD"));
        }

        @Test
        void rejectsUnknownCurrency() {
            create(newKey(), "merchant-1", "10", "ABC").then()
                    .statusCode(400)
                    .body("detail", equalTo("unknown currency ABC"));
        }

        @Test
        void requiresAnIdempotencyKey() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("merchantId", "merchant-1", "amount", "1", "currency", "USD"))
                    .post()
                    .then()
                    .statusCode(400);
        }

        @Test
        void rejectsAMalformedIdempotencyKey() {
            create("short", "merchant-1", "1", "USD").then().statusCode(400);
        }

        @Test
        void rejectsMissingFields() {
            given().contentType(ContentType.JSON)
                    .header("Idempotency-Key", newKey())
                    .body(Map.of("merchantId", "merchant-1"))
                    .post()
                    .then()
                    .statusCode(400);
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        void retryWithTheSameKeyReplaysTheOriginalPayment() {
            String key = newKey();
            String first = create(key, "merchant-1", "20.00", "EUR").then().statusCode(201).extract().path("id");

            create(key, "merchant-1", "20", "EUR").then()
                    .statusCode(200)
                    .header("Idempotent-Replayed", "true")
                    .body("id", equalTo(first));
        }

        @Test
        void sameKeyWithADifferentAmountIsRejected() {
            String key = newKey();
            create(key, "merchant-1", "20.00", "EUR").then().statusCode(201);

            create(key, "merchant-1", "20.01", "EUR").then()
                    .statusCode(422)
                    .body("type", equalTo("urn:problem:idempotency-key-reused"));
        }

        @Test
        void keysAreScopedPerMerchant() {
            String key = newKey();
            String a = create(key, "merchant-a", "5.00", "USD").then().statusCode(201).extract().path("id");
            String b = create(key, "merchant-b", "5.00", "USD").then().statusCode(201).extract().path("id");
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void concurrentRequestsWithOneKeyCreateExactlyOnePayment() throws Exception {
            String key = newKey();
            List<Response> responses = runConcurrently(10, () -> create(key, "merchant-1", "99.99", "USD"));

            long created = responses.stream().filter(r -> r.statusCode() == 201).count();
            long replayed = responses.stream().filter(r -> r.statusCode() == 200).count();
            long distinctIds = responses.stream().map(r -> r.<String>path("id")).distinct().count();
            Integer rows = jdbc.sql("select count(*) from payments where idempotency_key = :k")
                    .param("k", key).query(Integer.class).single();

            assertThat(created).isEqualTo(1);
            assertThat(replayed).isEqualTo(9);
            assertThat(distinctIds).isEqualTo(1);
            assertThat(rows).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        void captureThenPartialThenFullRefund() {
            String id = capturedId("100.00");

            refund(id, "40.00").then().statusCode(201).body("amount", equalTo("40.00"));
            given().get("/{id}", id).then()
                    .body("status", equalTo("PARTIALLY_REFUNDED"))
                    .body("refundedAmount", equalTo("40.00"));

            refund(id, "60.00").then().statusCode(201);
            given().get("/{id}", id).then()
                    .body("status", equalTo("REFUNDED"))
                    .body("refundedAmount", equalTo("100.00"));
            given().get("/{id}/refunds", id).then().statusCode(200).body("size()", equalTo(2));
        }

        @Test
        void refundMoreThanCapturedIsAConflict() {
            String id = capturedId("10.00");

            refund(id, "10.01").then()
                    .statusCode(409)
                    .body("type", equalTo("urn:problem:invalid-state"))
                    .body("detail", equalTo("refund of 10.01 exceeds the refundable balance of 10.00"));
        }

        @Test
        void cannotRefundBeforeCapture() {
            refund(createdId("10.00", "USD"), "1.00").then().statusCode(409);
        }

        @Test
        void cannotCaptureTwice() {
            String id = capturedId("10.00");
            given().post("/{id}/capture", id).then().statusCode(409);
        }

        @Test
        void voidOnlyWhileAuthorised() {
            String authorised = createdId("10.00", "USD");
            given().post("/{id}/void", authorised).then().statusCode(200).body("status", equalTo("VOIDED"));
            given().post("/{id}/capture", authorised).then().statusCode(409);

            String captured = capturedId("10.00");
            given().post("/{id}/void", captured).then().statusCode(409);
        }

        @Test
        void unknownPaymentIs404() {
            given().get("/{id}", UUID.randomUUID()).then()
                    .statusCode(404)
                    .body("type", equalTo("urn:problem:payment-not-found"));
        }

        @Test
        void concurrentRefundsNeverExceedTheCapturedAmount() throws Exception {
            String id = capturedId("100.00");

            List<Response> responses = runConcurrently(5, () -> refund(id, "30.00"));

            long ok = responses.stream().filter(r -> r.statusCode() == 201).count();
            long conflict = responses.stream().filter(r -> r.statusCode() == 409).count();
            assertThat(ok).isEqualTo(3);
            assertThat(conflict).isEqualTo(2);
            given().get("/{id}", id).then()
                    .body("refundedAmount", equalTo("90.00"))
                    .body("status", equalTo("PARTIALLY_REFUNDED"));
        }
    }

    @Nested
    @DisplayName("Database constraints")
    class DatabaseConstraints {

        @Test
        void schemaRejectsRefundedAboveCapturedEvenIfCodeIsBypassed() {
            String id = capturedId("10.00");
            assertThatThrownBy(() -> jdbc.sql("update payments set refunded_minor = 1001 where id = :id")
                    .param("id", UUID.fromString(id)).update())
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    /** Fires n calls at once (released together by a latch) and returns all responses. */
    private static List<Response> runConcurrently(int n, Callable<Response> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Response>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Response> out = new ArrayList<>();
            for (Future<Response> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
