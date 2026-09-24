package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalStatus;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.spring.actor.IdentityAuthenticationToken;
import com.gallop.fastddd.identity.spring.actor.SecurityContextActorResolver;
import com.gallop.fastddd.identity.spring.actor.SpringSystemJobContext;
import com.gallop.fastddd.identity.spring.bridge.IdentityCurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §5.6 Actor 桥行为：C 会话 Authentication → Actor（principal 对象/getName 语义）、
 * SYSTEM_JOB 栈优先、身份缺失拒绝不降 system、线程池不继承 + 连续任务隔离 +
 * Spring SystemJob 上下文的 SecurityContext 保存/恢复。
 */
class IdentityActorBridgeTest {

    private final SecurityContextActorResolver resolver = new SecurityContextActorResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        com.gallop.fastddd.identity.core.system.SystemJobPrincipalContext.clear();
    }

    private static VerifiedPrincipal human(String id) {
        return new VerifiedPrincipal("tenant-1", PrincipalId.of(id), "login", "Display",
                PrincipalType.HUMAN, PrincipalStatus.ACTIVE, 0L);
    }

    /** Supplier 体内不可抛受检异常：Future.get() 包装为非受检。 */
    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void sessionAuthenticationYieldsActorWithPrincipalId() {
        VerifiedPrincipal principal = human("01234567-89ab-cdef-0123-456789abcdef");
        SecurityContextHolder.getContext().setAuthentication(IdentityAuthenticationToken.forSession(principal));

        Optional<Actor> actor = resolver.tryCurrentActor();
        assertThat(actor).isPresent();
        assertThat(actor.get().principalId().value()).isEqualTo("01234567-89ab-cdef-0123-456789abcdef");
        assertThat(actor.get().principalType()).isEqualTo(PrincipalType.HUMAN);
        // getName()=principalId 是装配结果（§5），供旧审计切面兼容
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(principal.principalId().value());
    }

    @Test
    void missingIdentityIsRejectedNotDegradeToSystem() {
        assertThat(resolver.tryCurrentActor()).isEmpty();
        assertThatThrownBy(resolver::currentActor).isInstanceOf(IdentityRequiredException.class)
                .hasMessageContaining("拒绝降级");

        IdentityCurrentUserProvider provider = new IdentityCurrentUserProvider(resolver);
        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(IdentityRequiredException.class); // 不返回 "system"
    }

    @Test
    void systemJobContextTakesPrecedenceAndInstallsSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(IdentityAuthenticationToken.forSession(human("01234567-89ab-cdef-0123-456789abcdee")));
        PrincipalId jobId = PrincipalId.forSystemJob("nightly-sync");

        Actor inside = SpringSystemJobContext.run(jobId, () -> {
            Actor actor = resolver.currentActor();
            // 框架操作日志/切面视角：name=principalId（SYSTEM_JOB）
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("system-job:nightly-sync");
            return actor;
        });

        assertThat(inside.principalId().value()).isEqualTo("system-job:nightly-sync");
        assertThat(inside.principalType()).isEqualTo(PrincipalType.SYSTEM_JOB);
        // finally 恢复外层员工会话
        assertThat(resolver.currentActor().principalId().value())
                .isEqualTo("01234567-89ab-cdef-0123-456789abcdee");
    }

    @Test
    void systemJobContextRestoresOuterOnFailure() {
        SecurityContextHolder.getContext()
                .setAuthentication(IdentityAuthenticationToken.forSession(human("01234567-89ab-cdef-0123-456789abcd0")));
        try {
            SpringSystemJobContext.run(PrincipalId.forSystemJob("failing-job"), () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // 预期异常
        }
        assertThat(resolver.currentActor().principalId().value())
                .isEqualTo("01234567-89ab-cdef-0123-456789abcd0");
    }

    @Test
    void threadPoolDoesNotInheritIdentityAndConsecutiveTasksAreIsolated() throws Exception {
        PrincipalId jobId = PrincipalId.forSystemJob("pool-owner");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 任务 A 在系统作业身份下运行；任务 B 无身份：B 拒绝（不继承 A）
            Future<String> taskA = SpringSystemJobContext.run(jobId, () ->
                    pool.submit(() -> {
                        // 线程池线程不继承提交者的 SystemJob/Spring 上下文
                        return String.valueOf(resolver.tryCurrentActor());
                    }));
            assertThat(taskA.get()).isEqualTo(Optional.empty().toString());

            // 任务 A'：池线程内显式建立系统身份执行；任务 B'：同池线程紧随其后 → 无残留
            SpringSystemJobContext.run(jobId, () -> await(pool.submit(() -> {
                SpringSystemJobContext.run(PrincipalId.forSystemJob("pooled-job"), () ->
                        assertThat(resolver.currentActor().principalId().value())
                                .isEqualTo("system-job:pooled-job"));
                return null;
            })));
            await(pool.submit(() -> {
                assertThat(resolver.tryCurrentActor()).isEmpty();
                return null;
            }));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void currentUserProviderReturnsSystemJobPrincipalId() {
        IdentityCurrentUserProvider provider = new IdentityCurrentUserProvider(resolver);
        String id = SpringSystemJobContext.run(PrincipalId.forSystemJob("audit-job"), () ->
                provider.getCurrentUserId());
        assertThat(id).isEqualTo("system-job:audit-job");
    }

    @Test
    void invalidSystemJobPrincipalIsRejectedAtEntry() {
        AtomicReference<Exception> caught = new AtomicReference<>();
        try {
            SpringSystemJobContext.run(PrincipalId.of("not-a-system-job-id"), () -> "x");
        } catch (IllegalArgumentException e) {
            caught.set(e);
        }
        assertThat(caught.get()).hasMessageContaining("SYSTEM_JOB");
    }
}
