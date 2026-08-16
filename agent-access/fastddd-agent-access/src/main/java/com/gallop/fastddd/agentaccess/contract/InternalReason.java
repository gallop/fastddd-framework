package com.gallop.fastddd.agentaccess.contract;

import java.util.regex.Pattern;

/**
 * 内部 reason code（AA-02 冻结映射规则的值承载）。
 *
 * <p>规则（Contract §9）：内部 reason 采用小写点分形式（如
 * {@code target.authorization.denied}），粒度允许细于外部码——多个内部
 * reason 可映射同一外部 {@link ActionOutcomeCode}，<b>反向必须唯一</b>
 * （一个内部 reason 只能映射一个外部码）。本类型为不可变值对象，反向唯一性
 * 由使用侧映射注册承担（完整对照表由 AA02 egress 字段映射文档维护，
 * 骨架期不做全局注册表——不过度冻结）。</p>
 *
 * @param code    小写点分 reason code
 * @param outcome 唯一映射的外部结果码
 */
public record InternalReason(String code, ActionOutcomeCode outcome) {

    private static final Pattern LOWER_DOT_CODE = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$");

    public InternalReason {
        if (code == null || !LOWER_DOT_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("code 必须是小写点分形式（如 target.authorization.denied）：" + code);
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome 必选：内部 reason 必须唯一映射一个外部结果码");
        }
    }
}
