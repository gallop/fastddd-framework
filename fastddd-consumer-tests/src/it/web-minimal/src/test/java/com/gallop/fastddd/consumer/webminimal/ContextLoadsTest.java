package com.gallop.fastddd.consumer.webminimal;

import com.gallop.fastddd.web.exception.CqrsExceptionAdvice;
import com.gallop.fastddd.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FRM-002 验收① + WEB-001 验收②：断言最小 Web 消费者（含 fast-ddd-application）在独立
 * classpath 下 Spring 上下文启动成功，且 CQRS 异常映射仍生效。
 *
 * <p>启动即证明 fast-ddd-starter（含 web/crud/mybatis-plus + application）从本地仓库制品
 * 可解析、可装配。WEB-001 后 CQRS 异常处理位于 {@link CqrsExceptionAdvice}
 * （{@code @ConditionalOnClass(CommandValidationException.class)}）——application 在 classpath
 * 时该 advice 应装配，证明异常映射在有 application 时仍生效。
 */
@SpringBootTest(classes = WebMinimalApp.class)
class ContextLoadsTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "Spring 上下文应成功启动");
    }

    @Test
    void cqrsExceptionMappingIsActiveWhenApplicationPresent() {
        assertNotNull(applicationContext.getBean(GlobalExceptionHandler.class),
                "GlobalExceptionHandler 应装配");
        assertNotNull(applicationContext.getBean(CqrsExceptionAdvice.class),
                "CqrsExceptionAdvice 应在 application 在 classpath 时装配（WEB-001 验收②）");
    }
}

