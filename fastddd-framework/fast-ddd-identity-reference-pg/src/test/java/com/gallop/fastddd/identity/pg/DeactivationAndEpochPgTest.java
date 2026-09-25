package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.domain.PrincipalStatus;
import com.gallop.fastddd.identity.core.error.AdmissionDeniedException;
import com.gallop.fastddd.identity.core.error.PrincipalNotFoundException;
import com.gallop.fastddd.identity.core.error.UnknownSessionFormatException;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 真实 PG 门禁（§7/§9.2/T4）：停用同事务撤会话 + epoch 递增、幂等/NOT_FOUND、
 * 重新启用不复活旧会话、未知会话格式拒绝、进程"重启"后撤销状态仍生效。
 */
class DeactivationAndEpochPgTest {

    private final DataSource dataSource = Support.dataSource();

    private PgIdentityBindingStore bindingStore;
    private PgLocalSessionStore sessionStore;
    private com.gallop.fastddd.identity.core.port.IdentityTransactionRunner runner;
    private LocalLoginUseCase loginUseCase;
    private PrincipalLifecycleUseCase lifecycleUseCase;

    @BeforeEach
    void setUp() {
        Support.cleanIdentityTables(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        bindingStore = new PgIdentityBindingStore(jdbc);
        sessionStore = new PgLocalSessionStore(jdbc);
        runner = Support.runner(dataSource);
        ExternalLoginProvider provider = command -> new com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin(
                new com.gallop.fastddd.identity.core.domain.ExternalIdentity(
                        "feishu", "tenant-1", "app-1", "union_id", "ou_epoch"),
                "login-e", "Display E");
        AdmissionPolicy permit = principal -> { };
        loginUseCase = new LocalLoginUseCase(provider, bindingStore, permit, sessionStore, runner,
                Duration.ofHours(8));
        lifecycleUseCase = new PrincipalLifecycleUseCase(
                new PgIdentityLifecycleStore(jdbc, sessionStore), runner);
    }

    private LoginResult login() {
        return loginUseCase.login(new ExternalLoginCommand(Map.of()));
    }

    @Test
    void deactivateRevokesAllSessionsAndBumpsEpochSameTransaction() {
        LoginResult first = login();
        LoginResult second = login();
        String principalId = first.principal().principalId().value();

        var deactivated = lifecycleUseCase.deactivate(principalId);

        assertThat(deactivated.status()).isEqualTo(PrincipalStatus.DISABLED);
        assertThat(deactivated.authEpoch()).isEqualTo(1L);
        // 同事务撤销：两条会话 revoked_at 均非空
        Integer revokedCount = new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM local_session WHERE principal_id = ? AND revoked_at IS NOT NULL",
                Integer.class, principalId);
        assertThat(revokedCount).isEqualTo(2);
        // 每请求复验拒绝
        assertThat(sessionStore.validateSession(first.session().sessionId())).isEmpty();
        assertThat(sessionStore.validateSession(second.session().sessionId())).isEmpty();
    }

    @Test
    void deactivateIsIdempotentForAlreadyDisabledAndNotFoundOtherwise() {
        LoginResult result = login();
        String principalId = result.principal().principalId().value();

        var first = lifecycleUseCase.deactivate(principalId);
        var second = lifecycleUseCase.deactivate(principalId);

        // 幂等成功：第二次不再递增 epoch
        assertThat(second.status()).isEqualTo(PrincipalStatus.DISABLED);
        assertThat(second.authEpoch()).isEqualTo(first.authEpoch()).isEqualTo(1L);

        // 主体不存在 = NOT_FOUND，不冒充停用成功（FI-03）
        assertThatThrownBy(() -> lifecycleUseCase.deactivate("01234567-89ab-cdef-0123-456789abcdef"))
                .isInstanceOf(PrincipalNotFoundException.class);
    }

    @Test
    void reactivationDoesNotReviveOldSessions() {
        LoginResult before = login();
        String principalId = before.principal().principalId().value();

        lifecycleUseCase.deactivate(principalId);
        var reactivated = lifecycleUseCase.reactivate(principalId);

        // epoch 再递增；旧会话保持撤销（不复活）
        assertThat(reactivated.status()).isEqualTo(PrincipalStatus.ACTIVE);
        assertThat(reactivated.authEpoch()).isEqualTo(2L);
        assertThat(sessionStore.validateSession(before.session().sessionId())).isEmpty();

        // 新登录按新 epoch 签发并可复验
        LoginResult after = login();
        assertThat(after.principal().authEpoch()).isEqualTo(2L);
        assertThat(after.session().authEpochAtIssue()).isEqualTo(2L);
        assertThat(sessionStore.validateSession(after.session().sessionId())).isPresent();
    }

    @Test
    void loginOnDisabledPrincipalIsRejectedByFrameworkGate() {
        LoginResult result = login();
        lifecycleUseCase.deactivate(result.principal().principalId().value());

        // 停用后新授权决策：即使宿主策略放行，框架状态门禁拒绝签发
        assertThatThrownBy(this::login).isInstanceOf(AdmissionDeniedException.class)
                .hasMessageContaining("停用");
    }

    @Test
    void unknownSessionFormatIsRejectedNotAuthenticated() {
        LoginResult result = login();
        // 直接注入未知格式版本会话行（模拟升级矩阵外的遗留/未来格式）
        new JdbcTemplate(dataSource).update(
                "UPDATE local_session SET session_format_version = 99 WHERE session_id = ?",
                result.session().sessionId().value());

        assertThatThrownBy(() -> sessionStore.validateSession(result.session().sessionId()))
                .isInstanceOf(UnknownSessionFormatException.class)
                .hasMessageContaining("99");
    }

    @Test
    void revokedStateSurvivesAdapterRestart() {
        // "进程重启"：重新构建全部适配器/用例（同一 PG），撤销与停用状态仍生效
        LoginResult result = login();
        lifecycleUseCase.deactivate(result.principal().principalId().value());

        // "重启"= 重建适配器对象，但复用同一 DataSource Bean 实例（W21-03 runner 契约：
        // 事务与适配器必须绑定同一实例；换实例正是门禁要拒绝的错误装配）
        JdbcTemplate freshJdbc = new JdbcTemplate(dataSource);
        PgLocalSessionStore freshSessionStore = new PgLocalSessionStore(freshJdbc);
        var freshLifecycle = new PrincipalLifecycleUseCase(
                new PgIdentityLifecycleStore(freshJdbc, freshSessionStore), Support.runner(dataSource));

        assertThat(freshSessionStore.validateSession(result.session().sessionId())).isEmpty();
        var still = freshLifecycle.deactivate(result.principal().principalId().value());
        assertThat(still.status()).isEqualTo(PrincipalStatus.DISABLED);
        assertThat(still.authEpoch()).isEqualTo(1L); // 幂等：重启后不重复递增
    }

    @Test
    void expiredSessionIsRejected() {
        // 固定时钟签发已过期会话（先清掉登录产生的会话行，保证唯一的固定时钟行）
        java.time.Instant fixed = java.time.Instant.now();
        com.gallop.fastddd.identity.core.domain.VerifiedPrincipal principal = bindingStore
                .findPrincipal(login().principal().principalId().value()).orElseThrow();
        new JdbcTemplate(dataSource).update("DELETE FROM local_session");
        PgLocalSessionStore fixedClockStore = new PgLocalSessionStore(
                new JdbcTemplate(dataSource), () -> fixed);
        runner.inTransaction(() -> fixedClockStore.issueSession(principal, Duration.ofSeconds(1)));

        PgLocalSessionStore laterClockStore = new PgLocalSessionStore(
                new JdbcTemplate(dataSource), () -> fixed.plusSeconds(10));
        String sessionId = new JdbcTemplate(dataSource).queryForObject(
                "SELECT session_id FROM local_session", String.class);
        assertThat(laterClockStore.validateSession(
                com.gallop.fastddd.identity.core.domain.SessionId.of(sessionId))).isEmpty();
    }
}
