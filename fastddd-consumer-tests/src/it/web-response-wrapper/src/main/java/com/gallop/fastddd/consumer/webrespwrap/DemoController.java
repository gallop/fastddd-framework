package com.gallop.fastddd.consumer.webrespwrap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 最小控制器：返回 {@link DemoResponse}。与 web-response-default-off 同形，
 * 区别仅在 application.yml 是否 opt-in。
 *
 * @author gallop
 * @since 2026-08-12
 */
@RestController
public class DemoController {

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
