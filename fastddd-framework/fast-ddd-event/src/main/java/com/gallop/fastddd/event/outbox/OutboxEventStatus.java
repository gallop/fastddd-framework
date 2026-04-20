package com.gallop.fastddd.event.outbox;

/**
 * Outbox事件状态枚举
 * 
 * 定义了Outbox事件在处理过程中的各种状态。
 * 
 * @author gallop
 * @since 2025-01-09
 */
public enum OutboxEventStatus {
    
    /**
     * 待处理状态
     * 事件已创建，等待被调度器处理
     */
    PENDING("待处理"),
    
    /**
     * 处理中状态
     * 事件正在被调度器处理
     */
    PROCESSING("处理中"),
    
    /**
     * 已发布状态
     * 事件已成功发布到消息队列
     */
    PUBLISHED("已发布"),
    
    /**
     * 失败状态
     * 事件发布失败，可能需要重试
     */
    FAILED("失败"),
    
    /**
     * 已取消状态
     * 事件被手动取消，不再处理
     */
    CANCELLED("已取消");
    
    private final String description;
    
    OutboxEventStatus(String description) {
        this.description = description;
    }
    
    /**
     * 获取状态描述
     * 
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 判断是否为终态
     * 
     * @return 如果是终态返回true，否则返回false
     */
    public boolean isFinalState() {
        return this == PUBLISHED || this == CANCELLED;
    }
    
    /**
     * 判断是否可以重试
     * 
     * @return 如果可以重试返回true，否则返回false
     */
    public boolean canRetry() {
        return this == FAILED || this == PENDING;
    }
}