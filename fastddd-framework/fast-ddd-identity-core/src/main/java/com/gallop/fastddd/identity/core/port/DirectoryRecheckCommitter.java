package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
import com.gallop.fastddd.identity.core.domain.RecheckResult;

/**
 * 权威回查提交端口（W5 设计 §3.3/§7，W5-b 签名冻结）：以 CAS 基线裁决回查
 * 证据的提交事务（事件只触发回查 + local_revision CAS——W0-6 P-8 结论）。
 *
 * <p>调用契约：须在<b>提交事务内</b>调用；{@code evidence.expectedRevision()}
 * 为 CAS 基线——事务内镜像行 local_revision 已前移（他方先提交）则
 * {@code stale=true} 零写零推返回，调用方以新基线重走回查（锁序
 * metadata → advisory，无环）。提交事务内先做<b>路径裁决（与增量同构，P1-2，
 * 设计 :178）</b>：锁 metadata 行时存在非终态构建代 → 预合并语义（推 committed_seq +
 * G.premerged_seq，不推 applied_seq、不发通知）；空闲态 → 推 committed_seq +
 * applied_seq、非空变更时发通知。{@code conclusion=CONTENT_DIFFERS} 须携带
 * 观察对象（实现层校验）；DEACTIVATED 分支的 external_rejection 门禁链
 * 写入与登录同序（W5-e 集成）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryRecheckCommitter {

    /** CAS 裁决提交（事务内调用；stale 结果零写）。 */
    RecheckResult commitCas(DirectoryScope scope, RecheckEvidence evidence);
}
