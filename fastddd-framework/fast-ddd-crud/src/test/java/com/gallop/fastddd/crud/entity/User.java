package com.gallop.fastddd.crud.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gallop.fastddd.model.po.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用于测试BaseRepository功能的用户实体。
 * 扩展BasePO以继承通用元数据字段。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BasePO {
    
    /**
     * Primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * Username
     */
    private String username;
    
    /**
     * Email address
     */
    private String email;
    
    /**
     * User status: ACTIVE, INACTIVE, DELETED
     */
    private String status;
    
    /**
     * User department
     */
    private String department;
}