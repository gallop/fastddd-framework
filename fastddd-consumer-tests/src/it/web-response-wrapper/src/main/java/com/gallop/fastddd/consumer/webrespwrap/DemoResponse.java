package com.gallop.fastddd.consumer.webrespwrap;

import java.time.LocalDateTime;

/**
 * 演示返回体：Long + LocalDateTime + String。
 *
 * @author gallop
 * @since 2026-08-12
 */
public class DemoResponse {

    private Long id;
    private String name;
    private LocalDateTime time;

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

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }
}
