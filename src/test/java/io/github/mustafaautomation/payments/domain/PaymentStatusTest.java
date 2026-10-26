package io.github.mustafaautomation.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentStatusTest {

    @ParameterizedTest(name = "{0}: capture={1} void={2} refund={3}")
    @CsvSource({
        "AUTHORIZED, true, true, false",
        "CAPTURED, false, false, true",
        "PARTIALLY_REFUNDED, false, false, true",
        "REFUNDED, false, false, false",
        "VOIDED, false, false, false"
    })
    void transitionTable(PaymentStatus status, boolean capture, boolean voidable, boolean refund) {
        assertThat(status.canCapture()).isEqualTo(capture);
        assertThat(status.canVoid()).isEqualTo(voidable);
        assertThat(status.canRefund()).isEqualTo(refund);
    }

    @Test
    void statusAfterRefund() {
        assertThat(PaymentStatus.afterRefund(1000, 1)).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(PaymentStatus.afterRefund(1000, 999)).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(PaymentStatus.afterRefund(1000, 1000)).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundTotalMustBeWithinCapturedAmount() {
        assertThatThrownBy(() -> PaymentStatus.afterRefund(1000, 1001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentStatus.afterRefund(1000, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
