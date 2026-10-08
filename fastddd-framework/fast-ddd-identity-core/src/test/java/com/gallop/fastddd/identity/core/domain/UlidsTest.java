package com.gallop.fastddd.identity.core.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ULID 辅件测试（W5-b；W5 设计 §3.3/§4.1）：26 字符 Crockford base32 形态、
 * 唯一性、进程内单调与 isValid 拒绝面。
 */
class UlidsTest {

    @Test
    void newUlidProducesValidCanonicalFormat() {
        for (int i = 0; i < 1_000; i++) {
            String ulid = Ulids.newUlid();
            assertThat(ulid).hasSize(Ulids.LENGTH);
            assertThat(Ulids.isValid(ulid)).as("生成的 ULID 必须自检合法：%s", ulid).isTrue();
        }
    }

    @Test
    void newUlidIsUniqueAcrossLargeSample() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(Ulids.newUlid());
        }
        assertThat(seen).hasSize(10_000);
    }

    @Test
    void newUlidIsStrictlyMonotonicWithinProcess() {
        List<String> sequence = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            sequence.add(Ulids.newUlid());
        }
        // 48bit 时间戳定宽编码：字典序 = 时间序；同毫秒内随机量 +1 递增——
        // 两者合取 = 进程内严格递增（设计 §3.3 Ulids 契约）。
        for (int i = 1; i < sequence.size(); i++) {
            assertThat(sequence.get(i).compareTo(sequence.get(i - 1))).as(
                    "第 %d 个取值必须严格大于前值（%s > %s）", i, sequence.get(i), sequence.get(i - 1))
                    .isPositive();
        }
    }

    @Test
    void isValidRejectsMalformedCandidates() {
        assertThat(Ulids.isValid(null)).isFalse();
        assertThat(Ulids.isValid("")).isFalse();
        assertThat(Ulids.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAV")).isTrue(); // 规范样例
        assertThat(Ulids.isValid("01ARZ3NDEKTSV4RRFFQ69G5FA")).isFalse(); // 25 字符
        assertThat(Ulids.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAVA")).isFalse(); // 27 字符
        // Crockford 排除字符：I/L/O/U（歧义字母）
        assertThat(Ulids.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAI")).isFalse();
        assertThat(Ulids.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAL")).isFalse();
        assertThat(Ulids.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAO")).isFalse();
        assertThat(Ulids.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAU")).isFalse();
        assertThat(Ulids.isValid("01arz3ndektsv4rrffq69g5fav")).isFalse(); // 小写
    }
}
