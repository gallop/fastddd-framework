package com.gallop.fastddd.analyzer.constants;

import java.util.Set;

/**
 * 字段模式常量
 * <p>
 * 统一管理表分析中使用的各种命名模式常量，避免在多个类中重复定义。
 * </p>
 *
 * <h3>分类</h3>
 * <ul>
 *   <li>贫血模型相关：前缀、后缀、全名匹配</li>
 *   <li>充血模型相关：关键词、模式</li>
 *   <li>字段特征：状态、金额、数量、时间等</li>
 *   <li>实体类型：子实体、值对象、关联表后缀</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class FieldPatterns {

    private FieldPatterns() {
        // 工具类，禁止实例化
    }

    // ==================== 贫血模型相关 ====================

    /**
     * 贫血模型前缀（高优先级）
     */
    public static final Set<String> ANEMIC_PREFIXES = Set.of(
        "sys_", "dict_", "config_", "log_", "audit_", "temp_", "tmp_",
        "系统", "字典", "配置", "日志", "审计", "临时"
    );

    /**
     * 贫血模型后缀
     */
    public static final Set<String> ANEMIC_SUFFIXES = Set.of(
        "_log", "_history", "_audit", "_config", "_relation", "_mapping",
        "_ref", "_lookup", "_link",
        "日志", "历史", "审计", "配置", "关系", "映射", "引用", "查找"
    );

    /**
     * 贫血模型全名匹配
     */
    public static final Set<String> ANEMIC_EXACT_MATCHES = Set.of(
        "dictionary", "lookup", "reference", "metadata", "settings",
        "parameter", "option", "config",
        "字典表", "参数表", "配置表", "选项表"
    );

    // ==================== 充血模型相关 ====================

    /**
     * 充血模型关键词（核心业务实体）
     */
    public static final Set<String> RICH_KEYWORDS = Set.of(
        "order", "product", "inventory", "payment", "shipment", "invoice",
        "contract", "account", "wallet", "coupon", "cart",
        "customer", "merchant", "supplier", "warehouse",
        "订单", "产品", "库存", "支付", "发货", "发票", "合同",
        "客户", "商户", "供应商", "仓库"
    );

    // ==================== 字段特征 ====================

    /**
     * 状态字段名模式
     */
    public static final Set<String> STATUS_FIELD_PATTERNS = Set.of(
        "status", "state", "order_status", "product_status",
        "payment_status", "audit_status", "verify_status",
        "review_status", "approve_status",
        "状态", "订单状态", "支付状态", "审核状态", "审批状态"
    );

    /**
     * 金额字段名模式
     */
    public static final Set<String> AMOUNT_FIELD_PATTERNS = Set.of(
        "amount", "price", "cost", "fee", "discount", "total",
        "balance", "quota", "limit", "budget", "credit",
        "金额", "价格", "费用", "余额", "预算", "额度"
    );

    /**
     * 数量字段名模式
     */
    public static final Set<String> QUANTITY_FIELD_PATTERNS = Set.of(
        "quantity", "count", "number", "stock", "inventory",
        "数量", "库存", "存货"
    );

    /**
     * 时间范围字段
     */
    public static final Set<String> TIME_RANGE_FIELDS = Set.of(
        "start_time", "end_time", "begin_time", "expire_time",
        "effective_time", "valid_from", "valid_until",
        "start_date", "end_date",
        "开始时间", "结束时间", "生效时间", "失效时间"
    );

    /**
     * 业务操作字段
     */
    public static final Set<String> OPERATION_FIELDS = Set.of(
        "action", "operation", "type", "category",
        "操作", "类型", "类别"
    );

    /**
     * 订单号相关字段模式
     */
    public static final Set<String> ORDER_NUMBER_PATTERNS = Set.of(
        "order_no", "order_number", "订单号", "订单编号"
    );

    // ==================== 实体类型后缀 ====================

    /**
     * 子实体后缀模式
     */
    public static final Set<String> ENTITY_SUFFIXES = Set.of(
        "_item", "_detail", "_line", "_entry", "_record", "_log",
        "项目", "明细", "行", "记录", "日志"
    );

    /**
     * 值对象后缀模式
     */
    public static final Set<String> VALUE_OBJECT_SUFFIXES = Set.of(
        "_info", "_profile", "_extension", "_data",
        "信息", "资料", "扩展", "数据"
    );

    /**
     * 关联表后缀
     */
    public static final Set<String> RELATION_SUFFIXES = Set.of(
        "_relation", "_mapping", "_link", "_ref",
        "关系", "映射", "链接", "引用"
    );

    // ==================== 外键检测相关 ====================

    /**
     * 外键后缀模式
     */
    public static final String FOREIGN_KEY_SUFFIX = "_id";

    /**
     * 可能引用订单表的字段模式
     */
    public static final Set<String> ORDER_REF_PATTERNS = Set.of(
        "order_no", "order_id", "订单号", "订单编号"
    );

    // ==================== 金额字段数据类型 ====================

    /**
     * 金额相关的数据类型
     */
    public static final Set<String> AMOUNT_DATA_TYPES = Set.of(
        "decimal", "double", "money", "bigint"
    );
}
