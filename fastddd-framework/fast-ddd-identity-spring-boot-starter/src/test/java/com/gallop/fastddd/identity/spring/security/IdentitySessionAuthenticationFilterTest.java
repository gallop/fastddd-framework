package com.gallop.fastddd.identity.spring.security;

import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalStatus;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.port.LocalSessionPort;
import com.gallop.fastddd.identity.spring.actor.IdentityAuthenticationToken;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W2-1 修复轮 W21-02：已有 Authentication 不豁免本地会话权威复验——
 * 恢复的会话型 identity token 一律重验（有效替换/失效清理）；作业型 token 与
 * 宿主认证类型不改写。过滤器边界语义；完整宿主链（登录→保存上下文→停用→恢复
 * 旧上下文被拒）见消费者 identity-host-security RestoredContextRevalidationConsumerTest。
 */
class IdentitySessionAuthenticationFilterTest {

    private final LocalSessionPort localSessionPort = mock(LocalSessionPort.class);
    private final IdentitySessionAuthenticationFilter filter =
            new IdentitySessionAuthenticationFilter(localSessionPort, "FASTDDD_SESSION");

    private VerifiedPrincipal principal(String id, long epoch) {
        return new VerifiedPrincipal("tenant-1", PrincipalId.of(id), "login-1", "Display 1",
                PrincipalType.HUMAN, PrincipalStatus.ACTIVE, epoch);
    }

    private MockHttpServletRequest requestWithCookie(String sessionId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");
        if (sessionId != null) {
            request.setCookies(new Cookie("FASTDDD_SESSION", sessionId));
        }
        return request;
    }

    private Authentication passThroughFilter(MockHttpServletRequest request) throws Exception {
        Authentication[] captured = new Authentication[1];
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                captured[0] = SecurityContextHolder.getContext().getAuthentication());
        return captured[0];
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoredSessionTokenWithValidSessionIsRevalidatedAndReplaced() throws Exception {
        // 宿主链恢复旧 token（epoch=0 旧快照）；权威状态当前 epoch=1——必须以复验产物替换
        VerifiedPrincipal stale = principal("01234567-89ab-cdef-0123-456789abc001", 0L);
        VerifiedPrincipal fresh = principal("01234567-89ab-cdef-0123-456789abc001", 1L);
        IdentityAuthenticationToken restoredToken = IdentityAuthenticationToken.forSession(stale);
        SecurityContextHolder.getContext().setAuthentication(restoredToken);
        when(localSessionPort.validateSession(any()))
                .thenReturn(Optional.of(fresh));

        Authentication downstream = passThroughFilter(requestWithCookie("session-id-0123456789abcdef"));

        assertThat(downstream).isInstanceOf(IdentityAuthenticationToken.class);
        // 不是恢复的旧实例，且携带刷新后的权威快照（VerifiedPrincipal 等值只看
        // principalId，故以实例同一性与 epoch 区分"替换"与"保留"）
        assertThat(downstream).isNotSameAs(restoredToken);
        assertThat(((VerifiedPrincipal) downstream.getPrincipal()).authEpoch()).isEqualTo(1L);
        assertThat(downstream.getName()).isEqualTo(fresh.principalId().value());
        verify(localSessionPort).validateSession(any());
    }

    @Test
    void restoredSessionTokenWithDeactivatedPrincipalIsClearedNotKept() throws Exception {
        // 另一实例已停用：validateSession 空——旧身份必须被清理（不因"已有认证"保留）
        SecurityContextHolder.getContext().setAuthentication(
                IdentityAuthenticationToken.forSession(principal("01234567-89ab-cdef-0123-456789abc002", 0L)));
        when(localSessionPort.validateSession(any())).thenReturn(Optional.empty());

        Authentication downstream = passThroughFilter(requestWithCookie("session-id-0123456789abcdef"));

        assertThat(downstream).isNull(); // 请求继续（不中断），授权规则按未认证拒绝
        verify(localSessionPort).validateSession(any());
    }

    @Test
    void restoredSessionTokenWithoutCookieIsClearedWithoutStoreCall() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                IdentityAuthenticationToken.forSession(principal("01234567-89ab-cdef-0123-456789abc003", 0L)));

        Authentication downstream = passThroughFilter(requestWithCookie(null));

        assertThat(downstream).isNull();
        verify(localSessionPort, never()).validateSession(any());
    }

    @Test
    void systemJobTokenIsNotRewrittenBySessionFilter() throws Exception {
        // 作业型 token 由 SpringSystemJobContext 显式管理：会话过滤器不改写
        Actor jobActor = new Actor("tenant-1", PrincipalId.forSystemJob("nightly-sync"), PrincipalType.SYSTEM_JOB);
        IdentityAuthenticationToken jobToken = IdentityAuthenticationToken.forSystemJob(jobActor);
        SecurityContextHolder.getContext().setAuthentication(jobToken);

        Authentication downstream = passThroughFilter(requestWithCookie(null));

        assertThat(downstream).isSameAs(jobToken);
        verify(localSessionPort, never()).validateSession(any());
    }

    @Test
    void foreignHostAuthenticationIsNotRewritten() throws Exception {
        // 宿主自有认证类型：不覆盖（host-owned 链的宿主身份属宿主权限）
        UsernamePasswordAuthenticationToken hostToken = UsernamePasswordAuthenticationToken
                .authenticated("host-user", "n/a", java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(hostToken);

        Authentication downstream = passThroughFilter(requestWithCookie("session-id-0123456789abcdef"));

        assertThat(downstream).isSameAs(hostToken);
        verify(localSessionPort, never()).validateSession(any());
    }

    @Test
    void absentAuthenticationResolvesFromCookieAsBefore() throws Exception {
        // 既有行为不变：无已有认证时从 cookie 解析并安装会话 token
        when(localSessionPort.validateSession(any()))
                .thenReturn(Optional.of(principal("01234567-89ab-cdef-0123-456789abc004", 0L)));

        Authentication downstream = passThroughFilter(requestWithCookie("session-id-0123456789abcdef"));

        assertThat(downstream).isInstanceOf(IdentityAuthenticationToken.class);
        assertThat(downstream.getName()).isEqualTo("01234567-89ab-cdef-0123-456789abc004");
    }
}
