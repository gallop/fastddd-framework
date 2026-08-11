package com.gallop.fastddd.monitor.sql;

import java.util.regex.Pattern;

/**
 * SQL 敏感参数脱敏工具（MON-002）。
 *
 * <p>用于在结构化日志输出 SQL 文本前，对凭据/密钥类字面量进行掩码，满足"默认日志不包含敏感参数"。
 * 仅作用于日志路径——Micrometer 指标只按操作类型聚合，从不携带 SQL 文本。
 *
 * <p>覆盖形态：{@code key = 'value'} / {@code key = "value"} / {@code key = ?}（JDBC 绑定占位），
 * key 命中 password/passwd/pwd/secret/token/api[_-]key/access[_-]key/credential/private[_-]key/authorization
 * （大小写不敏感）。值替换为 {@code ***}，保留 key 与结构便于排查。
 *
 * <p>已知局限：INSERT ... VALUES(...) 这类位置型（无 key=value）敏感值不在覆盖范围；
 * 此类场景应由 {@link SqlMonitoringProperties#isLogParameters()} 默认关闭 + 业务侧不内联凭据共同兜底。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public final class SqlMaskingUtils {

    /**
     * 敏感 key 列表（大小写不敏感）。
     */
    private static final String SENSITIVE_KEYS =
            "password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|credential|private[_-]?key|authorization";

    /**
     * 匹配 {@code <sensitive-key> <op> <value>}：
     * <ul>
     *   <li>组1：key</li>
     *   <li>组2：引号或占位符（' / " / ?），用于判断保留引号风格</li>
     *   <li>组3：引号内的字面量内容（仅在引号场景下有意义）</li>
     * </ul>
     * 运算符支持 {@code =、IS}（如 {@code secret IS NULL} 不替换 NULL）仅 {@code =} 触发值替换；
     * 同时兼容前后空白与反引号包裹的列名。
     */
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(`?(?:" + SENSITIVE_KEYS + ")`?)\\s*=\\s*('([^']*)'|\"([^\"]*)\"|\\?)");

    private static final Pattern SENSITIVE_IN_LIST = Pattern.compile(
            "(?i)(`?(?:" + SENSITIVE_KEYS + ")`?)\\s+IN\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);

    private SqlMaskingUtils() {
    }

    /**
     * 对 SQL 文本做敏感参数脱敏。null 安全。
     *
     * @param sql 原 SQL（可能含内联字面量）
     * @return 脱敏后 SQL；入参 null 返回 null
     */
    public static String mask(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        String masked = SENSITIVE_ASSIGNMENT.matcher(sql)
                .replaceAll(mr -> mr.group(1) + "=***");
        masked = SENSITIVE_IN_LIST.matcher(masked)
                .replaceAll(mr -> mr.group(1) + " IN (***)");
        return masked;
    }
}
