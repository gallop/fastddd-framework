package com.gallop.fastddd.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * HMAC-SHA256 工具类
 *
 * <p>提供统一的 HMAC 签名与验证方法，避免各模块重复实现。
 *
 * <pre>{@code
 * // 签名
 * String sig = HmacUtils.sign("message", "secret");
 *
 * // 签名（拼接多个部分）
 * String sig = HmacUtils.sign(new byte[]{...}, "timestamp", "secret");
 *
 * // 验证（恒定时间比较，防时序攻击）
 * boolean ok = HmacUtils.verify("expected", "actual");
 * }</pre>
 *
 * @author gallop
 * @since 1.0.0
 */
public final class HmacUtils {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacUtils() {
    }

    /**
     * 对字符串消息进行 HMAC-SHA256 签名
     *
     * @param message 明文消息
     * @param secret  密钥
     * @return 十六进制签名字符串
     */
    public static String sign(String message, String secret) {
        return sign(message.getBytes(StandardCharsets.UTF_8), null, secret);
    }

    /**
     * 对字节数组 + 可选时间戳进行 HMAC-SHA256 签名
     *
     * @param body      消息体字节
     * @param timestamp 可选时间戳（为 null 时跳过）
     * @param secret    密钥
     * @return 十六进制签名字符串
     */
    public static String sign(byte[] body, String timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update(body);
            if (timestamp != null) {
                mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            }
            return bytesToHex(mac.doFinal());
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    /**
     * 恒定时间比较两个签名字符串，防止时序攻击
     *
     * @param expected 期望的签名
     * @param actual   实际的签名
     * @return true 如果匹配
     */
    public static boolean verify(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
