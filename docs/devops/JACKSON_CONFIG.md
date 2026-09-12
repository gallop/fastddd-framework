# Spring Boot Jackson 配置开发规范

> 基于实际踩坑经验总结，避免类似问题再次发生
>
> 文档版本：1.0.0
> 更新日期：2026-02-28
> 作者：FastDDD Team

---

## 一、问题背景

### 1.1 问题描述

在 FastDDD 框架中开发了 `FastDddJacksonAutoConfiguration` 自动配置类，通过 `Jackson2ObjectMapperBuilderCustomizer` 配置：
- LocalDateTime/LocalDate/LocalTime 的时间格式化
- Long 类型转 String（防止前端 JS 精度丢失）

配置类已正确加载（日志显示配置被应用），但接口返回的时间仍然是数组格式，Long 类型仍然是数字。

### 1.2 期望输出 vs 实际输出

| 字段 | 期望输出 | 实际输出（修复前） |
|------|----------|-------------------|
| localDateTime | `"2026-02-28 09:50:16"` | `[2026,2,28,9,50,16,123456789]` |
| localDate | `"2026-02-28"` | `[2026,2,28]` |
| localTime | `"09:50:16"` | `[9,50,16,123456789]` |
| longValue | `"9007199254740992"` | `9007199254740992` |

---

## 二、根因分析

### 2.1 问题根源

**在 `fast-ddd-event` 模块的 `OutboxEventConfiguration.java` 中，直接创建并注册了 `ObjectMapper` Bean：**

```java
@Configuration
public class OutboxEventConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;  // ❌ 这会破坏 Spring Boot 的 Jackson 自动配置！
    }
}
```

### 2.2 为什么会破坏自动配置？

Spring Boot 的自动配置遵循"用户定义优先"原则：

```
┌─────────────────────────────────────────────────────────────┐
│ Spring Boot Bean 加载优先级                                  │
├─────────────────────────────────────────────────────────────┤
│ 1. 用户定义的 @Bean（最高优先级）                             │
│ 2. @Import 导入的 Bean                                       │
│ 3. @AutoConfiguration 自动配置的 Bean（最低优先级）           │
└─────────────────────────────────────────────────────────────┘
```

当 `OutboxEventConfiguration` 中的 `objectMapper()` 方法被扫描到后：
1. Spring Boot 发现已存在 `ObjectMapper` Bean
2. `JacksonAutoConfiguration` 被跳过（因为有 `@ConditionalOnMissingBean`）
3. `Jackson2ObjectMapperBuilderCustomizer` 虽然被创建，但已经无法影响已存在的 `ObjectMapper`

### 2.3 问题隐蔽性

这个问题非常隐蔽，因为：
- 配置类日志显示正常加载
- `@ConditionalOnMissingBean` 看起来很安全
- 模块加载顺序不确定，可能在不同环境下表现不同

---

## 三、排查步骤（标准流程）

当 Jackson 配置不生效时，按以下顺序排查：

### 3.1 嫌疑人一：@EnableWebMvc 或 WebMvcConfigurationSupport（80% 概率）

**检查命令：**
```bash
# 全局搜索
grep -r "@EnableWebMvc" --include="*.java"
grep -r "extends WebMvcConfigurationSupport" --include="*.java"
```

**问题代码：**
```java
// ❌ 绝对不要这样写！
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer { }

// ❌ 这也会破坏自动配置！
@Configuration
public class WebConfig extends WebMvcConfigurationSupport { }
```

**正确做法：**
```java
// ✅ 只实现接口，不要加 @EnableWebMvc
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // 重写 addInterceptors, addCorsMappings 等方法
}
```

### 3.2 嫌疑人二：直接 new ObjectMapper() 注册为 Bean（本次问题）

**检查命令：**
```bash
# 搜索所有 @Bean ObjectMapper
grep -r "@Bean" --include="*.java" -A 5 | grep -B 5 "ObjectMapper"
grep -r "new ObjectMapper()" --include="*.java"
```

**问题代码：**
```java
// ❌ 破坏自动配置
@Bean
public ObjectMapper objectMapper() {
    return new ObjectMapper();
}
```

**正确做法：**
```java
// ✅ 方案一：删除这个 Bean，使用 Spring Boot 自动配置

// ✅ 方案二：如果确实需要自定义，注入 builder
@Bean
@Primary
public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.createXmlMapper(false).build();
}
```

### 3.3 嫌疑人三：Fastjson/Gson 抢夺 HttpMessageConverter

**检查命令：**
```bash
grep -r "FastJson" --include="*.java"
grep -r "fastjson" pom.xml
```

**问题：** 如果使用了 Fastjson 的 `HttpMessageConverter`，Controller 的出入参根本不经过 Jackson。

### 3.4 嫌疑人四：实体类局部注解覆盖全局配置

**检查代码：**
```java
public class User {
    // ❌ 局部注解会覆盖全局配置
    @JsonFormat(pattern = "yyyy/MM/dd")
    private LocalDateTime createTime;

    @JsonSerialize(using = NumberSerializer.class)
    private Long id;
}
```

**解决：** 移除测试字段上的局部注解。

### 3.5 嫌疑人五：AutoConfiguration.imports 文件问题

**检查清单：**
- [ ] 文件名必须是 `org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] 必须放在 `src/main/resources/META-INF/spring/` 目录
- [ ] 内容不能有前后空格，每行一个类名

---

## 四、Jackson 配置最佳实践

### 4.1 推荐方案：Jackson2ObjectMapperBuilderCustomizer

```java
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@EnableConfigurationProperties(JacksonProperties.class)
@ConditionalOnProperty(prefix = "fastddd.jackson", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class FastDddJacksonAutoConfiguration {

    private final JacksonProperties properties;

    public FastDddJacksonAutoConfiguration(JacksonProperties properties) {
        this.properties = properties;
    }

    /**
     * 使用 Jackson2ObjectMapperBuilderCustomizer 增强，不覆盖原生配置
     *
     * 关键点：
     * 1. 使用 @Order(Ordered.LOWEST_PRECEDENCE) 确保最后执行
     * 2. 使用 builder.modulesToInstall() 安装自定义模块
     * 3. 禁用 WRITE_DATES_AS_TIMESTAMPS
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public Jackson2ObjectMapperBuilderCustomizer fastDddJacksonCustomizer() {
        return builder -> {
            // 1. 禁用时间戳格式
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            // 2. Long 转 String
            if (properties.isLongToString()) {
                builder.serializerByType(Long.class, ToStringSerializer.instance);
                builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            }

            // 3. 自定义时间格式
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
                properties.getDateTimeFormat());

            JavaTimeModule customModule = new JavaTimeModule();
            customModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(dateTimeFormatter));
            customModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(dateTimeFormatter));
            // ... 其他类型

            builder.modulesToInstall(customModule);
        };
    }
}
```

### 4.2 配置属性类

```java
@ConfigurationProperties(prefix = "fastddd.jackson")
public class JacksonProperties {

    /** 是否启用自定义配置 */
    private boolean enabled = true;

    /** Long 转 String（防止 JS 精度丢失） */
    private boolean longToString = true;

    /** 枚举序列化模式 */
    private EnumSerializationMode enumSerialization = EnumSerializationMode.TO_STRING;

    /** LocalDateTime 格式 */
    private String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

    /** LocalDate 格式 */
    private String dateFormat = "yyyy-MM-dd";

    /** LocalTime 格式 */
    private String timeFormat = "HH:mm:ss";

    // getters and setters...
}
```

### 4.3 枚举序列化模式

```java
public enum EnumSerializationMode {
    /** 使用 ordinal() 值（数字：0, 1, 2...）- Jackson 默认 */
    ORDINAL,

    /** 使用 name() 值（字符串："MALE", "FEMALE"...）- 推荐 */
    TO_STRING,

    /** 使用 toString() 值（如果枚举重写了 toString()） */
    TO_STRING_METHOD
}
```

### 4.4 配置文件示例

```yaml
# application.yml
fastddd:
  jackson:
    enabled: true
    long-to-string: true
    # 枚举序列化方式：ORDINAL(数字)、TO_STRING(枚举名称-推荐)、TO_STRING_METHOD(toString方法)
    enum-serialization: TO_STRING
    date-time-format: "yyyy-MM-dd HH:mm:ss"
    date-format: "yyyy-MM-dd"
    time-format: "HH:mm:ss"

# Spring Boot 原生配置仍然生效！
spring:
  jackson:
    time-zone: Asia/Shanghai
    default-property-inclusion: non_null
```

---

## 五、开发规范总结

### 5.1 禁止事项

| 禁止操作 | 原因 | 替代方案 |
|---------|------|---------|
| `@EnableWebMvc` | 破坏 WebMvc 自动配置 | 只实现 `WebMvcConfigurer` |
| `extends WebMvcConfigurationSupport` | 同上 | 同上 |
| `@Bean public ObjectMapper()` | 破坏 Jackson 自动配置 | 使用 `Jackson2ObjectMapperBuilderCustomizer` |
| `new ObjectMapper()` 作为静态变量 | 绕过 Spring 配置 | 注入 Spring 管理的 ObjectMapper |

### 5.2 推荐事项

| 推荐操作 | 说明 |
|---------|------|
| 使用 `Jackson2ObjectMapperBuilderCustomizer` | 非侵入式增强，保留原生配置 |
| 添加 `@Order(Ordered.LOWEST_PRECEDENCE)` | 确保自定义配置最后执行 |
| 使用 `@ConditionalOnProperty` | 允许用户禁用自定义配置 |
| 配置项使用 `@ConfigurationProperties` | 支持外部化配置 |

### 5.3 代码审查检查清单

在 Code Review 时，检查以下内容：

- [ ] 是否有 `@EnableWebMvc` 注解
- [ ] 是否有 `extends WebMvcConfigurationSupport`
- [ ] 是否有 `@Bean` 创建 `ObjectMapper`
- [ ] 是否有 `new ObjectMapper()` 作为类成员变量
- [ ] 是否引入了 Fastjson 并配置了 HttpMessageConverter
- [ ] AutoConfiguration.imports 文件是否正确

---

## 六、调试技巧

### 6.1 终极调试类

当配置不生效时，添加以下调试类快速定位问题：

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

@Component
public class JacksonDebugRunner implements ApplicationRunner {

    private final ObjectMapper objectMapper;
    private final RequestMappingHandlerAdapter handlerAdapter;

    public JacksonDebugRunner(ObjectMapper objectMapper,
                              RequestMappingHandlerAdapter handlerAdapter) {
        this.objectMapper = objectMapper;
        this.handlerAdapter = handlerAdapter;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("================ JACKSON DEBUG ================");

        // 1. 检查 Long 序列化器
        boolean isLongRegistered = objectMapper.getSerializerProvider()
            .findValueSerializer(Long.class, null)
            .getClass().getSimpleName().contains("ToStringSerializer");
        System.out.println("1. Long转String生效: " + isLongRegistered);

        // 2. 检查时间模块
        boolean hasTimeModule = objectMapper.getRegisteredModuleIds().stream()
            .anyMatch(id -> id.toString().contains("JavaTimeModule"));
        System.out.println("2. JavaTimeModule注册: " + hasTimeModule);

        // 3. 打印 MessageConverters
        System.out.println("3. HttpMessageConverters:");
        handlerAdapter.getMessageConverters().forEach(c ->
            System.out.println("   - " + c.getClass().getName()));

        System.out.println("================================================");
    }
}
```

### 6.2 结果分析

| 检查项 | 结果 | 问题定位 |
|--------|------|---------|
| Long转String生效=false | Customizer 未执行 | 检查是否有其他 ObjectMapper Bean |
| HttpMessageConverters 含 FastJson | 使用了 Fastjson | 配置 Fastjson 或移除 |
| 一切正常但接口返回不对 | 局部注解覆盖 | 检查实体类 @JsonFormat 等注解 |

---

## 七、参考资料

- [Spring Boot 官方文档 - Jackson](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.spring-mvc.customize-jackson-objectmapper)
- [Spring Framework - Jackson2ObjectMapperBuilderCustomizer](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/jackson/Jackson2ObjectMapperBuilderCustomizer.html)
- FastDDD Jackson 内部排查清单（已移至仓外私有档案，不随开源仓发布）
