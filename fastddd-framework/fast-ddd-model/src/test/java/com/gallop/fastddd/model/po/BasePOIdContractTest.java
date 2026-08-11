package com.gallop.fastddd.model.po;

import com.baomidou.mybatisplus.annotation.TableId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ID-004 特征测试：锁定 {@link BasePO} 泛型化（{@code BasePO<ID extends Serializable>}）后的主键契约。
 *
 * <p>验证维度：
 * <ul>
 *   <li><b>类型绑定</b>：{@code BasePO<Long>} 的 getId/setId 为 Long，{@code BasePO<String>} 为 String，
 *       {@code BasePO<UUID>} 为 UUID——主键类型在签名层绑定，消费方无需强转。</li>
 *   <li><b>字段注解</b>：{@code BasePO.id} 不携带字段级 {@code @TableId}（ID 策略由全局 {@code id-type} 决定），
 *       修复 ID-001/ADR-001 §M1 的矛盾。</li>
 *   <li><b>审计字段</b>：上移到 {@link AuditedPO}，{@code BasePO} 仅额外承载主键。</li>
 *   <li><b>{@link VersionedPO}</b>：透传 {@code ID}，{@code @Version} 字段保留。</li>
 * </ul>
 * 编译期类型安全（错误 ID 类型在编译失败）由 {@code IServiceIdTypeCompileTest} 在 service 层另行锁定；
 * 本测试聚焦 PO 基类的运行时行为与结构契约。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class BasePOIdContractTest {

    /** Long 主键 PO（框架默认形态）。 */
    static class LongPO extends BasePO<Long> {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** String 业务键主键 PO。 */
    static class StringPO extends BasePO<String> {
        private String code;
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    /** UUID 主键 PO。 */
    static class UuidPO extends BasePO<UUID> {
    }

    /** VersionedPO 透传 Long 主键。 */
    static class VersionedLongPO extends VersionedPO<Long> {
        private String doc;
        public String getDoc() { return doc; }
        public void setDoc(String doc) { this.doc = doc; }
    }

    @Nested
    @DisplayName("类型绑定：BasePO<ID> 的 getId/setId 按 ID 类型收窄")
    class TypedIdContract {

        @Test
        @DisplayName("BasePO<Long>：getId/setId 为 Long，无需强转")
        void longPo_typedIdRoundTrip() {
            LongPO po = new LongPO();
            assertThat(po.getId()).isNull();
            po.setId(1L);
            assertThat(po.getId()).isEqualTo(1L).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("BasePO<String>：getId/setId 为 String（业务键形态）")
        void stringPo_typedIdRoundTrip() {
            StringPO po = new StringPO();
            po.setId("ORD-2026-0001");
            assertThat(po.getId()).isEqualTo("ORD-2026-0001").isInstanceOf(String.class);
        }

        @Test
        @DisplayName("BasePO<UUID>：getId/setId 为 UUID（UUID 主键形态）")
        void uuidPo_typedIdRoundTrip() {
            UuidPO po = new UuidPO();
            UUID id = UUID.randomUUID();
            po.setId(id);
            assertThat(po.getId()).isEqualTo(id).isInstanceOf(UUID.class);
        }

        @Test
        @DisplayName("VersionedPO<Long>：透传 Long 主键 + @Version 字段")
        void versionedPo_inheritsTypedId() {
            VersionedLongPO po = new VersionedLongPO();
            po.setId(42L);
            assertThat(po.getId()).isInstanceOf(Long.class).isEqualTo(42L);
            assertThat(po.getVersion()).isNotNull();
        }
    }

    @Nested
    @DisplayName("ID 策略：BasePO.id 不携带字段级 @TableId（全局 id-type 驱动）")
    class IdStrategyAnnotation {

        @Test
        @DisplayName("BasePO.id 字段不携带 @TableId（修复字段注解覆盖全局配置的矛盾）")
        void idField_hasNoTableIdAnnotation() throws NoSuchFieldException {
            Field idField = BasePO.class.getDeclaredField("id");
            // 字段级 @TableId 已移除——策略由全局 id-type（默认 assign_id）决定。
            // 子类需不同策略时自行重声明 id + @TableId(type=...)。修复 ID-001/ADR-001 §M1。
            assertThat(idField.getAnnotation(TableId.class))
                    .as("BasePO.id 不应携带字段级 @TableId").isNull();
        }
    }

    @Nested
    @DisplayName("审计字段：已上移到 AuditedPO，BasePO 仅承载主键")
    class AuditFieldsExtraction {

        @Test
        @DisplayName("审计字段在 AuditedPO 声明，BasePO 继承但不重复声明")
        void auditFields_declaredOnAuditedPO() {
            assertThat(fieldOrNull(AuditedPO.class, "createTime")).isNotNull();
            assertThat(fieldOrNull(AuditedPO.class, "updateTime")).isNotNull();
            assertThat(fieldOrNull(AuditedPO.class, "createUser")).isNotNull();
            assertThat(fieldOrNull(AuditedPO.class, "updateUser")).isNotNull();
            assertThat(fieldOrNull(AuditedPO.class, "deleted")).isNotNull();
            // BasePO 自身不再声明审计字段（继承自 AuditedPO）
            assertThat(fieldOrNull(BasePO.class, "createTime")).isNull();
            assertThat(fieldOrNull(BasePO.class, "deleted")).isNull();
        }

        @Test
        @DisplayName("AuditedPO 不含 id；BasePO 在 AuditedPO 之上添加主键")
        void auditedPO_hasNoId_basePOAddsId() {
            assertThat(fieldOrNull(AuditedPO.class, "id")).isNull();
            assertThat(fieldOrNull(BasePO.class, "id")).isNotNull();
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
            LongPO a = new LongPO();
            LongPO b = new LongPO();
            a.setId(1L);
            b.setId(1L);
            a.setName("x");
            b.setName("x");
            // 持久化对象退化为 Object 同一性（与领域侧 DomainEntity 身份等价是两套并行契约）
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("同一实例自等")
        void equals_selfEquality() {
            LongPO a = new LongPO();
            assertThat(a).isEqualTo(a);
        }
    }

    @Nested
    @DisplayName("VersionedPO.@Version 注解")
    class VersionedPoContract {

        @Test
        @DisplayName("VersionedPO.version 字段携带 @Version")
        void versionField_hasVersionAnnotation() throws NoSuchFieldException {
            Field versionField = VersionedPO.class.getDeclaredField("version");
            assertThat(versionField.getAnnotation(com.baomidou.mybatisplus.annotation.Version.class))
                    .as("VersionedPO.version 必须有 @Version").isNotNull();
        }
    }
}
