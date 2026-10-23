package com.gallop.fastddd.identity.core.error;

/**
 * 目录源暂不可用（W3 设计 §6.5）：分页超时/连接失败/HTTP 5xx/429 限流等
 * 瞬时形态——结果不确定，本次同步无法给出确定结论。
 *
 * <p><b>重试责任在源适配器</b>（幂等 GET 有界重试 + 退避，配额内；W3-b 交付）；
 * 编排用例不重试分页——重试耗尽后源抛出本异常，编排按整代废弃处理。
 * 本异常不得解释为任何人员的删除/离职。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectorySourceUnavailableException extends RuntimeException {

    private final String reasonCode;

    public DirectorySourceUnavailableException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public DirectorySourceUnavailableException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    /** 稳定原因码（如 transport / rate-limited / upstream-system）。 */
    public String reasonCode() {
        return reasonCode;
    }
}
