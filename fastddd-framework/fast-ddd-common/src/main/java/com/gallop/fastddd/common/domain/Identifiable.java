package com.gallop.fastddd.common.domain;

import java.io.Serializable;

/**
 * 可标识领域对象契约：暴露类型化的稳定标识。
 *
 * <p>所有领域实体与聚合根实现本接口，使框架 SPI（仓储、事件、序列化）可基于
 * {@code ID} 类型而非弱类型 {@code Serializable} 约束交互（ID-004 进一步收口）。
 *
 * @param <ID> 标识类型，必须可序列化
 * @author gallop
 * @since 1.0.0-jdk21
 */
public interface Identifiable<ID extends Serializable> extends Serializable {

    /**
     * 获取领域标识。
     *
     * @return 标识值；未持久化的瞬态实体可能为 {@code null}
     */
    ID getId();
}
