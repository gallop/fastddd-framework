package com.gallop.fastddd.web.openapi;

import com.gallop.fastddd.web.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WEB-002 Phase C 测试用控制器：覆盖「应包装」与「不应二次包装」两类端点。
 *
 * @author gallop
 * @since 2026-08-12
 */
@RestController
@RequestMapping("/openapi-test")
public class OpenApiDemoController {

    /** 应被信封包装的 POJO 返回。 */
    public static class DemoPojo {
        private Long id;
        private String name;

        public DemoPojo() {
        }

        public DemoPojo(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @GetMapping("/pojo")
    public DemoPojo pojo() {
        return new DemoPojo(1L, "x");
    }

    /** 已声明 ResponseEntity<ApiResponse<T>>——predicate 排除 ResponseEntity，不应被二次包装。 */
    @GetMapping("/entity")
    public ResponseEntity<ApiResponse<String>> entity() {
        return ResponseEntity.ok(ApiResponse.success("hello"));
    }
}
