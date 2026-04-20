package com.gallop.fastddd.event;

/**
 * 事件发布异常
 * 
 * 当事件发布过程中发生错误时抛出此异常。
 * 这是一个运行时异常，用于封装事件发布过程中的各种错误情况。
 * 
 * @author FastDDD Framework Team
 * @version 1.0.0-SNAPSHOT
 */
public class EventPublishException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造一个新的事件发布异常
     */
    public EventPublishException() {
        super();
    }
    
    /**
     * 构造一个带有指定详细消息的事件发布异常
     * 
     * @param message 详细消息
     */
    public EventPublishException(String message) {
        super(message);
    }
    
    /**
     * 构造一个带有指定详细消息和原因的事件发布异常
     * 
     * @param message 详细消息
     * @param cause 原因（允许为null，表示原因不存在或未知）
     */
    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 构造一个带有指定原因的事件发布异常
     * 
     * @param cause 原因（允许为null，表示原因不存在或未知）
     */
    public EventPublishException(Throwable cause) {
        super(cause);
    }
}