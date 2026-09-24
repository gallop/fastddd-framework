package com.gallop.fastddd.identity.spring;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * C/Worker 最终 Bean/路由检查（v0.2 §4.5，W1-b）：单例全部装配完成后复核——
 *
 * <ul>
 *   <li>Worker 形态断言：worker=true 与 web 运行形态冲突即失败；identity 启用的
 *       非 web 进程必须 worker=true；</li>
 *   <li>禁用项（C 含 host-owned、Worker）：任何来源的默认用户名认证
 *       （{@link UserDetailsService}，含 Boot inMemoryUserDetailsManager）、
 *       框架本地认证族策略/控制器、旧 {@code /api/auth/**} 或验证码路由映射——
 *       存在即失败并报告来源 bean/路由；</li>
 *   <li>C 模式过滤链审计：host-owned 声明与最终链数量互证（声明却无宿主链、
 *       有宿主链却未声明，均失败）；</li>
 *   <li>C 模式必需端口：缺真实 {@code ExternalLoginProvider} 即启动失败
 *       （外部身份桩仅允许存在于测试/隔离夹具，W2-2 前宿主提供实现）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityModeValidator implements SmartInitializingSingleton {

    private static final String AUTH_STRATEGY_CLASS = "com.gallop.fastddd.security.channel.AuthStrategy";

    private final ConfigurableApplicationContext applicationContext;

    private final FastDddIdentityProperties properties;

    public IdentityModeValidator(ConfigurableApplicationContext applicationContext,
                                 FastDddIdentityProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isEnabled()) {
            return;
        }
        boolean cMode = properties.getLogin().getMode() == FastDddIdentityProperties.Login.Mode.FEISHU_WEB;
        boolean worker = properties.isWorker();
        boolean webApp = applicationContext instanceof WebApplicationContext;

        List<String> violations = new ArrayList<>();
        if (worker && webApp) {
            violations.add("fastddd.identity.worker=true 与 Servlet/Reactive web 运行形态冲突（§4.3 条 5）");
        }
        if (!worker && !webApp) {
            violations.add("非 web 进程启用 identity 须显式 fastddd.identity.worker=true（§4.3 条 5）");
        }
        if (cMode || worker) {
            checkNoLegacyAuthentication(violations);
            if (webApp) {
                checkNoLegacyRoutes(violations);
            }
        }
        if (cMode) {
            checkSecurityFilterChainAudit(violations);
            if (applicationContext.getBeansOfType(
                    com.gallop.fastddd.identity.core.port.ExternalLoginProvider.class).isEmpty()) {
                violations.add("login.mode=feishu-web 需要至少一个 ExternalLoginProvider bean（真实提供者；"
                        + "W2-1 不内置飞书适配器——W2-2 交付前由宿主提供，测试桩仅存在于测试/隔离夹具）");
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("FastDDD identity 启动检查失败（§4.5 最终 Bean/路由检查）：\n  - "
                    + String.join("\n  - ", violations));
        }
    }

    /** 默认用户名认证服务（任何来源，含 Boot inMemoryUserDetailsManager）与框架本地认证族件。 */
    private void checkNoLegacyAuthentication(List<String> violations) {
        Map<String, UserDetailsService> userDetails = applicationContext.getBeansOfType(UserDetailsService.class);
        if (!userDetails.isEmpty()) {
            violations.add("存在默认用户名认证服务 UserDetailsService（来源 bean：" + userDetails.keySet()
                    + "）——C/Worker 最终清单禁止任何来源的默认用户名认证（含 Boot inMemoryUserDetailsManager）");
        }
        for (String beanName : List.of("authController", "verificationCodeController")) {
            if (applicationContext.containsBean(beanName)) {
                violations.add("存在框架本地认证族控制器 bean '" + beanName + "'——C/Worker 禁止旧登录/验证码路由");
            }
        }
        for (String name : beanNamesFor(AUTH_STRATEGY_CLASS)) {
            violations.add("存在认证策略 bean '" + name + "'（AuthStrategy）——C/Worker 禁止框架本地认证族");
        }
    }

    /** 旧本地认证路由映射（/api/auth/login|logout|refresh|userinfo|code/send，RequestMappingHandlerMapping 权威判据）。
     *  判据只认框架前缀 /api/auth（复评 RV-07：contains("/code/") 会误伤宿主自有 /code/ 业务路由）。 */
    private void checkNoLegacyRoutes(List<String> violations) {
        try {
            Class<?> mappingType = Class.forName(
                    "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
                    false, applicationContext.getClassLoader());
            String[] names = applicationContext.getBeanNamesForType(mappingType, true, false);
            for (String name : names) {
                Object mapping = applicationContext.getBean(name);
                java.lang.reflect.Method getHandlerMethods = mapping.getClass().getMethod("getHandlerMethods");
                Map<?, ?> handlerMethods = (Map<?, ?>) getHandlerMethods.invoke(mapping);
                for (Object info : handlerMethods.keySet()) {
                    java.lang.reflect.Method getPatternValues = info.getClass().getMethod("getPatternValues");
                    @SuppressWarnings("unchecked")
                    java.util.Set<String> patterns = (java.util.Set<String>) getPatternValues.invoke(info);
                    for (String pattern : patterns) {
                        if (pattern.startsWith("/api/auth")) {
                            violations.add("存在旧本地认证路由映射 '" + pattern + "'（handler mapping '" + name
                                    + "'）——C/Worker 拒绝旧登录/刷新/验证码路径旁路");
                        }
                    }
                }
            }
        } catch (ClassNotFoundException noWebMvc) {
            // 无 Spring MVC（如 reactive/纯 filter 形态）：路由判据不存在，跳过
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("identity 旧路由检查反射失败", e);
        }
    }

    /** host-owned 声明与最终过滤链互证（§4.3 条 3：防"以为有会话防护实际没有"）。 */
    private void checkSecurityFilterChainAudit(List<String> violations) {
        Map<String, SecurityFilterChain> chains = applicationContext.getBeansOfType(SecurityFilterChain.class);
        boolean identityChainPresent = chains.containsKey("identitySecurityFilterChain");
        long hostChains = chains.keySet().stream()
                .filter(name -> !"identitySecurityFilterChain".equals(name)).count();
        boolean hostOwned = properties.getSecurity().isHostOwned();
        if (hostOwned) {
            if (identityChainPresent || hostChains == 0) {
                violations.add("fastddd.identity.security.host-owned=true 但宿主未提供 SecurityFilterChain"
                        + "（当前链：" + chains.keySet() + "）——host-owned 声明必须有宿主链承接 CSRF/会话复验责任");
            }
        } else {
            if (hostChains > 0) {
                violations.add("宿主自定义 SecurityFilterChain 存在（" + chains.keySet()
                        + "）但未声明 fastddd.identity.security.host-owned=true——会话防护责任移交宿主须显式声明");
            } else if (!identityChainPresent) {
                violations.add("C 模式无任何 SecurityFilterChain（identity 链未装配且无宿主链）");
            }
        }
    }

    private List<String> beanNamesFor(String className) {
        try {
            Class<?> type = Class.forName(className, false, applicationContext.getClassLoader());
            return List.of(applicationContext.getBeanNamesForType(type, true, false));
        } catch (ClassNotFoundException notOnClasspath) {
            return List.of();
        }
    }
}
