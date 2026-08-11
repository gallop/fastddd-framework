package com.gallop.fastddd.consumer.webwithoutapp;

import com.gallop.fastddd.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WEB-001 正向验收：fast-ddd-web 在【不】含 fast-ddd-application 的 classpath 下可启动。
 *
 * <p>历史（FRM-002）此处为复现 P0-2 的特征测试——断言启动抛出含
 * {@code CommandValidationException} 的异常。WEB-001 已将 CQRS 异常处理抽离至
 * {@code CqrsExceptionAdvice}（{@code @ConditionalOnClass(CommandValidationException.class)}），
 * {@link GlobalExceptionHandler} 不再引用 application 包，故 context 正常启动，特征测试翻转，
 * 此处改为正向 {@code contextLoads}。
 *
 * <p>断言：上下文启动成功 + application-free 的 {@link GlobalExceptionHandler} 装配。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = WebWithoutApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WebWithoutAppStartsTest {

    @Autowired
    ApplicationContext ctx;

    @Test
    void contextStartsWithoutApplicationLayer() {
        assertThat(ctx).isNotNull();
        // web 的全局异常处理器装配成功，证明 web 对 application 的硬引用已消除
        assertThat(ctx.getBean(GlobalExceptionHandler.class))
                .as("GlobalExceptionHandler 在无 application 时应正常装配")
                .isNotNull();
    }
}
