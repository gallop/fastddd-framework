package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import com.gallop.fastddd.web.idcodec.IdCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ID-005 特征测试：锁定 {@link AbstractBaseController#parseId(String)} 基于 {@link IdCodec} 的
 * 类型化解析行为，取代历史上 {@code resolveId(String)} 对 MyBatis-Plus {@code IdType} 的"DB 生成策略
 * → Java 值类型"猜测（ADR-001 §2.6）。
 *
 * <p>核心不变量：主键的目标 Java 类型由控制器声明的 {@code <ID>} 泛型参数决定，与底层表的
 * {@code IdType}（ASSIGN_ID / AUTO / INPUT / ...）完全无关。本测试在无 Spring、无 MyBatis-Plus
 * TableInfo 注册的环境下精确刻画——不再依赖 {@code TableInfoHelper.initTableInfo}，因为 codec
 * 只读声明的类型实参。</p>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class AbstractBaseControllerIdResolutionTest {

    /** Long 主键实体（历史上对应 ASSIGN_ID/AUTO）。 */
    static class LongEntity extends BasePO<Long> {
    }

    /** String 业务主键实体（历史上对应 INPUT）。 */
    static class StringEntity extends BasePO<String> {
    }

    /** UUID 主键实体（历史上对应 ASSIGN_UUID）。 */
    static class UuidEntity extends BasePO<UUID> {
    }

    /** 无内置 codec 的主键类型——刻画"业务自定义类型须覆写 idCodec()"门禁。 */
    static class IntEntity extends BasePO<Integer> {
    }

    /** 每个实体一个具体控制器子类：使 resolveTypeArgument 经参数化超类解析到实参与主键类型。 */
    static class LongController
            extends AbstractBaseController<IService<LongEntity, Long>, LongEntity, Long> {
    }

    static class StringController
            extends AbstractBaseController<IService<StringEntity, String>, StringEntity, String> {
    }

    static class UuidController
            extends AbstractBaseController<IService<UuidEntity, UUID>, UuidEntity, UUID> {
    }

    static class IntController
            extends AbstractBaseController<IService<IntEntity, Integer>, IntEntity, Integer> {
    }

    @Nested
    @DisplayName("Long 主键：数字串解析为 Long")
    class LongIdResolution {

        @Test
        @DisplayName("数字串解析为 Long 实例")
        void numericParsesToLong() {
            LongController c = new LongController();
            Serializable resolved = c.parseId("12345");
            assertThat(resolved).isEqualTo(12345L).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("非数字串抛 IllegalArgumentException（类型化错误，非低级 NumberFormatException）")
        void nonNumericThrows() {
            LongController c = new LongController();
            assertThatThrownBy(() -> c.parseId("not-a-number"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非法数值 ID");
        }

        @Test
        @DisplayName("空串 / null 抛 IllegalArgumentException")
        void blankThrows() {
            LongController c = new LongController();
            assertThatThrownBy(() -> c.parseId("")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> c.parseId(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("format：Long → 字符串双向回环")
        void formatRoundTrip() {
            LongController c = new LongController();
            assertThat(c.idCodec().format(12345L)).isEqualTo("12345");
            assertThat(c.idCodec().format(null)).isNull();
        }
    }

    @Nested
    @DisplayName("String 业务主键：任意串原样返回")
    class StringIdResolution {

        @Test
        @DisplayName("业务键原样返回 String")
        void returnsRawString() {
            StringController c = new StringController();
            Serializable resolved = c.parseId("ORD-2026-0001");
            assertThat(resolved).isEqualTo("ORD-2026-0001").isInstanceOf(String.class);
        }

        @Test
        @DisplayName("即使是数字也返回 String（不解析为 Long）——ID 类型由 <ID> 决定，非 IdType")
        void numericStillReturnsString() {
            StringController c = new StringController();
            Serializable resolved = c.parseId("12345");
            assertThat(resolved).isEqualTo("12345").isInstanceOf(String.class);
        }

        @Test
        @DisplayName("空串 / null 抛 IllegalArgumentException")
        void blankThrows() {
            StringController c = new StringController();
            assertThatThrownBy(() -> c.parseId("")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> c.parseId(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("UUID 主键：合法串解析为 UUID")
    class UuidIdResolution {

        @Test
        @DisplayName("合法 UUID 串解析为 UUID 实例")
        void validUuidParses() {
            UuidController c = new UuidController();
            String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
            Serializable resolved = c.parseId(uuidStr);
            assertThat(resolved).isInstanceOf(UUID.class).isEqualTo(UUID.fromString(uuidStr));
        }

        @Test
        @DisplayName("非法 UUID 串抛 IllegalArgumentException")
        void invalidUuidThrows() {
            UuidController c = new UuidController();
            assertThatThrownBy(() -> c.parseId("not-a-uuid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非法 UUID ID");
        }

        @Test
        @DisplayName("format：UUID → 字符串双向回环")
        void formatRoundTrip() {
            UuidController c = new UuidController();
            UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            assertThat(c.idCodec().format(id)).isEqualTo(id.toString());
            assertThat(c.idCodec().format(null)).isNull();
        }
    }

    @Nested
    @DisplayName("类型解析与 codec 装配")
    class TypeResolution {

        @Test
        @DisplayName("idCodec().getIdType() 与声明的 <ID> 一致")
        void idTypeMatchesDeclared() {
            assertThat(new LongController().idCodec().getIdType()).isEqualTo(Long.class);
            assertThat(new StringController().idCodec().getIdType()).isEqualTo(String.class);
            assertThat(new UuidController().idCodec().getIdType()).isEqualTo(UUID.class);
        }

        @Test
        @DisplayName("entityClass() 推断到声明的实体 Class")
        void entityClassResolved() {
            assertThat(new LongController().entityClass()).isEqualTo(LongEntity.class);
            assertThat(new StringController().entityClass()).isEqualTo(StringEntity.class);
        }

        @Test
        @DisplayName("无内置 codec 的主键类型：控制器构造抛 IllegalStateException（提示覆写 idCodec()）")
        void unknownIdTypeThrowsOnConstruction() {
            // Integer 不在内置 codec 覆盖面内——刻画"业务自定义主键类型须显式注入 codec"门禁
            assertThatThrownBy(IntController::new)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("无内置 IdCodec")
                    .hasMessageContaining("idCodec()");
        }
    }
}
