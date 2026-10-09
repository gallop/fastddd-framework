package com.gallop.fastddd.identity.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest;
import com.gallop.fastddd.identity.core.domain.InboundEvent;
import com.gallop.fastddd.identity.core.domain.RecheckTarget;
import com.gallop.fastddd.identity.core.domain.WebhookReceipt;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectoryEventExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryEventRejectedException;
import com.gallop.fastddd.identity.core.port.DirectoryEventChannel;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * 飞书事件订阅接收适配器（W5 设计 §5.1 六步序，W5-d）：① 验签（Encrypt Key
 * sha256 签名，或 Verification Token 比对——按形态二选一）→ ② 解密
 * （AES-256-CBC，key=SHA256(EncryptKey)，base64(iv‖密文)，PKCS7 填充）→
 * ③ 防重放窗（{@code header.create_time} 对本地时钟的偏差超窗即拒收）→
 * ④ scope 判定（tenant_key/app_id 与期望不符 → 拒收）→ ⑤ challenge 回显 →
 * ⑥ 规范化（v2.0 信封 → {@link InboundEvent} / unconsumed 判定）。
 *
 * <p><b>纯本地零外呼零存储</b>（端口契约）：鉴别/解密/判定/规范化之外无任何
 * 动作——入箱持久化归宿主接收端（starter），回查归宿主 worker。</p>
 *
 * <p><b>鉴别形态（fail-closed）</b>：加密事件（body 含 {@code encrypt}）→
 * 签名头三件套必须在场且恒时比对通过（{@link MessageDigest#isEqual}），
 * Encrypt Key 未配置即拒收；明文事件/表单 → Verification Token 必须在场
 * 比对，未配置即拒收；密钥值不入异常消息不入日志。解密失败统一口径
 * （{@code decrypt-failed}，不区分密钥不符/密文损坏——不泄露可探测差异）。</p>
 *
 * <p><b>challenge 特例</b>：url_verification 表单无 {@code header.create_time}/
 * {@code tenant_key}（窗与 scope 判定不适用）——安全性由第①步鉴别承载
 * （明文 token 比对 / 加密形态签名+解密密钥双重鉴别），鉴别通过即回显。</p>
 *
 * <p><b>非消费类型（M-30）</b>：六类消费事件（contact.user/department ×
 * created_v3/updated_v3/deleted_v3）之外（含 {@code contact.scope.updated_v3}
 * ——范围变化走全量再发布域，不经事件增量）→ {@code unconsumedType=true}
 * （接收端 200 忽略 + 结构化日志零事实）。</p>
 *
 * <p><b>构造期校验</b>：app-id/expected-tenant-key 缺失即启动失败（scope 判定
 * 无从谈起，fail-closed 列出缺项——与目录源 Bean 同模式）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class FeishuEventChannel implements DirectoryEventChannel {

    /** v2.0 信封消费事件类型全集（其余一律 unconsumed）。 */
    private static final Set<String> CONSUMED_EVENT_TYPES = Set.of(
            "contact.user.created_v3", "contact.user.updated_v3", "contact.user.deleted_v3",
            "contact.department.created_v3", "contact.department.updated_v3",
            "contact.department.deleted_v3");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeishuEventProperties properties;
    private final FeishuLoginProperties loginProperties;

    public FeishuEventChannel(FeishuEventProperties properties, FeishuLoginProperties loginProperties) {
        if (isBlank(loginProperties.getAppId()) || isBlank(loginProperties.getExpectedTenantKey())) {
            throw new IllegalStateException("FeishuEventChannel 需要 fastddd.identity.feishu.app-id "
                    + "与 expected-tenant-key（scope 判定依据）——缺失项无法判定事件归属，fail-closed");
        }
        this.properties = properties;
        this.loginProperties = loginProperties;
    }

    @Override
    public WebhookReceipt receive(DirectoryWebhookRequest request) {
        JsonNode outer = parse(request.body(), "请求体");
        boolean encrypted = outer.hasNonNull("encrypt");
        // ①② 验签先于解密（§5.1 序）：签名覆盖原始密文 body
        JsonNode envelope = encrypted
                ? parse(decrypt(outer.path("encrypt").asText(), verifySignature(request)), "解密后事件体")
                : outer;
        if (!encrypted) {
            // ① 明文形态鉴别退化为 token 比对（表单顶级 token / v2.0 事件 header.token）
            JsonNode tokenNode = envelope.path("token");
            if (!tokenNode.isTextual()) {
                tokenNode = envelope.path("header").path("token");
            }
            verifyPlainToken(tokenNode);
        }
        // ⑤ challenge 回显（url_verification 表单；鉴别已在 ① 承载）
        JsonNode challenge = envelope.path("challenge");
        if (challenge.isTextual() && !challenge.asText().isBlank()) {
            return new WebhookReceipt(challenge.asText(), null, false);
        }
        JsonNode header = envelope.path("header");
        if (!header.isObject()) {
            throw reject("malformed-envelope", "缺少 header 对象（v2.0 信封必需）");
        }
        // ③ 防重放窗（本地策略窗；重试携带原始 create_time——窗长须覆盖平台重试阶梯）
        long createTimeMillis = parseCreateTime(header);
        long skewMillis = Math.abs(Instant.now().toEpochMilli() - createTimeMillis);
        if (skewMillis > properties.getReplayWindow().toMillis()) {
            throw new DirectoryEventExpiredException("事件超出防重放窗（|now-create_time|="
                    + skewMillis + "ms > window=" + properties.getReplayWindow().toMillis() + "ms）");
        }
        // ④ scope 判定（tenant_key/app_id 与期望不符 → 拒收，不解释不落箱）
        String tenantKey = header.path("tenant_key").asText(null);
        String appId = header.path("app_id").asText(null);
        if (!loginProperties.getExpectedTenantKey().equals(tenantKey)
                || !loginProperties.getAppId().equals(appId)) {
            throw reject("scope-mismatch", "tenant_key/app_id 与期望不符（scope 判定拒收）");
        }
        // ⑥ 规范化：v2.0 → InboundEvent（payload = event 对象原文）
        String eventId = requiredText(header, "event_id");
        String eventType = requiredText(header, "event_type");
        JsonNode event = envelope.path("event");
        if (!event.isObject()) {
            throw reject("malformed-envelope", "缺少 event 对象（v2.0 信封必需）");
        }
        boolean unconsumed = !CONSUMED_EVENT_TYPES.contains(eventType);
        return new WebhookReceipt(null, new InboundEvent(eventId, eventType, createTimeMillis,
                tenantKey, appId, event.toString()), unconsumed);
    }

    @Override
    public Optional<RecheckTarget> resolveObject(String eventType, String payloadJson) {
        if (!CONSUMED_EVENT_TYPES.contains(eventType)) {
            return Optional.empty();
        }
        JsonNode event = parse(payloadJson, "事件体");
        boolean person = eventType.startsWith("contact.user.");
        String keyField = person ? "open_id" : "open_department_id";
        JsonNode value = event.path("object").path(keyField);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new DirectoryContractViolationException("missing-object-key",
                    "消费事件缺少 event.object." + keyField + "（eventType=" + eventType + "）");
        }
        return Optional.of(new RecheckTarget(
                person ? DirectoryObjectType.PERSON : DirectoryObjectType.DEPARTMENT, value.asText()));
    }

    /** ①（加密形态）：X-Lark-Signature = hex(sha256(timestamp + nonce + EncryptKey + body))。 */
    private String verifySignature(DirectoryWebhookRequest request) {
        String encryptKey = properties.getEncryptKey();
        if (isBlank(encryptKey)) {
            throw reject("config-missing-encrypt-key", "事件体为加密形态但 Encrypt Key 未配置（fail-closed 拒收）");
        }
        String signature = request.signature();
        String timestamp = request.timestampHeader();
        String nonce = request.nonceHeader();
        if (isBlank(signature) || isBlank(timestamp) || isBlank(nonce)) {
            throw reject("signature-missing",
                    "加密事件缺少签名头（X-Lark-Signature/-Timestamp/-Nonce 三件套必需）");
        }
        String expected = sha256Hex(timestamp + nonce + encryptKey + request.body());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw reject("signature-mismatch", "事件签名不符（验签失败拒收）");
        }
        return encryptKey;
    }

    /** ①（明文形态）：Verification Token 恒时比对（缺配置/缺字段/不符均拒收）。 */
    private void verifyPlainToken(JsonNode tokenNode) {
        String expected = properties.getVerificationToken();
        if (isBlank(expected)) {
            throw reject("config-missing-verification-token",
                    "明文事件需 Verification Token 鉴别但未配置（fail-closed 拒收）");
        }
        if (!tokenNode.isTextual() || tokenNode.asText().isBlank()) {
            throw reject("token-missing", "明文事件缺少 token 字段（鉴别失败拒收）");
        }
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                tokenNode.asText().getBytes(StandardCharsets.UTF_8))) {
            throw reject("token-mismatch", "Verification Token 不符（鉴别失败拒收）");
        }
    }

    /** ②：AES-256-CBC 解密（key=SHA256(EncryptKey)；JCE PKCS5Padding 即 AES 块 PKCS7，含填充校验）。 */
    private static String decrypt(String encryptBase64, String encryptKey) {
        byte[] cipherAll;
        try {
            cipherAll = Base64.getDecoder().decode(encryptBase64);
        } catch (IllegalArgumentException e) {
            throw reject("malformed-cipher", "encrypt 字段非合法 base64");
        }
        if (cipherAll.length <= 16 || (cipherAll.length - 16) % 16 != 0) {
            throw reject("malformed-cipher", "密文长度形态非法（iv + 16 字节块对齐失败）");
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(encryptKey.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(cipherAll, 0, 16));
            byte[] plain = cipher.doFinal(cipherAll, 16, cipherAll.length - 16);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw reject("decrypt-failed", "事件解密失败（密钥不符或密文损坏）");
        }
    }

    /** ③ 输入：header.create_time（epoch 毫秒字符串，v2.0 契约）。 */
    private static long parseCreateTime(JsonNode header) {
        JsonNode node = header.path("create_time");
        if (!node.isTextual()) {
            throw reject("malformed-create-time", "header.create_time 缺失或非字符串（毫秒字面）");
        }
        try {
            return Long.parseLong(node.asText());
        } catch (NumberFormatException e) {
            throw reject("malformed-create-time", "header.create_time 非法毫秒字面");
        }
    }

    private static JsonNode parse(String json, String label) {
        try {
            return json == null || json.isBlank()
                    ? MAPPER.createObjectNode() : MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw reject("malformed-body", label + "非合法 JSON");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw reject("malformed-envelope", "header." + field + " 缺失或非文本/为空（v2.0 信封必需）");
        }
        return value.asText();
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用（JVM 必备算法，不可达）", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static DirectoryEventRejectedException reject(String reasonCode, String message) {
        return new DirectoryEventRejectedException(reasonCode, message);
    }
}
