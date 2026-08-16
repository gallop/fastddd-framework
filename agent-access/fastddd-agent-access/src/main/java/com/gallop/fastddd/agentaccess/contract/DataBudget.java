package com.gallop.fastddd.agentaccess.contract;

/**
 * READ 预算约束（Contract §5.2 usageLimit/dataBudget：行/字段/字节预算，
 * 按 Descriptor 声明；缺少明确声明时高风险 READ 默认一次性——该默认语义由
 * Verifier/编排执行，本类型只承载声明值）。
 *
 * @param maxRows   最大行数
 * @param maxFields 最大字段数
 * @param maxBytes  最大字节数
 */
public record DataBudget(Long maxRows, Long maxFields, Long maxBytes) {

    public DataBudget {
        if (maxRows != null && maxRows <= 0) {
            throw new IllegalArgumentException("maxRows 必须为正数");
        }
        if (maxFields != null && maxFields <= 0) {
            throw new IllegalArgumentException("maxFields 必须为正数");
        }
        if (maxBytes != null && maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes 必须为正数");
        }
    }
}
