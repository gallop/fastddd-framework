package com.gallop.fastddd.identity.spring.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录安全事件日志（W2-2）：与业务 {@code @AuditLog} 分开——登录链路在认证前
 * 发生，无 HUMAN Actor 可挂，记为结构化安全事件（独立 logger，便于宿主单独
 * 采集/告警）。
 *
 * <p><b>脱敏契约（硬约束）</b>：只记事件名 + 稳定原因码 + principalId（内部 UUID）；
 * 不记授权码/code_verifier/state 全值/会话 ID/token/app-secret/错误描述原文——
 * 字段级白名单，非黑名单。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
final class IdentityLoginEvents {

    private static final Logger LOG = LoggerFactory.getLogger("FASTDDD.IDENTITY.LOGIN");

    private IdentityLoginEvents() {
    }

    static void untrustedPostLoginRedirectRejected() {
        LOG.warn("[login] post-login redirect 不在白名单，回退 /（不记录原始值）");
    }

    static void callbackStateMissing() {
        LOG.warn("[login] 回调缺少 state 参数");
    }

    static void callbackStateRejected() {
        LOG.warn("[login] 回调 state 无效/过期/已使用（一次性消费拒绝）");
    }

    static void callbackBindingMismatch() {
        LOG.warn("[login] 回调浏览器绑定不匹配（state 已消费，绑定 Cookie 缺失或不等）");
    }

    static void upstreamDenied(String error) {
        LOG.warn("[login] 授权页拒绝回调：error={}", error);
    }

    static void callbackCodeMissing() {
        LOG.warn("[login] 回调缺少授权码（state 已消费）");
    }

    static void loginDeniedByProvider(String reasonCode) {
        LOG.warn("[login] 外部提供者拒绝登录：reasonCode={}", reasonCode);
    }

    static void admissionDenied() {
        LOG.warn("[login] 宿主准入策略拒绝（AdmissionDenied）");
    }

    static void loginUnavailable(String reasonCode) {
        LOG.warn("[login] 外部提供者结果不确定/暂不可用：reasonCode={}（重新发起登录；不推断身份状态）",
                reasonCode);
    }

    static void loginConcurrencyExhausted() {
        LOG.warn("[login] 签发事务冲突重试耗尽（请重新发起登录）");
    }

    static void loginServerFailure(Throwable cause) {
        LOG.error("[login] 登录链路服务端失败（配置/实现类；响应体不携带细节）", cause);
    }

    static void loginSucceeded(String principalId) {
        LOG.info("[login] 登录成功：principalId={}", principalId);
    }

    static void loggedOut(boolean revoked) {
        LOG.info("[login] 登出：服务端会话撤销={}", revoked);
    }
}
