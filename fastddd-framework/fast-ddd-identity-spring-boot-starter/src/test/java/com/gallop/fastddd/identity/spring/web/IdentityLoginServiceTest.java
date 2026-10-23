package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.IssuedSession;
import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.domain.LoginStateEntry;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.SessionId;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.AdmissionDeniedException;
import com.gallop.fastddd.identity.core.error.ExternalLoginRejectedException;
import com.gallop.fastddd.identity.core.error.ExternalLoginUnavailableException;
import com.gallop.fastddd.identity.core.error.IdentityConcurrencyException;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.port.LoginStateStore;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.core.usecase.LogoutUseCase;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录编排服务状态机（单元级，桩端口直接回调）：回跳白名单、state/绑定/错误回调
 * 分支、登录三态结局、登出幂等。
 */
class IdentityLoginServiceTest {

    /** 内存 state 存储：consume = remove（原子，模拟 PG 条件更新语义）。 */
    static final class InMemoryStateStore implements LoginStateStore {
        final Map<String, LoginStateEntry> states = new ConcurrentHashMap<>();
        volatile LoginStateEntry lastSaved;

        @Override
        public void save(LoginStateEntry entry) {
            states.put(entry.state(), entry);
            lastSaved = entry;
        }

        @Override
        public Optional<LoginStateEntry> consume(String state) {
            return Optional.ofNullable(states.remove(state));
        }
    }

    /** 直调 runner（无事务语义；服务只依赖回调执行）。 */
    static final class DirectRunner implements IdentityTransactionRunner {
        @Override
        public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
            return callback.doInTransaction();
        }
    }

    private final InMemoryStateStore stateStore = new InMemoryStateStore();
    private final DirectRunner runner = new DirectRunner();

    /** 可编程用例行为：默认成功。 */
    private Function<com.gallop.fastddd.identity.core.domain.ExternalLoginCommand, LoginResult> loginBehavior =
            command -> successfulLogin();

    private RuntimeException loginException;

    private final FastDddIdentityProperties properties = new FastDddIdentityProperties();

    private IdentityLoginService service;

    @BeforeEach
    void setUp() {
        properties.getLogin().setMode(FastDddIdentityProperties.Login.Mode.FEISHU_WEB);
        properties.getLogin().setStateTtl(Duration.ofMinutes(10));
        properties.getLogin().getAllowedPostLoginRedirects().add("/home");
        service = new IdentityLoginService(
                (state, challenge) -> "https://idp.example/authorize?state=" + state
                        + "&code_challenge=" + challenge + "&code_challenge_method=S256",
                stateStore,
                new LocalLoginUseCase(stubProvider(), noOpBindingStore(),
                        (AdmissionPolicy) principal -> { }, noOpSessionPort(), runner,
                        Duration.ofHours(8)) {
                    @Override
                    public LoginResult login(
                            com.gallop.fastddd.identity.core.domain.ExternalLoginCommand command) {
                        if (loginException != null) {
                            throw loginException;
                        }
                        return loginBehavior.apply(command);
                    }
                },
                new LogoutUseCase(noOpSessionPort(), runner),
                runner,
                properties);
    }

    private static ExternalLoginProvider stubProvider() {
        return command -> new VerifiedExternalLogin(
                new ExternalIdentity("feishu-stub", "t", "a", "union_id", "ou_x"), "login", "Display");
    }

    /** 用例桩：绑定/会话端口仅占位（本测试聚焦编排层状态机，不走真实 T2）。 */
    private static com.gallop.fastddd.identity.core.port.IdentityBindingStore noOpBindingStore() {
        return new com.gallop.fastddd.identity.core.port.IdentityBindingStore() {
            @Override
            public VerifiedPrincipal findOrCreatePrincipalWithBinding(
                    ExternalIdentity externalIdentity,
                    com.gallop.fastddd.identity.core.domain.PrincipalCreationSpec creationSpec) {
                return successfulLogin().principal();
            }

            @Override
            public Optional<VerifiedPrincipal> findPrincipal(String principalId) {
                return Optional.empty();
            }
        };
    }

    private static com.gallop.fastddd.identity.core.port.LocalSessionPort noOpSessionPort() {
        return new com.gallop.fastddd.identity.core.port.LocalSessionPort() {
            @Override
            public com.gallop.fastddd.identity.core.domain.IssuedSession issueSession(
                    VerifiedPrincipal principal, Duration ttl) {
                return successfulLogin().session();
            }

            @Override
            public Optional<VerifiedPrincipal> validateSession(SessionId sessionId) {
                return Optional.empty();
            }

            @Override
            public void revokeSession(SessionId sessionId) {
            }

            @Override
            public int revokeAllSessions(String principalId) {
                return 0;
            }
        };
    }

    private static LoginResult successfulLogin() {
        VerifiedPrincipal principal = new VerifiedPrincipal("t",
                PrincipalId.of("01234567-0123-4567-89ab-cdef01234567"),
                "login", "Display",
                com.gallop.fastddd.identity.core.domain.PrincipalType.HUMAN,
                com.gallop.fastddd.identity.core.domain.PrincipalStatus.ACTIVE, 1L);
        return new LoginResult(principal, new IssuedSession(SessionId.of("session-id-1234567890"), "t",
                principal.principalId(), 1L, 1, Instant.now(), Instant.now().plusSeconds(1)));
    }

    // ==== 回跳白名单 ====

    @Test
    void postLoginRedirectWhitelistAndShapeRules() {
        assertThat(beginAndCaptureRedirect(null)).isEqualTo("/");
        assertThat(beginAndCaptureRedirect("")).isEqualTo("/");
        assertThat(beginAndCaptureRedirect("/home")).isEqualTo("/home");     // 白名单内
        assertThat(beginAndCaptureRedirect("/nope")).isEqualTo("/");          // 不在白名单
        assertThat(beginAndCaptureRedirect("//evil.com")).isEqualTo("/");     // 协议相对
        assertThat(beginAndCaptureRedirect("https://evil.com")).isEqualTo("/");// 绝对地址
        assertThat(beginAndCaptureRedirect("/home?a=1")).isEqualTo("/");      // 非精确匹配
        assertThat(beginAndCaptureRedirect("/\\evil")).isEqualTo("/");        // 反斜杠
    }

    private String beginAndCaptureRedirect(String requested) {
        // begin 保存的 entry.postLoginRedirect 即回跳裁决结果；取最后保存的 entry
        // （多次 begin 累积时 map 迭代序不确定）
        service.begin(requested);
        return stateStore.lastSaved.postLoginRedirect();
    }

    // ==== 回调状态机 ====

    @Test
    void callbackRequiresState() {
        assertThat(service.handleCallback("code", null, null, "binding"))
                .isInstanceOf(IdentityLoginService.CallbackOutcome.Rejected.class);
    }

    @Test
    void callbackConsumesStateOnceAndRejectsReplay() {
        service.begin("/");
        String state = stateOfLastSaved();
        String binding = bindingOf(state);

        assertThat(service.handleCallback("code", state, null, binding))
                .isInstanceOf(IdentityLoginService.CallbackOutcome.Success.class);
        // 重放：state 已消费
        assertThat(service.handleCallback("code", state, null, binding))
                .isInstanceOf(IdentityLoginService.CallbackOutcome.Rejected.class);
    }

    @Test
    void callbackBindingMustMatch() {
        service.begin("/");
        String state = stateOfLastSaved();
        assertThat(service.handleCallback("code", state, null, "wrong-binding"))
                .isEqualTo(new IdentityLoginService.CallbackOutcome.Rejected("binding-mismatch",
                        "回调与授权发起的浏览器不一致，请重新发起登录"));
        assertThat(service.handleCallback("code", state, null, null))
                .isInstanceOf(IdentityLoginService.CallbackOutcome.Rejected.class);
    }

    @Test
    void errorCallbackIsRejectedAsAssociatedCallback() {
        service.begin("/");
        String state = stateOfLastSaved();
        String binding = bindingOf(state);
        // 错误回调也必须带匹配 state/绑定（防登录 CSRF 旁路），随后按拒绝处理
        IdentityLoginService.CallbackOutcome outcome =
                service.handleCallback(null, state, "access_denied", binding);
        assertThat(outcome).isInstanceOf(IdentityLoginService.CallbackOutcome.Rejected.class);
        assertThat(((IdentityLoginService.CallbackOutcome.Rejected) outcome).reason())
                .isEqualTo("upstream-denied");
        // 规范形态的 error 码原样保留（W22-RV-04 白名单内）
        assertThat(((IdentityLoginService.CallbackOutcome.Rejected) outcome).message())
                .contains("access_denied");
    }

    // ==== W22-RV-03/RV-04 修复轮 ====

    /** consume 阶段基础设施故障（如 DB 不可达）→ 四态 ServerFailure，不逃逸到容器 /error。 */
    @Test
    void consumeInfrastructureFailureMapsToServerFailure() {
        LoginStateStore brokenStore = new LoginStateStore() {
            @Override
            public void save(LoginStateEntry entry) {
            }

            @Override
            public Optional<LoginStateEntry> consume(String state) {
                throw new IllegalStateException("boom: 数据库连接失败（模拟 DataAccessException）");
            }
        };
        IdentityLoginService serviceWithBrokenStore = new IdentityLoginService(
                (state, challenge) -> "https://idp.example/authorize", brokenStore,
                (LocalLoginUseCase) null, null, runner, properties);

        IdentityLoginService.CallbackOutcome outcome =
                serviceWithBrokenStore.handleCallback("code", "state-any", null, "binding");
        assertThat(outcome).isInstanceOf(IdentityLoginService.CallbackOutcome.ServerFailure.class);
    }

    /** 攻击者可控的 error 参数越界（超长/控制字符/非白名单字符集）→ 回显与日志只留白名单值。 */
    @Test
    void upstreamErrorParamIsSanitizedBeforeEchoOrLog() {
        service.begin("/");
        String state = stateOfLastSaved();
        String poison = "x\n2026-09-26 fake-log-line<script>" + "A".repeat(200);

        IdentityLoginService.CallbackOutcome outcome =
                service.handleCallback(null, state, poison, bindingOf(state));

        assertThat(outcome).isInstanceOf(IdentityLoginService.CallbackOutcome.Rejected.class);
        String message = ((IdentityLoginService.CallbackOutcome.Rejected) outcome).message();
        assertThat(message).contains("unrecognized");
        assertThat(message).doesNotContain("<script>");
        assertThat(message).doesNotContain("fake-log-line");

        // 单元级白名单裁决：规范 OAuth error 码字符集（ALPHA/DIGIT/_-.，≤64）保留，其余替换
        assertThat(IdentityLoginService.sanitizeUpstreamError("access_denied")).isEqualTo("access_denied");
        assertThat(IdentityLoginService.sanitizeUpstreamError("invalid_scope-2.x")).isEqualTo("invalid_scope-2.x");
        assertThat(IdentityLoginService.sanitizeUpstreamError("has space")).isEqualTo("unrecognized");
        assertThat(IdentityLoginService.sanitizeUpstreamError("中文错误")).isEqualTo("unrecognized");
        assertThat(IdentityLoginService.sanitizeUpstreamError("a" + "b".repeat(64))).isEqualTo("unrecognized");
        assertThat(IdentityLoginService.sanitizeUpstreamError("  access_denied  ")).isEqualTo("access_denied");
        assertThat(IdentityLoginService.sanitizeUpstreamError(null)).isEqualTo("unrecognized");
    }

    @Test
    void missingCodeAfterValidStateIsRejected() {
        service.begin("/");
        String state = stateOfLastSaved();
        assertThat(service.handleCallback(null, state, null, bindingOf(state)))
                .isInstanceOf(IdentityLoginService.CallbackOutcome.Rejected.class);
    }

    @Test
    void loginOutcomeMapping() {
        // 确定拒绝（提供者）
        loginException = new ExternalLoginRejectedException("20021", "离职");
        assertThat(callWithFreshState()).isInstanceOf(IdentityLoginService.CallbackOutcome.Rejected.class);
        // 宿主准入拒绝
        loginException = new AdmissionDeniedException("no");
        assertThat(callWithFreshState()).isInstanceOf(IdentityLoginService.CallbackOutcome.Rejected.class);
        // 结果不确定（上游/传输）
        loginException = new ExternalLoginUnavailableException("transport", "网络");
        assertThat(callWithFreshState())
                .isInstanceOf(IdentityLoginService.CallbackOutcome.Unavailable.class);
        // 冲突重试耗尽
        loginException = new IdentityConcurrencyException("busy", null);
        assertThat(callWithFreshState())
                .isInstanceOf(IdentityLoginService.CallbackOutcome.Unavailable.class);
        // 服务端配置/实现失败（如飞书适配 IllegalState）
        loginException = new IllegalStateException("PKCE/配置错误（细节只进服务端日志）");
        assertThat(callWithFreshState())
                .isInstanceOf(IdentityLoginService.CallbackOutcome.ServerFailure.class);
    }

    @Test
    void successReturnsPostLoginRedirectFromStateEntry() {
        service.begin("/home");
        String state = stateOfLastSaved();
        IdentityLoginService.CallbackOutcome outcome =
                service.handleCallback("code", state, null, bindingOf(state));
        IdentityLoginService.CallbackOutcome.Success success =
                (IdentityLoginService.CallbackOutcome.Success) outcome;
        assertThat(success.postLoginRedirect()).isEqualTo("/home");
        assertThat(success.login().principal().principalId().value()).isNotBlank();
    }

    @Test
    void verifierIsPassedToProviderWithCode() {
        StringBuilder seenCommand = new StringBuilder();
        loginBehavior = command -> {
            seenCommand.append(command.credential("code")).append('|')
                    .append(command.credential("code_verifier"));
            return successfulLogin();
        };
        service.begin("/");
        String state = stateOfLastSaved();
        String savedVerifier = stateStore.states.get(state).codeVerifier();
        service.handleCallback("code-1", state, null, bindingOf(state));
        // 回调把"与 state 同存的 verifier"而非任何客户端输入交给提供者
        assertThat(seenCommand.toString()).isEqualTo("code-1|" + savedVerifier);
    }

    // ==== 登出 ====

    @Test
    void logoutIsShapeGuardedAndIdempotent() throws Exception {
        LogoutProbe probe = new LogoutProbe();
        IdentityLoginService logoutService = new IdentityLoginService(
                (state, challenge) -> "https://idp.example/authorize", stateStore,
                (LocalLoginUseCase) null, new LogoutUseCase(null, null) {
                    @Override
                    public void logout(SessionId sessionId) {
                        probe.revoked.add(sessionId.value());
                    }
                }, runner, properties);

        assertThat(logoutService.logout(null)).isFalse();
        assertThat(logoutService.logout("short")).isFalse();           // 形状不合法
        assertThat(logoutService.logout("session-id-1234567890")).isTrue(); // 撤销一次
        assertThat(logoutService.logout("session-id-1234567890")).isTrue(); // 幂等再撤销
        assertThat(probe.revoked).containsExactly("session-id-1234567890", "session-id-1234567890");
    }

    static final class LogoutProbe {
        final java.util.List<String> revoked = new java.util.ArrayList<>();
    }

    // ==== 辅助 ====

    private String stateOfLastSaved() {
        return stateStore.lastSaved.state();
    }

    private String bindingOf(String state) {
        return stateStore.states.get(state).browserBinding();
    }

    private IdentityLoginService.CallbackOutcome callWithFreshState() {
        service.begin("/");
        String state = stateOfLastSaved();
        return service.handleCallback("code", state, null, bindingOf(state));
    }
}
