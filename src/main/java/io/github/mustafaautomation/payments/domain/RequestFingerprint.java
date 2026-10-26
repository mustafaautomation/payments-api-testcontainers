package io.github.mustafaautomation.payments.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 of the normalised create-payment request, used to detect idempotency-key reuse. */
public final class RequestFingerprint {

    private RequestFingerprint() {}

    public static String of(String merchantId, long amountMinor, String currency) {
        String canonical = merchantId + "|" + amountMinor + "|" + currency;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
