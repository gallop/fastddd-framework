package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox事件实体类
 * 
 * 用于实现Outbox模式，确保事件的可靠发布。
 * 该表存储所有待发布的领域事件，通过后台调度器异步处理。
 * 
 * @author gallop
 * @since 2025-01-09
 */
@TableName("outbox_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 事件唯一标识符
     */
    @TableField("event_id")
    private String eventId;
    
    /**
     * 聚合根ID
     */
    @TableField("aggregate_id")
    private String aggregateId;
    
    /**
     * 聚合根类型
     */
    @TableField("aggregate_type")
    private String aggregateType;
    
    /**
     * 事件类型
     */
    @TableField("event_type")
    private String eventType;
    
    /**
     * 事件数据（JSON格式）
     */
    @TableField("event_data")
    private String eventData;
    
    /**
     * 事件状态
     */
    @TableField("status")
    private OutboxEventStatus status;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    /**
     * 发布时间
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;
    
    /**
     * 重试次数
     */
    @TableField("retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    /**
     * 最大重试次数常量
     */
    public static final int MAX_RETRY_COUNT = 3;
    
    /**
     * 最大重试次数
     */
    @TableField("max_retry_count")
    @Builder.Default
    private Integer maxRetryCount = MAX_RETRY_COUNT;
    
    /**
     * 下次重试时间
     */
    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;
    
    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * claim（抢占置 PROCESSING）时间（EVT-003 租约机制）。
     */
    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    /**
     * 租约到期时间（EVT-003）：超过此时间仍未 ack 的 PROCESSING 事件视为 dispatcher 崩溃，
     * 由 reclaim 扫描重置回 PENDING 重新投递（ADR-003 §2.2）。
     */
    @TableField("lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    /**
     * 版本号（乐观锁）
     */
    @Version
    @TableField("version")
    @JsonIgnore
    @Builder.Default
    private Long version = 0L;
    
    /**
     * 初始化默认值
     */
    public void initDefaults() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = OutboxEventStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetryCount == null) {
            maxRetryCount = MAX_RETRY_COUNT;
        }
        if (version == null) {
            version = 0L;
        }
        updatedAt = now;
    }
    
    /**
     * 标记为已发布
     */
    public void markAsPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
    
    /**
     * 标记为失败
     */
    public void markAsFailed(String errorMessage) {
        this.status = OutboxEventStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    /**
     * 标记为死信（EVT-002）
     * 重试次数耗尽时由 {@link OutboxEventServiceImpl#markAsFailed} 触发，
     * 进入终态不再重试。
     */
    public void markAsDeadLetter() {
        this.status = OutboxEventStatus.DEAD_LETTER;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 标记为处理中
     */
    public void markAsProcessing() {
        this.status = OutboxEventStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 抢占并设置租约（EVT-003）：置 PROCESSING 并记录租约到期时间。
     * dispatcher 崩溃后，到期未 ack 的 PROCESSING 由 reclaim 重置回 PENDING。
     *
     * @param leaseExpiresAt 租约到期时间
     */
    public void claim(LocalDateTime claimedAt, LocalDateTime leaseExpiresAt) {
        this.status = OutboxEventStatus.PROCESSING;
        this.claimedAt = claimedAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.updatedAt = claimedAt;
    }

    /**
     * 判断租约是否已过期（EVT-003 reclaim 用）。
     *
     * @param now 当前时间
     * @return PROCESSING 状态且租约到期时间早于 now 时返回 true
     */
    public boolean isLeaseExpired(LocalDateTime now) {
        return status == OutboxEventStatus.PROCESSING
                && leaseExpiresAt != null && leaseExpiresAt.isBefore(now);
    }

    /**
     * 重置过期租约（EVT-003 reclaim）：PROCESSING → PENDING，清租约字段，保留 retryCount。
     */
    public void resetExpiredLease(LocalDateTime now) {
        this.status = OutboxEventStatus.PENDING;
        this.claimedAt = null;
        this.leaseExpiresAt = null;
        this.updatedAt = now;
    }

    /**
     * 是否可以重试
     */
    public boolean canRetry() {
        return retryCount < maxRetryCount && 
               (status == OutboxEventStatus.FAILED || status == OutboxEventStatus.PENDING);
    }
    
    /**
     * 检查事件是否准备好重试
     * 
     * @return 如果准备好重试返回true，否则返回false
     */
    public boolean isReadyForRetry() {
        if (nextRetryAt == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(nextRetryAt);
    }
    
    /**
     * 计算下次重试时间（指数退避）
     */
    public void calculateNextRetryTime() {
        if (canRetry()) {
            // 指数退避：2^retryCount 分钟
            long delayMinutes = (long) Math.pow(2, retryCount);
            this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        }
    }
}