package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest;
import com.gallop.fastddd.identity.core.domain.InboundEvent;
import com.gallop.fastddd.identity.core.domain.RecheckTarget;
import com.gallop.fastddd.identity.core.domain.WebhookReceipt;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectoryEventExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryEventRejectedException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 事件接收通道单测（W5 设计 §5.1 六步序，W5-d）：验签（±/缺头/缺配置）、解密
 * （成功/密钥不符统一口径/密文形态非法）、防重放窗（窗外拒/重试阶梯内放行）、
 * scope（tenant/app 双向不符）、challenge 回显（明文/加密两形态）、六类消费
 * 类型与 unconsumed、明文 token 路径、malformed 族、resolveObject 四分支、
 * 构造期 scope 键 fail-closed。
 *
 * <p>测试密钥为编造字面值（非任何真实凭据）；密文用固定 IV 只为确定性断言，
 * 生产 IV 由平台随机（通道侧无 IV 假设）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
class FeishuEventChannelTest {

    private static final String APP_ID = "cli_test_app";

    private static final String TENANT = "tenant-1";

    private static final String ENCRYPT_KEY = "test-encrypt-key-not-real";

    private static final String VERIFICATION_TOKEN = "v-token-not-real";

    // ---- 构造与信封工具 ----

    private static FeishuEventChannel channel(String encryptKey, String verificationToken, Duration window) {
        FeishuEventProperties events = new FeishuEventProperties();
        events.setEncryptKey(encryptKey);
        events.setVerificationToken(verificationToken);
        events.setReplayWindow(window);
        FeishuLoginProperties login = new FeishuLoginProperties();
        login.setAppId(APP_ID);
        login.setExpectedTenantKey(TENANT);
        return new FeishuEventChannel(events, login);
    }

    private static FeishuEventChannel channel() {
        return channel(ENCRYPT_KEY, VERIFICATION_TOKEN, Duration.ofHours(12));
    }

    /** v2.0 信封（create_time 毫秒字面）。 */
    private static String envelope(String eventType, String eventJson, String createTime,
                                   String tenantKey, String appId) {
        return "{\"schema\":\"2.0\",\"header\":{\"event_id\":\"ev-1\",\"token\":\"" + VERIFICATION_TOKEN
                + "\",\"create_time\":\"" + createTime + "\",\"event_type\":\"" + eventType
                + "\",\"tenant_key\":\"" + tenantKey + "\",\"app_id\":\"" + appId + "\"},"
                + "\"event\":" + eventJson + "}";
    }

    private static String nowMillis() {
        return String.valueOf(Instant.now().toEpochMilli());
    }

    private static String personEventBody() {
        return "{\"object\":{\"open_id\":\"ou_9\",\"name\":\"张三\"}}";
    }

    private static String departmentEventBody() {
        return "{\"object\":{\"open_department_id\":\"od_9\"}}";
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
            throw new IllegalStateException(e);
        }
    }

    /** 平台同式加密：AES-256-CBC，key=SHA256(EncryptKey)，base64(iv‖密文)，固定 IV 仅测试确定性。 */
    private static String encryptForTest(String plain, String encryptKey) throws GeneralSecurityException {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptKey.getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[16];
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[16 + encrypted.length];
        System.arraycopy(iv, 0, all, 0, 16);
        System.arraycopy(encrypted, 0, all, 16, encrypted.length);
        return Base64.getEncoder().encodeToString(all);
    }

    /** 加密形态完整请求（正确签名）。 */
    private static DirectoryWebhookRequest encryptedRequest(String payload, String encryptKey)
            throws GeneralSecurityException {
        String body = "{\"encrypt\":" + quote(encryptForTest(payload, encryptKey)) + "}";
        String timestamp = "1700000000";
        String nonce = "n-1";
        String signature = sha256Hex(timestamp + nonce + encryptKey + body);
        return new DirectoryWebhookRequest(body, signature, timestamp, nonce);
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    // ---- ①② 加密形态：验签 + 解密 ----

    @Test
    void encryptedEventVerifiesDecryptsAndNormalizes() throws Exception {
        String payload = envelope("contact.user.updated_v3", personEventBody(),
                nowMillis(), TENANT, APP_ID);
        WebhookReceipt receipt = channel().receive(encryptedRequest(payload, ENCRYPT_KEY));

        assertThat(receipt.challenge()).isNull();
        assertThat(receipt.unconsumedType()).isFalse();
        InboundEvent event = receipt.event();
        assertThat(event.eventId()).isEqualTo("ev-1");
        assertThat(event.eventType()).isEqualTo("contact.user.updated_v3");
        assertThat(event.tenantKey()).isEqualTo(TENANT);
        assertThat(event.appId()).isEqualTo(APP_ID);
        assertThat(event.payloadJson()).contains("ou_9");
        assertThat(event.createTimeMillis()).isPositive();
    }

    @Test
    void signatureMismatchIsRejected() throws Exception {
        String payload = envelope("contact.user.updated_v3", personEventBody(),
                nowMillis(), TENANT, APP_ID);
        DirectoryWebhookRequest request = encryptedRequest(payload, ENCRYPT_KEY);
        DirectoryWebhookRequest tampered = new DirectoryWebhookRequest(request.body(),
                sha256Hex("tampered"), request.timestampHeader(), request.nonceHeader());

        assertThatThrownBy(() -> channel().receive(tampered))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("signature-mismatch"));
    }

    @Test
    void missingSignatureHeadersAreRejected() throws Exception {
        String payload = envelope("contact.user.updated_v3", personEventBody(),
                nowMillis(), TENANT, APP_ID);
        String body = "{\"encrypt\":" + quote(encryptForTest(payload, ENCRYPT_KEY)) + "}";

        assertThatThrownBy(() -> channel().receive(new DirectoryWebhookRequest(body, null, null, null)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("signature-missing"));
    }

    @Test
    void encryptedBodyWithoutConfiguredKeyIsRejected() throws Exception {
        String payload = envelope("contact.user.updated_v3", personEventBody(),
                nowMillis(), TENANT, APP_ID);

        assertThatThrownBy(() -> channel("", VERIFICATION_TOKEN, Duration.ofHours(12))
                .receive(encryptedRequest(payload, ENCRYPT_KEY)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("config-missing-encrypt-key"));
    }

    @Test
    void decryptFailureIsUnifiedWithoutLeakingCause() throws Exception {
        String payload = envelope("contact.user.updated_v3", personEventBody(),
                nowMillis(), TENANT, APP_ID);
        // 签名用服务端正确 Encrypt Key 计算（验签先过），密文却以另一密钥加密
        // → 解密失败（统一口径，不区分密钥不符/密文损坏）
        String body = "{\"encrypt\":" + quote(encryptForTest(payload, "another-key-entirely")) + "}";
        String timestamp = "1700000000";
        String nonce = "n-1";
        String signature = sha256Hex(timestamp + nonce + ENCRYPT_KEY + body);

        assertThatThrownBy(() -> channel().receive(
                new DirectoryWebhookRequest(body, signature, timestamp, nonce)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("decrypt-failed"));
    }

    @Test
    void malformedCipherShapeIsRejected() {
        // 签名正确（覆盖原 body），密文形态非法（7 字节 < iv 下界）——验签后形态检查拒
        String body = "{\"encrypt\":\"" + Base64.getEncoder().encodeToString(new byte[7]) + "\"}";
        String timestamp = "1700000000";
        String nonce = "n-1";
        String signature = sha256Hex(timestamp + nonce + ENCRYPT_KEY + body);

        assertThatThrownBy(() -> channel().receive(
                new DirectoryWebhookRequest(body, signature, timestamp, nonce)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("malformed-cipher"));
    }

    // ---- ① 明文形态：token 鉴别 + challenge ----

    @Test
    void plaintextChallengeIsEchoed() {
        String body = "{\"token\":\"" + VERIFICATION_TOKEN + "\",\"type\":\"url_verification\","
                + "\"challenge\":\"ck-plain-1\"}";

        WebhookReceipt receipt = channel().receive(new DirectoryWebhookRequest(body, null, null, null));

        assertThat(receipt.challenge()).isEqualTo("ck-plain-1");
        assertThat(receipt.event()).isNull();
    }

    @Test
    void encryptedChallengeIsEchoedAfterDecrypt() throws Exception {
        String payload = "{\"token\":\"" + VERIFICATION_TOKEN + "\",\"type\":\"url_verification\","
                + "\"challenge\":\"ck-enc-1\"}";

        WebhookReceipt receipt = channel().receive(encryptedRequest(payload, ENCRYPT_KEY));

        assertThat(receipt.challenge()).isEqualTo("ck-enc-1");
    }

    @Test
    void plaintextTokenMismatchIsRejected() {
        String body = "{\"token\":\"wrong-token\",\"type\":\"url_verification\",\"challenge\":\"c\"}";

        assertThatThrownBy(() -> channel().receive(new DirectoryWebhookRequest(body, null, null, null)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("token-mismatch"));
    }

    @Test
    void plaintextEventWithoutConfiguredTokenIsRejected() {
        String body = "{\"token\":\"whatever\",\"header\":{\"event_id\":\"e\",\"event_type\":\"t\","
                + "\"create_time\":\"" + nowMillis() + "\",\"tenant_key\":\"t\",\"app_id\":\"a\"}}";

        assertThatThrownBy(() -> channel(ENCRYPT_KEY, "", Duration.ofHours(12))
                .receive(new DirectoryWebhookRequest(body, null, null, null)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("config-missing-verification-token"));
    }

    @Test
    void plaintextEventTokenMayLiveInHeader() {
        // v2.0 事件形态：token 在 header.token（非表单顶级）
        String body = "{\"schema\":\"2.0\",\"header\":{\"event_id\":\"ev-2\",\"token\":"
                + quote(VERIFICATION_TOKEN) + ",\"create_time\":" + quote(nowMillis())
                + ",\"event_type\":\"contact.user.created_v3\",\"tenant_key\":" + quote(TENANT)
                + ",\"app_id\":" + quote(APP_ID) + "},\"event\":" + personEventBody() + "}";

        WebhookReceipt receipt = channel().receive(new DirectoryWebhookRequest(body, null, null, null));

        assertThat(receipt.event().eventId()).isEqualTo("ev-2");
    }

    // ---- ③ 防重放窗 ----

    @Test
    void staleEventBeyondWindowIsExpired() {
        String thirteenHoursAgo = String.valueOf(Instant.now().minus(Duration.ofHours(13)).toEpochMilli());
        String body = "{\"schema\":\"2.0\",\"header\":{\"event_id\":\"ev-3\",\"token\":"
                + quote(VERIFICATION_TOKEN) + ",\"create_time\":" + quote(thirteenHoursAgo)
                + ",\"event_type\":\"contact.user.updated_v3\",\"tenant_key\":" + quote(TENANT)
                + ",\"app_id\":" + quote(APP_ID) + "},\"event\":" + personEventBody() + "}";

        assertThatThrownBy(() -> channel().receive(new DirectoryWebhookRequest(body, null, null, null)))
                .isInstanceOf(DirectoryEventExpiredException.class);
    }

    @Test
    void retryLadderTailStillInsideWindowIsAccepted() {
        // 平台最长重试时点 ≈ 6h 后（携带原始 create_time）——12h 窗必须放行
        String sixHoursAgo = String.valueOf(Instant.now()
                .minus(Duration.ofHours(6).plusMinutes(5)).toEpochMilli());
        String body = "{\"schema\":\"2.0\",\"header\":{\"event_id\":\"ev-4\",\"token\":"
                + quote(VERIFICATION_TOKEN) + ",\"create_time\":" + quote(sixHoursAgo)
                + ",\"event_type\":\"contact.user.updated_v3\",\"tenant_key\":" + quote(TENANT)
                + ",\"app_id\":" + quote(APP_ID) + "},\"event\":" + personEventBody() + "}";

        WebhookReceipt receipt = channel().receive(new DirectoryWebhookRequest(body, null, null, null));

        assertThat(receipt.event().eventId()).isEqualTo("ev-4");
    }

    @Test
    void malformedCreateTimeIsRejected() {
        String body = "{\"schema\":\"2.0\",\"header\":{\"event_id\":\"ev-5\",\"token\":"
                + quote(VERIFICATION_TOKEN) + ",\"create_time\":\"not-a-number\","
                + "\"event_type\":\"contact.user.updated_v3\",\"tenant_key\":" + quote(TENANT)
                + ",\"app_id\":" + quote(APP_ID) + "},\"event\":" + personEventBody() + "}";

        assertThatThrownBy(() -> channel().receive(new DirectoryWebhookRequest(body, null, null, null)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("malformed-create-time"));
    }

    // ---- ④ scope ----

    @Test
    void tenantMismatchIsRejectedWithoutPersuasion() {
        String body = envelope("contact.user.updated_v3", personEventBody(),
                nowMillis(), "tenant-other", APP_ID);

        assertThatThrownBy(() -> channel().receive(new DirectoryWebhookRequest(body, null, null, null)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("scope-mismatch"));
    }

    @Test
    void appIdMismatchIsRejected() {
        String body = envelope("contact.user.updated_v3", personEventBody(),
                nowMillis(), TENANT, "cli_other_app");

        assertThatThrownBy(() -> channel().receive(new DirectoryWebhookRequest(body, null, null, null)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("scope-mismatch"));
    }

    // ---- ⑥ 规范化：消费类型与 unconsumed ----

    @Test
    void allSixConsumedTypesAreRecognized() {
        Stream<String> types = Stream.of(
                "contact.user.created_v3", "contact.user.updated_v3", "contact.user.deleted_v3",
                "contact.department.created_v3", "contact.department.updated_v3",
                "contact.department.deleted_v3");
        types.forEach(type -> {
            String eventJson = type.startsWith("contact.user.")
                    ? personEventBody() : departmentEventBody();
            String body = envelope(type, eventJson, nowMillis(), TENANT, APP_ID);
            WebhookReceipt receipt = channel().receive(new DirectoryWebhookRequest(body, null, null, null));
            assertThat(receipt.unconsumedType()).as("type=%s 应为消费类型", type).isFalse();
        });
    }

    @Test
    void contactScopeUpdateTypeIsUnconsumed() {
        String body = envelope("contact.scope.updated_v3", "{}", nowMillis(), TENANT, APP_ID);

        WebhookReceipt receipt = channel().receive(new DirectoryWebhookRequest(body, null, null, null));

        assertThat(receipt.unconsumedType()).isTrue();
        assertThat(receipt.event()).isNotNull();
    }

    @Test
    void malformedBodyIsRejected() {
        assertThatThrownBy(() -> channel().receive(new DirectoryWebhookRequest("not-json{", null, null, null)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("malformed-body"));
    }

    @Test
    void missingHeaderObjectIsRejected() {
        String body = "{\"token\":" + quote(VERIFICATION_TOKEN) + ",\"event\":" + personEventBody() + "}";

        assertThatThrownBy(() -> channel().receive(new DirectoryWebhookRequest(body, null, null, null)))
                .isInstanceOfSatisfying(DirectoryEventRejectedException.class,
                        e -> assertThat(e.reasonCode()).isEqualTo("malformed-envelope"));
    }

    // ---- resolveObject ----

    @Test
    void resolveObjectPersonAndDepartment() {
        FeishuEventChannel channel = channel();

        Optional<RecheckTarget> person = channel.resolveObject(
                "contact.user.deleted_v3", personEventBody());
        Optional<RecheckTarget> department = channel.resolveObject(
                "contact.department.updated_v3", departmentEventBody());

        assertThat(person).contains(new RecheckTarget(DirectoryObjectType.PERSON, "ou_9"));
        assertThat(department).contains(new RecheckTarget(DirectoryObjectType.DEPARTMENT, "od_9"));
    }

    @Test
    void resolveObjectUnconsumedTypeIsEmpty() {
        assertThat(channel().resolveObject("contact.scope.updated_v3", "{}")).isEmpty();
    }

    @Test
    void resolveObjectMissingKeyIsContractViolation() {
        assertThatThrownBy(() -> channel().resolveObject("contact.user.updated_v3",
                "{\"object\":{\"name\":\"无键\"}}"))
                .isInstanceOf(DirectoryContractViolationException.class);
    }

    // ---- 构造期 fail-closed ----

    @Test
    void constructorRequiresScopeKeys() {
        FeishuLoginProperties noTenant = new FeishuLoginProperties();
        noTenant.setAppId(APP_ID);
        FeishuEventProperties events = new FeishuEventProperties();

        assertThatThrownBy(() -> new FeishuEventChannel(events, noTenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected-tenant-key");
    }
}
