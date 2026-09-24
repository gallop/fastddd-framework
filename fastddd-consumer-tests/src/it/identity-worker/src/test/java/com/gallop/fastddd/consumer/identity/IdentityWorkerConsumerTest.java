package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.spring.actor.SpringSystemJobContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 Worker（E 形态）消费者（§4.3 条 5）：非 web 进程显式 worker=true。
 *
 * <ol>
 *   <li><b>装配</b>：Actor 桥/员工 provider/事务桥装配；登录用例、任何过滤链、
 *       默认用户名认证不装配。</li>
 *   <li><b>SYSTEM_JOB 身份</b>：显式作业身份下 CRUD 员工身份返回作业 principalId；
 *       无身份拒绝（不降 system）；线程池任务不继承提交者身份。</li>
 * </ol>
 */
@SpringBootTest(classes = IdentityWorkerConsumerTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdentityWorkerConsumerTest {

    @SpringBootApplication
    @Import(WorkerSupport.class)
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ActorResolver actorResolver;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Test
    void workerAssemblesBridgeWithoutLoginChain() {
        assertThat(context.containsBean("identityActorResolver")).isTrue();
        assertThat(context.containsBean("springIdentityTransactionRunner")).isTrue();
        assertThat(context.containsBean("identityCurrentUserProvider")).isTrue();
        assertThat(context.getBeanNamesForType(LocalLoginUseCase.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
        assertThat(context.getBeanNamesForType(
                org.springframework.security.core.userdetails.UserDetailsService.class)).isEmpty();
    }

    @Test
    void systemJobIdentityFeedsCrudProviderAndDoesNotLeakToPool() throws Exception {
        // 无身份：拒绝而非降级 system
        assertThatThrownBy(currentUserProvider::getCurrentUserId)
                .isInstanceOf(IdentityRequiredException.class);

        // 显式 SYSTEM_JOB：员工身份 = 作业 principalId
        String id = SpringSystemJobContext.run(PrincipalId.forSystemJob("consumer-sync-job"),
                currentUserProvider::getCurrentUserId);
        assertThat(id).isEqualTo("system-job:consumer-sync-job");

        // 线程池任务不继承提交者的 SYSTEM_JOB 身份（连续任务隔离）
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            SpringSystemJobContext.run(PrincipalId.forSystemJob("pool-owner"), () -> {
                // Supplier 体内不可抛受检异常：Future.get() 包装为非受检
                String inside;
                try {
                    inside = pool.submit(() ->
                            String.valueOf(actorResolver.tryCurrentActor())).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                // 池线程视角：无身份（Optional.empty 的字符串形式）
                assertThat(inside).isEqualTo(String.valueOf(java.util.Optional.empty()));
                return null;
            });
        } finally {
            pool.shutdownNow();
        }
    }
}
