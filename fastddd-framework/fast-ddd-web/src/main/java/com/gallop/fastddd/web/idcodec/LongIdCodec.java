package com.gallop.fastddd.web.idcodec;

/**
 * {@code Long} 主键（雪花 / DB 自增）的内置 {@link IdCodec}。
 *
 * <p>对应全局 {@code id-type: assign_id}（默认）或字段 {@code @TableId(AUTO)}。
 * 非数字字符串抛 {@link IllegalArgumentException}（区别于 {@code NumberFormatException}
 * 的低级异常，供控制器统一异常处理）。</p>
 *
 * @author gallop
 * @since 1.0
 * @version 1.0.0-SNAPSHOT
 */
public final class LongIdCodec implements IdCodec<Long> {

    @Override
    public Long parse(String idStr) {
        if (idStr == null || idStr.isEmpty()) {
            throw new IllegalArgumentException("ID 不能为空");
        }
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法数值 ID: " + idStr, e);
        }
    }

    @Override
    public String format(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    @Override
    public Class<Long> getIdType() {
        return Long.class;
    }
}
