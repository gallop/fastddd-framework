package com.gallop.fastddd.model.po;

import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 支持乐观锁的 PO 基类。
 * <p>
 * 数据表必须有 version 列（INT/BIGINT DEFAULT 0）。
 * 不需要乐观锁的实体直接继承 {@link BasePO}。
 *
 * @author FastDDD Framework Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class VersionedPO extends BasePO {

    private static final long serialVersionUID = 1L;

    @Version
    private Long version = 0L;
}
