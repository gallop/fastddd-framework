package com.gallop.fastddd.identity.core.error;

/**
 * 身份模式下的操作缺少员工/系统身份（v0.2 §5.6）：CRUD 写入与身份模式审计
 * 拒绝降级为 {@code system}——本异常即"拒绝"。
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityRequiredException extends RuntimeException {

    public IdentityRequiredException(String message) {
        super(message);
    }
}
