package com.gallop.fastddd.common.domain;

import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 值对象基类，提供DDD中值对象的通用行为
 * 值对象的特征：
 * 1. 不可变性（Immutable）
 * 2. 相等性基于属性值而非标识
 * 3. 无副作用的行为
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@EqualsAndHashCode
public abstract class ValueObject implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 验证值对象的有效性
     * 子类应该重写此方法来实现特定的验证逻辑
     * 
     * @throws IllegalArgumentException 当值对象无效时抛出
     */
    protected void validate() {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 获取值对象的字符串表示
     * 建议子类重写此方法以提供有意义的字符串表示
     */
    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    }
    
    /**
     * 检查值对象是否与另一个值对象相等
     * 基于Lombok的@EqualsAndHashCode注解实现
     */
    public boolean sameValueAs(ValueObject other) {
        return this.equals(other);
    }
}