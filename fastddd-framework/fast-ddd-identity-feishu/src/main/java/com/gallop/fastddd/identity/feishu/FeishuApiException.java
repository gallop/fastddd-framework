package com.gallop.fastddd.identity.feishu;

import java.util.Map;

/**
 * 飞书 OAuth 端点返回的结构化业务错误（W0-4 §3 按端点分列的编码输入）。
 *
 * <p><b>按端点分类，不跨端点混用</b>：同一错误码在 token v3 与 user_info v1 的语义
 * 不同（如 20008），客户端必须用对应端点的分类表。未知错误码归
 * {@link Category#UNKNOWN}，由上层按"默认拒绝本次登录"处置（不推断离职）。</p>
 *
 * <p>消息脱敏：{@link #getMessage()} 只含分类与错误码，不透传
 * {@code error_description}（其内容不可控，可能携带敏感细节）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class FeishuApiException extends RuntimeException {

    /** 发生错误的端点（token / user-info），分类表的作用域。 */
    public enum Endpoint {
        TOKEN("token"), USER_INFO("user-info");

        private final String label;

        Endpoint(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** W0-4 §3 错误类别（token v3 与 user_info v1 分别核对）。 */
    public enum Category {
        /** code 无效/过期/重用/不匹配（token）：重新发起授权，不重试兑换。 */
        CODE_INVALID,
        /** PKCE 校验失败（20049）：实现缺陷或参数篡改，显式失败并审计。 */
        PKCE_FAILED,
        /** client/应用配置错误：管理员修复（启动自检 + 显式失败）。 */
        CONFIG_ERROR,
        /** 用户状态/范围（准入拒绝）：拒绝登录并审计，不是重试类。 */
        LOGIN_REJECTED,
        /** token 无效（user_info 20005）：按过期处理，引导重新登录。 */
        TOKEN_INVALID,
        /** 系统错误/不可用（20050/20072 等）：结果不确定类。 */
        SYSTEM,
        /** 未核对过的错误码：默认拒绝本次登录，不推断身份状态。 */
        UNKNOWN
    }

    /** token v3 端点分类表（W0-4 §3，CW-01 复核 2026-09-26）。 */
    static final Map<Integer, Category> TOKEN_CATEGORIES = Map.ofEntries(
            Map.entry(20003, Category.CODE_INVALID),
            Map.entry(20004, Category.CODE_INVALID),
            Map.entry(20065, Category.CODE_INVALID),
            Map.entry(20024, Category.CODE_INVALID),
            Map.entry(20049, Category.PKCE_FAILED),
            Map.entry(20002, Category.CONFIG_ERROR),
            Map.entry(20048, Category.CONFIG_ERROR),
            Map.entry(20063, Category.CONFIG_ERROR),
            Map.entry(20036, Category.CONFIG_ERROR),
            Map.entry(20069, Category.CONFIG_ERROR),
            Map.entry(20070, Category.CONFIG_ERROR),
            Map.entry(20071, Category.CONFIG_ERROR),
            Map.entry(20008, Category.LOGIN_REJECTED),
            Map.entry(20009, Category.LOGIN_REJECTED),
            Map.entry(20010, Category.LOGIN_REJECTED),
            Map.entry(20066, Category.LOGIN_REJECTED),
            Map.entry(20067, Category.LOGIN_REJECTED),
            Map.entry(20068, Category.LOGIN_REJECTED),
            Map.entry(20050, Category.SYSTEM),
            Map.entry(20072, Category.SYSTEM));

    /** user_info v1 端点分类表（20008/20021/20022/20023 为准入拒绝；20050 为系统错误）。 */
    static final Map<Integer, Category> USER_INFO_CATEGORIES = Map.ofEntries(
            Map.entry(20005, Category.TOKEN_INVALID),
            Map.entry(20008, Category.LOGIN_REJECTED),
            Map.entry(20021, Category.LOGIN_REJECTED),
            Map.entry(20022, Category.LOGIN_REJECTED),
            Map.entry(20023, Category.LOGIN_REJECTED),
            Map.entry(20050, Category.SYSTEM));

    private final Endpoint endpoint;

    private final Category category;

    /** W22-C1：long 承载——畸形整型业务码（如 2^32）原值入诊断，不截断成 int。 */
    private final long code;

    private final int httpStatus;

    public FeishuApiException(Endpoint endpoint, Category category, long code, int httpStatus) {
        super("飞书" + endpoint.label() + "端点错误[" + category + "] code=" + code
                + " httpStatus=" + httpStatus);
        this.endpoint = endpoint;
        this.category = category;
        this.code = code;
        this.httpStatus = httpStatus;
    }

    static Category classify(Endpoint endpoint, long code) {
        if (code < Integer.MIN_VALUE || code > Integer.MAX_VALUE) {
            return Category.UNKNOWN;
        }
        Map<Integer, Category> table =
                endpoint == Endpoint.TOKEN ? TOKEN_CATEGORIES : USER_INFO_CATEGORIES;
        return table.getOrDefault((int) code, Category.UNKNOWN);
    }

    public Endpoint endpoint() {
        return endpoint;
    }

    public Category category() {
        return category;
    }

    /** 飞书错误码（未知码也原样携带，供审计定位；W22-C1 起 long 不截断）。 */
    public long code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    String label() {
        return endpoint.label();
    }
}
