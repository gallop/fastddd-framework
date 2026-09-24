package com.gallop.fastddd.identity.core.error;

/**
 * 准入拒绝（v0.2 §6 AdmissionPolicy）：登录成功不自动准入；本地权威状态 + 宿主策略在 T2 内复验。
 *
 * @author gallop
 * @since 1.1.0
 */
public class AdmissionDeniedException extends RuntimeException {

    public AdmissionDeniedException(String message) {
        super(message);
    }
}
