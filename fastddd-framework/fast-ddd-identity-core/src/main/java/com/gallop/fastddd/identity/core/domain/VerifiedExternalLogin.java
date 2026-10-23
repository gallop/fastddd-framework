package com.gallop.fastddd.identity.core.domain;

/**
 * 外部登录提供者验证成功的结果：经验证的外部身份 + 首次创建主体时可用的可读信息。
 *
 * <p>调用成功不等于宿主自动准入（W0-4 §1.2：user_info 无 status 字段，
 * 停用判定走错误码分类；本地准入在 T2 内复验）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public record VerifiedExternalLogin(ExternalIdentity identity, String loginName, String displayName) {
}
