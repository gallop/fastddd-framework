package com.gallop.fastddd.application.annotation;

import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * 应用层服务注解
 *
 * 用于标记应用服务类，区别于领域服务和基础设施服务。
 * 应用服务负责协调领域对象执行用例，不包含业务逻辑。
 *
 * <p>使用示例：
 * <pre>{@code
 * @ApplicationService
 * public class OrderApplicationService {
 *     public void createOrder(CreateOrderCommand command) {
 *         // 协调领域对象执行用例
 *     }
 * }
 * }</pre>
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface ApplicationService {

    /**
     * 应用服务名称，默认为空（使用类名首字母小写）
     *
     * @return 服务名称
     */
    String value() default "";

    /**
     * 该应用服务管理的聚合根类型
     * 用于文档生成和依赖关系分析
     *
     * @return 聚合根类
     */
    Class<?> aggregate() default Object.class;

    /**
     * 应用服务描述
     *
     * @return 描述信息
     */
    String description() default "";
}
