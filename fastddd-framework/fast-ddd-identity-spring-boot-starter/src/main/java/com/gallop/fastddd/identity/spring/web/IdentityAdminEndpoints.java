package com.gallop.fastddd.identity.spring.web;

/**
 * identity 本地生命周期管理端点路径（W3 设计 v3 §9.2，W3-c）。
 *
 * <p>固定常量路径（对照 {@link IdentityLoginEndpoints}——不存在 base-path 配置）；
 * 两端点均为 POST、要求已认证 + CSRF（C 链维持 anyRequest().authenticated()，
 * 不入 permitAll；宿主链下 CSRF 归宿主责任项，§9.3 双链等效）。装配条件 =
 * login.mode=C ∧ {@code fastddd.identity.admin.enabled=true}（缺省 false）。
 * <b>管理停用端点不是手动同步入口</b>（§9.4——手动同步经
 * {@code DirectorySyncManualTrigger} 组件，宿主自选 CLI/JMX/消息/内部 HTTP 入口）。</p>
 *
 * <p>W5-d 追加第三端点：目录增量事件 FAILED 重放（POST，已认证 + CSRF——
 * 与两端点同防线面；装配条件独立 = increment.enabled ∧ 管理四端口齐备）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class IdentityAdminEndpoints {

    /** 本地受控停用（T4：DISABLED + epoch+1 + 全会话撤销，同事务）。 */
    public static final String DEACTIVATE_PATH =
            "/api/identity/admin/principals/{principalId}/deactivate";

    /** 本地受控启用（T4 逆操作，独立裁决）。 */
    public static final String REACTIVATE_PATH =
            "/api/identity/admin/principals/{principalId}/reactivate";

    /** 目录增量事件 FAILED 终态显式重放（W5 设计 §5.1 管理面，W5-d）。 */
    public static final String EVENTS_REPLAY_PATH =
            "/api/identity/admin/directory/events/{eventId}/replay";

    /**
     * 目录事实回收（W5 设计 §8.3 M-26 管理入口先例，W5-f f-1）：POST，
     * body = scope 两键 + preview 标志；装配条件独立于管理三端点
     * （web ∧ DirectoryRetentionExecutor/审计/Actor 端口齐备 ∧ directory.enabled）。
     */
    public static final String DIRECTORY_RETENTION_PATH =
            "/api/identity/admin/directory/retention";

    private IdentityAdminEndpoints() {
    }
}
