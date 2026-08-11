package com.gallop.fastddd.model.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * Base Persistence Object (PO) — 带强类型主键的持久化基类（ID-003 重构）。
 *
 * <p>在 {@link AuditedPO}（审计字段 + 逻辑删除）之上增加主键契约：
 * <ul>
 *   <li>主键 Java 类型为 <b>{@code Long}</b>（与数据库 BIGINT 列类型明确对应），不再是
 *       {@code Serializable} 弱类型——消费方无需 {@code (Long) po.getId()} 强转，
 *       MyBatis-Plus 内置 {@code LongTypeHandler} 直接处理主键读写，不再依赖框架全局注册的
 *       {@code SerializableTypeHandler}（满足 ID-003 验收"不需要 SerializableTypeHandler 才能读取主键"）。</li>
 *   <li><b>不</b>在字段上声明 {@code @TableId}：主键生成策略交由 MyBatis-Plus 全局配置
 *       {@code mybatis-plus.global-config.db-config.id-type}（框架默认 {@code assign_id} 雪花）。
 *       这修复了 ID-001/ADR-001 指出的"字段级 {@code @TableId(ASSIGN_ID)} 覆盖全局配置、文档却声称
 *       全局可覆盖"的矛盾。需要不同策略（如数据库自增）的 PO，自行声明 {@code @TableId(type=...)}。</li>
 * </ul>
 *
 * <p><b>子类使用</b>：直接 {@code extends BasePO} 即继承 {@code Long id} 与全部审计字段，
 * 无需重复声明 {@code id}、{@code @TableId}、{@code getId()}、{@code setId()}。子类用
 * {@code @Getter/@Setter}（不要 {@code @Data}——其 equals/hashCode 对持久化对象有副作用）。
 *
 * <p><b>等价性</b>：本类不声明 {@code @EqualsAndHashCode}，退化为 {@code Object} 同一性比较
 * （持久化对象以主键 + 表为身份，跨聚合的字段相等比较有副作用风险）。这与领域侧
 * {@code DomainEntity} 的稳定身份等价性是两套并行契约（持久层 vs 领域层）。
 *
 * <p><b>ID-004 展望</b>：本类当前为非泛型 {@code Long} 主键（覆盖绝大多数业务表）。需要
 * {@code UUID}/{@code String}/{@code 值对象} 主键的 PO，当前以独立实体形式实现（如
 * {@code OutboxEvent}/{@code AuditLogEntity}/{@code TableDesignVersion}）；ID-004 在
 * Repository/Service 泛型化时按需将本类泛型化为 {@code BasePO<ID>}。
 *
 * @author FastDDD Framework Team
 * @version 1.0.0-jdk21
 */
@Getter
@Setter
@ToString(callSuper = true)
public abstract class BasePO extends AuditedPO {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。类型为 {@link Long}（对应 BIGINT 列）。
     *
     * <p>不声明字段级 {@code @TableId}——生成策略由全局配置
     * {@code mybatis-plus.global-config.db-config.id-type}（默认 {@code assign_id}）决定。
     * 需要覆盖策略的 PO 自行重声明 {@code id} 并加 {@code @TableId(type=...)}。
     */
    protected Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
