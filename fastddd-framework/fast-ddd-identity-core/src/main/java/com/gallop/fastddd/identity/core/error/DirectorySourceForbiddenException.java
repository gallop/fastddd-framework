package com.gallop.fastddd.identity.core.error;

/**
 * 目录源无权限（W3 设计 §6.5，W0-4 §3：230002/41050/40004 等）：
 * 配置类错误——fail-fast，整代废弃，<b>禁止退避重试风暴</b>。
 *
 * <p><b>禁止把无权限/缺失当作离职/删除</b>：本异常不构成对任何人员状态的判定，
 * 不写墓碑、不置人员级待核对（范围证据已不完整，按范围级待核对登记）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectorySourceForbiddenException extends RuntimeException {

    private final String reasonCode;

    public DirectorySourceForbiddenException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public DirectorySourceForbiddenException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    /** 稳定原因码（如 upstream-forbidden），不含敏感细节。 */
    public String reasonCode() {
        return reasonCode;
    }
}
