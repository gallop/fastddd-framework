package com.gallop.fastddd.identity.core.domain;

/**
 * 权威回查结论（W5 设计 §7，W5-b 签名冻结）：事务外只读单点查询的确证结果，
 * 驱动 {@code DirectoryRecheckCommitter.commitCas} 的提交分支。
 *
 * <p>与证据的携带约定（实现层校验）：{@code CONTENT_DIFFERS} 须携带观察到的
 * 权威内容对象（人员/部门观察值，用于内容替换）；其余结论不携带替换内容。
 * 「待验不关闭」不变量（pending_confirm 行不被任何自动路径关闭）不属本枚举面。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public enum RecheckConclusion {

    /** 确证存在且内容不同 → 内容替换（rev+1、origin=RECHECK、updated_seq=新 H）。 */
    CONTENT_DIFFERS,

    /** 确证存在且一致 → 仅复位 pending_confirm（零水位推进、零墓碑动作）。 */
    IDENTICAL,

    /** 确证删除（或部门 40015"不存在"锚）→ 写墓碑。 */
    ABSENT,

    /** 确证停用/离职（status 布尔组）→ 写墓碑 + external_rejection 门禁链。 */
    DEACTIVATED
}
