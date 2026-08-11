package com.gallop.fastddd.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ID-001 特征测试：锁定 {@link BasePO} / {@link VersionedPO} 当前的 ID 契约。
 *
 * <p>刻画现状（不改生产行为）：{@code BasePO.id} 为 {@link Serializable}（非 {@code Long}），
 * 字段级 {@code @TableId(ASSIGN_ID)} 硬编码、覆盖全局 id-type 配置；未声明
 * {@code @EqualsAndHashCode} 故退化为对象同一性比较。这些是 ID-003 将重构的目标行为。
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
    @DisplayName("getId 契约：返回 Serializable（弱类型，消费方需强转）")
    class GetIdContract {

        @Test
        @DisplayName("getId() 返回类型为 Serializable，非 Long")
        void getId_returnType_isSerializable() throws NoSuchMethodException {
            Method getId = BasePO.class.getMethod("getId");
            // 当前行为：返回 Serializable——所有 (Long) x.getId() 强转的根源
            assertThat(getId.getReturnType()).isEqualTo(Serializable.class);
        }

        @Test
        @DisplayName("setId 接受任意 Serializable（Long/String/UUID 均可，弱类型）")
        void setId_acceptsAnySerializable() {
            SamplePO po = new SamplePO();
            po.setId(1L);
            assertThat(po.getId()).isInstanceOf(Long.class).isEqualTo(1L);

            po.setId("abc-123");
            assertThat(po.getId()).isEqualTo("abc-123");

            UUID uid = UUID.randomUUID();
            po.setId(uid);
            assertThat(po.getId()).isEqualTo(uid);
        }
    }

    @Nested
    @DisplayName("ID 策略：字段级 @TableId(ASSIGN_ID) 硬编码（覆盖全局 id-type）")
    class IdStrategyAnnotation {

        @Test
        @DisplayName("BasePO.id 字段携带 @TableId(ASSIGN_ID)")
        void idField_hasAssignIdTableId() throws NoSuchFieldException {
            Field idField = BasePO.class.getDeclaredField("id");
            TableId tableId = idField.getAnnotation(TableId.class);
            assertThat(tableId).as("BasePO.id 必须有 @TableId").isNotNull();
            // 当前行为：硬编码 ASSIGN_ID（雪花）。字段级注解优先于全局 id-type 配置，
            // 故所有 BasePO 子类运行期策略恒为 ASSIGN_ID（见 ADR-001 §M1）。
            assertThat(tableId.type()).isEqualTo(IdType.ASSIGN_ID);
        }

        @Test
        @DisplayName("BasePO Javadoc 声称全局 id-type 可覆盖——实际字段注解优先（文档与实现漂移）")
        void documentsGlobalOverrideButFieldAnnotationWins() throws NoSuchFieldException {
            // 刻画已知文档/实现漂移：BasePO.java:22-23,37-38 Javadoc 称全局配置可覆盖，
            // 但 MyBatis-Plus 解析字段级 @TableId 优先。本断言锁定"字段级 ASSIGN_ID 存在"这一事实，
            // 全局 id-type 的实际无效性由 ID-003 重构时统一处理。
            TableId tableId = BasePO.class.getDeclaredField("id").getAnnotation(TableId.class);
            assertThat(tableId.type()).isEqualTo(IdType.ASSIGN_ID);
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
            // 当前行为：BasePO 无 @EqualsAndHashCode → Object.equals（同一性）。
            // 与 BaseEntity（字段比较）形成反向不一致——ID-003 将统一。
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
    @DisplayName("VersionedPO：在 BasePO 基础上增加 @Version")
    class VersionedPoContract {

        @Test
        @DisplayName("VersionedPO.version 字段携带 @Version")
        void versionField_hasVersionAnnotation() throws NoSuchFieldException {
            Field versionField = VersionedPO.class.getDeclaredField("version");
            assertThat(versionField.getAnnotation(Version.class))
                    .as("VersionedPO.version 必须有 @Version").isNotNull();
        }

        @Test
        @DisplayName("VersionedPO 继承 BasePO 的 Serializable id（弱类型未改变）")
        void versionedPo_inheritsSerializableId() {
            SampleVersionedPO po = new SampleVersionedPO();
            po.setId(1L);
            // 当前行为：继承的 id 仍为 Serializable
            assertThat(po.getId()).isInstanceOf(Long.class);
        }
    }
}
