package com.gallop.fastddd.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * FastDDD Jackson 自动配置类
 * 使用 Jackson2ObjectMapperBuilderCustomizer 增强，不覆盖 Spring Boot 原生配置
 *
 * <p>WEB-002 opt-in 门控：类级别 {@code @ConditionalOnProperty(matchIfMissing=false)}——
 * 仅当 {@code fastddd.jackson.enabled=true} 时装配（默认关）。「仅引入 fast-ddd-web」不应改写
 * 消费者既有的 Long/日期/枚举 JSON 表示；框架聚合消费者（studio/examples/archetype）显式设
 * {@code fastddd.jackson.enabled=true} 还原能力。
 *
 * <p>退让契约：customizer 为 {@link Jackson2ObjectMapperBuilderCustomizer}（增强 builder、不替换
 * {@code ObjectMapper} bean），与业务自定义 {@code @Bean ObjectMapper} 不冲突——后者触发 Spring Boot
 * {@code @ConditionalOnMissingBean} back-off，框架 customizer 对业务 bean 零影响。
 *
 * @author gallop
 * @since 2026-02-28
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@EnableConfigurationProperties(JacksonProperties.class)
@ConditionalOnProperty(prefix = "fastddd.jackson", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class FastDddJacksonAutoConfiguration {

    private final JacksonProperties properties;

    @PostConstruct
    public void init() {
        log.info("=== FastDddJacksonAutoConfiguration INITIALIZED ===");
        log.info("=== fastddd.jackson.enabled: {} ===", properties.isEnabled());
        log.info("=== fastddd.jackson.long-to-string: {} ===", properties.isLongToString());
        log.info("=== fastddd.jackson.enum-serialization: {} ===", properties.getEnumSerialization());
        log.info("=== fastddd.jackson.date-time-format: {} ===", properties.getDateTimeFormat());
    }

    /**
     * 自定义 Jackson 配置
     * 使用 Jackson2ObjectMapperBuilderCustomizer 而不是直接创建 ObjectMapper Bean
     * 这样可以保留 Spring Boot 原生的 spring.jackson.* 配置
     *
     * 使用 @Order(Ordered.LOWEST_PRECEDENCE) 确保我们的配置最后应用
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public Jackson2ObjectMapperBuilderCustomizer fastDddJacksonCustomizer() {
        log.info("=== FastDddJacksonAutoConfiguration: Applying Jackson configuration ===");
        log.info("=== fastddd.jackson.long-to-string: {} ===", properties.isLongToString());
        log.info("=== fastddd.jackson.enum-serialization: {} ===", properties.getEnumSerialization());
        log.info("=== fastddd.jackson.date-time-format: {} ===", properties.getDateTimeFormat());

        return builder -> {
            // 1. 禁用 WRITE_DATES_AS_TIMESTAMPS，确保日期以字符串格式输出
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            // 2. 配置 Long 转 String（防止前端 JS 精度丢失）
            if (properties.isLongToString()) {
                builder.serializerByType(Long.class, ToStringSerializer.instance);
                builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
                log.info("=== Long to String serializer configured ===");
            }

            // 3. 配置枚举序列化方式
            configureEnumSerialization(builder);

            // 4. 创建自定义的 JavaTimeModule 来覆盖默认的时间序列化
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(properties.getDateTimeFormat());
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(properties.getDateFormat());
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(properties.getTimeFormat());

            JavaTimeModule customJavaTimeModule = new JavaTimeModule();
            // 自定义 LocalDateTime 序列化/反序列化
            customJavaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
            customJavaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));
            // 自定义 LocalDate 序列化/反序列化
            customJavaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
            customJavaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));
            // 自定义 LocalTime 序列化/反序列化
            customJavaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormatter));
            customJavaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));

            // 使用 modulesToInstall 替换默认的 JavaTimeModule
            builder.modulesToInstall(customJavaTimeModule);

            log.info("=== FastDddJacksonAutoConfiguration: Jackson configuration applied successfully ===");
        };
    }

    /**
     * 配置枚举序列化方式
     */
    private void configureEnumSerialization(Jackson2ObjectMapperBuilder builder) {
        EnumSerializationMode mode = properties.getEnumSerialization();

        switch (mode) {
            case ORDINAL:
                // 使用默认行为（数字），不需要额外配置
                log.info("=== Enum serialization: ORDINAL (default) ===");
                break;

            case TO_STRING:
                // 使用枚举名称（name()）作为字符串
                // 这是推荐的方式，更加直观和可读
                builder.featuresToEnable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
                log.info("=== Enum serialization: TO_STRING (enum name) ===");
                break;

            case TO_STRING_METHOD:
                // 使用枚举的 toString() 方法返回值
                // 如果枚举重写了 toString()，则使用自定义值
                builder.featuresToEnable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
                log.info("=== Enum serialization: TO_STRING_METHOD (toString()) ===");
                break;

            default:
                log.warn("=== Unknown enum serialization mode: {}, using default ===", mode);
        }
    }
}
