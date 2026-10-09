package com.gallop.fastddd.identity.core.error;

/**
 * 入站目录事件防重放窗外（W5 设计 §5.1，W5-d；M-4）：信封
 * {@code header.create_time}（毫秒）超出本地重放窗（窗长为部署本地策略，
 * 非平台协议值）。
 *
 * <p>调用方映射 403——与验签失败 401 区分：报文真实性已过，败在时效。
 * 窗外事件不落 Inbox（免于陈旧事件占位）；窗内去重由 event_id 承载
 * （Inbox 唯一键）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryEventExpiredException extends RuntimeException {

    public DirectoryEventExpiredException(String message) {
        super(message);
    }
}
