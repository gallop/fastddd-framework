package com.gallop.fastddd.identity.spring.web;

/**
 * 目录增量事件端点路径（W5 设计 §10，W5-d）。
 *
 * <p>固定常量路径（对照 {@link IdentityLoginEndpoints}——设计 §10 登记：
 * <b>不引入可配路径</b>，暴露面确定、审计简单）。POST、无会话依赖——
 * 安全性由接收链验签/解密承载（§5.1，M-1），C 链下 permitAll + CSRF 豁免
 * 仅此常量路径。装配条件 =
 * {@code fastddd.identity.directory.increment.enabled=true} ∧ 通道/收件箱
 * 端口齐备（缺任一由 {@code IdentityModeValidator} 终检使启动失败）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class IdentityDirectoryEventsEndpoints {

    /** 飞书事件订阅回调（验签 → 解密 → 防重放 → scope → challenge/入箱）。 */
    public static final String EVENTS_PATH = "/api/identity/directory/events";

    private IdentityDirectoryEventsEndpoints() {
    }
}
