create table payments (
    id               uuid        primary key,
    merchant_id      varchar(64) not null,
    idempotency_key  varchar(64) not null,
    request_hash     char(64)    not null,
    amount_minor     bigint      not null check (amount_minor > 0),
    currency         char(3)     not null,
    status           varchar(20) not null,
    captured_minor   bigint      not null default 0,
    refunded_minor   bigint      not null default 0,
    created_at       timestamptz not null default now(),
    constraint uq_payments_merchant_idempotency unique (merchant_id, idempotency_key),
    constraint ck_payments_capture_le_amount check (captured_minor <= amount_minor),
    constraint ck_payments_refund_le_capture check (refunded_minor <= captured_minor)
);

create table refunds (
    id            uuid        primary key,
    payment_id    uuid        not null references payments (id),
    amount_minor  bigint      not null check (amount_minor > 0),
    created_at    timestamptz not null default now()
);

create index ix_refunds_payment on refunds (payment_id);
