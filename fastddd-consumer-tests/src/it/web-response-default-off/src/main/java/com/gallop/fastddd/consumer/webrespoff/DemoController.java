package com.gallop.fastddd.consumer.webrespoff;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 最小控制器：返回 {@link DemoResponse}（Long id + LocalDateTime time + name）。
 * 用于机械锁定「默认关」时响应 = 原始 JSON。
 *
 * @author gallop
 * @since 2026-08-12
 */
@RestController
public class DemoController {

    /**
     * 超出 JS 安全整数（2^53 = 9007199254740992）的 Long，
     * 用于明确区分「数值」与「字符串」两种序列化形态。
     */
    public static final long BIG_LONG = 9007199254740992L;

    @GetMapping("/demo")
    public DemoResponse demo() {
        DemoResponse response = new DemoResponse();
        response.setId(BIG_LONG);
        response.setName("gallop");
        response.setTime(LocalDateTime.of(2026, 8, 12, 10, 30, 0));
        return response;
    }
}
