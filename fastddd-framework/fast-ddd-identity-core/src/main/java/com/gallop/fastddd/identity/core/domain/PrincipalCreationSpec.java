package com.gallop.fastddd.identity.core.domain;

/**
 * 首次登录创建主体的规格（v0.2 §8.2：绑定缺失时按本规格创建，不自动从目录镜像推断可登录主体）。
 *
 * @author gallop
 * @since 1.1.0
 */
public record PrincipalCreationSpec(String loginName, String displayName) {
}
