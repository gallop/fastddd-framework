package com.gallop.fastddd.identity.spring;

import org.springframework.core.env.Environment;

/**
 * identity 配置组合守卫（v0.2 §4.5 配置选择顺序；W1-b）。
 *
 * <p>属性级 fail-fast（与 security-starter W1-a {@code LocalAuthConfigurationGuard}
 * 同语义：仅认显式配置值）。eager 构造期校验，冲突抛 {@link IllegalStateException}。
 * Bean/路由级最终检查（宿主链、默认用户名认证、旧路由旁路）由
 * {@link IdentityModeValidator} 在单例装配完成后执行。</p>
 *
 * @author gallop
 * @since 1.1.0（W1-b/W2-1）
 */
public final class IdentityModeGuard {

    static final String ENABLED = "fastddd.identity.enabled";
    static final String LOGIN_MODE = "fastddd.identity.login.mode";
    static final String WORKER = "fastddd.identity.worker";
    static final String DIRECTORY_ENABLED = "fastddd.identity.directory.enabled";
    static final String HOST_OWNED = "fastddd.identity.security.host-owned";
    static final String AUDIT_ACTOR_COLUMNS = "fastddd.identity.audit.actor-columns";
    static final String PG_INITIALIZE_SCHEMA = "fastddd.identity.pg.initialize-schema";
    static final String PG_ENSURE_AUDIT_TABLE = "fastddd.identity.pg.ensure-audit-table";
    static final String TX_DATA_SOURCE_BEAN = "fastddd.identity.transaction.data-source-bean";
    /** 无效属性（从未被任何生产代码读取）：显式设置即失败，防按错误指引静默绑错库（复评 RV-01）。 */
    static final String PG_DATA_SOURCE_BEAN = "fastddd.identity.pg.data-source-bean";
    static final String JWT_ENABLED = "fastddd.security.jwt.enabled";
    static final String LOCAL_AUTH_ENABLED = "fastddd.security.local-auth.enabled";

    private static final String MODE_NONE = "none";
    private static final String MODE_FEISHU_WEB = "feishu-web";

    private final Environment environment;

    public IdentityModeGuard(Environment environment) {
        this.environment = environment;
        validate();
    }

    private void validate() {
        boolean enabled = isExplicitly(ENABLED, "true");

        if (!enabled) {
            // 总开关 false + 子功能显式开启 = 配置错误（§4.5）
            String explicit = firstExplicitSubFeature();
            if (explicit != null) {
                throw new IllegalStateException(String.format(
                        "FastDDD identity 配置冲突：%s=false 但 %s 显式开启。"
                                + "identity 子功能只在 %s=true 时有意义；请一并开启总开关或移除该子功能配置。",
                        ENABLED, explicit, ENABLED));
            }
            // 死数据源属性守卫（关态也校验，避免带病上线）
            checkNoDeadDataSourceProperty();
            return;
        }

        String mode = environment.getProperty(LOGIN_MODE, MODE_NONE);
        if (!MODE_NONE.equals(mode) && !MODE_FEISHU_WEB.equals(mode)) {
            throw new IllegalStateException(String.format(
                    "FastDDD identity 配置错误：未知 %s=%s（W2-1 仅支持 none / feishu-web）。", LOGIN_MODE, mode));
        }

        boolean worker = isExplicitly(WORKER, "true");
        boolean cMode = MODE_FEISHU_WEB.equals(mode);

        if (cMode) {
            // §4.3 条 2：C 模式双开关必须显式关闭
            if (!isExplicitly(JWT_ENABLED, "false")) {
                throw new IllegalStateException(String.format(
                        "FastDDD identity C 模式冲突：login.mode=feishu-web 要求显式设置 %s=false"
                                + "（关闭旧 JWT 配置与令牌入口），当前未显式关闭。", JWT_ENABLED));
            }
            if (!isExplicitly(LOCAL_AUTH_ENABLED, "false")) {
                throw new IllegalStateException(String.format(
                        "FastDDD identity C 模式冲突：login.mode=feishu-web 要求显式设置 %s=false"
                                + "（关闭框架本地认证族），当前未显式关闭。", LOCAL_AUTH_ENABLED));
            }
            // §4.3 条 5：Worker 断言——worker=true 与登录互斥
            if (worker) {
                throw new IllegalStateException(String.format(
                        "FastDDD identity Worker 冲突：%s=true 时 %s 必须为 none（Worker 不装登录链/员工 provider）。",
                        WORKER, LOGIN_MODE));
            }
        } else {
            // 非 C：host-owned 无意义（宿主链声明只在 C 语境下存在）
            if (isExplicitly(HOST_OWNED, "true")) {
                throw new IllegalStateException(String.format(
                        "FastDDD identity 配置错误：%s=true 只在 login.mode=feishu-web（C 模式）下有意义，"
                                + "当前 mode=none 不存在 identity 登录链。", HOST_OWNED));
            }
            if (worker) {
                // §4.3 条 5：Worker 与 C 同语义，必须显式关闭旧 JWT 与本地认证族
                // （security-starter 的 JWT 门控无 web 条件，非 web 进程漏配即装配出孤立认证族）
                if (!isExplicitly(JWT_ENABLED, "false")) {
                    throw new IllegalStateException(String.format(
                            "FastDDD identity Worker 冲突：%s=true 要求显式设置 %s=false"
                                    + "（非 web 进程禁止旧 JWT 配置装配），当前未显式关闭。", WORKER, JWT_ENABLED));
                }
                if (!isExplicitly(LOCAL_AUTH_ENABLED, "false")) {
                    throw new IllegalStateException(String.format(
                            "FastDDD identity Worker 冲突：%s=true 要求显式设置 %s=false"
                                    + "（非 web 进程禁止框架本地认证族），当前未显式关闭。", WORKER, LOCAL_AUTH_ENABLED));
                }
            }
        }

        checkNoDeadDataSourceProperty();
    }

    /**
     * 单一数据源属性守卫（复评 RV-01）：starter 事务桥与 reference-pg 适配器<b>共用</b>
     * {@code fastddd.identity.transaction.data-source-bean} 按名绑定同一业务 DataSource；
     * {@code fastddd.identity.pg.data-source-bean} 不是有效属性（无生产代码读取），
     * 显式设置即失败并指回正确属性——防多数据源宿主按错误指引配置被静默忽略后写错库。
     */
    private void checkNoDeadDataSourceProperty() {
        if (environment.containsProperty(PG_DATA_SOURCE_BEAN)) {
            throw new IllegalStateException(String.format(
                    "FastDDD identity 配置错误：%s 不是有效属性。事务桥接与 reference-pg 适配器共用 %s"
                            + " 按名绑定同一业务 DataSource，请改用该属性。",
                    PG_DATA_SOURCE_BEAN, TX_DATA_SOURCE_BEAN));
        }
    }

    private String firstExplicitSubFeature() {
        if (isExplicitly(WORKER, "true")) {
            return WORKER + "=true";
        }
        if (isExplicitly(DIRECTORY_ENABLED, "true")) {
            return DIRECTORY_ENABLED + "=true";
        }
        if (isExplicitly(HOST_OWNED, "true")) {
            return HOST_OWNED + "=true";
        }
        if (isExplicitly(AUDIT_ACTOR_COLUMNS, "true")) {
            return AUDIT_ACTOR_COLUMNS + "=true";
        }
        if (isExplicitly(PG_INITIALIZE_SCHEMA, "true")) {
            return PG_INITIALIZE_SCHEMA + "=true";
        }
        if (isExplicitly(PG_ENSURE_AUDIT_TABLE, "true")) {
            return PG_ENSURE_AUDIT_TABLE + "=true";
        }
        String mode = environment.getProperty(LOGIN_MODE);
        if (mode != null && !MODE_NONE.equalsIgnoreCase(mode)) {
            return LOGIN_MODE + "=" + mode;
        }
        return null;
    }

    /** 与 @ConditionalOnProperty 取值比较语义一致：忽略大小写精确匹配，仅认显式配置值。 */
    private boolean isExplicitly(String property, String expected) {
        return environment.containsProperty(property)
                && expected.equalsIgnoreCase(environment.getProperty(property));
    }
}
