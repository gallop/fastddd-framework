package com.gallop.fastddd.identity.spring.web;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PKCE 生成契约：S256 派生正确性锚定 RFC 7636 附录 B 官方测试向量；
 * verifier 长度/字符集落在 RFC 允许区间（43–128，[A-Za-z0-9-._~]）。
 */
class PkceTest {

    /** RFC 7636 Appendix B：verifier → challenge 官方向量。 */
    private static final String RFC_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    private static final String RFC_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void challengeMatchesRfc7636Vector() {
        assertThat(Pkce.challenge(RFC_VERIFIER)).isEqualTo(RFC_CHALLENGE);
    }

    @Test
    void generatedVerifierIsRandomWithinRfcShape() {
        String first = Pkce.verifier();
        String second = Pkce.verifier();
        // 64 字节 → base64url 无填充 = 86 字符（43–128 区间）
        assertThat(first).hasSize(86);
        assertThat(second).hasSize(86);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("[A-Za-z0-9_-]{43,128}");
        // base64url 合法性（可解码回 64 字节）
        assertThat(Base64.getUrlDecoder().decode(first)).hasSize(64);
    }
}
