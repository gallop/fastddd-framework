package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.domain.PrincipalStatus;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.error.AdmissionDeniedException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 两实例共享 PG 的确定性交错（§7/校审七-2）：登录先提交再停用（FOR UPDATE
 * 互斥 + 停用撤会话）/ 停用先提交再尝试签发（状态门禁拒绝）。实例 A/B = 两个独立
 * DataSource + 独立适配器/用例，仅共享同一 PG 集群。
 */
class TwoInstanceInterleavePgTest {

    private final DataSource instanceA = Support.dataSource();
    private final DataSource instanceB = Support.dataSource();

    /** 实例 A 登录：准入钩子内可阻塞——持锁（FOR UPDATE）后、提交前唯一可控点。 */
    private final CountDownLatch admissionReached = new CountDownLatch(1);
    private final CountDownLatch releaseLogin = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        Support.cleanIdentityTables(instanceA);
    }

    private ExternalLoginProvider fixedProvider() {
        return command -> new VerifiedExternalLogin(
                new ExternalIdentity("feishu", "tenant-1", "app-1", "union_id", "ou_interleave"),
                "login-i", "Display I");
    }

    /** 准入放行但阻塞至 releaseLogin：T2 在持锁状态下可控暂停。 */
    private AdmissionPolicy gatedAdmission() {
        return principal -> {
            admissionReached.countDown();
            try {
                if (!releaseLogin.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("登录释放超时（测试编排故障）");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };
    }

    private LocalLoginUseCase loginOn(DataSource instance, AdmissionPolicy admission) {
        JdbcTemplate jdbc = new JdbcTemplate(instance);
        return new LocalLoginUseCase(fixedProvider(), new PgIdentityBindingStore(jdbc), admission,
                new PgLocalSessionStore(jdbc), Support.runner(instance), Duration.ofHours(8));
    }

    private PrincipalLifecycleUseCase lifecycleOn(DataSource instance) {
        JdbcTemplate jdbc = new JdbcTemplate(instance);
        return new PrincipalLifecycleUseCase(
                new PgIdentityLifecycleStore(jdbc, new PgLocalSessionStore(jdbc)),
                Support.runner(instance));
    }

    /** 交错 1：登录 T2 已持主体行锁并停在准入 → 实例 B 停用被 FOR UPDATE 阻塞 → 登录提交 → 停用完成。 */
    @Test
    void loginCommitsFirstThenDeactivateRevokesItsSession() throws Exception {
        // 实例 A：普通登录一次，为主体占位（复用同一 principalId 供交错）
        LoginResult warmup = loginOn(instanceA, principal -> { })
                .login(new ExternalLoginCommand(Map.of()));
        String principalId = warmup.principal().principalId().value();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 实例 A：第二次登录在 T2 内持锁后阻塞于准入
            Future<LoginResult> loginFuture = pool.submit(() ->
                    loginOn(instanceA, gatedAdmission()).login(new ExternalLoginCommand(Map.of())));
            assertThat(admissionReached.await(10, TimeUnit.SECONDS))
                    .as("登录已进入 T2 准入（持有主体行锁）").isTrue();

            // 实例 B：停用先发起，阻塞在 FOR UPDATE 行锁上（登录尚未提交）
            AtomicReference<Object> deactivated = new AtomicReference<>();
            CountDownLatch deactivateStarted = new CountDownLatch(1);
            Future<?> deactivateFuture = pool.submit(() -> {
                deactivateStarted.countDown();
                deactivated.set(lifecycleOn(instanceB).deactivate(principalId));
                return null;
            });
            assertThat(deactivateStarted.await(10, TimeUnit.SECONDS)).isTrue();
            // 给停用线程时间真正阻塞在行锁上（FOR UPDATE 不可能越过未提交的 T2）
            TimeUnit.MILLISECONDS.sleep(300);

            // 释放登录：T2 提交（行锁释放）→ 停用继续：DISABLED + epoch+1 + 撤销两条会话
            releaseLogin.countDown();
            LoginResult second = loginFuture.get(30, TimeUnit.SECONDS);
            deactivateFuture.get(30, TimeUnit.SECONDS);

            assertThat(second.principal().status()).isEqualTo(PrincipalStatus.ACTIVE); // 签发时仍 ACTIVE
            var result = (com.gallop.fastddd.identity.core.domain.VerifiedPrincipal) deactivated.get();
            assertThat(result.status()).isEqualTo(PrincipalStatus.DISABLED);
            assertThat(result.authEpoch()).isEqualTo(1L);

            JdbcTemplate jdbc = new JdbcTemplate(instanceA);
            Integer revoked = jdbc.queryForObject(
                    "SELECT count(*) FROM local_session WHERE principal_id = ? AND revoked_at IS NOT NULL",
                    Integer.class, principalId);
            Integer total = jdbc.queryForObject(
                    "SELECT count(*) FROM local_session WHERE principal_id = ?",
                    Integer.class, principalId);
            assertThat(revoked).isEqualTo(total).isEqualTo(2);
        } finally {
            releaseLogin.countDown();
            pool.shutdownNow();
        }
    }

    /** 交错 2：停用先完整提交 → 实例 A 随后尝试签发 → 状态门禁拒绝，无会话残留。 */
    @Test
    void deactivateCommitsFirstThenIssuanceIsDenied() throws Exception {
        LoginResult warmup = loginOn(instanceA, principal -> { })
                .login(new ExternalLoginCommand(Map.of()));
        String principalId = warmup.principal().principalId().value();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> deactivated = pool.submit(() -> lifecycleOn(instanceB).deactivate(principalId));
            deactivated.get(30, TimeUnit.SECONDS); // 停用事务已完整提交

            assertThatThrownBy(() -> loginOn(instanceA, principal -> { })
                    .login(new ExternalLoginCommand(Map.of())))
                    .isInstanceOf(AdmissionDeniedException.class)
                    .hasMessageContaining("停用");

            // 无新会话残留：仅 warmup 那一条，且已撤销
            JdbcTemplate jdbc = new JdbcTemplate(instanceA);
            assertThat(Support.count(instanceA, "local_session")).isEqualTo(1);
            Integer revoked = jdbc.queryForObject(
                    "SELECT count(*) FROM local_session WHERE principal_id = ? AND revoked_at IS NOT NULL",
                    Integer.class, principalId);
            assertThat(revoked).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
