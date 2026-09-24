package com.gallop.fastddd.identity.core.domain;

import com.gallop.fastddd.identity.core.error.InvalidPrincipalIdException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 主体公开 ID（CW-02 统一协议，W0-2 §3.0）：
 *
 * <ul>
 *   <li>协议上限 {@value #MAX_LENGTH} 字符，超长即拒绝——禁止截断、禁止适配器自行删连字符/变换格式；</li>
 *   <li>HUMAN：标准 UUID 文本（含连字符，36 字符），由本地主体存储一次性分配；</li>
 *   <li>SYSTEM_JOB：{@code system-job:} 前缀 + job-key，总长 ≤50；</li>
 *   <li>SYSTEM：常量 {@code system}。</li>
 * </ul>
 *
 * <p>所有入口（分配、查找、审计写入、CRUD 自动填充）经本类同源校验；
 * 存储列统一 VARCHAR(64)（协议 50 + 裕量），引用列与审计 username 协议一致。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class PrincipalId {

    /** 协议上限：审计 sys_audit_log.username VARCHAR(50) 恰好容纳。 */
    public static final int MAX_LENGTH = 50;

    /** SYSTEM_JOB 前缀（11 字符）。 */
    public static final String SYSTEM_JOB_PREFIX = "system-job:";

    /** SYSTEM 常量主体 ID。 */
    public static final String SYSTEM_VALUE = "system";

    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    private final String value;

    private PrincipalId(String value) {
        this.value = value;
    }

    /** 通用工厂：按协议上限校验，超长/空白/控制字符拒绝。 */
    public static PrincipalId of(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidPrincipalIdException("principalId 不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidPrincipalIdException(
                    "principalId 超长：" + value.length() + " > " + MAX_LENGTH
                            + "（协议上限 50，禁止截断；请修正来源而非裁剪）");
        }
        if (CONTROL_CHARS.matcher(value).find()) {
            throw new InvalidPrincipalIdException("principalId 含控制字符");
        }
        return new PrincipalId(value);
    }

    /** SYSTEM_JOB 工厂：前缀 + job-key 总长 ≤50，超长启动失败（不截断）。 */
    public static PrincipalId forSystemJob(String jobKey) {
        if (jobKey == null || jobKey.isBlank()) {
            throw new InvalidPrincipalIdException("system job-key 不能为空");
        }
        return of(SYSTEM_JOB_PREFIX + jobKey);
    }

    /** SYSTEM 常量工厂。 */
    public static PrincipalId system() {
        return new PrincipalId(SYSTEM_VALUE);
    }

    public String value() {
        return value;
    }

    /** 人类主体标准 UUID 文本长度（含连字符）。 */
    public static boolean isUuidForm(PrincipalId id) {
        return id.value.length() == 36 && id.value.charAt(8) == '-' && id.value.charAt(13) == '-';
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PrincipalId && value.equals(((PrincipalId) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
