package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.port.DirectoryFactMatcher;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 事实内容结构等值 PG 参考适配器（W5 设计 §7.3 结论派生，W5-d）：
 * {@code (?::jsonb) = (?::jsonb)}——jsonb 语义等值（键序/空白无关），
 * 观察侧序列化与事实列写入同源（{@link PgDirectoryIncrementApplier}
 * 规范序列化），真源单一、零解析依赖。
 *
 * <p><b>为什么不比字符串</b>：存储文本已经 PG jsonb 规范化（键长序重排），
 * 与写入侧序列化字面必然不等——字面比较会把一切 IDENTICAL 误判为
 * CONTENT_DIFFERS（每事件推 revision，水位噪声）。结构等值经数据库自身
 * 承载即唯一权威（W5-d 差异登记：等值裁决属存储方言面）。</p>
 *
 * <p><b>事务模型</b>：单条只读 SELECT 表达式求值，无副作用。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryFactMatcher implements DirectoryFactMatcher {

    private static final String JSONB_EQUALS_SQL = "SELECT (?::jsonb) = (?::jsonb)";

    private final JdbcTemplate jdbcTemplate;

    public PgDirectoryFactMatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean matches(String storedFactJson, DirectoryPerson observed) {
        return matches(storedFactJson, PgDirectoryIncrementApplier.personJson(observed));
    }

    @Override
    public boolean matches(String storedFactJson, DirectoryDepartment observed) {
        return matches(storedFactJson, PgDirectoryIncrementApplier.departmentJson(observed));
    }

    private boolean matches(String storedFactJson, String observedCanonicalJson) {
        Boolean equal = jdbcTemplate.queryForObject(
                JSONB_EQUALS_SQL, Boolean.class, storedFactJson, observedCanonicalJson);
        return Boolean.TRUE.equals(equal);
    }
}
