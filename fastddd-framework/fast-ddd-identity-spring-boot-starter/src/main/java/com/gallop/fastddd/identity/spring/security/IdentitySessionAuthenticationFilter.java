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
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            resolveSession(request).ifPresent(principal ->
                    SecurityContextHolder.getContext()
                            .setAuthentication(IdentityAuthenticationToken.forSession(principal)));
        }
        filterChain.doFilter(request, response);
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
