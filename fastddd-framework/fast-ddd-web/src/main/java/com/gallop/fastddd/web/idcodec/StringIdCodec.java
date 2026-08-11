package com.gallop.fastddd.web.idcodec;

/**
 * {@code String} 业务主键的内置 {@link IdCodec}（对应 {@code @TableId(INPUT)}）。
 *
 * <p>{@code parse}/{@code format} 均为恒等映射——业务键直接以原字符串承载。</p>
 *
 * @author gallop
 * @since 1.0
 * @version 1.0.0-SNAPSHOT
 */
public final class StringIdCodec implements IdCodec<String> {

    @Override
    public String parse(String idStr) {
        if (idStr == null || idStr.isEmpty()) {
            throw new IllegalArgumentException("ID 不能为空");
        }
        return idStr;
    }

    @Override
    public String format(String id) {
        return id;
    }

    @Override
    public Class<String> getIdType() {
        return String.class;
    }
}
