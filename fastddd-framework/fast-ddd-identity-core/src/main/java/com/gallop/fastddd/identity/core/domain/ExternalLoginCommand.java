package com.gallop.fastddd.identity.core.domain;

import java.util.Map;

/**
 * 外部登录命令：外部登录提供者的不透明凭证载体（W2-1 契约桩用固定凭证；
 * W2-2 飞书适配为授权码 + state 等参数）。
 *
 * @author gallop
 * @since 1.1.0
 */
public record ExternalLoginCommand(Map<String, String> credentials) {

    public ExternalLoginCommand {
        credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
    }

    public String credential(String name) {
        return credentials.get(name);
    }
}
