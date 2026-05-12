package com.gallop.fastddd.security.strategy;

import com.gallop.fastddd.security.channel.AuthChannel;
import lombok.Data;

import java.util.Map;

/**
 * 统一认证请求模型
 * 策略特有参数通过 extra Map 传递，避免每增加策略就加字段
 *
 * @author gallop
 * @date 2026-04-21
 */
@Data
public class AuthenticationRequest {

    /**
     * 认证策略类型：password / sms_code / email_code / wechat_mini 等。
     * 未设置时默认使用 password，避免通过 extra 字段隐式猜测认证方式。
     */
    private String type;

    private String username;
    private String password;
    private AuthChannel channel;
    private Map<String, String> extra;

    public String getExtraParam(String key) {
        return extra != null ? extra.get(key) : null;
    }
}
