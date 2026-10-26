# payments-api-testcontainers

[![CI](https://github.com/mustafaautomation/payments-api-testcontainers/actions/workflows/ci.yml/badge.svg)](https://github.com/mustafaautomation/payments-api-testcontainers/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)

A small payments API built so I could test the things that usually go wrong in payment systems: idempotent retries, money precision, illegal state changes and concurrent refunds. The integration tests run the real Spring Boot app against a real PostgreSQL started by Testcontainers, and drive it over HTTP with REST Assured.

This is a personal portfolio project. It is not based on any employer's or client's system.

## What the tests check

| Area | How it is tested |
|---|---|
| Idempotency | A retry with the same `Idempotency-Key` returns the original payment (`200`, `Idempotent-Replayed: true`); the same key with a different amount gets `422`; keys are scoped per merchant |
| Concurrent retries | 10 requests with one key are released at the same moment; exactly one payment row is created and the other 9 replay it |
| Money precision | Amounts are decimal strings converted to integer minor units using each currency's ISO 4217 scale (USD 2, JPY 0, KWD 3). `10.001 USD` and `100.5 JPY` are rejected; no floating point anywhere |
| Lifecycle | `AUTHORIZED -> CAPTURED -> PARTIALLY_REFUNDED -> REFUNDED`, or `AUTHORIZED -> VOIDED`. Capturing twice, refunding before capture, or voiding after capture returns `409` |
| Concurrent refunds | 5 parallel refunds of 30.00 against a 100.00 capture: exactly 3 succeed, 2 get `409`, refunded total is 90.00 (a `SELECT ... FOR UPDATE` row lock serialises them) |
| Defence in depth | A database check constraint rejects `refunded > captured` even when the application code is bypassed |
| Errors | Every error is an RFC 9457 problem detail (`application/problem+json`) with a stable `type` such as `urn:problem:invalid-amount` |

Unit tests (`*Test`) cover money conversion, the status transition table, request fingerprints and the service rules with the store mocked. Integration tests (`*IT`) re-check the behaviour end to end against PostgreSQL 17.

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/payments` | Header `Idempotency-Key` (8-64 chars). Body `{"merchantId","amount","currency"}`. `201` + `Location`, or `200` on replay |
| `GET` | `/api/v1/payments/{id}` | |
| `POST` | `/api/v1/payments/{id}/capture` | Full capture |
| `POST` | `/api/v1/payments/{id}/void` | Only while `AUTHORIZED` |
| `POST` | `/api/v1/payments/{id}/refunds` | Body `{"amount"}`, partial or full |
| `GET` | `/api/v1/payments/{id}/refunds` | |

## Run it

Requirements: JDK 21+, Maven 3.9+, and Docker for the integration tests.

```bash
mvn test        # unit tests only, no Docker needed
mvn verify      # unit tests + Testcontainers integration tests (needs Docker)
```

Run the app locally:

```bash
docker compose up -d            # PostgreSQL on localhost:5432
mvn spring-boot:run

curl -s -X POST localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: order-12345678' \
  -d '{"merchantId":"merchant-1","amount":"12.30","currency":"USD"}'
```

## CI

GitHub Actions runs `mvn -B verify` on JDK 21 and JDK 25. Unit tests and the Testcontainers integration tests both have to pass; reports are uploaded as artifacts.

## Design notes

- Plain SQL through Spring's `JdbcClient` so the queries and row locks are visible, with Flyway for the schema.
- The idempotency check reads first, then relies on a unique constraint `(merchant_id, idempotency_key)`. A request that loses the insert race reads the winner's row and replays it.
- The request fingerprint is a SHA-256 of the normalised request, so `"12.3"` and `"12.30"` count as the same request.

## Tech stack

Java 21 · Spring Boot 4.1 (Web MVC, JDBC, Validation) · PostgreSQL 17 · Flyway · JUnit 5 · AssertJ · Mockito · REST Assured 6 · Testcontainers 2 · GitHub Actions

## License

[MIT](LICENSE)
