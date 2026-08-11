package com.gallop.fastddd.model.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * Base Persistence Object (PO) — 带强类型主键的持久化基类（ID-003/ID-004 重构）。
 *
 * <p>在 {@link AuditedPO}（审计字段 + 逻辑删除）之上增加主键契约：
 * <ul>
 *   <li>主键 Java 类型由类型参数 <b>{@code ID}</b> 决定（{@code ID extends Serializable}），
 *       不再是 {@code Serializable} 弱类型——消费方无需 {@code (Long) po.getId()} 强转。
 *       MyBatis-Plus 按实际 {@code ID} 类型选择内置 TypeHandler（{@code Long}→{@code LongTypeHandler}、
 *       {@code UUID}→{@code UuidTypeHandler}、{@code String}→原生）。历史上框架为 {@code Serializable}
 *       弱类型主键全局注册的 {@code SerializableTypeHandler} 已在 ID-006 移除——强类型主键不再需要它。</li>
 *   <li><b>不</b>在字段上声明 {@code @TableId}：主键生成策略交由 MyBatis-Plus 全局配置
 *       {@code mybatis-plus.global-config.db-config.id-type}（框架默认 {@code assign_id} 雪花）。
 *       这修复了 ID-001/ADR-001 指出的"字段级 {@code @TableId(ASSIGN_ID)} 覆盖全局配置、文档却声称
 *       全局可覆盖"的矛盾。需要不同策略（如数据库自增）的 PO，自行声明 {@code @TableId(type=...)}。</li>
 * </ul>
 *
 * <p><b>子类使用</b>：{@code extends BasePO<Long>} 即继承强类型 {@code Long id} 与全部审计字段，
 * 无需重复声明 {@code id}、{@code @TableId}、{@code getId()}、{@code setId()}。需要 UUID/String 主键时
 * 用 {@code extends BasePO<UUID>} / {@code BasePO<String>}（配合 {@code @TableId(INPUT)} 等）。
 * 子类用 {@code @Getter/@Setter}（不要 {@code @Data}——其 equals/hashCode 对持久化对象有副作用）。
 *
 * <p><b>等价性</b>：本类不声明 {@code @EqualsAndHashCode}，退化为 {@code Object} 同一性比较
 * （持久化对象以主键 + 表为身份，跨聚合的字段相等比较有副作用风险）。这与领域侧
 * {@code DomainEntity} 的稳定身份等价性是两套并行契约（持久层 vs 领域层）。
 *
 * <p><b>MP 桥接</b>：MyBatis-Plus 的 {@code BaseMapper} 主键方法签名仍是 {@code Serializable}
 * （{@code selectById(Serializable)} 等）。因 {@code ID extends Serializable}，框架 Service 层
 * 在调用 MP 时由编译器自动上转，消费方无感（见 {@code BaseServiceImpl}）。
 *
 * @param <ID> 主键类型（必须 {@code extends Serializable}，框架默认 {@code Long}）
 * @author FastDDD Framework Team
 * @version 1.0.0-jdk21
 */
@Getter
@Setter
@ToString(callSuper = true)
public abstract class BasePO<ID extends Serializable> extends AuditedPO {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。类型由类型参数 {@link ID} 决定。
     *
     * <p>不声明字段级 {@code @TableId}——生成策略由全局配置
     * {@code mybatis-plus.global-config.db-config.id-type}（默认 {@code assign_id}）决定。
     * 需要覆盖策略的 PO 自行重声明 {@code id} 并加 {@code @TableId(type=...)}。
     */
    protected ID id;

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }
}
