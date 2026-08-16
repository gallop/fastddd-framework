package com.gallop.fastddd.agentaccess.spi;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

/**
 * 许可验证器配置（TM-20 关联：clock skew 必须有可配置上界，且上界本身有界）。
 *
 * <p>fail-closed 约束：受信 issuer 集合与期望 audience 必选非空；maxClockSkew
 * 必选且不超过 {@link #MAX_ALLOWED_CLOCK_SKEW}（禁止以「大 skew」变相放行
 * 过期许可）；时钟必选（禁止隐式系统时钟导致测试与生产语义漂移）。</p>
 *
 * @param clock                   验证用时钟（显式注入，可测试）
 * @param trustedIssuers          受信许可发行方集合（非空）
 * @param expectedAudience        本资源服务器的期望 audience（非空白）
 * @param maxClockSkew            时间窗校验允许的时钟偏移上界（正数且不超过上界）
 * @param requireSenderConfirmation 是否强制 sender binding（REMOTE_PROFILE 高风险必开；
 *                                  LOCAL_OBJECT 骨架期可为 false，升级时不得放宽）
 */
public record PermitVerifierConfig(
        Clock clock,
        Set<String> trustedIssuers,
        String expectedAudience,
        Duration maxClockSkew,
        boolean requireSenderConfirmation) {

    /** clock skew 上界的上界（治理默认 5 分钟；更大值属于配置错误而非许可宽限）。 */
    public static final Duration MAX_ALLOWED_CLOCK_SKEW = Duration.ofMinutes(5);

    public PermitVerifierConfig {
        if (clock == null) {
            throw new IllegalArgumentException("clock 必选：禁止隐式系统时钟");
        }
        if (trustedIssuers == null || trustedIssuers.isEmpty()) {
            throw new IllegalArgumentException("trustedIssuers 必选且不得为空：无受信发行方即无从验证");
        }
        trustedIssuers = Set.copyOf(trustedIssuers);
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new IllegalArgumentException("expectedAudience 必选且不得为空白");
        }
        if (maxClockSkew == null) {
            throw new IllegalArgumentException("maxClockSkew 必选");
        }
        if (maxClockSkew.isNegative() || maxClockSkew.isZero()) {
            throw new IllegalArgumentException("maxClockSkew 必须为正数");
        }
        if (maxClockSkew.compareTo(MAX_ALLOWED_CLOCK_SKEW) > 0) {
            throw new IllegalArgumentException("maxClockSkew 超过上界 " + MAX_ALLOWED_CLOCK_SKEW
                    + "：禁止以大偏移变相放行过期许可");
        }
    }
}
