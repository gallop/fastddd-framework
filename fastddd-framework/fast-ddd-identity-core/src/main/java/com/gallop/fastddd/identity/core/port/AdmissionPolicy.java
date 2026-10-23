package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.AdmissionDeniedException;

/**
 * 准入策略端口（v0.2 §6）：企业/主体状态（本地权威）+ 宿主策略决定准入；
 * 登录成功不自动准入。宿主提供实现；T2 内于签发前复验。
 *
 * @author gallop
 * @since 1.1.0
 */
public interface AdmissionPolicy {

    /**
     * 复验准入（在 T2 内、主体行已锁定后调用）。
     *
     * @throws AdmissionDeniedException 拒绝签发（整个 T2 回滚）
     */
    void checkAdmission(VerifiedPrincipal principal);
}
