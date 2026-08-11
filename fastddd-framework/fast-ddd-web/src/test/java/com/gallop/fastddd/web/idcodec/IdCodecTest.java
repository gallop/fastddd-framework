package com.gallop.fastddd.web.idcodec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ID-005：内置 {@link IdCodec} 单元测试与 {@link IdCodecs#forClass(Class)} 解析门禁。
 *
 * <p>独立于控制器刻画 codec 行为：每个内置 codec 的 parse/format/非法输入分支，
 * 以及 {@link IdCodecs} 按 {@code Class} 解析、未知类型抛 {@link IllegalStateException} 的契约。</p>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class IdCodecTest {

    @Nested
    @DisplayName("LongIdCodec")
    class LongCodec {

        private final LongIdCodec codec = new LongIdCodec();

        @Test
        void parseNumeric() {
            assertThat(codec.parse("12345")).isEqualTo(12345L);
        }

        @Test
        void parseNegative() {
            assertThat(codec.parse("-1")).isEqualTo(-1L);
        }

        @Test
        void parseNonNumericThrows() {
            assertThatThrownBy(() -> codec.parse("abc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非法数值 ID");
        }

        @Test
        void parseBlankThrows() {
            assertThatThrownBy(() -> codec.parse("")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> codec.parse(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void formatRoundTrip() {
            assertThat(codec.format(12345L)).isEqualTo("12345");
            assertThat(codec.format(null)).isNull();
        }

        @Test
        void idType() {
            assertThat(codec.getIdType()).isEqualTo(Long.class);
        }
    }

    @Nested
    @DisplayName("UuidIdCodec")
    class UuidCodec {

        private final UuidIdCodec codec = new UuidIdCodec();
        private static final String UUID_STR = "550e8400-e29b-41d4-a716-446655440000";

        @Test
        void parseValid() {
            assertThat(codec.parse(UUID_STR)).isEqualTo(UUID.fromString(UUID_STR));
        }

        @Test
        void parseInvalidThrows() {
            assertThatThrownBy(() -> codec.parse("not-a-uuid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非法 UUID ID");
        }

        @Test
        void parseBlankThrows() {
            assertThatThrownBy(() -> codec.parse("")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> codec.parse(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void formatRoundTrip() {
            UUID id = UUID.fromString(UUID_STR);
            assertThat(codec.format(id)).isEqualTo(UUID_STR);
            assertThat(codec.format(null)).isNull();
        }

        @Test
        void idType() {
            assertThat(codec.getIdType()).isEqualTo(UUID.class);
        }
    }

    @Nested
    @DisplayName("StringIdCodec")
    class StringCodec {

        private final StringIdCodec codec = new StringIdCodec();

        @Test
        void parsePassthrough() {
            assertThat(codec.parse("ORD-2026-0001")).isEqualTo("ORD-2026-0001");
            // 数字串保持 String，不被解析为数值
            assertThat(codec.parse("12345")).isEqualTo("12345");
        }

        @Test
        void parseBlankThrows() {
            assertThatThrownBy(() -> codec.parse("")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> codec.parse(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void formatRoundTrip() {
            assertThat(codec.format("ORD-2026-0001")).isEqualTo("ORD-2026-0001");
            // String codec 允许 null 透传（与 Long/UUID 的 null→null 一致）
            assertThat(codec.format(null)).isNull();
        }

        @Test
        void idType() {
            assertThat(codec.getIdType()).isEqualTo(String.class);
        }
    }

    @Nested
    @DisplayName("IdCodecs.forClass 解析门禁")
    class Resolver {

        @Test
        void resolvesBuiltinCodecs() {
            assertThat(IdCodecs.forClass(Long.class)).isInstanceOf(LongIdCodec.class);
            assertThat(IdCodecs.forClass(UUID.class)).isInstanceOf(UuidIdCodec.class);
            assertThat(IdCodecs.forClass(String.class)).isInstanceOf(StringIdCodec.class);
        }

        @Test
        void returnsConsistentIdType() {
            assertThat(IdCodecs.forClass(Long.class).getIdType()).isEqualTo(Long.class);
            assertThat(IdCodecs.forClass(UUID.class).getIdType()).isEqualTo(UUID.class);
            assertThat(IdCodecs.forClass(String.class).getIdType()).isEqualTo(String.class);
        }

        @Test
        void unknownTypeThrows() {
            assertThatThrownBy(() -> IdCodecs.forClass(Integer.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("无内置 IdCodec")
                    .hasMessageContaining("idCodec()");
        }

        @Test
        void nullTypeThrows() {
            assertThatThrownBy(() -> IdCodecs.forClass(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("idCodec()");
        }
    }
}
