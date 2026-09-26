package com.gallop.fastddd.identity.core.error;

/**
 * 外部登录提供者结果不确定/暂不可用（W0-4 §3 传输与系统类）：网络超时、连接失败、
 * 上游 5xx/限流重试耗尽、响应不可解析等——本次登录无法给出确定结论。
 *
 * <p>与 {@link ExternalLoginRejectedException}（确定拒绝，不重试）相区分：本异常
 * 映射"稍后重新发起登录"（HTTP 503），<b>不是</b>对用户身份状态的判定（不得据此
 * 推断离职/停用）。state 已消费，重新登录必须从授权发起重新开始。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class ExternalLoginUnavailableException extends RuntimeException {

    private final String reasonCode;

    public ExternalLoginUnavailableException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public ExternalLoginUnavailableException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    /** 稳定原因码（如 transport / upstream-system / malformed-response），不含敏感细节。 */
    public String reasonCode() {
        return reasonCode;
    }
}
