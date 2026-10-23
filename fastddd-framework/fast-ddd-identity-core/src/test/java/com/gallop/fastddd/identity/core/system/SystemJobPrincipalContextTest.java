package com.gallop.fastddd.identity.core.system;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** §5.6 系统上下文：显式设置、嵌套恢复、线程池不继承员工身份、连续任务不串身份。 */
class SystemJobPrincipalContextTest {

    private static Actor job(String key) {
        return new Actor("tenant-1", PrincipalId.forSystemJob(key), PrincipalType.SYSTEM_JOB);
    }

    @Test
    void runSetsAndFinallyClears() {
        assertThat(SystemJobPrincipalContext.currentActor()).isEqualTo(Optional.empty());
        String result = SystemJobPrincipalContext.run(job("nightly-sync"), () -> {
            assertThat(SystemJobPrincipalContext.currentActor()).contains(job("nightly-sync"));
            return "done";
        });
        assertThat(result).isEqualTo("done");
        assertThat(SystemJobPrincipalContext.currentActor()).isEqualTo(Optional.empty());
    }

    @Test
    void exceptionStillClears() {
        assertThatThrownBy(() -> SystemJobPrincipalContext.run(job("failing"), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(SystemJobPrincipalContext.currentActor()).isEqualTo(Optional.empty());
    }

    @Test
    void nestingRestoresOuter() {
        SystemJobPrincipalContext.run(job("outer"), () -> {
            SystemJobPrincipalContext.run(job("inner"), () ->
                    assertThat(SystemJobPrincipalContext.currentActor()).contains(job("inner")));
            assertThat(SystemJobPrincipalContext.currentActor()).contains(job("outer"));
            return null;
        });
    }

    @Test
    void nonSystemJobActorRejected() {
        Actor human = new Actor("t", PrincipalId.of("01234567-89ab-cdef-0123-456789abcdef"),
                PrincipalType.HUMAN);
        assertThatThrownBy(() -> SystemJobPrincipalContext.run(human, () -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYSTEM_JOB");
    }

    @Test
    void threadPoolDoesNotInheritCallerIdentityAndConsecutiveTasksAreIsolated() throws Exception {
        // 主线程设置员工不可行（本上下文仅系统作业），以系统作业身份模拟"外层身份"
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            SystemJobPrincipalContext.run(job("caller-job"), () -> {
                try {
                    // 提交任务 1：池线程内查询——不继承调用线程身份
                    Future<Optional<Actor>> first = pool.submit(SystemJobPrincipalContext::currentActor);
                    assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(Optional.empty());

                    // 任务 2：显式系统作业身份执行后正常清理
                    Future<Boolean> second = pool.submit(() ->
                            SystemJobPrincipalContext.run(job("task-two"), () -> {
                                assertThat(SystemJobPrincipalContext.currentActor()).contains(job("task-two"));
                                return true;
                            }));
                    assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();

                    // 任务 3：同一池线程连续任务——上一任务的 actor 必须已清理
                    Future<Optional<Actor>> third = pool.submit(SystemJobPrincipalContext::currentActor);
                    assertThat(third.get(5, TimeUnit.SECONDS)).isEqualTo(Optional.empty());
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            pool.shutdownNow();
        }
    }
}
