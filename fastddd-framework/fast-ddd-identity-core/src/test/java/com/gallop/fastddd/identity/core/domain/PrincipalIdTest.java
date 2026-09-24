package com.gallop.fastddd.identity.core.domain;

import com.gallop.fastddd.identity.core.error.InvalidPrincipalIdException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CW-02 统一 ID 协议（W0-2 §3.0）校验。 */
class PrincipalIdTest {

    @Test
    void standardUuidTextIsAccepted() {
        String uuid = "01234567-89ab-cdef-0123-456789abcdef";
        assertThatCode(() -> PrincipalId.of(uuid)).doesNotThrowAnyException();
        assertThat(PrincipalId.of(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void overLengthIsRejectedNotTruncated() {
        String over = "u".repeat(51);
        assertThatThrownBy(() -> PrincipalId.of(over))
                .isInstanceOf(InvalidPrincipalIdException.class)
                .hasMessageContaining("50");
    }

    @Test
    void systemJobPrefixPlusJobKeyWithinBudget() {
        // 11 前缀 + 39 job-key = 50，恰好上限
        assertThat(PrincipalId.forSystemJob("j".repeat(39)).value()).hasSize(50);
        assertThatThrownBy(() -> PrincipalId.forSystemJob("j".repeat(40)))
                .isInstanceOf(InvalidPrincipalIdException.class)
                .hasMessageContaining("超长");
    }

    @Test
    void blankAndControlCharsRejected() {
        assertThatThrownBy(() -> PrincipalId.of(" ")).isInstanceOf(InvalidPrincipalIdException.class);
        assertThatThrownBy(() -> PrincipalId.of("bad\u0000id")).isInstanceOf(InvalidPrincipalIdException.class);
    }

    @Test
    void systemConstant() {
        assertThat(PrincipalId.system().value()).isEqualTo("system");
    }
}
