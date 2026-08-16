package com.gallop.fastddd.agentaccess.spi;

/**
 * 授权事实修订号（Entitlement revision，TM-19/CAS 断言的值类型）。
 *
 * <p>语义：同一 {@code (tenant, entitlement)} 的授权事实单调递增修订；
 * 预检（事务外）与终检（事务内）之间 revision 漂移即「预检结论已失效」，
 * 终检必须拒绝（AAC-15：准入后撤销 ACL → 终检拒绝 + 漂移记录）。</p>
 *
 * <p>骨架期只固定值语义与比较；事务内 CAS 断言的接线
 * （与业务事务同一提交边界）属 FDD-AA-12 编排，不由本类型承担。</p>
 */
public record EntitlementRevision(long value) {

    public EntitlementRevision {
        if (value < 0) {
            throw new IllegalArgumentException("revision 必须非负");
        }
    }

    /** 是否相对 expected 发生漂移（不等即漂移）。 */
    public boolean driftedFrom(EntitlementRevision expected) {
        return !this.equals(expected);
    }
}
