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
 * 协议依据 W0-4（CW-01，2026-09-26 复核：token v3 请求体为 application/json）。
 */
class FeishuOAuthClientContractTest {

    private FeishuStubServer stub;

    private FeishuLoginProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        stub = new FeishuStubServer();
        properties = FeishuStubServer.propertiesAgainst(stub);
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

    /** W22-RV-05：异常消息携带真实 HTTP 状态（非字面量 200）——上游 5xx 信封不再伪装成"传输正常"。 */
    @Test
    void apiExceptionCarriesRealHttpStatusNotLiteral200() {
        FeishuOAuthClient client = new FeishuOAuthClient(properties);

        // token 端点：HTTP 503 信封 + 20003 → 异常消息 httpStatus=503
        stub.setTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 503, FeishuStubServer.tokenError(20003)));
        assertThatThrownBy(() -> client.exchange("code-abc", "verifier-43-chars-long-enough-xxxxxx"))
                .isInstanceOf(FeishuApiException.class)
                .hasMessageContaining("httpStatus=503");

        // user_info 端点：HTTP 500 信封 + 20008（非系统类不重试，直接抛出）→ httpStatus=500
        stub.setUserInfoHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 500, FeishuStubServer.userInfoError(20008)));
        assertThatThrownBy(() -> client.fetchUserInfo("u-access-token"))
                .isInstanceOf(FeishuApiException.class)
                .hasMessageContaining("httpStatus=500");
    }
}
