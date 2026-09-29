package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.core.port.IdentityBindingStore;
import com.gallop.fastddd.identity.core.port.IdentityLifecyclePort;
import com.gallop.fastddd.identity.core.port.LocalSessionPort;
import com.gallop.fastddd.identity.pg.audit.PgActorColumnIdentityAuditWriter;
import com.gallop.fastddd.identity.pg.audit.PgLegacyIdentityAuditWriter;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * identity reference-pg 自动配置：identity.enabled=true 时装配 PG 写端口。
 *
 * <p>数据源显式绑定（§8.3a）：按 {@code fastddd.identity.transaction.data-source-bean}
 * 具名解析（缺省 dataSource），不猜 @Primary；具名 Bean 缺失或多数据源无名可指即启动失败。
 * 适配器经同一 DataSource 的线程绑定连接加入 starter runner 的外层事务（MANDATORY）。</p>
 *
 * <p><b>装配顺序（§4.5 显式声明）</b>：本类显式先于 starter 的
 * {@code IdentityAutoConfiguration} 注册——后者的登录/生命周期用例以
 * {@code @ConditionalOnBean} 引用本类端口，依赖本类的 Bean 定义先行注册
 * （Boot 排序器对 before 约束做前移，仅靠包名字母序不可靠）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@AutoConfiguration(beforeName = "com.gallop.fastddd.identity.spring.IdentityAutoConfiguration")
@ConditionalOnProperty(prefix = "fastddd.identity", name = "enabled", havingValue = "true")
@ConditionalOnClass(DataSource.class)
public class FastDddIdentityPgAutoConfiguration {

    private final DataSource dataSource;
    private final boolean initializeSchema;
    private final boolean ensureAuditTable;
    private final boolean actorColumns;
    private final Duration loginStateRetention;
    private final Duration directoryLeaseTimeout;

    public FastDddIdentityPgAutoConfiguration(
            ListableBeanFactory beanFactory,
            @Value("${fastddd.identity.transaction.data-source-bean:dataSource}") String dataSourceBeanName,
            @Value("${fastddd.identity.pg.initialize-schema:false}") boolean initializeSchema,
            @Value("${fastddd.identity.pg.ensure-audit-table:false}") boolean ensureAuditTable,
            @Value("${fastddd.identity.audit.actor-columns:false}") boolean actorColumns,
            @Value("${fastddd.identity.pg.login-state-retention-seconds:3600}") long loginStateRetentionSeconds,
            @Value("${fastddd.identity.directory.lease-timeout:10m}") String directoryLeaseTimeout) {
        this.dataSource = resolveDataSource(beanFactory, dataSourceBeanName);
        // W21c（复评 F1）：池预置 autoCommit=false 使写端口门禁第三判据失去区分力，
        // 装配期即拒绝（fail-closed；先于任何 schema 初始化动作）
        rejectPoolPresetAutoCommitFalse(this.dataSource, dataSourceBeanName);
        this.initializeSchema = initializeSchema;
        this.ensureAuditTable = ensureAuditTable;
        this.actorColumns = actorColumns;
        this.loginStateRetention = Duration.ofSeconds(loginStateRetentionSeconds);
        // W3-c：与 starter 规范键同源（单一旋钮），解析口径与 IdentityModeGuard
        // requirePositiveDuration 一致（DurationStyle：10m/30s/PT10M）
        try {
            this.directoryLeaseTimeout = DurationStyle.detectAndParse(directoryLeaseTimeout);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("fastddd.identity.directory.lease-timeout 非法："
                    + directoryLeaseTimeout + "（须为正时长，如 10m/30s/PT10M）", ex);
        }
        if (initializeSchema) {
            PgIdentitySchema.initialize(dataSource);
        }
        if (ensureAuditTable) {
            PgIdentitySchema.initializeAuditTable(dataSource, actorColumns);
        }
    }

    @Bean
    @ConditionalOnMissingBean(IdentityBindingStore.class)
    public IdentityBindingStore identityBindingStore() {
        return new PgIdentityBindingStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(LocalSessionPort.class)
    public LocalSessionPort localSessionPort() {
        return new PgLocalSessionStore(new JdbcTemplate(dataSource));
    }

    /**
     * 登录授权上下文（state 原子一次性消费，W2-2）；schema 增量 V2 随 initialize 执行。
     * 过期回收（W22-RV-01）：save 惰性清理越过保留窗口的行，窗口经
     * {@code fastddd.identity.pg.login-state-retention-seconds} 配置（缺省 3600）。
     */
    @Bean
    @ConditionalOnMissingBean(com.gallop.fastddd.identity.core.port.LoginStateStore.class)
    public com.gallop.fastddd.identity.core.port.LoginStateStore loginStateStore() {
        return new PgLoginStateStore(new JdbcTemplate(dataSource), Instant::now, loginStateRetention);
    }

    @Bean
    @ConditionalOnMissingBean(IdentityLifecyclePort.class)
    public IdentityLifecyclePort identityLifecyclePort(LocalSessionPort localSessionPort) {
        return new PgIdentityLifecycleStore(new JdbcTemplate(dataSource), (PgLocalSessionStore) localSessionPort);
    }

    @Bean
    @ConditionalOnMissingBean(IdentityAuditWriter.class)
    public IdentityAuditWriter identityAuditWriter() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return actorColumns
                ? new PgActorColumnIdentityAuditWriter(jdbcTemplate, dataSource)
                : new PgLegacyIdentityAuditWriter(jdbcTemplate);
    }

    /**
     * 目录镜像存储 PG 参考适配器（W3 设计 v3 §5/§6，W3-c）：schema 增量 V3 随
     * initialize 执行。租约 TTL 绑定与 starter 相同的规范键
     * {@code fastddd.identity.directory.lease-timeout}（缺省 10m；W3-b 复评 N2
     * 携带条件：宽租约按"最宽单层子部门数 × read timeout × (1+maxRetries)"
     * 评估调大）。写方法 MANDATORY——编排经 starter runner 的外层事务调用，
     * 与登录/生命周期端口同一数据源绑定（§8.3a）。
     */
    @Bean
    @ConditionalOnMissingBean(DirectoryMirrorStore.class)
    public DirectoryMirrorStore directoryMirrorStore() {
        return new PgDirectoryMirrorStore(new JdbcTemplate(dataSource), Instant::now, directoryLeaseTimeout);
    }

    private static DataSource resolveDataSource(ListableBeanFactory beanFactory, String beanName) {
        var candidates = Arrays.stream(beanFactory.getBeanNamesForType(DataSource.class))
                .collect(Collectors.toList());
        if (candidates.contains(beanName)) {
            return beanFactory.getBean(beanName, DataSource.class);
        }
        throw new IllegalStateException("identity 数据源显式绑定失败：Bean '" + beanName
                + "' 不存在（可用 DataSource Bean：" + candidates
                + "）。多数据源环境必须以 fastddd.identity.transaction.data-source-bean 显式指名，不使用 @Primary 猜测");
    }

    /**
     * W21c（复评 F1，fail-closed）：适配器数据源（经 DelegatingDataSource 解包后）
     * 为预置 {@code autoCommit=false} 的 Hikari 池时拒绝启动。
     *
     * <p>该配置下 {@code PgTransactionGate} 第三判据（绑定连接 autoCommit=false）无法
     * 区分"因参与事务为 false"与"因池预置为 false"——异源事务 + 目标源预读取形态会
     * 放行写入、写返回成功、数据随池回滚<b>静默丢失</b>（复评 S2a 实测，含
     * {@code revokeAllSessions}）；门禁层不存在不误伤的公开 API 判别子（详见
     * {@code PgTransactionGate} javadoc 边界），故处置上移到装配期。仅探测 Hikari
     * （Boot 默认池，optional 依赖 + ClassUtils.isPresent 守卫）；其他池/深包装形态
     * 按不支持边界处理。修正指引：移除池的 autoCommit=false 预置（恢复默认 true）——
     * 受支持的 DataSourceTransactionManager 本地事务会自行管理自动提交，无语义损失
     * （事务开在适配器数据源上的写入行为不变）。</p>
     */
    private static void rejectPoolPresetAutoCommitFalse(DataSource dataSource, String beanName) {
        if (!org.springframework.util.ClassUtils.isPresent(
                "com.zaxxer.hikari.HikariDataSource",
                FastDddIdentityPgAutoConfiguration.class.getClassLoader())) {
            return;
        }
        DataSource target = dataSource;
        while (target instanceof org.springframework.jdbc.datasource.DelegatingDataSource delegating) {
            target = delegating.getTargetDataSource();
        }
        if (target instanceof com.zaxxer.hikari.HikariDataSource pool && !pool.isAutoCommit()) {
            throw new IllegalStateException("identity 适配器数据源（fastddd.identity.transaction.data-source-bean='"
                    + beanName + "'）是预置 autoCommit=false 的 Hikari 连接池，拒绝启动：该配置下写端口事务门禁"
                    + "无法证明绑定连接参与事务（autoCommit=false 既可能来自事务开始，也可能来自池预置），"
                    + "异源事务预读取形态的写入会静默丢失（含 revokeAllSessions）。请移除池的 autoCommit=false"
                    + " 预置（恢复默认 true）——受支持的 DataSourceTransactionManager 本地事务会自行管理"
                    + "自动提交，事务开在适配器数据源上的写入行为不变");
        }
    }
}
