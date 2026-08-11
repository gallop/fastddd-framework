package com.gallop.fastddd.common.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ID-001 特征测试：锁定 {@link BaseEntity} 当前的 ID 契约与 equals/hashCode 行为。
 *
 * <p>本测试为 characterization（刻画现状），<b>不改生产行为</b>。所有断言记录的是 1.0 当前
 * 真实行为——包括已知的脆弱点（可变 id 进入 hashCode、getId 抽象化等）。ID-002/003 重构后
 * 这些断言将被更新，届时行为变化即被显式捕获。
 *
 * <p>覆盖方案步骤 3（Long/String/UUID ID 特征）与步骤 4（equals/hashCode 可变 id 问题）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class BaseEntityIdentityContractTest {

    /** Long ID 实体（仿 studio Order 模式：显式实现抽象 getId/setId，访问继承的 protected id）。 */
    @Getter
    @Setter
    @EqualsAndHashCode(callSuper = true)
    static class LongEntity extends BaseEntity<Long> {
        @Override
        public Long getId() { return id; }

        @Override
        public void setId(Long id) { this.id = id; }
    }

    /** String ID 实体。 */
    @Getter
    @Setter
    @EqualsAndHashCode(callSuper = true)
    static class StringEntity extends BaseEntity<String> {
        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }
    }

    /** UUID ID 实体。 */
    @Getter
    @Setter
    @EqualsAndHashCode(callSuper = true)
    static class UuidEntity extends BaseEntity<UUID> {
        @Override
        public UUID getId() { return id; }

        @Override
        public void setId(UUID id) { this.id = id; }
    }

    @Nested
    @DisplayName("步骤3：Long / String / UUID ID 均由泛型承载")
    class IdGenericTypeSupport {

        @Test
        @DisplayName("Long ID：getId/setId 强类型往返")
        void longId_roundTripsTyped() {
            LongEntity e = new LongEntity();
            assertThat(e.getId()).isNull();
            e.setId(12345L);
            assertThat(e.getId()).isEqualTo(12345L);
            assertThat(e.getId()).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("String ID：getId/setId 强类型往返")
        void stringId_roundTripsTyped() {
            StringEntity e = new StringEntity();
            e.setId("ORD-2026-0001");
            assertThat(e.getId()).isEqualTo("ORD-2026-0001");
        }

        @Test
        @DisplayName("UUID ID：getId/setId 强类型往返")
        void uuidId_roundTripsTyped() {
            UuidEntity e = new UuidEntity();
            UUID uid = UUID.randomUUID();
            e.setId(uid);
            assertThat(e.getId()).isEqualTo(uid);
        }

        @Test
        @DisplayName("isNew：id 为 null 即新实体；赋值后非新")
        void isNew_reflectsIdPresence() {
            LongEntity e = new LongEntity();
            assertThat(e.isNew()).isTrue();
            e.setId(1L);
            assertThat(e.isNew()).isFalse();
        }
    }

    @Nested
    @DisplayName("步骤4：equals/hashCode 可变 id 问题（刻画当前已知缺陷）")
    class MutableIdInEqualsAndHashCode {

        @Test
        @DisplayName("BaseEntity 的 equals 包含 id：仅 id 不同的两实体不相等")
        void equals_includesIdField() {
            LongEntity a = new LongEntity();
            LongEntity b = new LongEntity();
            a.setId(1L);
            b.setId(2L);
            // 当前行为：BaseEntity @EqualsAndHashCode 涵盖 id → 不同 id 不相等
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("BaseEntity 的 equals 涵盖全部字段：相同 id 但其它字段不同仍不相等")
        void equals_includesAllFields() {
            LongEntity a = new LongEntity();
            LongEntity b = new LongEntity();
            a.setId(1L);
            b.setId(1L);
            a.setCreateUser("alice");
            b.setCreateUser("bob");
            // 当前行为：createTime/createUser 等均参与 equals
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("已知缺陷：hashCode 随 id 赋值而变化（可变字段进入 hashCode）")
        void hashCode_changesWhenIdAssigned() {
            LongEntity e = new LongEntity();
            int hashBefore = e.hashCode();
            e.setId(42L);
            int hashAfter = e.hashCode();
            // 刻画根因缺陷：id 赋值后 hashCode 改变。后果是"先放入 HashSet/HashMap 再被持久化
            // 回填 id"的实体，其查找依赖的桶由旧 hashCode 决定——查找行为变为非确定（取决于
            // 新旧 hashCode 是否落入同一桶）。ID-002 重构将改为基于 id 的稳定 hashCode。
            assertThat(hashAfter).isNotEqualTo(hashBefore);
        }

        @Test
        @DisplayName("已知缺陷：插入 HashSet 后赋 id，物理仍在集合中（容量未变不触发重哈希）")
        void entity_mutatedAfterHashSetInsertion_remainsPhysicallyPresent() {
            LongEntity e = new LongEntity();
            Set<LongEntity> set = new HashSet<>();
            set.add(e);
            e.setId(99L); // 模拟 snowflake 在 insert 时回填 id
            // 刻画缺陷的确定性一面：元素物理上仍占位（size 不变），但 contains 的正确性
            // 取决于新旧 hashCode 是否同桶——即查找契约被破坏。hashCode 变化由前一测试锁定。
            assertThat(set).hasSize(1);
            assertThat(set.iterator().next()).isSameAs(e);
        }
    }
}
