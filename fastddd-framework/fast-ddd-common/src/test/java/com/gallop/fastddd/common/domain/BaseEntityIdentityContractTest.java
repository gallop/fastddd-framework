package com.gallop.fastddd.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ID-002 特征测试：锁定 {@link DomainEntity}（经 {@link BaseEntity} 兼容门面）的稳定身份契约。
 *
 * <p>本测试在 ID-002 重构后验证<b>新契约</b>（取代 ID-001 刻画的旧缺陷行为）：
 * <ul>
 *   <li>{@code getId}/{@code setId} 由 {@link DomainEntity} 提供具体实现——<b>子类无需重复实现</b>
 *      （本测试的 fixture 为空子类，直接继承即可调用）。</li>
 *   <li>相等性只基于稳定身份与明确类型规则：同 id + 同 {@code getClass()} 才相等；瞬态实体
 *       （id 为 null）退化为引用同一性。</li>
 *   <li>{@code hashCode} 仅基于 {@code getClass()}——持久化回填 id <b>不</b>改变 hashCode，
 *       HashSet/HashMap 查找契约恢复成立（修复 ID-001 锁定的可变 id 缺陷）。</li>
 * </ul>
 *
 * <p>覆盖方案步骤 6（ID 创建时机/非空约束）、步骤 8（HashSet、相同 ID 的相等性）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class BaseEntityIdentityContractTest {

    /**
     * 空 fixture：不声明任何字段、不重写 getId/setId，证明"领域实体无需重复实现 getId/setId"。
     * 不同 fixture 仅用于参数化 ID 类型与 {@code getClass()} 区分。
     */
    static class LongEntity extends BaseEntity<Long> {
    }

    /** 同为 {@code BaseEntity<Long>} 的另一个类——用于刻画"明确类型规则"（同 id 不同类不相等）。 */
    static class AnotherLongEntity extends BaseEntity<Long> {
    }

    static class StringEntity extends BaseEntity<String> {
    }

    static class UuidEntity extends BaseEntity<UUID> {
    }

    @Nested
    @DisplayName("步骤1/6：泛型 ID 由基类承载，getId/setId 无需子类重复实现")
    class IdGenericTypeSupport {

        @Test
        @DisplayName("Long ID：继承的 getId/setId 强类型往返")
        void longId_roundTripsTyped() {
            LongEntity e = new LongEntity();
            assertThat(e.getId()).isNull();
            e.setId(12345L);
            assertThat(e.getId()).isEqualTo(12345L).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("String ID：继承的 getId/setId 强类型往返")
        void stringId_roundTripsTyped() {
            StringEntity e = new StringEntity();
            e.setId("ORD-2026-0001");
            assertThat(e.getId()).isEqualTo("ORD-2026-0001");
        }

        @Test
        @DisplayName("UUID ID：继承的 getId/setId 强类型往返")
        void uuidId_roundTripsTyped() {
            UuidEntity e = new UuidEntity();
            UUID uid = UUID.randomUUID();
            e.setId(uid);
            assertThat(e.getId()).isEqualTo(uid);
        }

        @Test
        @DisplayName("isNew：id 为 null 即新实体；赋值后非新（ID 创建时机）")
        void isNew_reflectsIdPresence() {
            LongEntity e = new LongEntity();
            assertThat(e.isNew()).isTrue();
            e.setId(1L);
            assertThat(e.isNew()).isFalse();
        }
    }

    @Nested
    @DisplayName("步骤4/8：相等性只基于稳定身份与明确类型规则")
    class StableIdentityEquality {

        @Test
        @DisplayName("同 id 同类 → 相等")
        void equals_sameIdSameClass_areEqual() {
            LongEntity a = new LongEntity();
            LongEntity b = new LongEntity();
            a.setId(1L);
            b.setId(1L);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("不同 id → 不相等")
        void equals_differentId_notEqual() {
            LongEntity a = new LongEntity();
            LongEntity b = new LongEntity();
            a.setId(1L);
            b.setId(2L);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("同 id 但不同类 → 不相等（明确类型规则：getClass() 必须相同）")
        void equals_differentClassSameId_notEqual() {
            LongEntity a = new LongEntity();
            AnotherLongEntity b = new AnotherLongEntity();
            a.setId(1L);
            b.setId(1L);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("瞬态实体（id 为 null）互不相等；仅引用同一时相等")
        void equals_transientEntities_notEqualUnlessSameReference() {
            LongEntity a = new LongEntity();
            LongEntity b = new LongEntity();
            assertThat(a).isNotEqualTo(b); // 两个未保存实体
            assertThat(a).isEqualTo(a);    // 同一引用
            assertThat(a).isEqualTo((Object) a);
        }

        @Test
        @DisplayName("瞬态与已持久化（同引用除外）不相等")
        void equals_transientVsPersisted_notEqual() {
            LongEntity transientEntity = new LongEntity();
            LongEntity persisted = new LongEntity();
            persisted.setId(5L);
            assertThat(transientEntity).isNotEqualTo(persisted);
        }

        @Test
        @DisplayName("equals 与 null、其它类型对称地返回 false")
        void equals_nullAndForeignType_returnFalse() {
            LongEntity a = new LongEntity();
            a.setId(1L);
            assertThat(a).isNotEqualTo(null);
            assertThat(a).isNotEqualTo("not-an-entity");
        }
    }

    @Nested
    @DisplayName("步骤8：稳定 hashCode——持久化回填 id 不改变 hashCode，HashSet 契约恢复")
    class StableHashCode {

        @Test
        @DisplayName("hashCode 在 id 赋值前后保持不变（修复 ID-001 可变 id 缺陷）")
        void hashCode_stableAcrossIdAssignment() {
            LongEntity e = new LongEntity();
            int hashBefore = e.hashCode();
            e.setId(42L); // 模拟 snowflake 在 insert 时回填 id
            int hashAfter = e.hashCode();
            assertThat(hashAfter).isEqualTo(hashBefore);
        }

        @Test
        @DisplayName("两个同 id 同类实例的 hashCode 相等（满足 equals ⇒ hashCode 契约）")
        void hashCode_sameForTwoInstancesWithSameId() {
            LongEntity a = new LongEntity();
            LongEntity b = new LongEntity();
            a.setId(7L);
            b.setId(7L);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("HashSet 契约恢复：先放入再回填 id，contains 仍可找到")
        void hashSet_containsWorksAfterIdAssignment() {
            LongEntity e = new LongEntity();
            Set<LongEntity> set = new HashSet<>();
            set.add(e);
            e.setId(99L); // 模拟持久化回填
            // hashCode 未变 → 元素仍在原桶 → contains 正确
            assertThat(set).contains(e);
            assertThat(set).hasSize(1);
        }

        @Test
        @DisplayName("身份语义：两个同 id 的不同实例在 HashSet 中塌缩为单一条目")
        void hashSet_twoDistinctInstancesWithSameId_collapseToOne() {
            LongEntity a = new LongEntity();
            LongEntity b = new LongEntity();
            a.setId(100L);
            b.setId(100L); // a.equals(b) → true
            Set<LongEntity> set = new HashSet<>();
            set.add(a);
            set.add(b);
            assertThat(set).hasSize(1);
        }
    }
}
