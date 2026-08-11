package com.gallop.fastddd.web.idcodec;

import java.io.Serializable;
import java.util.UUID;

/**
 * 内置 {@link IdCodec} 按主键类型解析的工具入口。
 *
 * <p>{@link AbstractBaseController#idCodec()} 默认经此按声明的 {@code <ID>} 类型获取 codec；
 * 业务主键类型（如领域值对象 {@code OrderId}）不在内置覆盖面内时，抛
 * {@link IllegalStateException} 提示子类覆写 {@code idCodec()} 注入自定义实现。</p>
 *
 * @author gallop
 * @since 1.0
 * @version 1.0.0-SNAPSHOT
 * @see AbstractBaseController#idCodec()
 */
public final class IdCodecs {

    private IdCodecs() {
    }

    /**
     * 按主键 {@code Class} 返回内置 {@link IdCodec}。
     *
     * @param idClass 主键类型
     * @param <ID>    主键类型
     * @return 内置 codec
     * @throws IllegalStateException 该类型无内置 codec（子类须覆写 {@code idCodec()}）
     */
    @SuppressWarnings("unchecked")
    public static <ID extends Serializable> IdCodec<ID> forClass(Class<ID> idClass) {
        if (idClass == null) {
            throw new IllegalStateException("无法解析主键类型；子类须覆写 idCodec()");
        }
        if (idClass == Long.class || idClass == Long.TYPE) {
            return (IdCodec<ID>) new LongIdCodec();
        }
        if (idClass == UUID.class) {
            return (IdCodec<ID>) new UuidIdCodec();
        }
        if (idClass == String.class) {
            return (IdCodec<ID>) new StringIdCodec();
        }
        throw new IllegalStateException(
            "无内置 IdCodec for " + idClass.getName() + "；子类须覆写 idCodec() 提供自定义实现");
    }
}
