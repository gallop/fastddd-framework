package com.gallop.fastddd.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WEB-002 步骤 4「退让」契约测试。
 *
 * <p>退让的诚实机制：框架 Jackson customizer 是 {@code Jackson2ObjectMapperBuilderCustomizer}，
 * 仅在 Spring Boot 自动构建 {@link ObjectMapper} 时生效；当业务自定义 {@code @Bean ObjectMapper} 时，
 * Spring Boot 不再创建自动 builder（{@code @ConditionalOnMissingBean}），框架 customizer 对业务 bean 零影响。
 * 故「框架 enabled=true + 业务自有 ObjectMapper」→ 业务胜，Long 保持数值。
 *
 * <p>实现注记：业务 ObjectMapper 通过 {@link ApplicationContextRunner#withBean} 直接注册，
 * <b>禁止</b>使用嵌套 {@code @Configuration} 类——后者会被 {@code @SpringBootApplication} 的
 * 组件扫描（含测试类路径、且 static nested 类 {@code isIndependent()=true}）误收进共享
 * {@code @SpringBootTest} 上下文，污染其他测试的 ObjectMapper 装配。
 *
 * @author gallop
 * @since 2026-08-12
 */
class JacksonYieldContractTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FastDddJacksonAutoConfiguration.class,
                    JacksonAutoConfiguration.class));

    /**
     * kill switch：fastddd.jackson.enabled=false → 自动配置类不装配，customizer 不注册，Long 保持数值。
     */
    @Test
    void killSwitchShouldLeaveLongAsNumber() {
        contextRunner
                .withPropertyValues("fastddd.jackson.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("fastDddJacksonCustomizer");
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    Map<String, Object> data = new HashMap<>();
                    data.put("longValue", 9007199254740992L);
                    String json = mapper.writeValueAsString(data);
                    // 未启用 Long→string，应为数值
                    assertThat(json).contains("\"longValue\":9007199254740992");
                });
    }

    /**
     * 业务自有 ObjectMapper bean → Spring Boot back-off，框架 customizer 不触及业务 bean，Long 保持数值。
     *
     * <p>用 withBean 注册业务 ObjectMapper（而非嵌套 @Configuration），避免污染共享 @SpringBootTest 上下文。
     */
    @Test
    void businessObjectMapperBeanShouldWinOverFrameworkCustomizer() {
        contextRunner
                .withPropertyValues("fastddd.jackson.enabled=true")
                .withBean("businessObjectMapper", ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    Map<String, Object> data = new HashMap<>();
                    data.put("longValue", 9007199254740992L);
                    String json = mapper.writeValueAsString(data);
                    // 业务自有 ObjectMapper 未配 Long→string，应为数值——框架 customizer 未触及它
                    assertThat(json).contains("\"longValue\":9007199254740992");
                });
    }
}
