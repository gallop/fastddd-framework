package com.gallop.fastddd.agentaccess.spi;

/**
 * 事务内资源终检 Port（两段式授权第二段；TM-19）。
 *
 * <p>职责：在业务事务<b>之内</b>（聚合已加载、写锁已持有时）对具体资源做
 * 终检，并以 {@link EntitlementRevision} CAS 断言「授权事实自预检以来未漂移」。
 * 预检后撤 ACL/改归属（revision 漂移）→ 终检拒绝（AAC-15）。</p>
 *
 * <p>硬约束：<b>终检不得调用远程 PDP</b>（事务内网络调用会把 PDP 延迟/故障
 * 变成业务事务悬挂）；只能使用本地授权事实或
 * {@link LocalDecisionCache} 三要素校验（{@link LocalDecisionCache#isStillApplicable}）
 * 通过的已验证本地决定。</p>
 */
public interface ResourceAuthorizationPort {

    /**
     * 事务内资源终检。
     *
     * @param check            资源终检查询
     * @param expectedRevision 预检时捕获的授权事实修订号（CAS 断言基准）
     * @return 授权决定（deny-overrides；revision 漂移 → DENY）
     */
    AuthorizationDecision authorizeResource(ResourceCheck check, EntitlementRevision expectedRevision);

    /**
     * 资源终检查询参数。
     *
     * @param tenantId     租户作用域（先按 tenant 定位资源，再比较版本/状态）
     * @param resourceType 业务资源类型
     * @param resourceId   tenant 内资源标识
     */
    record ResourceCheck(String tenantId, String resourceType, String resourceId) {
        public ResourceCheck {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId 必选且不得为空白");
            }
            if (resourceType == null || resourceType.isBlank()) {
                throw new IllegalArgumentException("resourceType 必选且不得为空白");
            }
            if (resourceId == null || resourceId.isBlank()) {
                throw new IllegalArgumentException("resourceId 必选且不得为空白");
            }
        }
    }
}
