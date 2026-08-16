package com.gallop.fastddd.agentaccess.kit;

import com.gallop.fastddd.agentaccess.spi.CallerIdentityBridge;
import com.gallop.fastddd.agentaccess.spi.VerifiedCaller;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Objects;

/**
 * Agent Access 显式装配校验器首版（MC-5：无 starter、无 AutoConfiguration、fail-fast）。
 *
 * <p>在全部单例实例化完成后校验安全关键治理 Bean 的装配事实，违例即启动失败并指名：</p>
 * <ol>
 *   <li><b>必需 Bean 缺失</b>：宿主必须显式提供 {@link VerifiedCaller} 与
 *       {@link CallerIdentityBridge}（首版清单；后续任务按编排落地扩充），缺一不可；</li>
 *   <li><b>条件化/越权声明</b>：必需 Bean 的声明（类、工厂方法，或外围
 *       {@code @Configuration} 配置类）携带 {@code @Conditional} 家族（含
 *       Spring Boot 的 {@code @ConditionalOnMissingBean} 等全部 meta 注解）或
 *       {@code @Primary} 即失败——安全关键治理 Bean 禁止被条件化替换或声明为优先覆盖。</li>
 * </ol>
 *
 * <p>宿主显式注册本校验器（非自动配置）；清单可通过带参构造器定制，定制只允许
 * <b>扩充</b>治理类型，不允许收窄到空来绕过校验（空清单构造即失败）。</p>
 */
public final class AgentAccessAssemblyValidator implements SmartInitializingSingleton {

    /** 首版必需 Bean 清单：Agent 路径唯一 Caller 构造入口 + 人工/SystemJob 桥接 Port。 */
    public static final List<Class<?>> DEFAULT_REQUIRED_BEAN_TYPES =
            List.of(VerifiedCaller.class, CallerIdentityBridge.class);

    private final ConfigurableListableBeanFactory beanFactory;
    private final List<Class<?>> requiredBeanTypes;

    public AgentAccessAssemblyValidator(ConfigurableListableBeanFactory beanFactory) {
        this(beanFactory, DEFAULT_REQUIRED_BEAN_TYPES);
    }

    public AgentAccessAssemblyValidator(ConfigurableListableBeanFactory beanFactory,
                                        List<Class<?>> requiredBeanTypes) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory 必选");
        Objects.requireNonNull(requiredBeanTypes, "requiredBeanTypes 必选");
        if (requiredBeanTypes.isEmpty()) {
            throw new IllegalArgumentException("requiredBeanTypes 不得为空：校验器不允许被收窄为无操作");
        }
        List<Class<?>> removed = DEFAULT_REQUIRED_BEAN_TYPES.stream()
                .filter(type -> !requiredBeanTypes.contains(type))
                .toList();
        if (!removed.isEmpty()) {
            throw new IllegalArgumentException("requiredBeanTypes 只允许在默认清单上扩充，不得移除默认必需类型："
                    + removed);
        }
        this.requiredBeanTypes = List.copyOf(requiredBeanTypes);
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (Class<?> requiredType : requiredBeanTypes) {
            String[] names = beanFactory.getBeanNamesForType(requiredType);
            if (names.length == 0) {
                throw new IllegalStateException("agent-access 装配校验失败：必需 Bean 缺失 "
                        + requiredType.getName()
                        + "；宿主必须显式提供该治理 Bean（禁止条件化回退）");
            }
            for (String name : names) {
                assertNotConditionallyDeclared(requiredType, name);
            }
        }
    }

    private void assertNotConditionallyDeclared(Class<?> requiredType, String beanName) {
        // @Conditional 家族（含 Boot ConditionalOn* meta 注解与 @Profile）经 findAnnotationOnBean
        // 的 beanClass + factory method 双路径检出
        assertNoConditionalOrPrimary(requiredType, beanName, beanName);
        // @Bean 工厂方法声明的必需 Bean：外围 @Configuration 类上的条件化声明同样拒绝
        // （findAnnotationOnBean 查 factory bean 本身的 beanClass 注解）
        var definition = beanFactory.getBeanDefinition(beanName);
        String factoryBeanName = definition.getFactoryBeanName();
        if (factoryBeanName != null) {
            assertNoConditionalOrPrimary(requiredType, factoryBeanName, beanName);
        }
    }

    private void assertNoConditionalOrPrimary(Class<?> requiredType, String declaredName, String requiredBeanName) {
        var conditional = beanFactory.findAnnotationOnBean(declaredName, org.springframework.context.annotation.Conditional.class);
        if (conditional != null) {
            throw new IllegalStateException("agent-access 装配校验失败：必需 Bean [" + requiredBeanName + "] ("
                    + requiredType.getName() + ") 的声明 [" + declaredName + "] 携带 @Conditional 家族注解（"
                    + java.util.Arrays.toString(conditional.value()) + "）；安全关键治理 Bean 禁止条件化声明");
        }
        if (beanFactory.findAnnotationOnBean(declaredName, Primary.class) != null) {
            throw new IllegalStateException("agent-access 装配校验失败：必需 Bean [" + requiredBeanName + "] ("
                    + requiredType.getName() + ") 的声明 [" + declaredName + "] 携带 @Primary；安全关键治理 Bean 禁止优先覆盖装配");
        }
    }
}
