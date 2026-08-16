package com.gallop.fastddd.agentaccess.spi;

/**
 * 必做审计义务（合成规则：求并——两侧要求审计的主题全部保留）。
 */
public record RequiredAuditObligation(String auditTopic) implements Obligation {

    public RequiredAuditObligation {
        if (auditTopic == null || auditTopic.isBlank()) {
            throw new IllegalArgumentException("auditTopic 必选且不得为空白");
        }
    }

    @Override
    public String type() {
        return "REQUIRED_AUDIT";
    }
}
