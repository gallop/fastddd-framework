package com.gallop.fastddd.identity.core.error;

/**
 * 主体不存在（T4 语义）：与"已停用（幂等成功）"严格区分，不存在返回 NOT_FOUND。
 *
 * @author gallop
 * @since 1.1.0
 */
public class PrincipalNotFoundException extends RuntimeException {

    public PrincipalNotFoundException(String message) {
        super(message);
    }
}
