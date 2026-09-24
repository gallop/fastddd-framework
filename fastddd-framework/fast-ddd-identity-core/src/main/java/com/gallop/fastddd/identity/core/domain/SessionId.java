package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 不透明本地会话 ID（W0-2 §3.3：≥256bit 随机不透明文本；适配器以 32 字节随机数的
 * base64url 表示生成，43 字符）。值对象仅做形状校验，不承担生成责任。
 *
 * @author gallop
 * @since 1.1.0
 */
public final class SessionId {

    private final String value;

    private SessionId(String value) {
        this.value = value;
    }

    public static SessionId of(String value) {
        if (value == null || value.length() < 16 || value.length() > 64) {
            throw new IllegalArgumentException("SessionId 长度须在 16–64 之间（不透明随机文本）");
        }
        return new SessionId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SessionId && value.equals(((SessionId) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
