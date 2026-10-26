package io.github.mustafaautomation.payments.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.regex.Pattern;

/**
 * Converts between decimal amount strings ("12.34") and integer minor units (1234),
 * using each currency's ISO 4217 number of decimal places. Amounts are never held
 * as floating point.
 */
public final class Money {

    /** Upper bound per payment, in major units, to keep amounts well inside a long. */
    public static final BigDecimal MAX_MAJOR = new BigDecimal("1000000");

    private static final Pattern DECIMAL = Pattern.compile("^\\d{1,12}(\\.\\d{1,4})?$");

    private Money() {}

    public static Currency currency(String code) {
        if (code == null || !code.matches("^[A-Z]{3}$")) {
            throw new InvalidMoneyException("currency must be a 3-letter ISO 4217 code");
        }
        try {
            Currency currency = Currency.getInstance(code);
            if (currency.getDefaultFractionDigits() < 0) {
                throw new InvalidMoneyException("currency " + code + " has no minor unit");
            }
            return currency;
        } catch (IllegalArgumentException e) {
            throw new InvalidMoneyException("unknown currency " + code);
        }
    }

    public static long toMinor(String amount, String currencyCode) {
        Currency currency = currency(currencyCode);
        if (amount == null || !DECIMAL.matcher(amount).matches()) {
            throw new InvalidMoneyException("amount must be a positive decimal string such as \"12.34\"");
        }
        BigDecimal value = new BigDecimal(amount);
        int digits = currency.getDefaultFractionDigits();
        if (value.stripTrailingZeros().scale() > digits) {
            throw new InvalidMoneyException(
                    "amount has more than " + digits + " decimal places for " + currency.getCurrencyCode());
        }
        if (value.signum() <= 0) {
            throw new InvalidMoneyException("amount must be greater than zero");
        }
        if (value.compareTo(MAX_MAJOR) > 0) {
            throw new InvalidMoneyException("amount exceeds the per-payment limit of " + MAX_MAJOR.toPlainString());
        }
        return value.movePointRight(digits).longValueExact();
    }

    public static String format(long minor, String currencyCode) {
        int digits = currency(currencyCode).getDefaultFractionDigits();
        return BigDecimal.valueOf(minor).movePointLeft(digits).setScale(digits).toPlainString();
    }
}
