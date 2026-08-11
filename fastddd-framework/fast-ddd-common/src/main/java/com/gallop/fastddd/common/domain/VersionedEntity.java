package com.gallop.fastddd.common.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 支持乐观锁的领域实体基类。
 *
 * <p>对应持久层 {@link com.gallop.fastddd.model.po.VersionedPO}（其 {@code @Version} 由
 * MyBatis-Plus 解析）。本类仅承载版本字段，乐观锁的自增逻辑属于持久层职责，已从领域基类移除
 * （ID-002）。
 *
 * <p>身份等价性继承自 {@link DomainEntity}：{@code version} <b>不</b>参与 equals/hashCode。
 *
 * @author FastDDD Framework Team
 */
@Getter
@Setter
@ToString(callSuper = true)
public abstract class VersionedEntity<ID extends Serializable> extends BaseEntity<ID> {

    protected Long version;
}
