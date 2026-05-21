package com.gallop.fastddd.common.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HmacUtilsTest {

    @Test
    void sign_stringMessage_producesHexHmac() {
        String sig = HmacUtils.sign("hello", "secret");
        assertNotNull(sig);
        assertEquals(64, sig.length()); // SHA-256 = 32 bytes = 64 hex chars
    }

    @Test
    void sign_sameInputs_sameOutput() {
        String sig1 = HmacUtils.sign("message", "key");
        String sig2 = HmacUtils.sign("message", "key");
        assertEquals(sig1, sig2);
    }

    @Test
    void sign_differentInputs_differentOutput() {
        String sig1 = HmacUtils.sign("message1", "key");
        String sig2 = HmacUtils.sign("message2", "key");
        assertNotEquals(sig1, sig2);
    }

    @Test
    void sign_withTimestamp_includesTimestampInMac() {
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        String sig1 = HmacUtils.sign(body, "1234567890", "secret");
        String sig2 = HmacUtils.sign(body, "9999999999", "secret");
        assertNotEquals(sig1, sig2);
    }

    @Test
    void sign_nullTimestamp_sameAsNoTimestamp() {
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        String sigWithNull = HmacUtils.sign(body, null, "secret");
        String sigDirect = HmacUtils.sign(new String(body, StandardCharsets.UTF_8), "secret");
        assertEquals(sigDirect, sigWithNull);
    }

    @Test
    void verify_matchingSignatures_returnsTrue() {
        String sig = HmacUtils.sign("message", "key");
        assertTrue(HmacUtils.verify(sig, sig));
    }

    @Test
    void verify_mismatchedSignatures_returnsFalse() {
        String sig1 = HmacUtils.sign("message1", "key");
        String sig2 = HmacUtils.sign("message2", "key");
        assertFalse(HmacUtils.verify(sig1, sig2));
    }
}
