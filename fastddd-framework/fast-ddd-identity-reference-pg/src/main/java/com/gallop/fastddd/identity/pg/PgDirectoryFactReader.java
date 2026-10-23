package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.LocalFactView;
import com.gallop.fastddd.identity.core.port.DirectoryFactReader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 单键本地事实读取 PG 参考适配器（W5 设计 §7.3 三步之首，W5-d）：worker
 * 事务外基线读取——CAS 基线（local_revision）+ 形态标志 + 内容文本
 * （{@code fact::text}，PG 规范化后文本——比较面经
 * {@link PgDirectoryFactMatcher} jsonb 结构等值，不比字面）。
 *
 * <p><b>事务模型</b>：单条只读 SELECT，无副作用无门禁需求；区别于
 * {@code readProtectedFacts}（W3 冻结、范围全量、无内容）——单键带内容为
 * worker 专属消费面（端口 javadoc 差异登记）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryFactReader implements DirectoryFactReader {

    private static final String SELECT_FACT_SQL = """
            SELECT local_revision, tombstone, pending_confirm, fact::text AS fact_json
              FROM directory_fact
             WHERE tenant_id = ? AND app_scope = ? AND object_type = ? AND external_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PgDirectoryFactReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LocalFactView> readFact(DirectoryScope scope, DirectoryObjectType objectType,
                                            String externalId) {
        List<LocalFactView> rows = jdbcTemplate.query(SELECT_FACT_SQL, (rs, i) -> new LocalFactView(
                rs.getLong("local_revision"),
                rs.getBoolean("tombstone"),
                rs.getBoolean("pending_confirm"),
                rs.getString("fact_json")),
                scope.tenantKey(), scope.appScope(), objectType.name(), externalId);
        if (rows.size() > 1) {
            throw new IllegalStateException("directory_fact 单键命中多行（"
                    + scope + ", " + objectType + ", " + externalId + "）——唯一键不变量被破坏");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
