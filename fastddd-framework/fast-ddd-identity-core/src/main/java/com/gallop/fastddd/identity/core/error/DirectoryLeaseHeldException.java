package com.gallop.fastddd.identity.core.error;

/**
 * 租约被他人持有（W3 设计 §6.1）：beginGeneration 时同 scope 存在有效租约
 * （另一实例正在同步）——<b>拒绝时零写入，不废弃他人的代</b>。
 *
 * <p>调用方语义 = "同步进行中"（手动触发入口据此返回；定时调度本轮跳过）。
 * 本异常不触发接管：接管只在租约失效/过期时发生。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryLeaseHeldException extends RuntimeException {

    public DirectoryLeaseHeldException(String message) {
        super(message);
    }
}
