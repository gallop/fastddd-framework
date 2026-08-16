package com.gallop.fastddd.agentaccess.kit.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AST-02 敏感数据扫描器（AA-03 §8.4）：对审计/Outbox/日志产物执行四类检测，
 * 0 命中（{@link #isClean}）为通过；命中事实进 Kit 证据 manifest。
 *
 * <p>四类检测：</p>
 * <ol>
 *   <li><b>token 模式匹配</b>——bearer 凭证、api key/secret/password 赋值、JWT、
 *       云厂商密钥形态；</li>
 *   <li><b>prompt 关键词</b>——模型 system prompt 片段标记；</li>
 *   <li><b>低熵裸摘要</b>——长 hex/base64 串；带 {@code sha256:}/{@code sha512:}
 *       标注前缀的摘要豁免（治理摘要允许），UUID 治理标识豁免；</li>
 *   <li><b>字段白名单 diff</b>——结构化产物字段名对照白名单
 *       （{@link #auditProjectionFieldNames()} 从 {@code AuditProjection}
 *       record 组件动态推导，单一事实源；Contract §10 白名单外默认不落审计）。</li>
 * </ol>
 *
 * <p>命中只报 {@link ScanFinding}（类别+位置），<b>不回显命中文本</b>。
 * 本扫描器是模式匹配防线，不承诺穷尽所有敏感形态——0 命中是必要条件，
 * 不是「产物绝无敏感数据」的充分证明（AA-03 §8 判定标准由 §10 白名单承载）。</p>
 */
public final class SensitiveDataScanner {

    /** 治理摘要标注前缀（带前缀=已声明的摘要，豁免低熵检测） */
    private static final Pattern ANNOTATED_DIGEST_PREFIX =
            Pattern.compile("(?i)\\b(sha256|sha512|urn):[a-z0-9.:/-]+");

    /** UUID 形态豁免（治理标识，非凭据/摘要） */
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final List<Pattern> TOKEN_PATTERNS = List.of(
            // bearer 凭证
            Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/-]{16,}={0,2}"),
            // 显式密钥赋值
            Pattern.compile("(?i)\\b(api[_-]?key|secret|password|passwd|client[_-]?secret|access[_-]?token)\\b\\s*[:=]\\s*\\S{8,}"),
            // JWT 三段形态
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]*"),
            // AWS access key id
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            // GitHub/Slack 风格 token 前缀（ghp_/ghp- 均覆盖）
            Pattern.compile("\\b(ghp|gho|xoxb|xoxp)[_-][A-Za-z0-9]{16,}\\b"));

    private static final List<Pattern> PROMPT_PATTERNS = List.of(
            Pattern.compile("(?i)<\\|im_start\\|>"),
            Pattern.compile("(?i)<\\|system\\|>"),
            Pattern.compile("(?i)\\bsystem\\s+prompt\\b"),
            Pattern.compile("(?i)###\\s*(instructions?|system)\\b"),
            Pattern.compile("(?i)you\\s+are\\s+an?\\s+(ai|assistant|large language model)\\b"));

    /** 未标注长 hex 串（≥32 位 = 至少 sha-256 输出长度） */
    private static final Pattern BARE_HEX = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{32,}(?![0-9a-f])");

    /** 未标注长 base64/base64url 串（≥40 字符，字符集含 RFC 4648 标准与 URL 安全变体） */
    private static final Pattern BARE_BASE64 = Pattern.compile("(?<![A-Za-z0-9+/_-])[A-Za-z0-9+/_-]{40,}={0,2}(?![A-Za-z0-9+/_-])");

    /**
     * 审计投影白名单字段名（Contract §10）：从 {@code AuditProjection} record
     * 组件动态推导——主制品字段演进时白名单自动同步，避免双源漂移。
     */
    public static Set<String> auditProjectionFieldNames() {
        java.lang.reflect.RecordComponent[] components =
                com.gallop.fastddd.agentaccess.spi.AuditProjection.class.getRecordComponents();
        Set<String> names = new java.util.LinkedHashSet<>();
        for (java.lang.reflect.RecordComponent component : components) {
            names.add(component.getName());
        }
        return java.util.Collections.unmodifiableSet(names);
    }

    /** 文本产物扫描：token/prompt/低熵三类（无结构化字段，不做白名单 diff） */
    public List<ScanFinding> scanText(String label, String text) {
        requireLabel(label);
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<ScanFinding> findings = new ArrayList<>();
        collectPatternMatches(findings, label, text, TOKEN_PATTERNS, ScanFinding.FindingKind.TOKEN_PATTERN);
        collectPatternMatches(findings, label, text, PROMPT_PATTERNS, ScanFinding.FindingKind.PROMPT_KEYWORD);
        collectLowEntropyDigests(findings, label, text);
        return List.copyOf(findings);
    }

    /**
     * 结构化产物扫描：字段白名单 diff + 逐字段值文本扫描。
     *
     * @param fields             产物字段名→值（值可为 null，仅字段名参与白名单 diff）
     * @param allowedFieldNames  白名单（如 {@link #auditProjectionFieldNames()}）
     */
    public List<ScanFinding> scanFields(String label, Map<String, String> fields, Set<String> allowedFieldNames) {
        requireLabel(label);
        if (fields == null || allowedFieldNames == null) {
            throw new IllegalArgumentException("fields 与 allowedFieldNames 必选");
        }
        List<ScanFinding> findings = new ArrayList<>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!allowedFieldNames.contains(field.getKey())) {
                findings.add(new ScanFinding(ScanFinding.FindingKind.FIELD_NOT_WHITELISTED,
                        label, "field:" + field.getKey()));
            }
            if (field.getValue() != null) {
                findings.addAll(scanText(label, field.getValue()));
            }
        }
        return List.copyOf(findings);
    }

    /**
     * 0 命中=通过（AST-02 验收）。
     *
     * @throws IllegalArgumentException findings 为 null——缺失的命中清单是编程错误，
     *                                  按本扫描器 fail-closed 姿态拒绝，不默认视为通过
     */
    public boolean isClean(List<ScanFinding> findings) {
        if (findings == null) {
            throw new IllegalArgumentException("findings 必选（null 命中清单不得默认判为通过）");
        }
        return findings.isEmpty();
    }

    private void collectPatternMatches(List<ScanFinding> findings, String label, String text,
                                       List<Pattern> patterns, ScanFinding.FindingKind kind) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                // 只报偏移不报内容
                findings.add(new ScanFinding(kind, label, "offset:" + matcher.start()));
            }
        }
    }

    private void collectLowEntropyDigests(List<ScanFinding> findings, String label, String text) {
        // 先剔除豁免段（带前缀标注摘要、UUID），再对剩余区间做裸摘要匹配
        String masked = maskExempt(text);
        Matcher hex = BARE_HEX.matcher(masked);
        while (hex.find()) {
            findings.add(new ScanFinding(ScanFinding.FindingKind.LOW_ENTROPY_DIGEST, label,
                    "offset:" + hex.start() + ":hex"));
        }
        Matcher base64 = BARE_BASE64.matcher(masked);
        while (base64.find()) {
            findings.add(new ScanFinding(ScanFinding.FindingKind.LOW_ENTROPY_DIGEST, label,
                    "offset:" + base64.start() + ":base64"));
        }
    }

    /** 把豁免段替换为等长占位符（保持偏移稳定），扫描器只对非豁免区间报命中 */
    private String maskExempt(String text) {
        String masked = mask(text, ANNOTATED_DIGEST_PREFIX);
        masked = mask(masked, UUID);
        return masked;
    }

    private static String mask(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, " ".repeat(matcher.group().length()));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static void requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label 必选（产物标识，供命中定位）");
        }
    }
}
