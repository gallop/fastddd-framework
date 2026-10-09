package com.gallop.fastddd.identity.core.domain;

/**
 * 入站 webhook 原始报文（W5 设计 §5.1，W5-d）：传输中立载体——core 不依赖
 * Servlet API，HTTP 细节（方法/路径/体读取上界）归宿主端点层。
 *
 * <p>四要素即协议面：原始体明文 + 参与签名计算的两个头（时间戳/nonce）+
 * 签名头。加密体的密文即 {@code body}（解密是 channel 职责）。头缺失以
 * null 传入——签名校验按缺失拒绝（M-1 fail-closed）。</p>
 *
 * @param body            原始请求体明文（不解释内容；null 非法）
 * @param signature       {@code X-Lark-Signature} 头（可 null = 缺失）
 * @param timestampHeader {@code X-Lark-Request-Timestamp} 头（可 null）
 * @param nonceHeader     {@code X-Lark-Request-Nonce} 头（可 null）
 * @author gallop
 * @since 1.1.0
 */
public record DirectoryWebhookRequest(String body, String signature,
                                      String timestampHeader, String nonceHeader) {

    public DirectoryWebhookRequest {
        if (body == null) {
            throw new IllegalArgumentException("body 必须非 null（空体按验签失败拒）");
        }
    }
}
