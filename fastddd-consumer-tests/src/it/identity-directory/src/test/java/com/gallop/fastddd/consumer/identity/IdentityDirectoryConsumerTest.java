package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityChangePublisher;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncScheduler;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-1 目录单开消费者（§4.3 条 5，A 形态）：web 宿主仅启用目录——
 * 宿主 JWT 默认链沿用（无 token 401 / 签名 token 200），identity 登录链与
 * 员工 provider 不装配（员工身份属 C/Worker），Actor 桥可用。
 *
 * <p>W3-a 起：directory.enabled=true 为 fail-closed（缺 DirectorySource/
 * DirectoryMirrorStore 拒绝启动）——本场景以<b>测试范围替身</b>满足端口要求，
 * 并锁装配面：目录用例装配、web 非 Worker 零调度线程、M1 无 publisher Bean、
 * 同步编排经上下文真实 Bean 完整走通（begin→分页写批→…→publish）。</p>
 */
@SpringBootTest(classes = IdentityDirectoryConsumerTest.App.class)
@AutoConfigureMockMvc
class IdentityDirectoryConsumerTest {

    @SpringBootApplication
    @Import({ProbeController.class, DirectoryDoubles.class})
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private DirectorySyncUseCase directorySyncUseCase;

    @Test
    void hostSecurityStaysAndIdentityLoginSideDoesNotAssemble() {
        // 宿主默认链沿用：唯一链来自 security-starter
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.containsBean("jwtAuthenticationFilter")).isTrue();
        assertThat(context.containsBean("identitySecurityFilterChain")).isFalse();
        // 员工身份 provider 不装配（C/Worker 专属）；B 竞争结果保持 crud 默认胜出
        assertThat(context.containsBean("identityCurrentUserProvider")).isFalse();
        assertThat(context.getBeanNamesForType(CurrentUserProvider.class)).hasSize(1);
        assertThat(context.containsBean("defaultCurrentUserProvider")).isTrue();
        // 登录用例不装配；Actor 桥可用（目录作业 SYSTEM_JOB 上下文，W3）
        assertThat(context.getBeanNamesForType(LocalLoginUseCase.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ActorResolver.class)).hasSize(1);
    }

    @Test
    void directorySkeletonAssemblesInConsumerEnvironment() {
        // 目录用例装配（替身满足端口）；web 非 Worker：零调度线程
        assertThat(context.getBeanNamesForType(DirectorySyncUseCase.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(DirectorySyncScheduler.class)).isEmpty();
        // M1：无 publisher/Outbox Bean 也能装配（端口冻结、装配零要求）
        assertThat(context.getBeanNamesForType(IdentityChangePublisher.class)).isEmpty();

        // 经上下文真实 Bean 跑一轮：2 部门 + 1 人员 → 替身记录完整契约序列并发布
        //（W3A-03 后：除全局首次源调用外，每次源调用前与末页转 completePaging 前续租）
        RecordingStore store = context.getBean(RecordingStore.class);
        DirectoryPublication publication =
                directorySyncUseCase.runFullSync(new DirectoryScope("consumer", "app"));
        assertThat(publication.generationId()).isEqualTo("consumer-gen-1");
        assertThat(store.calls).containsExactly(
                "begin", "write:2d/0p", "renew", "write:0d/1p", "renew", "write:0d/0p",
                "renew", "completePaging", "validate", "publish");
    }

    @Test
    void hostJwtChainStillAuthenticates() throws Exception {
        mockMvc.perform(get("/api/probe/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        String token = jwtTokenProvider.generateAccessToken(
                new UsernamePasswordAuthenticationToken(
                        new User("consumer", "password",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mockMvc.perform(get("/api/probe/secured").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("secured ok"));
    }

    record ProbeResponse(String message) {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/secured")
        public ProbeResponse secured() {
            return new ProbeResponse("secured ok");
        }
    }

    /** 消费者测试范围目录端口替身（仅测试夹具：真实源/存储属 W3-b/W3-c）。 */
    @Configuration(proxyBeanMethods = false)
    static class DirectoryDoubles {

        @Bean
        DirectorySource directorySource() {
            return new DirectorySource() {
                @Override
                public DirectoryScope scope() {
                    return new DirectoryScope("consumer", "app");
                }

                @Override
                public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
                    return DirectoryPage.last(List.of(
                            new DirectoryDepartment("D1", null, "dept-1"),
                            new DirectoryDepartment("D2", null, "dept-2")));
                }

                @Override
                public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
                    return "D1".equals(departmentId)
                            ? DirectoryPage.last(List.of(new DirectoryPerson(
                                    "OU1", "consumer-user", null, List.of("D1"))))
                            : DirectoryPage.last(List.of());
                }
            };
        }

        @Bean
        RecordingStore directoryMirrorStore() {
            return new RecordingStore();
        }
    }

    /** 记录型镜像存储替身：happy-path 直通，零持久化零外呼。 */
    static class RecordingStore implements DirectoryMirrorStore {

        final List<String> calls = new ArrayList<>();

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            calls.add("begin");
            return new GenerationHandle(scope, "consumer-gen-1", "consumer-token");
        }

        @Override
        public Lease renewLease(GenerationHandle handle) {
            calls.add("renew");
            return new Lease(handle.leaseToken(), Instant.now().plus(Duration.ofMinutes(10)));
        }

        @Override
        public void writeBatch(GenerationHandle handle, DirectoryBatch batch) {
            calls.add("write:" + batch.departments().size() + "d/" + batch.persons().size() + "p");
        }

        @Override
        public void completePaging(GenerationHandle handle) {
            calls.add("completePaging");
        }

        @Override
        public ValidationReport validate(GenerationHandle handle) {
            calls.add("validate");
            return new ValidationReport(handle.generationId(),
                    new ObservedSnapshot.ObservedScope(List.of("D1", "D2"), List.of("D1", "D2"),
                            true, true),
                    2, 1, 1, List.of("paging-complete", "relations-resolved"));
        }

        @Override
        public DirectoryPublication publish(GenerationHandle handle) {
            calls.add("publish");
            return new DirectoryPublication(handle.generationId(), 0, 0, 0, 0);
        }

        @Override
        public void abandon(GenerationHandle handle, String reason) {
            calls.add("abandon:" + reason);
        }

        @Override
        public ObservedSnapshot readObservation(DirectoryScope scope) {
            return new ObservedSnapshot("", 0, 0,
                    new ObservedSnapshot.ObservedScope(List.of(), List.of(), false, false),
                    0, 0, List.of());
        }

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            return List.of();
        }
    }
}
