package com.gallop.fastddd.web.idcodec;

import java.util.UUID;

/**
 * {@code UUID} 主键的内置 {@link IdCodec}。
 *
 * <p>非合法 UUID 字符串抛 {@link IllegalArgumentException}。</p>
 *
 * @author gallop
 * @since 1.0
 * @version 1.0.0-SNAPSHOT
 */
public final class UuidIdCodec implements IdCodec<UUID> {

    @Override
    public UUID parse(String idStr) {
        if (idStr == null || idStr.isEmpty()) {
            throw new IllegalArgumentException("ID 不能为空");
        }
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法 UUID ID: " + idStr, e);
        }
    }

    @Override
    public String format(UUID id) {
        return id == null ? null : id.toString();
    }

    @Override
    public Class<UUID> getIdType() {
        return UUID.class;
    }
}
