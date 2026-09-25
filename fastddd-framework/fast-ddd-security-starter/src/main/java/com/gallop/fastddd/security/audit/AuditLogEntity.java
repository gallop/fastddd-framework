package com.gallop.fastddd.security.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Data
@TableName("sys_audit_log")
public class AuditLogEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作用户
     */
    private String username;

    /**
     * 操作描述
     */
    private String operation;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求参数
     */
    private String requestParams;

    /**
     * 返回结果
     */
    private String result;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 异常信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    // identity 模式 Actor 捕获（W21-04）：由 AuditActorProvider 在业务执行前解析并随实体
    // 捕获（异步落库路径同样在切面线程完成捕获）；旧列 INSERT 不读取这些字段，A/B 零影响。

    /** 执行者租户（identity 模式；旧路径为 null） */
    private String actorTenantId;

    /** 执行者稳定主体标识 principalId（identity 模式；旧路径为 null） */
    private String actorPrincipalId;

    /** 执行者类型（HUMAN / SYSTEM_JOB / SYSTEM，identity 模式；旧路径为 null） */
    private String actorType;
}