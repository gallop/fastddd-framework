package com.gallop.fastddd.common.enums;

import lombok.Getter;

/**
 * 领域模型类型枚举
 * <p>
 * 定义了两种主要的领域模型类型：贫血模型和充血模型。
 * </p>
 *
 * <h2>贫血模型 (ANEMIC)</h2>
 * <p>适用于简单的 CRUD 操作，业务逻辑在 Service 层实现。</p>
 * <p>典型场景：配置表、字典表、日志表、简单的查询表等。</p>
 *
 * <h2>充血模型 (RICH)</h2>
 * <p>适用于复杂业务逻辑，业务逻辑封装在领域对象中。</p>
 * <p>典型场景：核心业务实体，包含状态转换、业务规则验证、领域事件发布等。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Getter
public enum DomainModelType {

    /**
     * 贫血模型 (Anemic Domain Model)
     * <p>
     * 领域对象只包含数据，不包含业务逻辑。
     * 业务逻辑通常在 Service 层或 Facade 层实现。
     * </p>
     *
     * <h3>特点</h3>
     * <ul>
     *   <li>实体类简单，主要是 getter/setter</li>
     *   <li>业务逻辑分散在 Service 层</li>
     *   <li>适合简单 CRUD 操作</li>
     *   <li>学习曲线平缓，易于上手</li>
     * </ul>
     *
     * <h3>适用场景</h3>
     * <ul>
     *   <li>系统配置表 (sys_config, dict_type)</li>
     *   <li>日志和审计表 (xxx_log, xxx_audit)</li>
     *   <li>简单的关联表 (xxx_relation, xxx_mapping)</li>
     *   <li>查询视图类表</li>
     * </ul>
     *
     * @see #RICH
     */
    ANEMIC("贫血模型", "适用于配置、字典、日志等简单表"),

    /**
     * 充血模型 (Rich Domain Model)
     * <p>
     * 领域对象既包含数据，也包含业务逻辑。
     * 业务规则、状态转换、领域事件等都封装在领域对象内部。
     * </p>
     *
     * <h3>特点</h3>
     * <ul>
     *   <li>业务逻辑封装在领域对象中</li>
     *   <li>支持领域事件发布</li>
     *   <li>状态转换有明确的规则</li>
     *   <li>符合 DDD 思想</li>
     * </ul>
     *
     * <h3>适用场景</h3>
     * <ul>
     *   <li>核心业务实体 (订单、产品、支付)</li>
     *   <li>包含状态转换的业务对象</li>
     *   <li>需要发布领域事件的场景</li>
     *   <li>复杂业务规则验证</li>
     * </ul>
     *
     * @see #ANEMIC
     */
    RICH("充血模型", "适用于核心业务实体，包含状态转换、业务规则");

    /**
     * 显示名称
     */
    private final String displayName;

    /**
     * 描述信息
     */
    private final String description;

    /**
     * 构造函数
     *
     * @param displayName 显示名称
     * @param description 描述信息
     */
    DomainModelType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 根据名称获取枚举值
     * <p>支持多种大小写格式，增强容错性</p>
     *
     * @param name 枚举名称或显示名称
     * @return 对应的枚举值，如果不存在返回 null
     */
    public static DomainModelType fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        // 规范化：转大写，替换连字符为下划线
        String normalized = name.toUpperCase().replaceAll("-", "_");

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // 尝试匹配 displayName
            for (DomainModelType type : values()) {
                if (type.getDisplayName().equalsIgnoreCase(name) ||
                    type.getDisplayName().contains(name) ||
                    name.contains(type.getDisplayName())) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 根据名称获取枚举值（带默认值）
     *
     * @param name 枚举名称或显示名称
     * @param defaultValue 默认值
     * @return 对应的枚举值，如果不存在返回默认值
     */
    public static DomainModelType fromNameOrDefault(String name, DomainModelType defaultValue) {
        DomainModelType result = fromName(name);
        return result != null ? result : defaultValue;
    }

    /**
     * 判断是否为贫血模型
     *
     * @return 如果是贫血模型返回 true
     */
    public boolean isAnemic() {
        return this == ANEMIC;
    }

    /**
     * 判断是否为充血模型
     *
     * @return 如果是充血模型返回 true
     */
    public boolean isRich() {
        return this == RICH;
    }
}
