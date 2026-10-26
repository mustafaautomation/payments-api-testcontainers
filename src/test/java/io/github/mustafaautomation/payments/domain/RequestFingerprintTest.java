package io.github.mustafaautomation.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    @Test
    void sameRequestGivesSameHash() {
        assertThat(RequestFingerprint.of("m1", 1234, "USD")).isEqualTo(RequestFingerprint.of("m1", 1234, "USD"));
    }

    @Test
    void anyFieldChangeGivesDifferentHash() {
        String base = RequestFingerprint.of("m1", 1234, "USD");
        assertThat(RequestFingerprint.of("m2", 1234, "USD")).isNotEqualTo(base);
        assertThat(RequestFingerprint.of("m1", 1235, "USD")).isNotEqualTo(base);
        assertThat(RequestFingerprint.of("m1", 1234, "EUR")).isNotEqualTo(base);
    }

    @Test
    void equivalentAmountsNormaliseToTheSameHash() {
        // "12.3" and "12.30" are the same money, so a retry with either must replay, not conflict.
        long a = Money.toMinor("12.3", "USD");
        long b = Money.toMinor("12.30", "USD");
        assertThat(RequestFingerprint.of("m1", a, "USD")).isEqualTo(RequestFingerprint.of("m1", b, "USD"));
    }

    @Test
    void isHexSha256() {
        assertThat(RequestFingerprint.of("m1", 1, "USD")).matches("^[0-9a-f]{64}$");
    }
}
