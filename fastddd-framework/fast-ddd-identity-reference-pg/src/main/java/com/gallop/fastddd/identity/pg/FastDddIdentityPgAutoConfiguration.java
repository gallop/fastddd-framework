package com.gallop.fastddd.identity.pg;

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
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
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

    public FastDddIdentityPgAutoConfiguration(
            ListableBeanFactory beanFactory,
            @Value("${fastddd.identity.transaction.data-source-bean:dataSource}") String dataSourceBeanName,
            @Value("${fastddd.identity.pg.initialize-schema:false}") boolean initializeSchema,
            @Value("${fastddd.identity.pg.ensure-audit-table:false}") boolean ensureAuditTable,
            @Value("${fastddd.identity.audit.actor-columns:false}") boolean actorColumns) {
        this.dataSource = resolveDataSource(beanFactory, dataSourceBeanName);
        this.initializeSchema = initializeSchema;
        this.ensureAuditTable = ensureAuditTable;
        this.actorColumns = actorColumns;
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
}
