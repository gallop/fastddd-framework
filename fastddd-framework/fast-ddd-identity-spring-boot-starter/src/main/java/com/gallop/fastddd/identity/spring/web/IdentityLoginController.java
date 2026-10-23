package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import com.gallop.fastddd.identity.spring.security.IdentitySessionCookies;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.util.Map;

/**
 * identity 浏览器登录/登出端点（W2-2，C 链上仅两个 GET 登录路径 permitAll）。
 *
 * <p><b>Cookie 契约</b>：会话只经 {@link IdentitySessionCookies#issue} 签发
 * （T2 提交成功后；复评 RV-03），清除走同名同路径同属性 maxAge=0；绑定 Cookie
 * 见 {@link IdentityLoginCookies}。响应体只含错误类别与通用文案（脱敏），不回显
 * 授权码/state/token。</p>
 *
 * <p><b>登出</b>：POST（已认证 + CSRF）撤销服务端会话（幂等）并清除会话与绑定
 * Cookie；不宣称外部（飞书）全局登出。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@RestController
public class IdentityLoginController {

    private final IdentityLoginService loginService;
    private final FastDddIdentityProperties properties;

    public IdentityLoginController(IdentityLoginService loginService, FastDddIdentityProperties properties) {
        this.loginService = loginService;
        this.properties = properties;
    }

    /** 发起授权：302 至外部授权页 + 种浏览器绑定 Cookie。GET permitAll。 */
    @GetMapping(IdentityLoginEndpoints.AUTHORIZE_PATH)
    ResponseEntity<Void> authorize(@RequestParam(name = "redirect", required = false) String redirect,
                                   HttpServletResponse response) {
        IdentityLoginService.BeginLogin begin = loginService.begin(redirect);
        boolean secure = properties.getLogin().getSession().isCookieSecure();
        response.addHeader(HttpHeaders.SET_COOKIE,
                IdentityLoginCookies.bindingIssue(begin.browserBinding(),
                        properties.getLogin().getStateTtl(), secure).toString());
        // W22-RV-09：携带绑定 Cookie 值的 302 禁止中间缓存层存储
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, begin.authorizeUrl())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    /** 外部回调：成功 302 站内回跳 + 会话 Cookie；拒绝 401 / 不确定 503 / 服务端 500（均清绑定）。 */
    @GetMapping(IdentityLoginEndpoints.CALLBACK_PATH)
    ResponseEntity<Map<String, String>> callback(@RequestParam(name = "code", required = false) String code,
                                                 @RequestParam(name = "state", required = false) String state,
                                                 @RequestParam(name = "error", required = false) String error,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        boolean secure = properties.getLogin().getSession().isCookieSecure();
        String binding = cookieValue(request, IdentityLoginCookies.BINDING_COOKIE_NAME);
        IdentityLoginService.CallbackOutcome outcome = loginService.handleCallback(code, state, error, binding);
        // 回调无论结局如何都终止本次授权上下文：清除绑定 Cookie
        response.addHeader(HttpHeaders.SET_COOKIE, IdentityLoginCookies.bindingClear(secure).toString());
        return switch (outcome) {
            case IdentityLoginService.CallbackOutcome.Success success -> {
                // T2 已提交（LocalLoginUseCase 返回即成功）；会话 Cookie 只在此时签发
                response.addHeader(HttpHeaders.SET_COOKIE, IdentitySessionCookies.issue(
                        properties.getLogin().getSession().getCookieName(),
                        success.login().session().sessionId().value(),
                        properties.getLogin().getSession().getTtl(), secure).toString());
                yield ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, success.postLoginRedirect())
                        // W22-RV-09：携带会话 Cookie 值的 302 禁止中间缓存层存储
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .<Map<String, String>>build();
            }
            case IdentityLoginService.CallbackOutcome.Rejected rejected ->
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "error", "identity_login_rejected",
                            "reason", rejected.reason(),
                            "message", rejected.message()));
            case IdentityLoginService.CallbackOutcome.Unavailable unavailable ->
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                            "error", "identity_login_unavailable",
                            "reason", unavailable.reason(),
                            "message", unavailable.message()));
            case IdentityLoginService.CallbackOutcome.ServerFailure ignored ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                            "error", "identity_login_server_error",
                            "message", "登录服务端错误，请联系管理员（细节见服务端日志）"));
        };
    }

    /** 登出：已认证 + CSRF；撤销会话（幂等）+ 清除同名同路径 Cookie。 */
    @PostMapping(IdentityLoginEndpoints.LOGOUT_PATH)
    ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = properties.getLogin().getSession().isCookieSecure();
        String cookieName = properties.getLogin().getSession().getCookieName();
        String rawSessionId = cookieValue(request, cookieName);
        boolean revoked = loginService.logout(rawSessionId);
        // 同名同路径同属性 maxAge=0（无论服务端是否找到会话）
        response.addHeader(HttpHeaders.SET_COOKIE, IdentitySessionCookies.clear(cookieName, secure).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, IdentityLoginCookies.bindingClear(secure).toString());
        return ResponseEntity.ok(Map.of("message", revoked ? "已登出" : "已登出（无有效会话）"));
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie cookie = WebUtils.getCookie(request, name);
        return cookie == null ? null : cookie.getValue();
    }
}
