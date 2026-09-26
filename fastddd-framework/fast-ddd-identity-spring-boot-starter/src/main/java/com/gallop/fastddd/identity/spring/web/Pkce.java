package com.gallop.fastddd.identity.spring.web;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE（RFC 7636，S256）生成器：verifier 随机 64 字节 → base64url 无填充（86 字符，
 * 落在 43–128 区间、字符集 [A-Za-z0-9_-] ⊂ [A-Za-z0-9-._~]）；challenge 为 verifier
 * SHA-256 的 base64url 无填充。
 *
 * <p>verifier 与 state 一一对应保存（LoginStateEntry），不写入日志；challenge 随
 * 授权 URL 出现在浏览器地址栏（公开值，无泄露面）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Pkce() {
    }

    /** 随机 code_verifier（一次性，与 state 同时生成）。 */
    static String verifier() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** S256 code_challenge = base64url(SHA-256(verifier))，无填充。 */
    static String challenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(
                    verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256（不可能形态）", e);
        }
    }
}
