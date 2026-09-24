package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.PrincipalCreationSpec;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.BindingConcurrentModificationException;

import java.util.Optional;

/**
 * 身份绑定存储端口（v0.2 §6）：查找→创建主体→绑定，作用域键唯一约束裁决并发首次登录。
 *
 * <p>事务责任：MANDATORY——写实现只能在外层 T2 事务内执行，不各自提交；
 * 并发插入绑定撞唯一约束时翻译为 {@link BindingConcurrentModificationException}。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface IdentityBindingStore {

    /**
     * 按外部身份查找主体；绑定缺失时按规格创建主体（HUMAN，UUID 文本 principalId，epoch=0）并绑定。
     * 存在时锁定主体行（FOR UPDATE，锁持有到最终提交）。
     */
    VerifiedPrincipal findOrCreatePrincipalWithBinding(ExternalIdentity externalIdentity,
                                                       PrincipalCreationSpec creationSpec);

    /** 按主体 ID 查询（不锁定；管理/复验用途）。 */
    Optional<VerifiedPrincipal> findPrincipal(String principalId);
}
