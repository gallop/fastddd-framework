package com.gallop.fastddd.identity.core.error;

/**
 * 租约已丢失（W3 设计 §6.1 迟到操作表）：token 与当前持有者不匹配——
 * 已被接管或租约被清理。旧持有者的一切写操作（writeBatch/publish/abandon）
 * 均被拒，<b>新持有者的代不受影响</b>。
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryLeaseLostException extends RuntimeException {

    public DirectoryLeaseLostException(String message) {
        super(message);
    }
}
