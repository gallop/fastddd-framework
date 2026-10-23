package com.gallop.fastddd.identity.core.domain;

import com.gallop.fastddd.identity.core.port.ExternalRejectionGate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W5-b 冻结守卫（W5 设计 §3.2/§3.3/§7.5；R3-P3-1/2 随本波成文钉住）：
 * 钉死词表取值域、advisory 锁类键常量与规范化锁键公式——防漂移回归。
 */
class W5bFreezeGuardTest {

    @Test
    void directoryChangeTypeVocabularyIsFrozen() {
        // 限定名四枚举（§3.2 冻结）：created/updated 归并 CHANGED、scope.updated_v3 不纳入
        assertThat(DirectoryChangeType.values()).containsExactly(
                DirectoryChangeType.PERSON_CHANGED,
                DirectoryChangeType.PERSON_DEACTIVATED,
                DirectoryChangeType.DEPARTMENT_CHANGED,
                DirectoryChangeType.DEPARTMENT_DEACTIVATED);
        assertThat(DirectoryChangeType.PERSON_CHANGED.objectType())
                .isEqualTo(DirectoryObjectType.PERSON);
        assertThat(DirectoryChangeType.PERSON_DEACTIVATED.objectType())
                .isEqualTo(DirectoryObjectType.PERSON);
        assertThat(DirectoryChangeType.DEPARTMENT_CHANGED.objectType())
                .isEqualTo(DirectoryObjectType.DEPARTMENT);
        assertThat(DirectoryChangeType.DEPARTMENT_DEACTIVATED.objectType())
                .isEqualTo(DirectoryObjectType.DEPARTMENT);
    }

    @Test
    void externalIdTypeConstantsAreFrozen() {
        assertThat(ExternalIdTypes.OPEN_ID).isEqualTo("open_id");
        assertThat(ExternalIdTypes.UNION_ID).isEqualTo("union_id");
    }

    @Test
    void advisoryLockClassConstantIsFrozen() {
        // ASCII "FDDD" = 0x46 0x44 0x44 0x44（§7.5(b) ⓪ 双参 advisory 锁的类键）。
        // 十进制注差异登记：设计 v4 :76/:181 与冻结轮执行记录 :38 所记
        // "1179874116" 系换算笔误（正确值 1,178,879,044）——十六进制冻结值
        // 0x46444444 为规范锚，本断言对钉两形态。
        assertThat(ExternalRejectionGate.ADVISORY_LOCK_CLASS).isEqualTo(0x46444444);
        assertThat(ExternalRejectionGate.ADVISORY_LOCK_CLASS).isEqualTo(1_178_879_044);
    }

    @Test
    void normalizedLockKeyIsExactFivePartFormula() {
        // R3-P3-2 冻结公式：五元组 '\n' 精确拼接——无大小写折叠、无裁剪，
        // 与 external_rejection uk 列值逐字节一致域
        ExternalIdentity identity = new ExternalIdentity(
                "feishu", "tenant-a", "app-1", "open_id", "ou_123");
        assertThat(ExternalRejectionGate.normalizedLockKey(identity))
                .isEqualTo("feishu\ntenant-a\napp-1\nopen_id\nou_123");

        // 值含分隔/大小写差异时不得折叠：不同键 → 不同规范化串（逐字节）
        ExternalIdentity other = new ExternalIdentity(
                "Feishu", "tenant-a", "app-1", "open_id", "ou_123");
        assertThat(ExternalRejectionGate.normalizedLockKey(other))
                .isNotEqualTo(ExternalRejectionGate.normalizedLockKey(identity));

        // 跨域同 externalId：id 类型不同 → 不同键
        ExternalIdentity union = new ExternalIdentity(
                "feishu", "tenant-a", "app-1", "union_id", "ou_123");
        assertThat(ExternalRejectionGate.normalizedLockKey(union))
                .isNotEqualTo(ExternalRejectionGate.normalizedLockKey(identity));
    }
}
