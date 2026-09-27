package com.gallop.fastddd.identity.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code fastddd.identity.*} 配置（v0.2 §4.5 配置选择顺序）。
 *
 * <ul>
 *   <li>{@code enabled} 默认 false——全部缺省即 B 零副作用（identity 消费者行为与
 *       1.0.0 无差异）；总开关 false + 子功能显式开启 = 配置错误（启动失败）；</li>
 *   <li>{@code login.mode} 默认 {@code none}；{@code feishu-web} = C 模式（要求显式
 *       {@code fastddd.security.jwt.enabled=false} 与 {@code fastddd.security.local-auth.enabled=false}）；</li>
 *   <li>{@code directory.enabled}/{@code worker}/{@code security.host-owned} 默认 false；</li>
 *   <li>starter 事务桥与 reference-pg 适配器<b>共用</b> {@code fastddd.identity.transaction.data-source-bean}
 *       按名绑定同一业务 DataSource（不猜 @Primary；不存在 {@code pg.data-source-bean} 属性，
 *       显式设置会被 IdentityModeGuard 拒绝）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "fastddd.identity")
public class FastDddIdentityProperties {

    /** 身份总开关：默认关闭。 */
    private boolean enabled = false;

    private final Login login = new Login();

    private final Security security = new Security();

    private final Directory directory = new Directory();

    /** identity Worker（E 模式）：非 web 进程显式声明；与 login.mode=feishu-web 互斥。 */
    private boolean worker = false;

    private final Admin admin = new Admin();

    private final Transaction transaction = new Transaction();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Login getLogin() {
        return login;
    }

    public Security getSecurity() {
        return security;
    }

    public Directory getDirectory() {
        return directory;
    }

    public boolean isWorker() {
        return worker;
    }

    public void setWorker(boolean worker) {
        this.worker = worker;
    }

    public Admin getAdmin() {
        return admin;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    /** 登录子域：mode / 会话 TTL / 会话 cookie 名。 */
    public static class Login {

        /** 站内回跳缺省与回退目标（W2-2 防开放重定向）。 */
        public static final String DEFAULT_POST_LOGIN_REDIRECT = "/";

        public enum Mode {
            /** 不装身份登录链（B/目录单开/Worker）。 */
            NONE,
            /** C 模式：飞书 Web 登录（浏览器回调在 W2-2 交付，W2-1 冻结协议依据）。 */
            FEISHU_WEB
        }

        private Mode mode = Mode.NONE;

        private final Session session = new Session();

        /** 授权 state 有效期（W2-2：一次性，缺省 10 分钟；过期即拒绝回调）。 */
        private Duration stateTtl = Duration.ofMinutes(10);

        /** 登录成功后允许的站内回跳地址白名单（防开放重定向；缺省仅 "/"）。 */
        private java.util.List<String> allowedPostLoginRedirects =
                new java.util.ArrayList<>(java.util.List.of(DEFAULT_POST_LOGIN_REDIRECT));

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public Session getSession() {
            return session;
        }

        public Duration getStateTtl() {
            return stateTtl;
        }

        public void setStateTtl(Duration stateTtl) {
            this.stateTtl = stateTtl;
        }

        public java.util.List<String> getAllowedPostLoginRedirects() {
            return allowedPostLoginRedirects;
        }

        public void setAllowedPostLoginRedirects(java.util.List<String> allowedPostLoginRedirects) {
            this.allowedPostLoginRedirects = allowedPostLoginRedirects;
        }

        /** 本地不透明会话参数（cookie 签发契约见 {@code IdentitySessionCookies}）。 */
        public static class Session {

            private Duration ttl = Duration.ofHours(8);

            private String cookieName = com.gallop.fastddd.identity.spring.security.IdentitySessionCookies.DEFAULT_COOKIE_NAME;

            /** Cookie Secure 属性：缺省 true（安全缺省）；本机 HTTP 调试须显式放宽。 */
            private boolean cookieSecure = true;

            public Duration getTtl() {
                return ttl;
            }

            public void setTtl(Duration ttl) {
                this.ttl = ttl;
            }

            public String getCookieName() {
                return cookieName;
            }

            public void setCookieName(String cookieName) {
                this.cookieName = cookieName;
            }

            public boolean isCookieSecure() {
                return cookieSecure;
            }

            public void setCookieSecure(boolean cookieSecure) {
                this.cookieSecure = cookieSecure;
            }
        }
    }

    /** 安全子域：host-owned 声明（§4.3 条 3）。 */
    public static class Security {

        private boolean hostOwned = false;

        public boolean isHostOwned() {
            return hostOwned;
        }

        public void setHostOwned(boolean hostOwned) {
            this.hostOwned = hostOwned;
        }
    }

    /** 目录同步子域（W3 设计 v3 §7 配置骨架，W3-a）。 */
    public static class Directory {

        /** 调度缺省间隔（两侧都未显式配置时的生效值）。 */
        public static final Duration DEFAULT_SYNC_INTERVAL = Duration.ofHours(6);

        private boolean enabled = false;

        /** 调度间隔；<b>null = 未显式配置</b>（cron/interval 互斥只认显式值，复评钉 ③）。 */
        private Duration syncInterval;

        /** cron 表达式（Spring {@code CronExpression} 语法）；null = 未显式配置。 */
        private String cron;

        private int pageSize = 100;

        private int maxPagesPerRun = 1000;

        /** 目录代租约有效期（W3-c 真实存储适配器消费；此处仅契约与校验）。 */
        private Duration leaseTimeout = Duration.ofMinutes(10);

        private final Retry retry = new Retry();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getSyncInterval() {
            return syncInterval;
        }

        public void setSyncInterval(Duration syncInterval) {
            this.syncInterval = syncInterval;
        }

        /** 生效调度间隔：显式值或缺省 6h（cron 模式不读此值）。 */
        public Duration getEffectiveSyncInterval() {
            return syncInterval != null ? syncInterval : DEFAULT_SYNC_INTERVAL;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getMaxPagesPerRun() {
            return maxPagesPerRun;
        }

        public void setMaxPagesPerRun(int maxPagesPerRun) {
            this.maxPagesPerRun = maxPagesPerRun;
        }

        public Duration getLeaseTimeout() {
            return leaseTimeout;
        }

        public void setLeaseTimeout(Duration leaseTimeout) {
            this.leaseTimeout = leaseTimeout;
        }

        public Retry getRetry() {
            return retry;
        }

        /**
         * 源适配器有界重试参数（W3 设计 §5.2：重试责任在源，编排不叠加）。
         * M1 由真实源适配器（W3-b）消费；此处冻结配置契约与校验。
         */
        public static class Retry {

            private int maxAttempts = 2;

            private Duration backoff = Duration.ofMillis(500);

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            public Duration getBackoff() {
                return backoff;
            }

            public void setBackoff(Duration backoff) {
                this.backoff = backoff;
            }
        }
    }

    /** 管理子域（W3 设计 v3 §7）：独立于 directory.enabled——只管管理操作准入。 */
    public static class Admin {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** 事务子域：按名绑定业务 DataSource（不猜 @Primary）。 */
    public static class Transaction {

        private String dataSourceBean = "dataSource";

        public String getDataSourceBean() {
            return dataSourceBean;
        }

        public void setDataSourceBean(String dataSourceBean) {
            this.dataSourceBean = dataSourceBean;
        }
    }
}
