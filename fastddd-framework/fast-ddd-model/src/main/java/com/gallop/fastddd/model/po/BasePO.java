package com.gallop.fastddd.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base Persistence Object (PO) for all entities in FastDDD framework.
 * Provides common metadata fields that are automatically filled during
 * insert and update operations.
 *
 * <p>Subclasses inherit the {@code id} field directly — no need to declare
 * {@code private Long id}, {@code @TableId}, {@code getId()} or {@code setId()}.
 * The ID strategy is controlled via MyBatis-Plus global config
 * ({@code mybatis-plus.global-config.db-config.id-type}).</p>
 *
 * @author FastDDD Framework Team
 * @version 1.0.0-SNAPSHOT
 */
@Getter
@Setter
@ToString
public abstract class BasePO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key. Uses ASSIGN_ID (snowflake) as the default strategy.
     * Override via MyBatis-Plus global config ({@code mybatis-plus.global-config.db-config.id-type})
     * or per-entity via redeclaring this field with a different {@code @TableId} annotation.
     */
    @TableId(type = IdType.ASSIGN_ID)
    protected Serializable id;

    public Serializable getId() { return id; }

    public void setId(Serializable id) { this.id = id; }

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
     *
     * <p><b>Note:</b> When using {@code @TableLogic} with unique indexes, deleted records
     * will still occupy the unique slot. Solutions:
     * <ul>
     *   <li>PostgreSQL: {@code CREATE UNIQUE INDEX idx_name ON t(name) WHERE deleted = 0}</li>
     *   <li>MySQL 8.0+: virtual column + conditional unique index</li>
     * </ul>
     */
    @TableLogic
    private Integer deleted = 0;
}