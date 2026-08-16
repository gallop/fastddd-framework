package com.gallop.fastddd.agentaccess.kit.scan;

/**
 * 单条敏感数据扫描命中（AST-02）。kind + label + 位置信息，<b>不回显命中文本本体</b>
 * ——扫描器自身不得成为泄露信道（AA-03 §8：错误响应与产物不泄露敏感内容）。
 *
 * @param kind      命中类别
 * @param label     被扫产物标识（宿主自定义，如 "audit-row-17"）
 * @param location  命中位置描述（字段名或字符偏移），不含命中内容
 */
public record ScanFinding(FindingKind kind, String label, String location) {

    public enum FindingKind {
        /** token/secret 形态（bearer、api key、JWT、云厂商密钥等） */
        TOKEN_PATTERN,
        /** 模型 Prompt 片段关键词（system prompt 标记等） */
        PROMPT_KEYWORD,
        /** 未标注前缀的长 hex/base64 串（低熵裸摘要） */
        LOW_ENTROPY_DIGEST,
        /** 字段不在白名单内（Contract §10 白名单外默认不落审计） */
        FIELD_NOT_WHITELISTED
    }

    public ScanFinding {
        if (kind == null || isBlank(label) || isBlank(location)) {
            throw new IllegalArgumentException("kind/label/location 必选且非空白");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
