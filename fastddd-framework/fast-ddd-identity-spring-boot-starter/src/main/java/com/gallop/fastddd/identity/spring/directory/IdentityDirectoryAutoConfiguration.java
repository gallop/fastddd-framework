package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.domain.RetentionPolicy;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.DirectoryEventChannel;
import com.gallop.fastddd.identity.core.port.DirectoryEventInbox;
import com.gallop.fastddd.identity.core.port.DirectoryFactMatcher;
import com.gallop.fastddd.identity.core.port.DirectoryFactReader;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckCommitter;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.DirectoryRetentionExecutor;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.core.usecase.DirectoryEventWorkerUseCase;
import com.gallop.fastddd.identity.core.usecase.DirectoryRecheckManualTrigger;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import com.gallop.fastddd.identity.spring.IdentityAutoConfiguration;
import com.gallop.fastddd.identity.spring.web.DirectoryRecheckManualController;
import com.gallop.fastddd.identity.spring.web.DirectoryRetentionController;
import com.gallop.fastddd.identity.spring.web.IdentityDirectoryEventsController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 目录同步装配骨架（W3 设计 v3 §7，W3-a；W3-c 追加手动触发与差异报表组件）：
 * {@code fastddd.identity.directory.enabled=true} 时装配
 * {@link DirectorySyncUseCase}（要求宿主/引用适配器提供
 * {@link DirectorySource} 与 {@link DirectoryMirrorStore} Bean——缺任一由
 * {@code IdentityModeValidator} 终检使启动失败，fail-closed 不静默半装配）、
 * {@link DirectorySyncManualTrigger}（§9.4）与 {@link DirectoryDiffReporter}
 * （§6.6 管理视图）。
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
     *
     * <p><b>事务所有者（W3A-01）</b>：注入既有 {@code IdentityTransactionRunner}
     * ——每个存储步骤经它独立短事务执行（写端口 MANDATORY 由此满足），源调用在
     * 事务外，宿主已有事务在首个存储步骤即被 runner 契约拒绝。identity 开启的
     * 上下文恒有 runner 定义（宿主自定义或
     * {@code IdentityAutoConfiguration#springIdentityTransactionRunner} 兜底，
     * 二者 @ConditionalOnMissingBean 互斥），参数解析期必然可满足，不额外加
     * @ConditionalOnBean 条件。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
    @ConditionalOnBean({DirectorySource.class, DirectoryMirrorStore.class})
    @ConditionalOnMissingBean
    DirectorySyncUseCase directorySyncUseCase(DirectorySource directorySource,
                                              DirectoryMirrorStore directoryMirrorStore,
                                              com.gallop.fastddd.identity.core.port.IdentityTransactionRunner transactionRunner,
                                              FastDddIdentityProperties properties,
                                              ObjectProvider<com.gallop.fastddd.identity.core.port.DirectoryChangeLog> changeLogProvider,
                                              ObjectProvider<com.gallop.fastddd.identity.core.port.IdentityChangePublisher> generationPublisherProvider) {
        FastDddIdentityProperties.Directory cfg = properties.getDirectory();
        // W5-e §3.1：change_log 恒接线（事实写入驱动，不受通知开关影响；pg 适配器
        // 恒提供；非 pg 宿主无 DirectoryChangeLog bean 时：通知关 = M1 形态无日志
        // 条目、通知开 ∧ 全量链开 = DirectorySyncUseCase 构造契约校验 fail-loud
        // 拒绝启动〔publisher 非 null 而 changeLog null 抛 IllegalArgumentException，
        // core :136-139〕非静默回退——R7-P3-3 更正 2026-10-10：原注「回退 M1 形态
        // 无日志条目」对通知开形态失实）；T8 publisher 仅通知开时接线（关 = 用例
        // 跳过 T8；开而缺 bean 由终检拒绝启动——IdentityModeValidator M-20 第二格，
        // bean 先行装配 null、终检收口）
        com.gallop.fastddd.identity.core.port.DirectoryChangeLog changeLog =
                changeLogProvider.getIfAvailable();
        com.gallop.fastddd.identity.core.port.IdentityChangePublisher publisher =
                cfg.getNotifications().isEnabled() ? generationPublisherProvider.getIfAvailable() : null;
        return new DirectorySyncUseCase(directorySource, directoryMirrorStore,
                transactionRunner, cfg.getPageSize(), cfg.getMaxPagesPerRun(), changeLog, publisher);
    }

    /**
     * 手动同步触发组件（W3 设计 §9.4，W3-c）：{@code directory.enabled=true} 的
     * 任意形态进程装配（web 只装用例+本组件供管理入口调用，调度线程仍限 worker）。
     * <b>条件引用端口而非用例</b>（与调度嵌套类同理）：端口来自宿主/引用适配器，
     * 先于自动配置注册；{@link DirectorySyncUseCase} 参数在实例化期解析（同条件
     * 的用例 Bean 定义届时必已就绪）。{@link ObjectProvider} 惰性解析
     * {@link IdentityAdminPolicy}——交互式入口逐操作裁决，无策略 bean 形态
     * （admin.enabled=false）下运维入口 {@code runFullSync()} 仍可用。
     */
    @Bean
    @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
    @ConditionalOnBean({DirectorySource.class, DirectoryMirrorStore.class})
    @ConditionalOnMissingBean
    DirectorySyncManualTrigger directorySyncManualTrigger(DirectorySyncUseCase useCase,
                                                          DirectorySource directorySource,
                                                          ObjectProvider<IdentityAdminPolicy> adminPolicy) {
        return new DirectorySyncManualTrigger(useCase, directorySource, adminPolicy);
    }

    /**
     * 差异报表组件（W3 设计 §6.6 差异报表=管理视图，W3-c）：观察侧对事实侧实时
     * 比对 + 待核对清单呈现。只依赖镜像存储读契约；不裁决准入（§9.3 授权由
     * 宿主入口负责），待核对消解属 M2。
     */
    @Bean
    @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
    @ConditionalOnBean(DirectoryMirrorStore.class)
    @ConditionalOnMissingBean
    DirectoryDiffReporter directoryDiffReporter(DirectoryMirrorStore directoryMirrorStore) {
        return new DirectoryDiffReporter(directoryMirrorStore);
    }

    /**
     * 手动目录事实回收触发组件（W5 设计 §8.3 M-26 管理入口先例，W5-f f-1）：
     * {@code directory.enabled=true} ∧ {@link DirectoryRetentionExecutor} bean
     * 在场（pg 参考适配器提供；非 pg 宿主 = 不装配——F5：回收是可选运维能力，
     * 非 fail-closed 面）。装配条件<b>不含 web 条件</b>——worker/非 web 进程的
     * 运维入口 {@code runRetention(scope, preview)} 可用（HTTP 面见
     * {@code DirectoryRetentionEndpointConfiguration}）。
     * {@link RetentionPolicy} 自 §10 键族构建（{@code inbox-processed} 缺省 7d /
     * {@code change-log} 缺省不限期；非正值经 {@code RetentionPolicy} 构造校验
     * fail-loud 拒启动）。{@link ObjectProvider} 惰性 {@link IdentityAdminPolicy}
     * ——交互入口逐操作裁决（RETENTION_RUN），无策略 bean 形态下运维入口仍可用。
     */
    @Bean
    @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
    @ConditionalOnBean(DirectoryRetentionExecutor.class)
    @ConditionalOnMissingBean
    DirectoryRetentionTrigger directoryRetentionTrigger(DirectoryRetentionExecutor executor,
                                                        FastDddIdentityProperties properties,
                                                        ObjectProvider<IdentityAdminPolicy> adminPolicy) {
        FastDddIdentityProperties.Directory.Increment.Retention retention =
                properties.getDirectory().getIncrement().getRetention();
        return new DirectoryRetentionTrigger(executor,
                new RetentionPolicy(retention.getInboxProcessed(), retention.getChangeLog()),
                adminPolicy);
    }

    /**
     * 手动 pending_confirm 对账回查触发用例（W5 设计 §7.5 对账面；W5-f 设计
     * §5 M-17，W5-f f-2）：{@code directory.enabled=true} ∧ 五端口 bean 在场
     * （镜像枚举/事实读取/权威单查/内容匹配/CAS 提交——前四与 worker 六端口
     * 面重叠，{@code DirectoryRecheckQuery} 由引用适配器〔飞书〕提供，缺任一
     * = 不装配，F5 可选运维能力）。装配条件<b>不含 web 条件</b>——worker/非
     * web 进程的运维入口 {@code runPendingRecheck(scope)} 可用（HTTP 面见
     * {@code DirectoryRecheckEndpointConfiguration}）。
     *
     * <p><b>W5-e 三接线同构</b>（与 worker Bean 方法一致——「与 worker 回查
     * 同构」口径）：{@code ExternalRejectionGate} 惰性（缺席 = M1 无门禁）；
     * {@code DirectoryIncrementPublisher} 仅 {@code notifications.enabled=true}
     * 时接线；{@code rejection-provider} 与 worker 同值域注入，gate 在场而空 =
     * 用例构造器 fail-loud（第二道防线，第一道在终检）。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
    @ConditionalOnBean({DirectoryMirrorStore.class, DirectoryFactReader.class,
            DirectoryRecheckQuery.class, DirectoryFactMatcher.class, DirectoryRecheckCommitter.class})
    @ConditionalOnMissingBean
    DirectoryRecheckManualTrigger directoryRecheckManualTrigger(
            DirectoryMirrorStore directoryMirrorStore,
            DirectoryFactReader factReader,
            DirectoryRecheckQuery recheckQuery,
            DirectoryFactMatcher factMatcher,
            DirectoryRecheckCommitter committer,
            com.gallop.fastddd.identity.core.port.IdentityTransactionRunner transactionRunner,
            FastDddIdentityProperties properties,
            ObjectProvider<com.gallop.fastddd.identity.core.port.ExternalRejectionGate> rejectionGateProvider,
            ObjectProvider<com.gallop.fastddd.identity.core.port.DirectoryIncrementPublisher> incrementPublisherProvider) {
        boolean notificationsEnabled =
                properties.getDirectory().getNotifications().isEnabled();
        return new DirectoryRecheckManualTrigger(directoryMirrorStore, factReader,
                recheckQuery, factMatcher, committer, transactionRunner,
                rejectionGateProvider.getIfAvailable(),
                notificationsEnabled ? incrementPublisherProvider.getIfAvailable() : null,
                properties.getDirectory().getIncrement().getRejectionProvider());
    }

    /**
     * 增量事件 worker 编排用例（W5 设计 §7，W5-d；W5-e 通知链/门禁接线）：
     * {@code increment.enabled=true} 时装配（<b>独立于 directory.enabled</b>
     * ——事件链不依赖全量同步端口）。六端口（通道/收件箱/回查/读取/匹配/提交器）
     * 来自宿主/引用适配器，缺任一由 {@code IdentityModeValidator} 终检使启动
     * 失败（fail-closed）。
     *
     * <p><b>W5-e 三接线（ObjectProvider 惰性，bean 缺席各自退化为 M1 行为）</b>：
     * ① {@code ExternalRejectionGate}——回查确证停用 record / 权威恢复 clear
     * （PERSON-only）；② {@code DirectoryIncrementPublisher}——增量通知转发，
     * 仅 {@code notifications.enabled=true} 时接线（关 = 零通知；开而缺 bean
     * 由终检拒绝启动——M-20 第三格）；③ {@code rejection-provider}——拒绝行
     * provider 值（与登录绑定行同值域，飞书 = "feishu"；gate 在场而空 = 用例
     * 构造器 fail-loud）。batch-size/claim-lease 来自
     * {@code fastddd.identity.directory.increment.worker.*}（§10 键族）。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "fastddd.identity.directory.increment", name = "enabled",
            havingValue = "true")
    @ConditionalOnBean({DirectoryEventChannel.class, DirectoryEventInbox.class,
            DirectoryRecheckQuery.class, DirectoryFactReader.class,
            DirectoryFactMatcher.class, DirectoryRecheckCommitter.class})
    @ConditionalOnMissingBean
    DirectoryEventWorkerUseCase directoryEventWorkerUseCase(
            DirectoryEventInbox inbox,
            DirectoryEventChannel channel,
            DirectoryRecheckQuery recheckQuery,
            DirectoryFactReader factReader,
            DirectoryFactMatcher factMatcher,
            DirectoryRecheckCommitter committer,
            com.gallop.fastddd.identity.core.port.IdentityTransactionRunner transactionRunner,
            FastDddIdentityProperties properties,
            ObjectProvider<com.gallop.fastddd.identity.core.port.ExternalRejectionGate> rejectionGateProvider,
            ObjectProvider<com.gallop.fastddd.identity.core.port.DirectoryIncrementPublisher> incrementPublisherProvider) {
        FastDddIdentityProperties.Directory.Increment cfg =
                properties.getDirectory().getIncrement();
        FastDddIdentityProperties.Directory.Increment.Worker workerCfg = cfg.getWorker();
        boolean notificationsEnabled =
                properties.getDirectory().getNotifications().isEnabled();
        return new DirectoryEventWorkerUseCase(inbox, channel, recheckQuery,
                factReader, factMatcher, committer, transactionRunner,
                workerCfg.getBatchSize(), workerCfg.getClaimLease(),
                rejectionGateProvider.getIfAvailable(),
                notificationsEnabled ? incrementPublisherProvider.getIfAvailable() : null,
                cfg.getRejectionProvider());
    }

    /**
     * 事件接收端点（W5 设计 §5.1 接收链宿主侧，W5-d）：servlet web ∧
     * {@code increment.enabled=true} ∧ 通道/收件箱端口齐备时注册
     * {@link IdentityDirectoryEventsController}（固定常量路径；安全链侧的
     * permitAll + CSRF 豁免由 {@code IdentitySecurityAutoConfiguration} 承担）。
     * worker 形态（非 web 进程）不注册端点——收件箱仅由 worker 调度消费。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Conditional(IdentityDirectoryAutoConfiguration.IncrementEventsConditions.class)
    @ConditionalOnBean({DirectoryEventChannel.class, DirectoryEventInbox.class})
    static class DirectoryEventsEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdentityDirectoryEventsController identityDirectoryEventsController(
                DirectoryEventChannel channel,
                DirectoryEventInbox inbox,
                FastDddIdentityProperties properties) {
            return new IdentityDirectoryEventsController(channel, inbox,
                    properties.getDirectory().getEvents().getWebhook());
        }
    }

    /**
     * 目录事实回收 HTTP 端点（W5 设计 §8.3 M-26 管理入口先例，W5-f f-1；
     * P3-4 嵌套形态对齐 {@link DirectoryEventsEndpointConfiguration} 先例）：
     * servlet web ∧ {@code directory.enabled=true} ∧ 执行器/审计/Actor 端口
     * bean 齐备（均来自其他自动配置/宿主，先于本类注册，@ConditionalOnBean
     * 判定顺序安全）时注册 {@link DirectoryRetentionController}；worker 形态
     * （非 web 进程）HTTP 控制器不注册。{@link DirectoryRetentionTrigger} 以
     * 参数依赖表达——同自动配置内 Bean 定义对 @ConditionalOnBean 不可见，
     * 实例化期解析届时必已就绪（本嵌套类条件蕴含 Trigger bean 条件）。非 pg
     * 宿主（无 executor bean）= 端点不装配（F5）。策略缺席非条件：缺
     * {@code IdentityAdminPolicy} bean 时端点在座但逐请求缺省拒绝（403）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
    @ConditionalOnBean({DirectoryRetentionExecutor.class, IdentityAuditWriter.class, ActorResolver.class})
    static class DirectoryRetentionEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DirectoryRetentionController directoryRetentionController(DirectoryRetentionTrigger trigger,
                                                                 IdentityAuditWriter auditWriter,
                                                                 ActorResolver actorResolver) {
            return new DirectoryRetentionController(trigger, auditWriter, actorResolver);
        }
    }

    /**
     * 手动对账回查 HTTP 端点（W5-f 设计 §3.4/§5 M-17，W5-f f-2；嵌套形态对齐
     * {@link DirectoryRetentionEndpointConfiguration} 先例）：servlet web ∧
     * {@code directory.enabled=true} ∧ 回查五端口/审计/Actor bean 齐备（端口
     * 来自宿主/引用适配器，先于本类注册，@ConditionalOnBean 判定顺序安全）时
     * 注册 {@link DirectoryRecheckManualController}；worker 形态（非 web 进程）
     * HTTP 控制器不注册。{@link DirectoryRecheckManualTrigger} 以参数依赖表达
     * ——同自动配置内 Bean 定义对 @ConditionalOnBean 不可见，实例化期解析届时
     * 必已就绪（本嵌套类条件蕴含 Trigger bean 条件）。非 pg/飞书宿主（缺
     * {@code DirectoryRecheckQuery} 等端口）= 端点不装配（F5）。策略缺席非
     * 条件：缺 {@code IdentityAdminPolicy} bean 时端点在座但逐请求缺省拒绝
     * （403，控制器内联裁决——core 用例不进策略 §9.3）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled", havingValue = "true")
    @ConditionalOnBean({DirectoryMirrorStore.class, DirectoryFactReader.class,
            DirectoryRecheckQuery.class, DirectoryFactMatcher.class, DirectoryRecheckCommitter.class,
            IdentityAuditWriter.class, ActorResolver.class})
    static class DirectoryRecheckEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DirectoryRecheckManualController directoryRecheckManualController(
                DirectoryRecheckManualTrigger trigger,
                IdentityAuditWriter auditWriter,
                ActorResolver actorResolver,
                ObjectProvider<IdentityAdminPolicy> adminPolicy) {
            return new DirectoryRecheckManualController(trigger, auditWriter, actorResolver, adminPolicy);
        }
    }

    /** increment.enabled（单一条件；与 web/端口条件正交组合）。 */
    static final class IncrementEventsConditions extends AllNestedConditions {

        IncrementEventsConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.directory.increment", name = "enabled",
                havingValue = "true")
        static class IncrementEnabled {
        }
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

    /**
     * 增量事件 worker 专属调度（W5-d）：worker=true ∧ increment.enabled=true ∧
     * 六端口 Bean 齐备 + 用例 Bean 定义就绪（用例参数在实例化期解析——
     * {@link DirectoryEventWorkerUseCase} 由本类 Bean 方法注册，同自动配置内
     * {@code @ConditionalOnBean} 不可见故不引用，直接以参数依赖表达）。
     * 单守护线程串行（跨实例互斥由收件箱租约保证，§7）。
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(IdentityDirectoryAutoConfiguration.WorkerIncrementConditions.class)
    @ConditionalOnBean({DirectoryEventChannel.class, DirectoryEventInbox.class,
            DirectoryRecheckQuery.class, DirectoryFactReader.class,
            DirectoryFactMatcher.class, DirectoryRecheckCommitter.class})
    static class DirectoryEventWorkerSchedulerConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DirectoryEventWorkerScheduler directoryEventWorkerScheduler(DirectoryEventWorkerUseCase useCase,
                                                                   FastDddIdentityProperties properties) {
            return new DirectoryEventWorkerScheduler(useCase,
                    properties.getDirectory().getIncrement().getWorker().getPollInterval());
        }
    }

    /** worker ∧ increment.enabled（缺一即不装配增量事件调度）。 */
    static final class WorkerIncrementConditions extends AllNestedConditions {

        WorkerIncrementConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "fastddd.identity", name = "worker", havingValue = "true")
        static class WorkerMode {
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.directory.increment", name = "enabled",
                havingValue = "true")
        static class IncrementEnabled {
        }
    }
}
