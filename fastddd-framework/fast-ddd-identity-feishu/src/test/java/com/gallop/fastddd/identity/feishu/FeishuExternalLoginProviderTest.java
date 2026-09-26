package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.error.ExternalLoginRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 飞书提供者装配契约：授权 URL 形态、必填配置 fail-fast、企业租户校验、
 * union_id 作用域与显示名回退。
 */
class FeishuExternalLoginProviderTest {

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

    @Test
    void authorizeUrlCarriesOauthAndPkceParametersWithEncodedRedirect() {
        String url = new FeishuExternalLoginProvider(properties)
                .authorizeUrl("state-abc", "challenge-xyz");

        assertThat(url).startsWith("https://accounts.feishu.example/open-apis/authen/v1/authorize");
        assertThat(url).contains("client_id=cli_test_app");
        assertThat(url).contains("response_type=code");
        // redirect_uri 整体 URL 编码（W0-4 §1.3）
        assertThat(url).contains("redirect_uri=https%3A%2F%2Fapp.example.com%2Fapi%2Fidentity%2Flogin%2Fcallback");
        assertThat(url).contains("state=state-abc");
        assertThat(url).contains("code_challenge=challenge-xyz");
        assertThat(url).contains("code_challenge_method=S256");
    }

    @Test
    void missingRequiredPropertiesFailsFastListingEveryKey() {
        FeishuLoginProperties empty = new FeishuLoginProperties();
        empty.setAppId("cli_test_app");

        assertThatThrownBy(() -> new FeishuExternalLoginProvider(empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fastddd.identity.feishu.app-secret")
                .hasMessageContaining("fastddd.identity.feishu.redirect-uri")
                .hasMessageContaining("fastddd.identity.feishu.expected-tenant-key")
                .hasMessageNotContaining("fastddd.identity.feishu.app-id");
    }

    @Test
    void verifyWithoutCodeCredentialsIsProgrammingError() {
        FeishuExternalLoginProvider provider = new FeishuExternalLoginProvider(properties);

        assertThatThrownBy(() -> provider.verify(new ExternalLoginCommand(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tenantMismatchIsDeterminateRejection() {
        stub.setUserInfoHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                "{\"code\":0,\"data\":{\"name\":\"张三\",\"open_id\":\"ou_open_1\","
                        + "\"union_id\":\"ou_union_1\",\"tenant_key\":\"tenant-OTHER\"}}"));

        assertThatThrownBy(() -> new FeishuExternalLoginProvider(properties).verify(command()))
                .isInstanceOf(ExternalLoginRejectedException.class)
                .extracting(e -> ((ExternalLoginRejectedException) e).reasonCode())
                .isEqualTo("tenant-mismatch");
    }

    @Test
    void verifiedIdentityIsScopedByUnionIdUnderConfiguredApp() {
        VerifiedExternalLogin verified = new FeishuExternalLoginProvider(properties).verify(command());

        assertThat(verified.identity().provider()).isEqualTo("feishu");
        assertThat(verified.identity().tenantId()).isEqualTo("tenant-1");
        assertThat(verified.identity().appScope()).isEqualTo("cli_test_app");
        assertThat(verified.identity().externalIdType()).isEqualTo("union_id");
        assertThat(verified.identity().externalId()).isEqualTo("ou_union_1");
        assertThat(verified.loginName()).isEqualTo("feishu:ou_union_1");
        assertThat(verified.displayName()).isEqualTo("张三");
    }

    @Test
    void displayNameFallsBackToEnNameThenUnionId() {
        stub.setUserInfoHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                "{\"code\":0,\"data\":{\"en_name\":\"San Zhang\",\"open_id\":\"ou_open_1\","
                        + "\"union_id\":\"ou_union_1\",\"tenant_key\":\"tenant-1\"}}"));
        assertThat(new FeishuExternalLoginProvider(properties).verify(command()).displayName())
                .isEqualTo("San Zhang");

        stub.setUserInfoHandler(exchange -> FeishuStubServer.respondJson(exchange, 200,
                "{\"code\":0,\"data\":{\"open_id\":\"ou_open_1\","
                        + "\"union_id\":\"ou_union_1\",\"tenant_key\":\"tenant-1\"}}"));
        assertThat(new FeishuExternalLoginProvider(properties).verify(command()).displayName())
                .isEqualTo("ou_union_1");
    }

    private static ExternalLoginCommand command() {
        return new ExternalLoginCommand(Map.of(
                FeishuExternalLoginProvider.CREDENTIAL_CODE, "code-abc",
                FeishuExternalLoginProvider.CREDENTIAL_CODE_VERIFIER, "verifier-43-chars-long-enough-xxxxxx"));
    }
}
