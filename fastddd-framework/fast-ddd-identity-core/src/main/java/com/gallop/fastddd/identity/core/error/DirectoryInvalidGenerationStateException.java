package com.gallop.fastddd.identity.core.error;

/**
 * 代状态前置不满足（W3 设计 §4 操作前置表）：如 VALIDATED 后追加批次
 * （矩阵 12）、BUILDING 未完成三流即 completePaging、非 VALIDATED 发布、
 * 终态代再操作等。零行落库/零状态变更。
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryInvalidGenerationStateException extends RuntimeException {

    private final com.gallop.fastddd.identity.core.domain.DirectoryGeneration.State expected;
    private final com.gallop.fastddd.identity.core.domain.DirectoryGeneration.State actual;

    public DirectoryInvalidGenerationStateException(
            com.gallop.fastddd.identity.core.domain.DirectoryGeneration.State expected,
            com.gallop.fastddd.identity.core.domain.DirectoryGeneration.State actual,
            String message) {
        super(message);
        this.expected = expected;
        this.actual = actual;
    }

    /** 操作要求的代状态。 */
    public com.gallop.fastddd.identity.core.domain.DirectoryGeneration.State expected() {
        return expected;
    }

    /** 实际代状态。 */
    public com.gallop.fastddd.identity.core.domain.DirectoryGeneration.State actual() {
        return actual;
    }
}
