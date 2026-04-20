package com.gallop.fastddd.common.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类，提供通用的实体属性和行为
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Data
@EqualsAndHashCode(callSuper = false)
public abstract class BaseEntity<ID extends Serializable> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 实体唯一标识
     */
    protected ID id;
    
    /**
     * 创建时间
     */
    protected LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    protected LocalDateTime updatedAt;
    
    /**
     * 创建者
     */
    protected String createdBy;
    
    /**
     * 更新者
     */
    protected String updatedBy;
    
    /**
     * 版本号（用于乐观锁）
     */
    protected Long version;
    
    /**
     * 是否已删除
     */
    protected Boolean deleted = false;
    
    /**
     * 获取实体标识
     */
    public abstract ID getId();
    
    /**
     * 设置实体标识
     */
    public abstract void setId(ID id);
    
    /**
     * 判断实体是否为新实体（未持久化）
     */
    public boolean isNew() {
        return getId() == null;
    }
    
    /**
     * 实体创建前的回调
     */
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (version == null) {
            version = 0L;
        }
    }
    
    /**
     * 实体更新前的回调
     */
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (version != null) {
            version++;
        }
    }
}