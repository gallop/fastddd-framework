package com.gallop.fastddd.identity.core.error;

/**
 * 目录契约违反（W3 设计 §2/§6.5）：分页形态违反
 * {@link com.gallop.fastddd.identity.core.domain.DirectoryPage} 一致性契约
 * （hasMore 无游标/游标不前进/页元素非法）、关系引用缺失、范围外对象混入、
 * validate 前置不满足等——<b>确定失败，不重试</b>，整代废弃。
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryContractViolationException extends RuntimeException {

    private final String reasonCode;

    public DirectoryContractViolationException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    /** 稳定原因码（如 cursor-not-advancing / has-more-without-cursor / dangling-relation）。 */
    public String reasonCode() {
        return reasonCode;
    }
}
