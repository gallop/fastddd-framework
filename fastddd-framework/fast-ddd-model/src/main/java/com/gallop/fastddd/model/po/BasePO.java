package com.gallop.fastddd.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base Persistence Object (PO) for all entities in FastDDD framework.
 * Provides common metadata fields that are automatically filled during
 * insert and update operations.
 * 
 * @author FastDDD Framework Team
 * @version 1.0.0-SNAPSHOT
 */
@Data
public abstract class BasePO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Get the primary key of the entity.
     * Must be implemented by concrete entity classes.
     * 
     * @return the primary key value
     */
    public abstract Serializable getId();

    /**
     * Record creation time.
     * Automatically filled during INSERT operations.
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * Record last update time.
     * Automatically filled during INSERT and UPDATE operations.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * User who created the record.
     * Automatically filled during INSERT operations.
     */
    @TableField(fill = FieldFill.INSERT)
    private String createUser;

    /**
     * User who last updated the record.
     * Automatically filled during INSERT and UPDATE operations.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    /**
     * Soft delete flag.
     */
    @TableLogic
    private Integer deleted = 0;
}