package com.gallop.fastddd.cache;

/**
 * 缓存 null 值的标记，用于防止缓存穿透。
 * 使用 enum 保证单例，Jackson 原生支持 enum 按名称序列化。
 */
public enum NullValue {
    INSTANCE
}
