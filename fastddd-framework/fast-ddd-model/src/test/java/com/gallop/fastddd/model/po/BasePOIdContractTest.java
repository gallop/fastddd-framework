package com.gallop.fastddd.model.po;

import com.baomidou.mybatisplus.annotation.TableId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ID-003 特征测试：锁定 {@link BasePO} / {@link VersionedPO} 重构后的强类型 ID 契约。
 *
 * <p>本测试在 ID-003 重构后验证<b>新契约</b>（取代 ID-001 刻画的弱类型现状）：
 * <ul>
 *   <li>{@code BasePO.id} 类型为 {@code Long}（不再是 {@code Serializable}）——消费方无需强转，
 *       MyBatis-Plus 内置 {@code LongTypeHandler} 处理主键读写。</li>
 *   <li>{@code BasePO.id} 字段<b>不</b>携带 {@code @TableId}——生成策略交由全局配置
 *       {@code id-type}（默认 {@code assign_id}），修复 ID-001 锁定的"字段注解覆盖全局配置"矛盾。</li>
 *   <li>{@code setId} 收窄为 {@code Long}：此前可塞入 String/UUID 的弱类型缺陷被编译期关闭。</li>
 *   <li>审计字段已上移到 {@link AuditedPO}，{@code BasePO} 仅额外承载主键。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class BasePOIdContractTest {

    /** 最小 PO 子类（BasePO 为抽象类，需具体子类才能实例化）。 */
    static class SamplePO extends BasePO {
        private String name;

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }
    }

    static class SampleVersionedPO extends VersionedPO {
        private String code;

        public String getCode() { return code; }

        public void setCode(String code) { this.code = code; }
    }

    @Nested
    @DisplayName("getId 契约：返回强类型 Long（消费方无需强转）")
    class GetIdContract {

        @Test
        @DisplayName("getId() 返回类型为 Long，不再是 Serializable")
        void getId_returnType_isLong() throws NoSuchMethodException {
            Method getId = BasePO.class.getMethod("getId");
            // 新契约：返回 Long——此前所有 (Long) x.getId() 强转的根源已消除
            assertThat(getId.getReturnType()).isEqualTo(Long.class);
        }

        @Test
        @DisplayName("getId/setId 强类型 Long 往返")
        void getId_setId_typedLongRoundTrip() {
            SamplePO po = new SamplePO();
            assertThat(po.getId()).isNull();
            po.setId(1L);
            assertThat(po.getId()).isEqualTo(1L).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("setId 拒绝非 Long（编译期关闭弱类型——String/UUID 不再可塞入）")
        void setId_rejectsNonLong() {
            SamplePO po = new SamplePO();
            // 编译期类型安全： setId(String) / setId(UUID) 不再存在
            assertThatThrownBy(() -> SamplePO.class.getMethod("setId", String.class))
                    .isInstanceOf(NoSuchMethodException.class);
            assertThatThrownBy(() -> SamplePO.class.getMethod("setId", java.util.UUID.class))
                    .isInstanceOf(NoSuchMethodException.class);
        }
    }

    @Nested
    @DisplayName("ID 策略：BasePO.id 不携带字段级 @TableId（交由全局 id-type 决定）")
    class IdStrategyAnnotation {

        @Test
        @DisplayName("BasePO.id 字段不携带 @TableId（修复字段注解覆盖全局配置的矛盾）")
        void idField_hasNoTableIdAnnotation() throws NoSuchFieldException {
            Field idField = BasePO.class.getDeclaredField("id");
            TableId tableId = idField.getAnnotation(TableId.class);
            // 新契约：字段级 @TableId 已移除——策略由全局 id-type（默认 assign_id）决定，
            // 子类需要不同策略时自行重声明 id + @TableId。修复 ID-001/ADR-001 §M1 的矛盾。
            assertThat(tableId).as("BasePO.id 不应携带字段级 @TableId").isNull();
        }

        @Test
        @DisplayName("id 字段类型为 Long（对应 BIGINT 列）")
        void idField_typeIsLong() throws NoSuchFieldException {
            Field idField = BasePO.class.getDeclaredField("id");
            assertThat(idField.getType()).isEqualTo(Long.class);
        }
    }

    @Nested
    @DisplayName("审计字段：已上移到 AuditedPO，BasePO 不直接声明")
    class AuditFieldsExtraction {

        @Test
        @DisplayName("审计字段在 AuditedPO 声明，BasePO 继承")
        void auditFields_declaredOnAuditedPO() throws NoSuchFieldException {
            // 审计字段定义在 AuditedPO
            assertThat(AuditedPO.class.getDeclaredField("createTime")).isNotNull();
            assertThat(AuditedPO.class.getDeclaredField("updateTime")).isNotNull();
            assertThat(AuditedPO.class.getDeclaredField("createUser")).isNotNull();
            assertThat(AuditedPO.class.getDeclaredField("updateUser")).isNotNull();
            assertThat(AuditedPO.class.getDeclaredField("deleted")).isNotNull();
            // BasePO 自身不再声明这些字段（继承自 AuditedPO）
            assertThat(fieldOrNull(BasePO.class, "createTime")).isNull();
            assertThat(fieldOrNull(BasePO.class, "deleted")).isNull();
        }

        @Test
        @DisplayName("AuditedPO 不包含 id；BasePO 在 AuditedPO 之上添加 id")
        void auditedPO_hasNoId_basePOAddsId() throws NoSuchFieldException {
            assertThat(fieldOrNull(AuditedPO.class, "id")).isNull();
            assertThat(BasePO.class.getDeclaredField("id")).isNotNull();
        }

        private Field fieldOrNull(Class<?> clazz, String name) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                return null;
            }
        }
    }

    @Nested
    @DisplayName("equals/hashCode：未声明 @EqualsAndHashCode，退化为同一性比较")
    class EqualsHashCodeContract {

        @Test
        @DisplayName("两个相同 id 的 PO 实例不相等（对象同一性）")
        void equals_isIdentityBased() {
            SamplePO a = new SamplePO();
            SamplePO b = new SamplePO();
            a.setId(1L);
            b.setId(1L);
            a.setName("x");
            b.setName("x");
            // 持久化对象不按字段值相等——退化为 Object 同一性（与领域侧 DomainEntity 身份等价是两套并行契约）
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("同一实例自等")
        void equals_selfEquality() {
            SamplePO a = new SamplePO();
            assertThat(a).isEqualTo(a);
        }
    }

    @Nested
    @DisplayName("VersionedPO：在 BasePO 基础上增加 @Version，继承 Long id")
    class VersionedPoContract {

        @Test
        @DisplayName("VersionedPO.version 字段携带 @Version")
        void versionField_hasVersionAnnotation() throws NoSuchFieldException {
            Field versionField = VersionedPO.class.getDeclaredField("version");
            assertThat(versionField.getAnnotation(com.baomidou.mybatisplus.annotation.Version.class))
                    .as("VersionedPO.version 必须有 @Version").isNotNull();
        }

        @Test
        @DisplayName("VersionedPO 继承 BasePO 的强类型 Long id")
        void versionedPo_inheritsLongId() {
            SampleVersionedPO po = new SampleVersionedPO();
            po.setId(1L);
            assertThat(po.getId()).isInstanceOf(Long.class).isEqualTo(1L);
        }
    }
}
