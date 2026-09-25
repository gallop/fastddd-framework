package com.gallop.fastddd.identity.spring.security;

import com.gallop.fastddd.identity.core.domain.SessionId;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.UnknownSessionFormatException;
import com.gallop.fastddd.identity.core.port.LocalSessionPort;
import com.gallop.fastddd.identity.spring.actor.IdentityAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.Optional;

/**
 * C 模式会话认证过滤器（v0.2 §4.2）：每请求从不透明会话 cookie 直查权威状态
 * （会话行 JOIN 主体行：未过期/未撤销 + status=ACTIVE + epoch 一致，M1 无缓存），
 * 通过即安装 principal=VerifiedPrincipal、name=principalId 的 Authentication；
 * 任一不满足视为未认证（由链上授权规则/入口点拒绝）。
 *
 * <p><b>已有认证的复验契约（W2-1 修复轮 W21-02）</b>——"已存在 Authentication"
 * 不豁免本地会话权威复验：</p>
 * <ul>
 *   <li><b>会话型 identity token</b>（{@code IdentityAuthenticationToken} 且
 *       principal=VerifiedPrincipal——如宿主链从持久化 SecurityContext 恢复的旧
 *       token）：一律重新复验——有效则以<b>本请求复验产物替换</b>旧 token
 *       （刷新 epoch/状态快照）；失效/缺 Cookie/未知格式则<b>清理该身份</b>，
 *       请求继续由授权规则按未认证拒绝（不中断已开始的请求，不抛 500）；</li>
 *   <li><b>作业型 token</b>（principal=Actor）与<b>其他宿主认证类型</b>：不由本
 *       过滤器改写——作业身份由 {@code SpringSystemJobContext} 显式管理，宿主链
 *       上的其他 Authentication 属宿主权限（host-owned 链自担复验责任）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentitySessionAuthenticationFilter extends OncePerRequestFilter {

    private final LocalSessionPort localSessionPort;

    private final String cookieName;

    public IdentitySessionAuthenticationFilter(LocalSessionPort localSessionPort, String cookieName) {
        this.localSessionPort = localSessionPort;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing == null) {
            resolveSession(request).ifPresent(principal ->
                    SecurityContextHolder.getContext()
                            .setAuthentication(IdentityAuthenticationToken.forSession(principal)));
        } else if (isRestoredSessionToken(existing)) {
            // 恢复的本地会话 token：不复用旧身份，按本请求权威状态重判（W21-02）
            Optional<VerifiedPrincipal> revalidated = resolveSession(request);
            if (revalidated.isPresent()) {
                SecurityContextHolder.getContext()
                        .setAuthentication(IdentityAuthenticationToken.forSession(revalidated.get()));
            } else {
                // 失效/缺凭证：清理旧身份，请求继续（授权规则按未认证拒绝）
                SecurityContextHolder.getContext().setAuthentication(null);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 会话型 identity token 判定（作业型 Actor token 与宿主认证类型不算）。 */
    private boolean isRestoredSessionToken(Authentication authentication) {
        return authentication instanceof IdentityAuthenticationToken token
                && token.getPrincipal() instanceof VerifiedPrincipal;
    }

    private Optional<VerifiedPrincipal> resolveSession(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookieName);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return Optional.empty();
        }
        SessionId sessionId;
        try {
            sessionId = SessionId.of(cookie.getValue());
        } catch (IllegalArgumentException shapeMismatch) {
            // 形状不合协议（16–64）：视为无凭证，不作认证（不遮蔽链上 401 语义）
            return Optional.empty();
        }
        try {
            return localSessionPort.validateSession(sessionId);
        } catch (UnknownSessionFormatException unknown) {
            // 未知会话格式拒绝认证（§13.8/R6）：与无凭证同等处理，不留旁路
            return Optional.empty();
        }
    }
}
