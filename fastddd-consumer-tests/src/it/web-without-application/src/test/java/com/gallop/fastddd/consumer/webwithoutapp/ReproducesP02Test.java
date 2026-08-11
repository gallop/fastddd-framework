package com.gallop.fastddd.consumer.webwithoutapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FRM-002 验收②：复现审计 P0-2（Web 缺 application 的当前失败）。
 *
 * <p>特征测试（characterization）：当 P0-2 缺陷存在时本测试【通过】——断言上下文启动抛出异常，
 * 且因果链中出现 CommandValidationException（GlobalExceptionHandler 对 optional 类的硬引用）。
 * 当 WEB-001 修复该缺陷（上下文可正常启动）后，assertThrows 会因"未抛异常"而失败——
 * 这是预期的翻转信号，提示把本复现用例改为正向 contextLoads。
 */
class ReproducesP02Test {

    private static final String NEEDLE = "CommandValidationException";

    @Test
    void contextFailsWithMissingCommandValidationException() {
        Throwable thrown = assertThrows(Throwable.class, () ->
                new SpringApplicationBuilder(WebWithoutApp.class)
                        .web(WebApplicationType.NONE)
                        .run(),
                "P0-2 缺陷存在时上下文应启动失败；若本断言失败（上下文成功启动），"
                + "说明 WEB-001 已修复，请把本场景改为正向 contextLoads");

        String chain = describe(thrown);
        assertTrue(chain.contains(NEEDLE),
                () -> "期望因果链引用 " + NEEDLE + "，实际因果链: " + chain);
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable c = t;
        while (c != null) {
            sb.append(c.getClass().getName())
              .append(": ").append(String.valueOf(c.getMessage())).append(" || ");
            c = c.getCause();
        }
        return sb.toString();
    }
}
