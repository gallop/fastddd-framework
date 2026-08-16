package com.gallop.fastddd.agentaccess.spi;

/**
 * 数值上限义务（合成规则：同名取更严格值 min——两侧上限同时满足即取较小者）。
 */
public record NumericCapObligation(String capName, long maxValue) implements Obligation {

    public NumericCapObligation {
        if (capName == null || capName.isBlank()) {
            throw new IllegalArgumentException("capName 必选且不得为空白");
        }
        if (maxValue <= 0) {
            throw new IllegalArgumentException("maxValue 必须为正数");
        }
    }

    @Override
    public String type() {
        return "NUMERIC_CAP";
    }
}
