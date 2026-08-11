package com.gallop.fastddd.common.domain;

import java.io.Serializable;

/**
 * 领域实体基类（兼容门面，ID-002 重构）。
 *
 * <p>身份契约（稳定 equals/hashCode、类型化 {@code getId}/{@code setId}）已上移到
 * {@link DomainEntity}。本类保留为既有继承链（如 {@link AggregateRoot}，以及
 * {@code EntityRepository}/{@code EntityMapper} 的泛型上界）提供兼容入口，<b>新代码</b>
 * 应直接继承 {@link DomainEntity}（普通实体）或 {@link AggregateRoot}（聚合根）。
 *
 * <p>审计字段（{@code createTime}/{@code updateTime}/{@code createUser}/{@code updateUser}）、
 * 逻辑删除（{@code deleted}）与持久化回调（{@code prePersist}/{@code preUpdate}）已从此
 * 领域基类移除——它们是持久层职责，由 {@code BasePO} 体系承载（见 ID-003）。
 *
 * <p>历史变更：1.0 之前本类用 {@code @Data} 生成涵盖全部可变字段的 equals/hashCode，导致持久化
 * 回填 id 后 hashCode 改变、破坏集合契约（ID-001 特征测试锁定）。ID-002 统一为
 * {@link DomainEntity} 的稳定身份等价性。
 *
 * @param <ID> 实体标识类型
 * @author gallop
 * @since 2025-08-31
 */
public abstract class BaseEntity<ID extends Serializable> extends DomainEntity<ID> {

    private static final long serialVersionUID = 1L;
}
