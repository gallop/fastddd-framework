package com.gallop.fastddd.event.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox事件统计信息
 * 
 * 提供Outbox事件的统计数据，用于监控和管理。
 * 
 * @author gallop
 * @since 2025-01-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventStatistics {
    
    /**
     * 待处理事件数量
     */
    private long pendingCount;
    
    /**
     * 处理中事件数量
     */
    private long processingCount;
    
    /**
     * 已发布事件数量
     */
    private long publishedCount;
    
    /**
     * 失败事件数量
     */
    private long failedCount;
    
    /**
     * 已取消事件数量
     */
    private long cancelledCount;
    
    /**
     * 总事件数量
     */
    private long totalCount;
    
    /**
     * 超过最大重试次数的失败事件数量
     */
    private long failedExceedingMaxRetriesCount;

    /**
     * 死信事件数量（EVT-004 步骤6 可观测性）。
     *
     * <p>EVT-002 引入显式 {@code DEAD_LETTER} 终态后，死信不再停留于 FAILED——
     * 独立计数以支撑死信积压告警与人工干预清单。{@code failedExceedingMaxRetriesCount}
     * 为过渡期保留字段（历史 FAILED+超限查询），逐步由本字段取代。
     */
    private long deadLetterCount;

    /**
     * 平均投递延迟（毫秒，EVT-004 步骤6 可观测性）。
     *
     * <p>已发布事件 {@code created_at → published_at} 的平均值（秒粒度经 SQL 聚合后换算为毫秒）。
     * 持续上升表明 dispatcher 投递能力落后于事件产生速率（积压）。无已发布事件时为 {@code null}。
     */
    private Long averagePublishLatencyMillis;

    /**
     * 统计时间
     */
    @Builder.Default
    private LocalDateTime statisticsTime = LocalDateTime.now();
    
    /**
     * 成功率（已发布事件数量 / 总事件数量）
     */
    public double getSuccessRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) publishedCount / totalCount * 100;
    }
    
    /**
     * 失败率（失败事件数量 / 总事件数量）
     */
    public double getFailureRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) failedCount / totalCount * 100;
    }
    
    /**
     * 处理率（已处理事件数量 / 总事件数量）
     * 已处理 = 已发布 + 已取消
     */
    public double getProcessedRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) (publishedCount + cancelledCount) / totalCount * 100;
    }
    
    /**
     * 待处理率（待处理事件数量 / 总事件数量）
     */
    public double getPendingRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) pendingCount / totalCount * 100;
    }
    
    /**
     * 处理中率（处理中事件数量 / 总事件数量）
     */
    public double getProcessingRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) processingCount / totalCount * 100;
    }
    
    /**
     * 是否有需要关注的问题
     * 如果失败率超过10%或有死信事件，则需要关注
     */
    public boolean hasIssues() {
        return getFailureRate() > 10.0 || deadLetterCount > 0;
    }

    /**
     * 获取健康状态描述
     */
    public String getHealthStatus() {
        if (deadLetterCount > 0 || failedExceedingMaxRetriesCount > 0) {
            return "严重：有死信事件需人工干预 (deadLetter=" + deadLetterCount + ")";
        }

        double failureRate = getFailureRate();
        if (failureRate > 20.0) {
            return "警告：失败率过高 (" + String.format("%.1f", failureRate) + "%)";
        } else if (failureRate > 10.0) {
            return "注意：失败率较高 (" + String.format("%.1f", failureRate) + "%)";
        } else if (pendingCount > 1000) {
            return "注意：待处理事件较多 (" + pendingCount + ")";
        } else {
            return "正常";
        }
    }

    @Override
    public String toString() {
        return String.format(
            "OutboxEventStatistics{" +
            "总计=%d, 待处理=%d, 处理中=%d, 已发布=%d, 失败=%d, 已取消=%d, 死信=%d, " +
            "平均延迟=%s ms, 成功率=%.1f%%, 失败率=%.1f%%, 健康状态='%s', 统计时间=%s}",
            totalCount, pendingCount, processingCount, publishedCount, failedCount, cancelledCount,
            deadLetterCount, averagePublishLatencyMillis,
            getSuccessRate(), getFailureRate(), getHealthStatus(), statisticsTime
        );
    }
}