package com.gallop.fastddd.web.config;

/**
 * 枚举序列化模式
 * 定义枚举类型在 JSON 序列化时的输出格式
 *
 * @author gallop
 * @since 2026-02-28
 * @version 1.0.0-SNAPSHOT
 */
public enum EnumSerializationMode {

    /**
     * 使用枚举的 ordinal() 值（数字：0, 1, 2...）
     * 这是 Jackson 的默认行为
     */
    ORDINAL,

    /**
     * 使用枚举的 name() 值（字符串："MALE", "FEMALE"...）
     * 推荐使用，更加直观和可读
     */
    TO_STRING,

    /**
     * 使用枚举的 toString() 值
     * 如果枚举重写了 toString() 方法，则使用自定义值
     */
    TO_STRING_METHOD
}
