package com.gallop.fastddd.identity.core.error;

/**
 * 可重试冲突在有限次数（默认 3 次）后仍未收敛：向外失败，不映射为"已停用/已成功"。
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityConcurrencyException extends RuntimeException {

    public IdentityConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
