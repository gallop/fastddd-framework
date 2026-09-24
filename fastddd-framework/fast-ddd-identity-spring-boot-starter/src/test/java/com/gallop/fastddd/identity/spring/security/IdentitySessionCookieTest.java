package com.gallop.fastddd.identity.spring.security;

import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话 Cookie 签发契约（v0.2 §4.4"安全 Cookie"，复评 RV-03）：W2-1 无登录端点，
 * 先把 HttpOnly/Secure/SameSite=Lax/Path/maxAge 固定为代码契约——W2-2 回调必须经
 * {@link IdentitySessionCookies#issue} 签发，本测试即"漏设属性即失败"的锚点；
 * 过滤器读取名与签发缺省名同源（读写一致）。
 */
class IdentitySessionCookieTest {

    @Test
    void issueAppliesSafeCookieContract() {
        ResponseCookie cookie = IdentitySessionCookies.issue(
                "FASTDDD_SESSION", "session-id-value", Duration.ofHours(8), true);

        assertThat(cookie.isHttpOnly()).as("HttpOnly：防 XSS 读取会话").isTrue();
        assertThat(cookie.isSecure()).as("Secure：缺省只走 HTTPS").isTrue();
        assertThat(cookie.getSameSite()).as("SameSite=Lax：§4.2 CSRF 行锁定").isEqualTo("Lax");
        assertThat(cookie.getPath()).as("Path=/：全站会话").isEqualTo("/");
        assertThat(cookie.getMaxAge()).as("maxAge=会话 TTL（与 expires_at 同源）").isEqualTo(Duration.ofHours(8));
        assertThat(cookie.getName()).isEqualTo("FASTDDD_SESSION");
        assertThat(cookie.getValue()).isEqualTo("session-id-value");
    }

    @Test
    void secureOptOutIsExplicitAndDoesNotWeakenHttpOnlyOrSameSite() {
        // 本机 HTTP 调试显式放宽仅放开 Secure；HttpOnly/SameSite 不随之降级
        ResponseCookie cookie = IdentitySessionCookies.issue(
                "FASTDDD_SESSION", "session-id-value", Duration.ofHours(8), false);

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void filterReadsTheNameTheIssuerWritesByDefault() {
        // 读写同源锚点：过滤器 cookie 名来自 properties 缺省，签发缺省与之相等
        FastDddIdentityProperties properties = new FastDddIdentityProperties();
        assertThat(properties.getLogin().getSession().getCookieName())
                .isEqualTo(IdentitySessionCookies.DEFAULT_COOKIE_NAME);
        // 安全缺省：Secure=true（HTTP 环境须显式 opt-out，不得改默认）
        assertThat(properties.getLogin().getSession().isCookieSecure()).isTrue();
    }
}
