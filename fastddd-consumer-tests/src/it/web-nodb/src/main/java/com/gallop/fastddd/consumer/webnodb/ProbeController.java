package com.gallop.fastddd.consumer.webnodb;

import com.gallop.fastddd.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消费者自有探针端点：验证无库形态下真实响应与全局异常行为。
 * 响应包装默认关（fastddd.web.response-wrapper.enabled 缺省 false）→ 原始 DTO 直出。
 */
@RestController
@RequestMapping("/api/probe")
public class ProbeController {

    public record ItemResponse(Long id, String name, LocalDateTime time) {
    }

    public record ItemRequest(@NotBlank(message = "name must not be blank") String name) {
    }

    @GetMapping("/item")
    public ItemResponse item() {
        return new ItemResponse(9007199254740992L, "gallop", LocalDateTime.of(2026, 9, 6, 12, 0, 0));
    }

    @PostMapping("/item")
    public Map<String, String> create(@Valid @RequestBody ItemRequest request) {
        return Map.of("accepted", request.name());
    }

    @GetMapping("/boom")
    public ItemResponse boom() {
        // (String code, String message) 构造器：errorCode=null → 按框架推导归 400，errCode 透传
        throw new BusinessException("WEB_NO_DB_PROBE", "boom from web-nodb consumer");
    }
}
