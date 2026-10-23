package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.error.ExternalLoginRejectedException;
import com.gallop.fastddd.identity.core.error.ExternalLoginUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 类别 A：飞书 HTTP 协议契约（本地 HttpServer 桩）——请求形态/错误分类/重试边界。
 * 协议依据 W0-4（CW-01，2026-09-26 复核；2026-09-27 联调修订：token v2 端点配对）。
 */
class FeishuOAuthClientContractTest {

    private FeishuStubServer stub;

    private FeishuLoginProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        stub = new FeishuStubServer();
        properties = FeishuStubServer.propertiesAgainst(stub);
    }

    /**
     * 端点配对钉子（2026-09-27 联调实证）：v1 授权页 + PKCE 官方当前仅支持搭配
     * v2 token 端点（v3 对此类 code 一律 20049）；user_info 保持 v1。缺省值漂移
     * 即本用例失败。
     */
    @Test
    void endpointDefaultsPinOfficialV1AuthorizePlusV2TokenPairing() {
        FeishuLoginProperties defaults = new FeishuLoginProperties();
        assertThat(defaults.getAuthorizeEndpoint())
                .isEqualTo("https://accounts.feishu.cn/open-apis/authen/v1/authorize");
        assertThat(defaults.getTokenEndpoint())
                .isEqualTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token");
        assertThat(defaults.getUserInfoEndpoint())
                .isEqualTo("https://open.feishu.cn/open-apis/authen/v1/user_info");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private FeishuExternalLoginProvider provider() {
        return new FeishuExternalLoginProvider(properties);
    }

    private static com.gallop.fastddd.identity.core.domain.ExternalLoginCommand command() {
        return new com.gallop.fastddd.identity.core.domain.ExternalLoginCommand(java.util.Map.of(
                FeishuExternalLoginProvider.CREDENTIAL_CODE, "code-abc",
                FeishuExternalLoginProvider.CREDENTIAL_CODE_VERIFIER, "verifier-43-chars-long-enough-xxxxxx"));
    }

    @Test
    void tokenRequestIsJsonBodyWithAllRequiredFields() {
        var verified = provider().verify(command());

        // 请求形态断言（服务端已代读 body，桩记录后统一校验）
        assertThat(stub.lastTokenContentType()).contains("application/json");
        String body = stub.lastTokenRequestBody();
        // 五要素齐备（grant_type/client_id/client_secret/code/code_verifier + redirect_uri）
        assertThat(body).contains("\"grant_type\":\"authorization_code\"");
        assertThat(body).contains("\"client_id\":\"cli_test_app\"");
        assertThat(body).contains("\"client_secret\":\"secret-test\"");
        assertThat(body).contains("\"code\":\"code-abc\"");
        assertThat(body).contains("\"code_verifier\":\"verifier-43-chars-long-enough-xxxxxx\"");
        assertThat(body).contains("\"redirect_uri\":\"https://app.example.com/api/identity/login/callback\"");

        assertThat(verified.identity().externalId()).isEqualTo("ou_union_1");
        assertThat(stub.tokenHits.get()).isEqualTo(1);
        assertThat(stub.lastUserInfoAuthorization()).isEqualTo("Bearer u-access-token");
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }

    @Test
    void exchangeNeverRetriesOnTransportFailure() {
        properties.getHttp().setReadTimeout(Duration.ofMillis(200));
        stub.setTokenHandler(exchange -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.successToken());
        });

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .hasMessageContaining("结果不确定");
        // 契约：兑换不重试（code 一次性；首次结果不确定时重试有 20065 错配风险）
        assertThat(stub.tokenHits.get()).isEqualTo(1);
        assertThat(stub.userInfoHits.get()).isEqualTo(0);
    }

    @Test
    void exchangeDoesNotRetryOnSystemErrorEither() {
        stub.setTokenHandler(exchange -> FeishuStubServer.respondJson(exchange, 500,
                FeishuStubServer.tokenError(20050)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("upstream-system");
        assertThat(stub.tokenHits.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {20003, 20004, 20065, 20024})
    void codeInvalidFamilyIsDeterminateRejectionWithoutRetry(int code) {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.tokenError(code)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginRejectedException.class)
                .extracting(e -> ((ExternalLoginRejectedException) e).reasonCode())
                .isEqualTo(String.valueOf(code));
        assertThat(stub.tokenHits.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {20008, 20009, 20010, 20066, 20067, 20068})
    void userStatusOrScopeFamilyIsDeterminateRejection(int code) {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.tokenError(code)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginRejectedException.class)
                .extracting(e -> ((ExternalLoginRejectedException) e).reasonCode())
                .isEqualTo(String.valueOf(code));
        assertThat(stub.tokenHits.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {20049, 20002, 20071})
    void pkceAndConfigFamilyIsExplicitServerFailure(int code) {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.tokenError(code)));

        // PKCE/配置错误：显式失败（IllegalState，管理员/实现侧修复项），不静默重试、不当作用户拒绝
        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(code));
        assertThat(stub.tokenHits.get()).isEqualTo(1);
    }

    @Test
    void unknownTokenCodeDefaultsToRejectLoginWithoutCrossEndpointGuessing() {
        // 20021 在 user_info 表是"离职"；在 token 端点表不存在 → 未知码按"默认拒绝"，
        // 绝不跨端点套用语义（W0-4 §3：按端点分列，不混用）
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.tokenError(20021)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginRejectedException.class)
                .extracting(e -> ((ExternalLoginRejectedException) e).reasonCode())
                .isEqualTo("20021");
    }

    @ParameterizedTest
    @ValueSource(ints = {20021, 20022, 20023, 20008})
    void userInfoUserStatusFamilyIsDeterminateRejection(int code) {
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.userInfoError(code)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginRejectedException.class)
                .extracting(e -> ((ExternalLoginRejectedException) e).reasonCode())
                .isEqualTo(String.valueOf(code));
        // 用户状态类不是重试类：即使配置了 user_info 重试也只打一次
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }

    @Test
    void userInfoTokenInvalidIsDeterminateRejection() {
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.userInfoError(20005)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginRejectedException.class)
                .extracting(e -> ((ExternalLoginRejectedException) e).reasonCode())
                .isEqualTo("20005");
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }

    @Test
    void userInfoSystemErrorRetriesThenSucceeds() {
        stub.setUserInfoHandler(exchange -> {
            if (stub.userInfoHits.get() <= 2) {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.userInfoError(20050));
                return;
            }
            FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.successUserInfo());
        });

        var verified = provider().verify(command());

        assertThat(verified.identity().externalId()).isEqualTo("ou_union_1");
        assertThat(stub.userInfoHits.get()).isEqualTo(3);
    }

    @Test
    void userInfoSystemErrorRetriesExhaustedIsUnavailable() {
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.userInfoError(20050)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("upstream-system");
        assertThat(stub.userInfoHits.get()).isEqualTo(3);
    }

    @Test
    void userInfoTransportFailureIsRetriedIdempotently() {
        stub.setUserInfoHandler(exchange -> {
            if (stub.userInfoHits.get() == 1) {
                FeishuStubServer.dropConnection();
                return;
            }
            FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.successUserInfo());
        });

        var verified = provider().verify(command());

        assertThat(verified.identity().externalId()).isEqualTo("ou_union_1");
        assertThat(stub.userInfoHits.get()).isEqualTo(2);
    }

    @Test
    void malformedTokenBodyIsUnavailableNotGuessed() {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 502, "not-json-at-all"));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.tokenHits.get()).isEqualTo(1);
    }

    @Test
    void tokenSuccessWithoutAccessTokenIsUnavailable() {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, "{\"code\":0,\"expires_in\":7200}"));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
    }

    @Test
    void userInfoMissingStableIdIsUnavailable() {
        stub.setUserInfoHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                "{\"code\":0,\"data\":{\"name\":\"张三\",\"open_id\":\"ou_open_1\",\"tenant_key\":\"tenant-1\"}}"));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
    }

    @Test
    void errorDescriptionNeverLeaksIntoExceptionMessages() {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.tokenError(20004)));

        Throwable thrown = assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginRejectedException.class)
                .actual();

        assertThat(thrown.getMessage()).contains("20004");
        assertThat(thrown.getMessage()).doesNotContain("sensitive");
        assertThat(thrown.getMessage()).doesNotContain("stub described detail");
    }

    /**
     * W22-RV-05：异常消息携带真实 HTTP 状态（非字面量 200）。
     * W22-C1 后 FeishuApiException 仅在非 429/5xx 状态下抛出（过载状态业务结果不可信，
     * 归 upstream-system，见 w22c1* 专用用例），故本钉子改用 400/401 验证。
     */
    @Test
    void apiExceptionCarriesRealHttpStatusNotLiteral200() {
        FeishuOAuthClient client = new FeishuOAuthClient(properties);

        // token 端点：HTTP 400 信封 + 20003 → 异常消息 httpStatus=400
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.tokenError(20003)));
        assertThatThrownBy(() -> client.exchange("code-abc", "verifier-43-chars-long-enough-xxxxxx"))
                .isInstanceOf(FeishuApiException.class)
                .hasMessageContaining("httpStatus=400");

        // user_info 端点：HTTP 401 信封 + 20008（非系统类不重试，直接抛出）→ httpStatus=401
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 401, FeishuStubServer.userInfoError(20008)));
        assertThatThrownBy(() -> client.fetchUserInfo("u-access-token"))
                .isInstanceOf(FeishuApiException.class)
                .hasMessageContaining("httpStatus=401");
    }

    // ------------------------------------------------------------------
    // W22-C1（2026-09-27 独立校审）：严格成功判定——五个已复现反例的正式回归。
    // 反例原貌：target/w22-independent-probes/ResponseProbe.java + responses.log
    //（独立校审意见 §2.W22-C1）。修复前五组输入全部被提升为 VerifiedExternalLogin。
    // ------------------------------------------------------------------

    /** 反例 1：token 响应缺 code 节点（其余成功形状）——修复前 asInt(0) 默认成 0 被接受。 */
    @Test
    void w22c1TokenMissingBusinessCodeIsMalformedNotAccepted() {
        stub.setTokenHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                "{\"access_token\":\"u-access-token\",\"expires_in\":7200,"
                        + "\"token_type\":\"Bearer\",\"scope\":\"contact:user.base:readonly\"}"));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.tokenHits.get()).isEqualTo(1);
        assertThat(stub.userInfoHits.get()).isEqualTo(0);
    }

    /** 反例 2：user_info code 为非数字字符串——修复前 asInt(0) 把非数字默认成 0。 */
    @Test
    void w22c1UserInfoNonNumericBusinessCodeIsMalformedNotAccepted() {
        stub.setUserInfoHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                "{\"code\":\"9x\",\"msg\":\"success\",\"data\":{"
                        + "\"open_id\":\"ou_open_1\",\"union_id\":\"ou_union_1\",\"tenant_key\":\"tenant-1\"}}"));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }

    /**
     * 反例 3：user_info code=2^32（int 溢出）——修复前 intValue() 截断成 0 被接受；
     * 修复后按"合法整型非零未知码"默认拒绝，且诊断值原样保留（long 不截断）。
     */
    @Test
    void w22c1UserInfoOverflowBusinessCodeRejectsWithoutTruncation() {
        stub.setUserInfoHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                "{\"code\":4294967296,\"msg\":\"overflow\",\"data\":{"
                        + "\"open_id\":\"ou_open_1\",\"union_id\":\"ou_union_1\",\"tenant_key\":\"tenant-1\"}}"));

        // 未知整型非零码 → 默认拒绝本次登录（fail closed），reasonCode 保留原值
        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginRejectedException.class)
                .extracting(e -> ((ExternalLoginRejectedException) e).reasonCode())
                .isEqualTo("4294967296");
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }

    /** 反例 4：token HTTP 503 + 成功形状 JSON——修复前成功分支不看 HTTP 状态。 */
    @Test
    void w22c1TokenSuccessShapeOnHttp503IsConflictNotAccepted() {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 503, FeishuStubServer.successToken()));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("http-business-conflict");
        assertThat(stub.tokenHits.get()).isEqualTo(1);
        assertThat(stub.userInfoHits.get()).isEqualTo(0);
    }

    /** 反例 5：user_info HTTP 401 + 成功形状 JSON——同样矛盾即拒绝，且不重试。 */
    @Test
    void w22c1UserInfoSuccessShapeOnHttp401IsConflictNotAccepted() {
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 401, FeishuStubServer.successUserInfo()));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("http-business-conflict");
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }

    /**
     * W22-C1 类型边界：信封 code 节点为 JSON null / 布尔 / 浮点 / 字符串数字（含 "0"）/
     * 空串 / 超 long 整数——全部非法信封（malformed-response），绝不走默认值或有损转换。
     * 字符串 "0" 是关键边界：宽松转换会把它当成功。
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"code\":null,\"data\":{\"open_id\":\"o\",\"union_id\":\"u\",\"tenant_key\":\"t\"}}",
            "{\"code\":true,\"data\":{\"open_id\":\"o\",\"union_id\":\"u\",\"tenant_key\":\"t\"}}",
            "{\"code\":0.0,\"data\":{\"open_id\":\"o\",\"union_id\":\"u\",\"tenant_key\":\"t\"}}",
            "{\"code\":0.5,\"data\":{\"open_id\":\"o\",\"union_id\":\"u\",\"tenant_key\":\"t\"}}",
            "{\"code\":\"0\",\"data\":{\"open_id\":\"o\",\"union_id\":\"u\",\"tenant_key\":\"t\"}}",
            "{\"code\":\"\",\"data\":{\"open_id\":\"o\",\"union_id\":\"u\",\"tenant_key\":\"t\"}}",
            "{\"code\":18446744073709551616,\"data\":{\"open_id\":\"o\",\"union_id\":\"u\",\"tenant_key\":\"t\"}}"})
    void w22c1EnvelopeCodeTypeBoundariesAreAllMalformed(String userInfoBody) {
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, userInfoBody));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }

    /**
     * W22-C1 类型边界：token expires_in 必须是正整数——缺失/字符串/浮点/零/负数
     * 一律 malformed-response（修复前 asLong(0) 全盘接受）。
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"code\":0,\"access_token\":\"u-access-token\"}",
            "{\"code\":0,\"access_token\":\"u-access-token\",\"expires_in\":\"7200\"}",
            "{\"code\":0,\"access_token\":\"u-access-token\",\"expires_in\":7200.5}",
            "{\"code\":0,\"access_token\":\"u-access-token\",\"expires_in\":0}",
            "{\"code\":0,\"access_token\":\"u-access-token\",\"expires_in\":-5}"})
    void w22c1TokenExpiresInMustBePositiveIntegral(String tokenBody) {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, tokenBody));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
    }

    /** W22-C1 类型边界：必需身份字段拒绝数字/布尔宽松转换（修复前 asText(null) 会把 123 变 "123"）。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"code\":0,\"data\":{\"open_id\":123,\"union_id\":\"u\",\"tenant_key\":\"t\"}}",
            "{\"code\":0,\"data\":{\"open_id\":\"o\",\"union_id\":true,\"tenant_key\":\"t\"}}",
            "{\"code\":0,\"data\":{\"open_id\":\"o\",\"union_id\":\"u\",\"tenant_key\":42}}",
            "{\"code\":0,\"data\":\"not-an-object\"}",
            "{\"code\":0,\"msg\":\"success-no-data\"}"})
    void w22c1UserInfoRequiredIdentityFieldsMustBeTypedText(String userInfoBody) {
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, userInfoBody));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }

    /** W22-C1：token 的 access_token 必须是非空文本（数字 123 不再被 asText 宽松接受）。 */
    @Test
    void w22c1TokenNumericOrBlankAccessTokenIsMalformed() {
        stub.setTokenHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                "{\"code\":0,\"access_token\":123,\"expires_in\":7200}"));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
    }

    // ------------------ W22-C1：HTTP 429/5xx 处置 ------------------

    /** token 429 + 未知业务码：上游过载 → 结果不确定（upstream-system），绝不当用户拒绝，且不重试。 */
    @Test
    void w22c1TokenHttp429WithUnknownBusinessCodeIsUnavailableNotRejection() {
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 429, FeishuStubServer.tokenError(99991400)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("upstream-system");
        assertThat(stub.tokenHits.get()).isEqualTo(1);
    }

    /** user_info 503 + 业务码：幂等 GET 有界重试（429/5xx 计入），耗尽 → upstream-system。 */
    @Test
    void w22c1UserInfoHttp503WithBusinessCodeRetriesThenUnavailable() {
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 503, FeishuStubServer.userInfoError(99991400)));

        assertThatThrownBy(() -> provider().verify(command()))
                .isInstanceOf(ExternalLoginUnavailableException.class)
                .extracting(e -> ((ExternalLoginUnavailableException) e).reasonCode())
                .isEqualTo("upstream-system");
        assertThat(stub.userInfoHits.get()).isEqualTo(3);
    }

    /** user_info 429 两次后成功：重试边界内恢复，登录正常完成。 */
    @Test
    void w22c1UserInfoHttp429RetriesThenSucceeds() {
        stub.setUserInfoHandler(exchange -> {
            if (stub.userInfoHits.get() <= 2) {
                FeishuStubServer.respondJson(exchange, 429, FeishuStubServer.userInfoError(99991400));
                return;
            }
            FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.successUserInfo());
        });

        var verified = provider().verify(command());

        assertThat(verified.identity().externalId()).isEqualTo("ou_union_1");
        assertThat(stub.userInfoHits.get()).isEqualTo(3);
    }

    /** 正常成功回归（W22-C1 后基线）：200 + 整数零码 + 类型化字段齐备 → 通过。 */
    @Test
    void w22c1LegalSuccessStillAccepted() {
        var verified = provider().verify(command());

        assertThat(verified.identity().externalId()).isEqualTo("ou_union_1");
        assertThat(stub.tokenHits.get()).isEqualTo(1);
        assertThat(stub.userInfoHits.get()).isEqualTo(1);
    }
}
