package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.PrincipalCreationSpec;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.BindingConcurrentModificationException;

import java.util.List;
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

    /**
     * 按主体 ID 反查全部外部身份绑定（W5-e，T4 受控停用的拒绝行接线：停用事务
     * 须先取得五元组键——一主体可持多绑定〔open_id/union_id〕，逐键 record）。
     * default <b>fail-loud</b>：门禁在场的宿主若实现不支持反查，停用请求以
     * {@link UnsupportedOperationException} 失败（fail-closed——不静默跳过拒绝
     * 记录留下门禁缺口）。
     */
    default List<ExternalIdentity> findBindings(String principalId) {
        throw new UnsupportedOperationException(
                "findBindings 未实现——T4 受控停用的拒绝行记录需要按主体反查绑定（实现方必须覆盖）");
    }
}
