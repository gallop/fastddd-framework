package com.gallop.fastddd.web.idcodec;

import java.io.Serializable;

/**
 * ID 编解码 SPI：负责聚合主键在 HTTP 边界（URL 路径参数 / JSON 表示）的
 * {@code String}↔{@code ID} 双向转换。
 *
 * <p>每个聚合一个 {@code IdCodec}，取代历史上 {@code AbstractBaseController.resolveId(String)}
 * 基于 MyBatis-Plus {@code IdType} 的"DB 生成策略 → Java 值类型"猜测（ADR-001 §2.6）。
 * ID 类型由控制器声明的 {@code <ID>} 泛型参数显式决定，而非运行期推测。</p>
 *
 * <p>框架内置 {@link LongIdCodec} / {@link UuidIdCodec} / {@link StringIdCodec}，
 * 经 {@link IdCodecs#forClass(Class)} 按类型解析；业务可提供自定义实现（如 Hashid 混淆、
 * 领域值对象 {@code OrderId} 编解码）并在控制器覆写 {@code idCodec()} 注入。</p>
 *
 * @param <ID> 主键类型（透传自实体 {@code BasePO<ID>}）
 * @author gallop
 * @since 1.0
 * @version 1.0.0-SNAPSHOT
 */
public interface IdCodec<ID extends Serializable> {

    /**
     * 将 URL/JSON 中的字符串 ID 解析为强类型主键。
     *
     * @param idStr 字符串形式的 ID（来自路径参数 / 请求体）
     * @return 强类型主键
     * @throws IllegalArgumentException 字符串不是该 ID 类型的合法表示
     */
    ID parse(String idStr);

    /**
     * 将强类型主键格式化为字符串（用于 URL 拼接 / JSON 序列化 / 日志）。
     *
     * @param id 强类型主键（可为 {@code null}）
     * @return 字符串表示（{@code null} 输入返回 {@code null}）
     */
    String format(ID id);

    /**
     * @return 此 codec 处理的主键类型
     */
    Class<ID> getIdType();
}
