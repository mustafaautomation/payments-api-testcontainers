package io.github.mustafaautomation.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0} {1} -> {2} minor units")
    @CsvSource({
        "12.34, USD, 1234",
        "12.3, USD, 1230",
        "12, USD, 1200",
        "0.01, EUR, 1",
        "1000000, USD, 100000000",
        "500, JPY, 500",
        "1.234, KWD, 1234",
        "12.340, USD, 1234"
    })
    void convertsUsingTheCurrencyMinorUnit(String amount, String currency, long expected) {
        assertThat(Money.toMinor(amount, currency)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "rejects {0} {1}")
    @CsvSource({
        "1.234, USD, more than 2 decimal places",
        "100.5, JPY, more than 0 decimal places",
        "0, USD, greater than zero",
        "0.00, USD, greater than zero",
        "-5, USD, positive decimal string",
        "1e3, USD, positive decimal string",
        "'12,50', EUR, positive decimal string",
        "1000000.01, USD, per-payment limit",
        "10, usd, 3-letter ISO 4217",
        "10, ABC, unknown currency",
        "10, XAU, no minor unit"
    })
    void rejectsInvalidAmounts(String amount, String currency, String message) {
        assertThatThrownBy(() -> Money.toMinor(amount, currency))
                .isInstanceOf(InvalidMoneyException.class)
                .hasMessageContaining(message);
    }

    @Test
    void formatsWithTheCurrencyScale() {
        assertThat(Money.format(1230, "USD")).isEqualTo("12.30");
        assertThat(Money.format(500, "JPY")).isEqualTo("500");
        assertThat(Money.format(1, "KWD")).isEqualTo("0.001");
        assertThat(Money.format(0, "EUR")).isEqualTo("0.00");
    }

    @Test
    void roundTripsWithoutFloatingPointError() {
        // 0.1 + 0.2 style errors cannot happen: everything is integer minor units.
        long total = Money.toMinor("0.10", "USD") + Money.toMinor("0.20", "USD");
        assertThat(Money.format(total, "USD")).isEqualTo("0.30");
    }
}
