package com.example.fairnesstracker.pagerduty.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Checks PagerDuty's {@code X-PagerDuty-Signature}: {@code v1=} and the hex HMAC-SHA256 of the body. While a
 * webhook secret is being rotated PagerDuty signs with each one, comma-separated ({@code v1=ab12,v1=cd34});
 * the body is genuine when any of them matches.
 */
public final class HmacVerifier {

    private static final String VERSION = "v1=";

    private HmacVerifier() {
    }

    public static boolean verify(String payload, String signatureHeader, String secret) {
        if (payload == null || payload.isBlank() || signatureHeader == null || signatureHeader.isBlank()
                || secret == null || secret.isBlank()) {
            return false;
        }
        byte[] expected = (VERSION + HexFormat.of().formatHex(hmac(payload, secret))).getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (String signature : signatureHeader.split(",")) {
            // Compare every one in constant time, without stopping early
            matched |= MessageDigest.isEqual(expected, signature.strip().getBytes(StandardCharsets.UTF_8));
        }
        return matched;
    }

    private static byte[] hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }
}
