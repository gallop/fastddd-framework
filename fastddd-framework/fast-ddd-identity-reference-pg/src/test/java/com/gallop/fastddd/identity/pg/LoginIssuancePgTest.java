package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.domain.PrincipalCreationSpec;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.error.AdmissionDeniedException;
import com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 真实 PG 门禁（§12 门禁 2/8 的本地子集）：T2 原子性、准入失败全回滚、
 * 并发首次登录唯一约束 + 新事务重试、每请求权威复验。
 */
class LoginIssuancePgTest {

    private final DataSource dataSource = Support.dataSource();
    private final DataSource otherInstance = Support.dataSource();

    private com.gallop.fastddd.identity.pg.PgIdentityBindingStore bindingStore;
    private com.gallop.fastddd.identity.pg.PgLocalSessionStore sessionStore;
    private com.gallop.fastddd.identity.core.port.IdentityTransactionRunner runner;
    private com.gallop.fastddd.identity.pg.PgExternalRejectionGate rejectionGate;
    private LocalLoginUseCase loginUseCase;
    private final AtomicInteger admissionCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        Support.cleanIdentityTables(dataSource);
        bindingStore = new PgIdentityBindingStore(new org.springframework.jdbc.core.JdbcTemplate(dataSource));
        sessionStore = new PgLocalSessionStore(new org.springframework.jdbc.core.JdbcTemplate(dataSource));
        rejectionGate = new PgExternalRejectionGate(new org.springframework.jdbc.core.JdbcTemplate(dataSource));
        runner = Support.runner(dataSource);
        admissionCalls.set(0);
        loginUseCase = new LocalLoginUseCase(
                fixedProvider(), bindingStore, permitAll(), sessionStore, runner, Duration.ofHours(8));
    }

    private ExternalLoginProvider fixedProvider() {
        return command -> new VerifiedExternalLogin(identity(), "login-a", "Display A");
    }

    private static ExternalIdentity identity() {
        return new ExternalIdentity("feishu", "tenant-1", "app-1", "union_id", "ou_test_1");
    }

    private AdmissionPolicy permitAll() {
        return principal -> admissionCalls.incrementAndGet();
    }

    @Test
    void emptyDatabaseMigrationAndFirstLoginRoundTrip() {
        PgIdentitySchema.initialize(dataSource); // 空库幂等初始化（重复执行不报错）
        LoginResult result = loginUseCase.login(new ExternalLoginCommand(Map.of("token", "stub")));

        // CW-02：标准 UUID 文本 36 字符，主体/绑定/会话/复验同值不截断
        assertThat(result.principal().principalId().value()).hasSize(36);
        assertThat(Support.count(dataSource, "principal")).isEqualTo(1);
        assertThat(Support.count(dataSource, "external_binding")).isEqualTo(1);
        assertThat(Support.count(dataSource, "local_session")).isEqualTo(1);

        var validated = sessionStore.validateSession(result.session().sessionId());
        assertThat(validated).isPresent();
        assertThat(validated.get().principalId()).isEqualTo(result.principal().principalId());
        assertThat(validated.get().status().name()).isEqualTo("ACTIVE");

        // 引用一致性：绑定/会话引用列与主表完全相同
        String bindingRef = new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject(
                "SELECT principal_id FROM external_binding", String.class);
        String sessionRef = new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject(
                "SELECT principal_id FROM local_session", String.class);
        assertThat(bindingRef).isEqualTo(result.principal().principalId().value());
        assertThat(sessionRef).isEqualTo(result.principal().principalId().value());
    }

    @Test
    void admissionDeniedInsideT2RollsBackEverything() {
        LocalLoginUseCase denied = new LocalLoginUseCase(fixedProvider(), bindingStore,
                principal -> {
                    throw new AdmissionDeniedException("宿主策略拒绝");
                },
                sessionStore, runner, Duration.ofHours(8));

        assertThatThrownBy(() -> denied.login(new ExternalLoginCommand(Map.of())))
                .isInstanceOf(AdmissionDeniedException.class);

        // T2 全回滚：主体/绑定/会话均无残留
        assertThat(Support.count(dataSource, "principal")).isZero();
        assertThat(Support.count(dataSource, "external_binding")).isZero();
        assertThat(Support.count(dataSource, "local_session")).isZero();
    }

    @Test
    void concurrentFirstLoginCreatesSinglePrincipalWithRetry() throws Exception {
        int threads = 2;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var results = new Future<?>[threads];
            for (int i = 0; i < threads; i++) {
                results[i] = pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return loginUseCase.login(new ExternalLoginCommand(Map.of()));
                });
            }
            var loginA = (LoginResult) results[0].get(30, TimeUnit.SECONDS);
            var loginB = (LoginResult) results[1].get(30, TimeUnit.SECONDS);

            // 唯一约束裁决 + 新事务重试：双方成功、同一主体、各持一会话
            assertThat(loginA.principal().principalId()).isEqualTo(loginB.principal().principalId());
            assertThat(loginA.session().sessionId()).isNotEqualTo(loginB.session().sessionId());
            assertThat(Support.count(dataSource, "principal")).isEqualTo(1);
            assertThat(Support.count(dataSource, "external_binding")).isEqualTo(1);
            assertThat(Support.count(dataSource, "local_session")).isEqualTo(2);
            assertThat(admissionCalls.get()).isGreaterThanOrEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void secondLoginReusesPrincipalAndBothSessionsValidate() {
        LoginResult first = loginUseCase.login(new ExternalLoginCommand(Map.of()));
        LoginResult second = loginUseCase.login(new ExternalLoginCommand(Map.of()));

        assertThat(first.principal().principalId()).isEqualTo(second.principal().principalId());
        assertThat(Support.count(dataSource, "principal")).isEqualTo(1);
        assertThat(Support.count(dataSource, "local_session")).isEqualTo(2);
        assertThat(sessionStore.validateSession(first.session().sessionId())).isPresent();
        assertThat(sessionStore.validateSession(second.session().sessionId())).isPresent();
    }

    @Test
    void existingPrincipalDeniedByHostAdmissionDoesNotGetNewSession() {
        // W21-01：外部凭证验证成功≠宿主准入——既有主体被宿主策略拒绝时，
        // 不签发新会话、不新增主体/绑定（既有会话行数不变）
        LoginResult admitted = loginUseCase.login(new ExternalLoginCommand(Map.of()));

        LocalLoginUseCase denied = new LocalLoginUseCase(fixedProvider(), bindingStore,
                principal -> {
                    throw new AdmissionDeniedException("宿主策略在既有主体上拒绝");
                },
                sessionStore, runner, Duration.ofHours(8));

        assertThatThrownBy(() -> denied.login(new ExternalLoginCommand(Map.of())))
                .isInstanceOf(AdmissionDeniedException.class);

        assertThat(Support.count(dataSource, "principal")).isEqualTo(1);
        assertThat(Support.count(dataSource, "external_binding")).isEqualTo(1);
        assertThat(Support.count(dataSource, "local_session")).isEqualTo(1);
        assertThat(sessionStore.validateSession(admitted.session().sessionId())).isPresent();
    }

    @Test
    void writePortOutsideTransactionIsRejected() {
        // MANDATORY 门禁：不经 runner 直接调用写端口必须拒绝
        assertThatThrownBy(() -> bindingStore.findOrCreatePrincipalWithBinding(
                identity(), new PrincipalCreationSpec("login", "display")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MANDATORY");
        assertThatThrownBy(() -> sessionStore.revokeAllSessions("any"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runnerRejectsWhenTransactionAlreadyActive() {
        runner.inTransaction(() -> {
            assertThatThrownBy(() -> runner.inTransaction(() -> "nested"))
                    .isInstanceOf(IdentityTransactionActiveException.class);
            return null;
        });
    }

    @Test
    void perRequestValidationReadsOtherInstancesCommits() {
        // 实例 A 签发，实例 B（独立连接面）每请求复验可见
        LoginResult result = loginUseCase.login(new ExternalLoginCommand(Map.of()));
        var otherSessionStore = new PgLocalSessionStore(
                new org.springframework.jdbc.core.JdbcTemplate(otherInstance));
        assertThat(otherSessionStore.validateSession(result.session().sessionId())).isPresent();
    }

    // ---------------------------------------------- W5-e：T2 拒绝门禁集成（M-31）

    /** gate 接线登录用例（⓪①② 前置复验在 T2 事务首锁——8 参构造）。 */
    private LocalLoginUseCase gatedLoginUseCase() {
        return new LocalLoginUseCase(fixedProvider(), bindingStore, permitAll(), sessionStore,
                runner, Duration.ofHours(8), LocalLoginUseCase.DEFAULT_RETRY_LIMIT, rejectionGate);
    }

    @Test
    void firstLoginRejectedWhileActiveRejectionRowExistsAndClearControlledRestores() {
        runner.inTransaction(() -> rejectionGate.record(identity(), "上游确证停用"));

        // M-31①：⓪①② 命中活跃拒绝行 → 拒绝登录，不创建主体不签发会话
        assertThatThrownBy(() -> gatedLoginUseCase().login(new ExternalLoginCommand(Map.of())))
                .isInstanceOf(AdmissionDeniedException.class)
                .hasMessageContaining("活跃拒绝期");
        assertThat(Support.count(dataSource, "principal")).isZero();
        assertThat(Support.count(dataSource, "external_binding")).isZero();
        assertThat(Support.count(dataSource, "local_session")).isZero();

        // M-31③ 本地子集（live 面另属 W5-f）：受控清除（授权+审计载体）后可登录
        runner.inTransaction(() -> rejectionGate.clearControlled(identity(), "op-1", "误判恢复"));
        LoginResult restored = gatedLoginUseCase().login(new ExternalLoginCommand(Map.of()));
        assertThat(restored.principal().principalId().value()).hasSize(36);
        assertThat(Support.count(dataSource, "principal")).isEqualTo(1);
    }

    @Test
    void advisoryKeyLockBlocksFirstLoginUntilHoldingInstanceCommits() throws Exception {
        // M-31② 序 A：外部会话（独立实例）持同键 advisory 锁未提交期间，登录事务
        // 阻塞且不落 principal——阻塞下界即键级串行化证据（首创建复验由 ⓪ 承载）
        var holderGate = new PgExternalRejectionGate(
                new org.springframework.jdbc.core.JdbcTemplate(otherInstance));
        var holderRunner = Support.runner(otherInstance);
        CountDownLatch lockHeld = new CountDownLatch(1);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try {
                holderRunner.inTransaction(() -> {
                    holderGate.lockActive(identity()); // ⓪ 键 advisory 锁（无行不插）
                    lockHeld.countDown();
                    try {
                        Thread.sleep(400); // 持锁窗口
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Throwable t) {
                lockHeld.countDown();
                holderFailure.set(t);
            }
        }, "m31-lock-holder");
        holder.start();
        assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        LoginResult result = gatedLoginUseCase().login(new ExternalLoginCommand(Map.of()));
        long blockedMillis = (System.nanoTime() - start) / 1_000_000;
        holder.join(5000);

        assertThat(holderFailure.get()).isNull();
        assertThat(blockedMillis).isGreaterThanOrEqualTo(300); // 阻塞至持有者提交
        assertThat(result.principal().principalId().value()).hasSize(36); // 锁释放后正常签发
        assertThat(Support.count(dataSource, "principal")).isEqualTo(1);
    }

    @Test
    void rejectionWrittenAfterFirstLoginBlocksNextLoginNotFirst() {
        // M-31② 序 B：登录先提交 → 主体创建 + 拒绝行其后写入 → 作用于下一次登录；
        // 已签发会话存续（存量会话既知限制，W5 不引入会话级自动撤销——§7.5(b)）
        LocalLoginUseCase gated = gatedLoginUseCase();
        LoginResult first = gated.login(new ExternalLoginCommand(Map.of()));
        runner.inTransaction(() -> rejectionGate.record(identity(), "controlled-deactivation"));

        assertThatThrownBy(() -> gated.login(new ExternalLoginCommand(Map.of())))
                .isInstanceOf(AdmissionDeniedException.class)
                .hasMessageContaining("活跃拒绝期");
        assertThat(Support.count(dataSource, "local_session")).isEqualTo(1);
        assertThat(sessionStore.validateSession(first.session().sessionId())).isPresent();
    }

    @Test
    void findBindingsReturnsImmutableUnionIdFiveTupleForT4() {
        // T4 五元组反查端口（W5-e，M-31⑦ 供给面）：登录后恰返回绑定五元组，
        // 未知主体空表（binding 创建后五元组不可变，无竞态面）
        LoginResult result = loginUseCase.login(new ExternalLoginCommand(Map.of()));

        assertThat(bindingStore.findBindings(result.principal().principalId().value()))
                .containsExactly(identity());
        assertThat(bindingStore.findBindings("00000000-0000-0000-0000-000000000000")).isEmpty();
    }
}
