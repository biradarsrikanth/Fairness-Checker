package com.example.fairnesstracker.pagerduty.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class HmacVerifierTest {

    private static final String BODY = "{\"event\": {\"id\": \"E1\"}}";

    @Test
    void acceptsTheSignatureMadeWithTheSecret() throws Exception {
        assertThat(HmacVerifier.verify(BODY, sign("current"), "current")).isTrue();
        assertThat(HmacVerifier.verify(BODY, sign("other"), "current")).isFalse();
        assertThat(HmacVerifier.verify(BODY + " ", sign("current"), "current")).isFalse();
    }

    @Test
    void whileASecretIsRotatedAnyOfTheSignaturesWillDo() throws Exception {
        // PagerDuty signs with the old and the new secret, comma-separated
        String both = sign("old") + "," + sign("current");
        assertThat(HmacVerifier.verify(BODY, both, "current")).isTrue();
        assertThat(HmacVerifier.verify(BODY, sign("current") + ", " + sign("old"), "current")).isTrue();
        assertThat(HmacVerifier.verify(BODY, sign("old") + "," + sign("older"), "current")).isFalse();
    }

    @Test
    void nothingMissingIsAccepted() throws Exception {
        assertThat(HmacVerifier.verify(BODY, null, "current")).isFalse();
        assertThat(HmacVerifier.verify(BODY, sign("current"), "")).isFalse();
        assertThat(HmacVerifier.verify("", sign("current"), "current")).isFalse();
        assertThat(HmacVerifier.verify(BODY, "v1=", "current")).isFalse();
    }

    private static String sign(String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v1=" + HexFormat.of().formatHex(mac.doFinal(BODY.getBytes(StandardCharsets.UTF_8)));
    }
}
