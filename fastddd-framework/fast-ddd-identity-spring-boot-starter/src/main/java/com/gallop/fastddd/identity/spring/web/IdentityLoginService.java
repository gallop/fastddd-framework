package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.domain.LoginStateEntry;
import com.gallop.fastddd.identity.core.domain.SessionId;
import com.gallop.fastddd.identity.core.error.AdmissionDeniedException;
import com.gallop.fastddd.identity.core.error.ExternalLoginRejectedException;
import com.gallop.fastddd.identity.core.error.ExternalLoginUnavailableException;
import com.gallop.fastddd.identity.core.error.IdentityConcurrencyException;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.port.LoginStateStore;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.core.usecase.LogoutUseCase;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * 浏览器登录编排（W2-2，v0.2 §7.3 时序）：授权发起（state+PKCE+浏览器绑定）→
 * 回调（原子消费 state → 绑定匹配 → 外部凭证验证【事务外】→ T2 统一签发）→
 * 登出（撤销 + Cookie 清除）。
 *
 * <p><b>回调状态机（每一步失败即终止，state 一次性消费不可逆）</b>：</p>
 * <ol>
 *   <li>state 缺失 → 拒绝（401）。此时无可消费上下文，绑定 Cookie 一并清除；</li>
 *   <li><b>T-state 短事务</b>原子消费：不存在/过期/已消费 → 拒绝（401）。
 *       消费先于一切校验——并发重复回调恰有一个进入后续步骤，其余在存储层落败；</li>
 *   <li>浏览器绑定：回调请求携带的绑定 Cookie 与已消费条目不匹配 → 拒绝（401，
 *       state 已烧毁，防绑定值试探）；</li>
 *   <li>错误回调（error=access_denied 等）：作为关联回调处理（非旁路）→ 拒绝（401）；</li>
 *   <li>授权码缺失 → 拒绝（401）；</li>
 *   <li>登录：外部验证在事务外（LocalLoginUseCase 契约），T2 内复验状态/epoch/准入。
 *       确定拒绝（提供者拒绝/宿主准入拒绝）→ 401；结果不确定（传输/系统/并发耗尽）→
 *       503（重新发起登录，<b>不推断身份状态</b>）；配置/实现类失败（IllegalState 等）→
 *       500（服务端日志含细节，响应体脱敏）——W22-RV-03 起 consume 阶段基础设施
 *       异常（如登录状态存储不可达）同映射 500 ServerFailure；</li>
 *   <li>成功：T2 <b>提交后</b>由控制层经 {@code IdentitySessionCookies.issue} 签发
 *       会话 Cookie（响应丢失不回滚数据库，§8.3 条 6），302 至白名单内站内回跳地址。</li>
 * </ol>
 *
 * <p><b>回跳白名单</b>：仅接受以单斜杠开头、非 "//" 开头、无反斜杠且在
 * {@code fastddd.identity.login.allowed-post-login-redirects} 内的值；其余回退 "/"
 * （防开放重定向；不回显被拒值）。</p>
 *
 * <p>本类不感知飞书（对 {@code ExternalLoginProvider} 的具体实现保持中性）；
 * 所有事件经 {@link IdentityLoginEvents} 字段级脱敏。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 回跳缺省与回退目标（与配置口径同源）。 */
    public static final String DEFAULT_POST_LOGIN_REDIRECT =
            FastDddIdentityProperties.Login.DEFAULT_POST_LOGIN_REDIRECT;

    private final ExternalAuthorizePort authorizePort;
    private final LoginStateStore loginStateStore;
    private final LocalLoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final IdentityTransactionRunner runner;
    private final FastDddIdentityProperties properties;

    public IdentityLoginService(ExternalAuthorizePort authorizePort,
                                LoginStateStore loginStateStore,
                                LocalLoginUseCase loginUseCase,
                                LogoutUseCase logoutUseCase,
                                IdentityTransactionRunner runner,
                                FastDddIdentityProperties properties) {
        this.authorizePort = authorizePort;
        this.loginStateStore = loginStateStore;
        this.loginUseCase = loginUseCase;
        this.logoutUseCase = logoutUseCase;
        this.runner = runner;
        this.properties = properties;
    }

    /** 授权发起产物：302 目标 + 绑定 Cookie 值（HttpOnly 由控制层种下）。 */
    public record BeginLogin(String authorizeUrl, String browserBinding) {
    }

    /** 回调结局三态 + 服务端失败（控制层映射 401/503/500/302）。 */
    public sealed interface CallbackOutcome {
        record Success(LoginResult login, String postLoginRedirect) implements CallbackOutcome {
        }

        /** 确定拒绝本次登录（401）：state/绑定/用户拒绝/提供者拒绝/宿主准入。 */
        record Rejected(String reason, String message) implements CallbackOutcome {
        }

        /** 结果不确定/暂不可用（503）：重新发起登录，不推断身份状态。 */
        record Unavailable(String reason, String message) implements CallbackOutcome {
        }

        /** 服务端配置/实现类失败（500）：响应体脱敏。 */
        record ServerFailure() implements CallbackOutcome {
        }
    }

    /** 发起授权：state + PKCE verifier + 浏览器绑定 生成并保存（T-state 短事务）。 */
    public BeginLogin begin(String requestedPostLoginRedirect) {
        String postLoginRedirect = resolvePostLoginRedirect(requestedPostLoginRedirect);
        String state = randomToken();
        String verifier = Pkce.verifier();
        String browserBinding = randomToken();
        Instant expiresAt = Instant.now().plus(properties.getLogin().getStateTtl());
        runner.inTransaction(() -> {
            loginStateStore.save(new LoginStateEntry(state, verifier, browserBinding,
                    postLoginRedirect, expiresAt));
            return null;
        });
        return new BeginLogin(authorizePort.authorizeUrl(state, Pkce.challenge(verifier)), browserBinding);
    }

    /**
     * 处理回调：返回结局（见状态机）。绑定为 null 表示请求未携带绑定 Cookie。
     */
    public CallbackOutcome handleCallback(String code, String state, String error, String bindingCookieValue) {
        if (state == null || state.isBlank()) {
            IdentityLoginEvents.callbackStateMissing();
            return new CallbackOutcome.Rejected("state-missing", "回调缺少 state，请重新发起登录");
        }
        // T-state 短事务：原子一次性消费（并发重复回调此处落败）。
        // W22-RV-03：本阶段（含 runner）抛出的基础设施异常也归四态 ServerFailure——
        // 消费未发生（事务回滚），契约响应体不落容器默认 /error；细节只进服务端日志
        LoginStateEntry consumed;
        try {
            consumed = runner.inTransaction(() -> loginStateStore.consume(state))
                    .orElseGet(() -> null);
        } catch (RuntimeException infrastructureFailure) {
            IdentityLoginEvents.loginServerFailure(infrastructureFailure);
            return new CallbackOutcome.ServerFailure();
        }
        if (consumed == null) {
            IdentityLoginEvents.callbackStateRejected();
            return new CallbackOutcome.Rejected("state-rejected",
                    "state 无效、过期或已使用，请重新发起登录");
        }
        if (bindingCookieValue == null || !bindingCookieValue.equals(consumed.browserBinding())) {
            IdentityLoginEvents.callbackBindingMismatch();
            return new CallbackOutcome.Rejected("binding-mismatch",
                    "回调与授权发起的浏览器不一致，请重新发起登录");
        }
        if (error != null && !error.isBlank()) {
            // W22-RV-04：error 是攻击者可控的回调查询参数——白名单化后才回显/落日志
            //（OAuth 2.0 error 码字符集 ALPHA/DIGIT/_-.，长度上限 64）；越界值不回显原文
            String safeError = sanitizeUpstreamError(error);
            IdentityLoginEvents.upstreamDenied(safeError);
            return new CallbackOutcome.Rejected("upstream-denied",
                    "授权未完成（" + safeError + "），请重新发起登录");
        }
        if (code == null || code.isBlank()) {
            IdentityLoginEvents.callbackCodeMissing();
            return new CallbackOutcome.Rejected("code-missing", "回调缺少授权码，请重新发起登录");
        }
        try {
            LoginResult result = loginUseCase.login(new ExternalLoginCommand(Map.of(
                    "code", code, "code_verifier", consumed.codeVerifier())));
            IdentityLoginEvents.loginSucceeded(result.principal().principalId().value());
            return new CallbackOutcome.Success(result,
                    consumed.postLoginRedirect() == null ? DEFAULT_POST_LOGIN_REDIRECT
                            : consumed.postLoginRedirect());
        } catch (ExternalLoginRejectedException rejected) {
            IdentityLoginEvents.loginDeniedByProvider(rejected.reasonCode());
            return new CallbackOutcome.Rejected("login-denied:" + rejected.reasonCode(), rejected.getMessage());
        } catch (AdmissionDeniedException admission) {
            IdentityLoginEvents.admissionDenied();
            return new CallbackOutcome.Rejected("admission-denied", "宿主准入策略拒绝登录");
        } catch (ExternalLoginUnavailableException unavailable) {
            IdentityLoginEvents.loginUnavailable(unavailable.reasonCode());
            return new CallbackOutcome.Unavailable(unavailable.reasonCode(),
                    "外部登录暂不可用或结果不确定，请重新发起登录");
        } catch (IdentityConcurrencyException concurrency) {
            IdentityLoginEvents.loginConcurrencyExhausted();
            return new CallbackOutcome.Unavailable("concurrency",
                    "登录并发冲突，请重新发起登录");
        } catch (RuntimeException serverFailure) {
            // 配置/实现类失败（如飞书适配的 IllegalState：PKCE/配置错误）：细节只进服务端日志
            IdentityLoginEvents.loginServerFailure(serverFailure);
            return new CallbackOutcome.ServerFailure();
        }
    }

    /**
     * 登出：撤销服务端会话（幂等）。返回是否对形状合法的会话 ID 执行了撤销；
     * 缺失/形状不合法视为已登出（控制层仍清除 Cookie）。不宣称外部全局登出。
     */
    public boolean logout(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return false;
        }
        SessionId sessionId;
        try {
            sessionId = SessionId.of(rawSessionId);
        } catch (IllegalArgumentException invalidShape) {
            return false;
        }
        logoutUseCase.logout(sessionId);
        IdentityLoginEvents.loggedOut(true);
        return true;
    }

    /** 站内回跳白名单裁决（失败回退 "/"；被拒值不回显不记录）。 */
    private String resolvePostLoginRedirect(String requested) {
        String fallback = DEFAULT_POST_LOGIN_REDIRECT;
        if (requested == null || requested.isBlank()) {
            return fallback;
        }
        boolean shapeOk = requested.startsWith("/") && !requested.startsWith("//")
                && !requested.contains("\\");
        if (shapeOk && properties.getLogin().getAllowedPostLoginRedirects().contains(requested)) {
            return requested;
        }
        IdentityLoginEvents.untrustedPostLoginRedirectRejected();
        return fallback;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 上游 error 参数白名单化（W22-RV-04）：OAuth 2.0 错误码字符集
     * （ALPHA/DIGIT/下划线/连字符/点）且 ≤64 字符才原样保留；其余（超长、含
     * 换行/控制字符/非 ASCII）一律替换为 {@code unrecognized}——响应体与审计
     * 日志都不携带不可控原文（防日志行伪造与响应体注入）。
     */
    static String sanitizeUpstreamError(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.matches("[A-Za-z0-9_.\\-]{1,64}") ? trimmed : "unrecognized";
    }
}
