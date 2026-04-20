package com.gallop.fastddd.event.rocketmq;

/**
 * RocketMQ 监听器配置属性
 * <p>
 * 注意：不使用 @Component 或 @ConfigurationProperties 注解，
 * Bean 由 EventAutoConfiguration 中的 @Bean 方法创建并绑定配置
 *
 * @author gallop
 * @since 2025-01-22
 */
public class RocketMQListenerConfig {

    /**
     * 消费者主题名称
     */
    private String topic = "domain-events";

    /**
     * 消费者组名
     */
    private String group = "domain-event-consumer";

    /**
     * 最大重试次数
     */
    private int maxReconsumeTimes = 3;

    /**
     * 消费线程数最小值
     */
    private int consumeThreadMin = 1;

    /**
     * 消费线程数最大值
     */
    private int consumeThreadMax = 10;

    /**
     * 消费超时时间（分钟）
     */
    private long consumeTimeout = 15;

    /**
     * 是否启用有序消费
     */
    private boolean orderly = false;

    /**
     * 死信队列主题名称
     */
    private String dlqTopic = "domain-events-dlq";

    /**
     * 是否启用死信队列处理
     */
    private boolean dlqEnabled = true;

    /**
     * 死信队列处理器类名
     */
    private String dlqHandlerClass = "com.gallop.fastddd.event.rocketmq.DeadLetterQueueHandler";

    /**
     * 消息处理失败时的重试间隔（毫秒）
     */
    private long retryInterval = 1000;

    /**
     * 是否启用消息追踪
     */
    private boolean traceEnabled = true;

    /**
     * 消息过滤标签
     */
    private String selectorExpression = "*";

    // Getters and Setters
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(int maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    public int getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(int consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public int getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(int consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public long getConsumeTimeout() {
        return consumeTimeout;
    }

    public void setConsumeTimeout(long consumeTimeout) {
        this.consumeTimeout = consumeTimeout;
    }

    public boolean isOrderly() {
        return orderly;
    }

    public void setOrderly(boolean orderly) {
        this.orderly = orderly;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public boolean isDlqEnabled() {
        return dlqEnabled;
    }

    public void setDlqEnabled(boolean dlqEnabled) {
        this.dlqEnabled = dlqEnabled;
    }

    public String getDlqHandlerClass() {
        return dlqHandlerClass;
    }

    public void setDlqHandlerClass(String dlqHandlerClass) {
        this.dlqHandlerClass = dlqHandlerClass;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public String getSelectorExpression() {
        return selectorExpression;
    }

    public void setSelectorExpression(String selectorExpression) {
        this.selectorExpression = selectorExpression;
    }
}