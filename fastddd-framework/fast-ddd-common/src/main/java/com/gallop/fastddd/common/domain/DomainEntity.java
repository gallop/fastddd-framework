package com.gallop.fastddd.common.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * 轻量领域实体基类，只负责稳定身份契约（ID-002）。
 *
 * <p>身份等价性规则（满足验收"相等性只基于稳定身份和明确类型规则"）：
 * <ul>
 *   <li>两个已持久化实体（id 非空）当且仅当 <b>id 相等</b>且 <b>类型相同</b>
 *       （{@code getClass() == obj.getClass()}）时相等。明确的类型规则避免跨子类
 *       （含未来代理子类）的误判。</li>
 *   <li>任一为瞬态实体（id 为 {@code null}）时退化为引用同一性——两个未保存实体互不相等，
 *       避免把"尚未获得身份"的对象当作同一实体。</li>
 *   <li>{@code hashCode} 仅基于 {@code getClass()}，<b>不</b>依赖可变 id。持久化回填 id 不会
 *       改变 hashCode（满足禁止"不让持久化后补 ID 改变 hashCode"），从而不破坏
 *       HashSet/HashMap 查找契约。代价是同类实体共享同一桶位——这是身份等价性下保证
 *       Object 契约（{@code equals ⇒ hashCode})成立的必要取舍。</li>
 * </ul>
 *
 * <p>本类<b>不</b>携带审计字段、逻辑删除或持久化回调——它们是持久层职责，由 {@code BasePO}
 * 体系承载（ID-003）。因此 {@code getId}/{@code setId} 由本基类提供具体实现，
 * <b>领域实体无需重复实现</b>。
 *
 * @param <ID> 实体标识类型
 * @author gallop
 * @since 1.0.0-jdk21
 */
public abstract class DomainEntity<ID extends Serializable> implements Identifiable<ID> {

    private static final long serialVersionUID = 1L;

    /**
     * 实体唯一标识。由持久层在保存时回填（如雪花 id），或由领域在创建时赋予（如业务标识）。
     */
    protected ID id;

    /**
     * {@inheritDoc}
     *
     * <p>具体实现已由基类提供，子类无需重复实现。
     */
    @Override
    public ID getId() {
        return id;
    }

    /**
     * 设置实体标识。
     *
     * @param id 标识值
     */
    public void setId(ID id) {
        this.id = id;
    }

    /**
     * 判断实体是否为新实体（尚未持久化，id 为空）。
     *
     * @return id 为空返回 {@code true}
     */
    public boolean isNew() {
        return id == null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DomainEntity<?> other = (DomainEntity<?>) obj;
        ID thisId = getId();
        Object otherId = other.getId();
        if (thisId == null || otherId == null) {
            // 瞬态实体不具备稳定身份，退化为引用同一性（this == obj 已在上方处理）
            return false;
        }
        return thisId.equals(otherId);
    }

    @Override
    public int hashCode() {
        // 故意不使用 id：持久化回填 id 不能改变 hashCode，否则破坏 HashSet/HashMap 契约。
        // 同类实体共享同一 hashCode（桶退化），是身份等价性下保证 Object 契约成立的取舍。
        return Objects.hashCode(getClass());
    }
}
