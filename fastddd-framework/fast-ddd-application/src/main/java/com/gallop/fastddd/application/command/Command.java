package com.gallop.fastddd.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Command 基类
 *
 * 所有写操作的请求对象都应继承此类。
 * Command 表示改变系统状态的意图，是 CQRS 模式中写操作的抽象。
 *
 * <p>Command 特征：
 * <ul>
 *   <li>表示改变系统状态的意图</li>
 *   <li>以动宾结构命名（如 CreateOrderCommand）</li>
 *   <li>包含执行操作所需的所有数据</li>
 *   <li>返回结果通过 CommandResult 封装</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * public class CreateOrderCommand extends Command {
 *     @NotNull
 *     private String customerId;
 *     @NotNull
 *     private BigDecimal amount;
 *     // getters and setters
 * }
 * }</pre>
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@Getter
public abstract class Command implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 命令创建时间戳
     */
    private final Instant createdAt = Instant.now();

    /**
     * 命令元数据
     */
    private CommandMetadata metadata;

    /**
     * 获取命令类型
     *
     * @return 命令类型（类名）
     */
    public String getCommandType() {
        return this.getClass().getSimpleName();
    }

    /**
     * 验证命令是否有效
     *
     * @return 如果有效返回true，否则返回false
     */
    public boolean isValid() {
        return true;
    }

    /**
     * 获取命令唯一标识
     * 如果未设置，则生成一个新的 UUID
     *
     * @return 命令ID
     */
    public String getCommandId() {
        if (metadata != null && metadata.getCommandId() != null) {
            return metadata.getCommandId();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 设置命令元数据
     *
     * @param metadata 元数据
     */
    public void setMetadata(CommandMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * 命令元数据
     * 包含关联ID、因果ID、用户信息等上下文信息
     */
    @Getter
    public static class CommandMetadata implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 命令唯一标识
         */
        private String commandId;

        /**
         * 关联ID（用于关联多个命令）
         */
        private String correlationId;

        /**
         * 因果ID（上一个命令的ID）
         */
        private String causationId;

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 租户ID（多租户场景）
         */
        private String tenantId;

        /**
         * 其他扩展属性
         */
        private java.util.Map<String, Object> attributes;

        public CommandMetadata() {
            this.commandId = UUID.randomUUID().toString();
        }

        public CommandMetadata(String correlationId, String causationId) {
            this();
            this.correlationId = correlationId;
            this.causationId = causationId;
        }

        /**
         * 添加扩展属性
         *
         * @param key   键
         * @param value 值
         */
        public void putAttribute(String key, Object value) {
            if (this.attributes == null) {
                this.attributes = new java.util.HashMap<>();
            }
            this.attributes.put(key, value);
        }

        /**
         * 获取扩展属性
         *
         * @param key 键
         * @return 值
         */
        public Object getAttribute(String key) {
            if (this.attributes == null) {
                return null;
            }
            return this.attributes.get(key);
        }
    }
}
