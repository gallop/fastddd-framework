package com.gallop.fastddd.event.rocketmq;

/**
 * RocketMQ配置属性
 * <p>
 * 注意：不使用 @Component 或 @ConfigurationProperties 注解，
 * Bean 由 EventAutoConfiguration 中的 @Bean 方法创建并绑定配置
 *
 * @author gallop
 * @since 2025-01-22
 */
public class RocketMQConfig {

    /**
     * 默认主题名称
     */
    private String defaultTopic = "domain-events";

    /**
     * 批处理大小
     */
    private int batchSize = 100;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 发布任务执行间隔（毫秒）
     */
    private long interval = 5000;

    /**
     * 重试任务执行间隔（毫秒）
     */
    private long retryInterval = 30000;

    /**
     * 是否启用定时发布
     */
    private boolean scheduledPublishEnabled = true;

    /**
     * 消息发送超时时间（毫秒）
     */
    private long sendTimeout = 3000;

    /**
     * 是否启用事务消息
     */
    private boolean transactionEnabled = false;

    /**
     * 生产者组名
     */
    private String producerGroup = "domain-event-producer";

    // Getters and Setters
    public String getDefaultTopic() {
        return defaultTopic;
    }

    public void setDefaultTopic(String defaultTopic) {
        this.defaultTopic = defaultTopic;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public boolean isScheduledPublishEnabled() {
        return scheduledPublishEnabled;
    }

    public void setScheduledPublishEnabled(boolean scheduledPublishEnabled) {
        this.scheduledPublishEnabled = scheduledPublishEnabled;
    }

    public long getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(long sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public boolean isTransactionEnabled() {
        return transactionEnabled;
    }

    public void setTransactionEnabled(boolean transactionEnabled) {
        this.transactionEnabled = transactionEnabled;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }
}