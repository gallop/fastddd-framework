package com.gallop.fastddd.security.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T8.112 审计参数脱敏契约锚：password/secret/token 后缀族键值遮盖
 * （tracker §34 R-1 框架侧残留根治）。
 *
 * <p>1.0.0 切面正则仅五精确键名（password/secret/token/accessToken/refreshToken），
 * 复合键 oldPassword/newPassword 不匹配——明文持久化入 sys_audit_log（studio 侧已以
 * logParams=false+WRITE_ONLY 全堵；本锚保护框架直接消费者）。两层锚：纯函数
 * {@link AuditLogAspect#maskSensitiveJson(String)} 五面+切面端到端一面（真 @AuditLog
 * 注解+Mockito joinPoint 驱动 around 全程，沿 AuditLogAspectResultJudgingTest 驱动形）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
class AuditLogAspectMaskingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 端到端载体：真 @AuditLog 注解（logParams 缺省 true——R-1 前半「默认记参数」即启用面）。 */
    @SuppressWarnings("unused")
    static class Fixture {
        @AuditLog("脱敏探针")
        public String probeParams(String username, Map<String, Object> body) {
            return "ok";
        }
    }

    /** 捕获型 AuditLogService 假件（沿 ResultJudgingTest 形态，本类自持防跨测耦合）。 */
    static class CapturingService implements AuditLogService {
        volatile AuditLogEntity captured;

        @Override
        public void saveAuditLog(AuditLogEntity entity) {
            captured = entity;
        }

        @Override
        public void saveAuditLogAsync(AuditLogEntity entity) {
            captured = entity;
        }

        @Override
        public void persist(AuditLogEntity entity) {
            captured = entity;
        }
    }

    @Test
    @DisplayName("原五精确键面恒遮盖+键名保留（1.0.0 行为不回退）")
    void fiveOriginalExactKeysStayMaskedWithKeyNamePreserved() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("password", "p-1");
        body.put("secret", "s-1");
        body.put("token", "t-1");
        body.put("accessToken", "at-1");
        body.put("refreshToken", "rt-1");
        String masked = AuditLogAspect.maskSensitiveJson(toJson(body));

        for (String key : body.keySet()) {
            assertThat(masked).contains(kv(key, "***"));
        }
        assertThat(masked).doesNotContain("p-1").doesNotContain("s-1")
                .doesNotContain("t-1").doesNotContain("at-1").doesNotContain("rt-1");
    }

    @Test
    @DisplayName("复合口令族键（oldPassword/newPassword 等）全遮盖——R-1 注册缺陷直锚")
    void compoundPasswordFamilyKeysMasked() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("oldPassword", "old-plain-9");
        body.put("newPassword", "new-plain-9");
        body.put("currentPassword", "cur-plain-9");
        body.put("confirmPassword", "cfm-plain-9");
        body.put("clientSecret", "cs-plain-9");
        body.put("userToken", "ut-plain-9");
        body.put("username", "t8112-user");
        body.put("email", "t8112@example.com");
        String masked = AuditLogAspect.maskSensitiveJson(toJson(body));

        for (String key : new String[] {"oldPassword", "newPassword", "currentPassword",
                "confirmPassword", "clientSecret", "userToken"}) {
            assertThat(masked).contains(kv(key, "***"));
        }
        assertThat(masked).contains(kv("username", "t8112-user"))
                .contains(kv("email", "t8112@example.com"))
                .doesNotContain("plain-9");
    }

    @Test
    @DisplayName("含转义引号/反斜杠的口令整值遮盖且产物 JSON 恒可解析（原值正则提前截断根锚）")
    void escapedQuoteValueFullyMaskedAndJsonStaysValid() throws Exception {
        String tricky = "ab\"cd\\ef\"gh";
        Map<String, String> body = new LinkedHashMap<>();
        body.put("password", tricky);
        body.put("note", "keep-me");
        String json = toJson(body);
        assertThat(json).contains("\\\""); // 前置：载体确含 JSON 转义引号（Jackson 转义实证）

        String masked = AuditLogAspect.maskSensitiveJson(json);
        JsonNode parsed = MAPPER.readTree(masked); // 产物可解析=无截断劈裂
        assertThat(parsed.get("password").asText()).isEqualTo("***");
        assertThat(parsed.get("note").asText()).isEqualTo("keep-me");
        assertThat(masked).doesNotContain("cd").doesNotContain("ef");
    }

    @Test
    @DisplayName("非敏感键原值保留（username/tokenType 等零误伤——遮盖面不外溢）")
    void nonSensitiveKeysUntouched() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", "u-1");
        body.put("operation", "create-user");
        body.put("tokenType", "BEARER"); // 键含 token 而键尾非 token——信息键不遮
        body.put("note", "plain text : with colon");
        String masked = AuditLogAspect.maskSensitiveJson(toJson(body));
        assertThat(masked).isEqualTo(toJson(body));
    }

    @Test
    @DisplayName("端到端：around 全程驱动——数组形态入参复合键落库前遮盖")
    void endToEndAspectMasksCompoundKeysBeforePersist() throws Throwable {
        CapturingService service = new CapturingService();
        AuditLogAspect aspect = new AuditLogAspect(service);
        Method method = Fixture.class.getMethod("probeParams", String.class, Map.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "t8112-e2e");
        body.put("oldPassword", "e2e-old-secret");
        body.put("newPassword", "e2e-new-secret");
        when(joinPoint.getArgs()).thenReturn(new Object[] {"t8112-e2e", body});
        when(joinPoint.proceed()).thenReturn("ok");

        Object returned = aspect.around(joinPoint, method.getAnnotation(AuditLog.class));

        assertThat(returned).isEqualTo("ok");
        assertThat(service.captured).as("审计实体应落账").isNotNull();
        assertThat(service.captured.getRequestParams())
                .as("数组形态（safeArgs 序列化）+复合键遮盖+非敏感保留")
                .startsWith("[")
                .contains(kv("username", "t8112-e2e"))
                .contains(kv("oldPassword", "***"))
                .contains(kv("newPassword", "***"))
                .doesNotContain("e2e-old-secret")
                .doesNotContain("e2e-new-secret");
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 断言用键值形 {@code "key":"val"}——集中引号转义。 */
    private static String kv(String key, String val) {
        return "\"" + key + "\":\"" + val + "\"";
    }
}
