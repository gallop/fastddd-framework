package com.gallop.fastddd.common.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 支持乐观锁的领域实体基类。
 * <p>
 * 对应持久层 {@link com.gallop.fastddd.model.po.VersionedPO}。
 *
 * @author FastDDD Framework Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class VersionedEntity<ID extends Serializable> extends BaseEntity<ID> {

    protected Long version;

    @Override
    public void prePersist() {
        super.prePersist();
        if (version == null) {
            version = 0L;
        }
    }

    @Override
    public void preUpdate() {
        super.preUpdate();
        if (version != null) {
            version++;
        }
    }
}
