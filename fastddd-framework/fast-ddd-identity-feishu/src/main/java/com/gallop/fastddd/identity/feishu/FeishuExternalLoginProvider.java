package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.error.ExternalLoginRejectedException;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 飞书自建应用登录适配器（W2-2）：真实 {@link ExternalLoginProvider} +
 * {@link ExternalAuthorizePort}，浏览器 OAuth 2.0 + PKCE（S256）。
 *
 * <p><b>verify（服务端凭证验证，回调后调用）</b>：授权码兑换（不重试）→
 * user_info 拉取（幂等有限重试）→ 企业租户校验 → 以 union_id 为外部稳定标识构造
 * {@code ExternalIdentity(provider="feishu", tenant_key, app-id, "union_id", …)}。
 * 所有远程调用在事务外（由 LocalLoginUseCase 契约保证）。</p>
 *
 * <p><b>错误映射（W0-4 §3 按端点分类）</b>：</p>
 * <ul>
 *   <li>LOGIN_REJECTED / CODE_INVALID / TOKEN_INVALID / UNKNOWN →
 *       {@link ExternalLoginRejectedException}（确定拒绝本次登录，reasonCode=飞书错误码；
 *       不推断离职——离职只在 user_info 20021/20022/20023 语义下作为"本次拒绝"记录）；</li>
 *   <li>PKCE_FAILED / CONFIG_ERROR → {@link IllegalStateException}（实现缺陷或管理员
 *       修复项：显式失败并审计，不静默吞掉、不引导用户重试）；</li>
 *   <li>SYSTEM / 传输 / 响应不可解析 → {@code ExternalLoginUnavailableException}
 *       （结果不确定：重新发起登录，不是对身份状态的判定）。</li>
 * </ul>
 *
 * <p>凭据脱敏：异常与日志不携带 app_secret/授权码/verifier/access_token。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class FeishuExternalLoginProvider implements ExternalLoginProvider, ExternalAuthorizePort {

    /** verify 命令凭证键：回调携带的授权码。 */
    public static final String CREDENTIAL_CODE = "code";

    /** verify 命令凭证键：与 state 一一对应保存的 PKCE code_verifier。 */
    public static final String CREDENTIAL_CODE_VERIFIER = "code_verifier";

    /** 主体绑定作用域标识（v0.2 §5：外部标识类型固定 union_id，跨应用稳定）。 */
    static final String EXTERNAL_ID_TYPE_UNION_ID = "union_id";

    /** 首版单企业自建应用：provider 常量。 */
    static final String PROVIDER_FEISHU = "feishu";

    private final FeishuLoginProperties properties;

    private final FeishuOAuthClient client;

    public FeishuExternalLoginProvider(FeishuLoginProperties properties) {
        List<String> missing = properties.missingRequiredKeys();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("fastddd.identity.login.mode=feishu-web 且引入"
                    + " fast-ddd-identity-feishu 时，以下配置必填（当前缺失）：\n  - "
                    + String.join("\n  - ", missing)
                    + "\n（redirect-uri 须与开放平台安全设置登记一致；expected-tenant-key 用于企业租户校验）");
        }
        this.properties = properties;
        this.client = new FeishuOAuthClient(properties);
    }

    /**
     * 构造授权页 URL（302 目标；W0-4 §1.3：client_id/response_type=code/redirect_uri/
     * state/code_challenge + method=S256；redirect_uri 用适配器登记配置——与兑换
     * 端提交的 redirect_uri 同源，避免两处漂移）。
     */
    @Override
    public String authorizeUrl(String state, String codeChallenge) {
        return properties.getAuthorizeEndpoint()
                + "?client_id=" + encode(properties.getAppId())
                + "&response_type=code"
                + "&redirect_uri=" + encode(properties.getRedirectUri())
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=S256";
    }

    @Override
    public VerifiedExternalLogin verify(ExternalLoginCommand command) {
        String code = command.credential(CREDENTIAL_CODE);
        String codeVerifier = command.credential(CREDENTIAL_CODE_VERIFIER);
        if (code == null || code.isBlank() || codeVerifier == null || codeVerifier.isBlank()) {
            // 编程错误（服务端装配回调上下文缺参），非用户可重试路径
            throw new IllegalArgumentException("飞书登录回调缺少 " + CREDENTIAL_CODE + "/"
                    + CREDENTIAL_CODE_VERIFIER + " 凭证（服务端装配缺陷）");
        }
        try {
            return doVerify(code, codeVerifier);
        } catch (FeishuApiException apiError) {
            throw map(apiError);
        }
    }

    private VerifiedExternalLogin doVerify(String code, String codeVerifier) {
        FeishuOAuthClient.TokenResult token = client.exchange(code, codeVerifier);
        FeishuOAuthClient.UserInfo user = client.fetchUserInfo(token.accessToken());
        if (!properties.getExpectedTenantKey().equals(user.tenantKey())) {
            // 企业租户不匹配：防跨企业 code 混用/配置漂移，确定拒绝
            throw new ExternalLoginRejectedException("tenant-mismatch",
                    "飞书返回的企业租户与预期不符，拒绝本次登录（expected≠actual，不记录实际值）");
        }
        // 显示名仅取可读候选（name/en_name），缺省回退 union_id——客户端已保证非空
        String displayName = (user.name() != null && !user.name().isBlank()) ? user.name()
                : ((user.enName() != null && !user.enName().isBlank()) ? user.enName() : user.unionId());
        return new VerifiedExternalLogin(
                new ExternalIdentity(PROVIDER_FEISHU, user.tenantKey(), properties.getAppId(),
                        EXTERNAL_ID_TYPE_UNION_ID, user.unionId()),
                PROVIDER_FEISHU + ":" + user.unionId(),
                displayName);
    }

    /** W0-4 §3 类别 → 核心异常（拒绝/配置失败/结果不确定三态）。 */
    private RuntimeException map(FeishuApiException apiError) {
        return switch (apiError.category()) {
            case CODE_INVALID -> new ExternalLoginRejectedException(String.valueOf(apiError.code()),
                    "飞书授权码无效/过期/已使用/不匹配（code=" + apiError.code() + "），请重新发起登录");
            case LOGIN_REJECTED -> new ExternalLoginRejectedException(String.valueOf(apiError.code()),
                    "飞书用户状态或应用可用范围不满足要求（code=" + apiError.code() + "），拒绝本次登录");
            case TOKEN_INVALID -> new ExternalLoginRejectedException(String.valueOf(apiError.code()),
                    "飞书判定刚兑换的 user_access_token 无效（code=" + apiError.code() + "），请重新发起登录");
            case UNKNOWN -> new ExternalLoginRejectedException(String.valueOf(apiError.code()),
                    "飞书返回未核对错误码（code=" + apiError.code() + "），默认拒绝本次登录");
            case PKCE_FAILED -> new IllegalStateException(
                    "飞书 PKCE 校验失败（code=" + apiError.code() + "）：授权链路实现缺陷或参数被篡改，"
                            + "显式失败并审计；不静默重试");
            case CONFIG_ERROR -> new IllegalStateException(
                    "飞书应用配置错误（code=" + apiError.code() + "）：client_secret/redirect_uri/应用形态"
                            + "需管理员核对开放平台配置后修复");
            case SYSTEM -> new com.gallop.fastddd.identity.core.error.ExternalLoginUnavailableException(
                    "upstream-system",
                    "飞书端点系统错误（code=" + apiError.code() + "），登录结果不确定，请重新发起登录");
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
