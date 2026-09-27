package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import com.gallop.fastddd.identity.spring.IdentityAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 目录同步装配骨架（W3 设计 v3 §7，W3-a）：
 * {@code fastddd.identity.directory.enabled=true} 时装配
 * {@link DirectorySyncUseCase}（要求宿主/引用适配器提供
 * {@link DirectorySource} 与 {@link DirectoryMirrorStore} Bean——缺任一由
 * {@code IdentityModeValidator} 终检使启动失败，fail-closed 不静默半装配）。
 *
 * <p><b>边界（W3-a 不提前交付项）</b>：</p>
 * <ul>
 *   <li>调度仅限 Worker 形态（{@code fastddd.identity.worker=true}）——
 *       web 形态只装用例（供管理端手动触发，控制器本体属 W3-c），
 *       <b>不自动启动任何调度线程</b>；</li>
 *   <li>M1 无 {@code IdentityChangePublisher}/Outbox Bean 也能装配
 *       （编排用例对 publisher 零依赖，端口冻结不装配）；</li>
 *   <li>调度线程为守护线程、单线程串行——同一时刻至多一个同步轮次
 *       （跨进程互斥由存储租约保证，§6.1）；</li>
 *   <li>真实 {@code PgDirectoryMirrorStore} 与飞书源属 W3-b/W3-c，本类不提供
 *       任何默认替身（测试替身只存在于测试夹具——复评钉 ④）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
@AutoConfiguration(after = IdentityAutoConfiguration.class,
        afterName = "com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration")
@ConditionalOnProperty(prefix = "fastddd.identity", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FastDddIdentityProperties.class)
public class IdentityDirectoryAutoConfiguration {

    /**
     * 目录同步用例：分页页数与单轮页数护栏来自配置（非法值已被
     * {@code IdentityModeGuard} fail-fast；此处构造参数再做防线校验）。
     * 缺 source/store 时 Bean 不装配 + 终检失败，不会以空实现冒充。
     */
    @Bean
    @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
    @ConditionalOnBean({DirectorySource.class, DirectoryMirrorStore.class})
    @ConditionalOnMissingBean
    DirectorySyncUseCase directorySyncUseCase(DirectorySource directorySource,
                                              DirectoryMirrorStore directoryMirrorStore,
                                              FastDddIdentityProperties properties) {
        FastDddIdentityProperties.Directory cfg = properties.getDirectory();
        return new DirectorySyncUseCase(directorySource, directoryMirrorStore,
                cfg.getPageSize(), cfg.getMaxPagesPerRun());
    }

    /**
     * Worker 专属调度：worker=true ∧ directory.enabled=true ∧ 端口 Bean 齐备。
     * <b>条件引用端口而非用例</b>：嵌套配置类的读取先于本类 Bean 方法定义注册
     * （@ConditionalOnBean 对同自动配置内的 Bean 不可见），而端口来自宿主/引用
     * 适配器（用户配置，先于一切自动配置注册）——条件判定顺序安全；
     * {@link DirectorySyncUseCase} 参数在实例化期解析，届时用例定义必已就绪。
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(IdentityDirectoryAutoConfiguration.WorkerDirectoryConditions.class)
    @ConditionalOnBean({DirectorySource.class, DirectoryMirrorStore.class})
    static class DirectorySyncSchedulerConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DirectorySyncScheduler directorySyncScheduler(DirectorySyncUseCase useCase,
                                                      DirectorySource directorySource,
                                                      FastDddIdentityProperties properties) {
            FastDddIdentityProperties.Directory cfg = properties.getDirectory();
            return new DirectorySyncScheduler(useCase, directorySource,
                    cfg.getEffectiveSyncInterval(), cfg.getCron());
        }
    }

    /** worker ∧ directory.enabled（缺一即不装配调度；cron/interval 合法性由守卫保证）。 */
    static final class WorkerDirectoryConditions extends AllNestedConditions {

        WorkerDirectoryConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "fastddd.identity", name = "worker", havingValue = "true")
        static class WorkerMode {
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
        static class DirectoryEnabled {
        }
    }
}
