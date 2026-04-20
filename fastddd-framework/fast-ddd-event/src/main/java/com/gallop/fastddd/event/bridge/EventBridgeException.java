package com.gallop.fastddd.event.bridge;

import com.gallop.fastddd.event.EventPublishException;

/**
 * 事件桥接异常
 *
 * 当领域事件桥接过程中发生错误时抛出此异常。
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
public class EventBridgeException extends EventPublishException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     *
     * @param message 异常消息
     */
    public EventBridgeException(String message) {
        super(message);
    }

    /**
     * 构造函数
     *
     * @param message 异常消息
     * @param cause 原因
     */
    public EventBridgeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数
     *
     * @param cause 原因
     */
    public EventBridgeException(Throwable cause) {
        super(cause);
    }
}
