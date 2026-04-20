package com.gallop.fastddd.application.query;

import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Query 基类
 *
 * 所有读操作的请求对象都应继承此类。
 * Query 表示查询系统状态的请求，是 CQRS 模式中读操作的抽象。
 *
 * <p>Query 特征：
 * <ul>
 *   <li>表示查询系统状态的请求</li>
 *   <li>以名词或动宾结构命名（如 OrderQuery, GetOrderByIdQuery）</li>
 *   <li>包含执行查询所需的所有条件</li>
 *   <li>不改变系统状态</li>
 *   <li>返回查询结果或 DTO</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * public class OrderQuery extends Query {
 *     private String customerId;
 *     private LocalDateTime startDate;
 *     private LocalDateTime endDate;
 *     private Integer page = 0;
 *     private Integer size = 20;
 *     // getters and setters
 * }
 * }</pre>
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@Getter
public abstract class Query implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 查询创建时间戳
     */
    private final Instant createdAt = Instant.now();

    /**
     * 查询元数据
     */
    private QueryMetadata metadata;

    /**
     * 获取查询类型
     *
     * @return 查询类型（类名）
     */
    public String getQueryType() {
        return this.getClass().getSimpleName();
    }

    /**
     * 验证查询是否有效
     *
     * @return 如果有效返回true，否则返回false
     */
    public boolean isValid() {
        return true;
    }

    /**
     * 设置查询元数据
     *
     * @param metadata 元数据
     */
    public void setMetadata(QueryMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * 查询元数据
     * 包含用户信息、分页参数等上下文信息
     */
    @Getter
    public static class QueryMetadata implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 租户ID（多租户场景）
         */
        private String tenantId;

        /**
         * 是否需要审计日志
         */
        private boolean auditLog = false;

        /**
         * 其他扩展属性
         */
        private java.util.Map<String, Object> attributes;

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
