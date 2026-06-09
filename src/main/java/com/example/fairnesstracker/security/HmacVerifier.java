package com.example.fairnesstracker.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class HmacVerifier {

    private HmacVerifier() {
    }

    public static boolean verify(
            String payload,
            String receivedSignature,
            String secret) {

        try {

            if (payload == null || payload.isBlank()) {
                return false;
            }

            if (receivedSignature == null || receivedSignature.isBlank()) {
                return false;
            }

            if (secret == null || secret.isBlank()) {
                return false;
            }

            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec keySpec =
                    new SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256");

            mac.init(keySpec);

            byte[] hash =
                    mac.doFinal(
                            payload.getBytes(StandardCharsets.UTF_8));

            String expectedSignature =
                    "v1=" + HexFormat.of().formatHex(hash);

            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            return false;
        }
    }
}