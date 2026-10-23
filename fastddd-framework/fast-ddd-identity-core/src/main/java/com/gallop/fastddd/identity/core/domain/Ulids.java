package com.gallop.fastddd.identity.core.domain;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.locks.LockSupport;

/**
 * ULID 辅件（W5 设计 §3.3/§4.1，W5-b 新增）：26 字符 Crockford base32
 * （48 位毫秒时间戳 + 80 位随机量，字典序即时间序）。
 *
 * <p>用途：{@code directory_change_log.id} 的调用方预铸（P2-5——先铸 id 后写事实，
 * 事实行 {@code change_ref} 与事件 changeLogRef 引用该值）。宽度对齐 V4 约定
 * （ULID 26，Crockford base32）。</p>
 *
 * <p><b>同毫秒单调</b>：同一进程内同一毫秒的连续取值按随机量 +1 递增
 * （字典序严格递增）；随机量 80 位溢出（实践不可达）时等待下一毫秒重取。
 * 跨进程/跨实例唯一性由 80 位 SecureRandom 随机量承载，重复由 V4 主键/
 * 部分唯一约束兜底拒绝。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class Ulids {

    /** ULID 规范长度（Crockford base32：10 字符时间戳 + 16 字符随机量）。 */
    public static final int LENGTH = 26;

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int TIMESTAMP_BITS = 48;
    private static final int RANDOMNESS_BITS = 80;
    private static final BigInteger RADIX = BigInteger.valueOf(32);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object LOCK = new Object();

    private static long lastTimestamp = -1L;
    private static BigInteger lastValue = null;

    private Ulids() {
    }

    /** 铸造一个新 ULID（同毫秒内单调递增；线程安全）。 */
    public static String newUlid() {
        synchronized (LOCK) {
            long timestamp = System.currentTimeMillis();
            BigInteger value = randomValue(timestamp);
            if (timestamp == lastTimestamp && lastValue != null) {
                value = lastValue.add(BigInteger.ONE);
                if (value.shiftRight(RANDOMNESS_BITS).compareTo(
                        BigInteger.valueOf(timestamp)) != 0) {
                    // 随机量 80 位在本毫秒内溢出（实践不可达）：等待下一毫秒重取随机量。
                    value = awaitNextMillisecond(timestamp);
                }
            }
            lastTimestamp = timestamp;
            lastValue = value;
            return encode(value);
        }
    }

    /** 校验字符串是否为合法 ULID 形态（26 字符、Crockford base32 字符集）。 */
    public static boolean isValid(String candidate) {
        if (candidate == null || candidate.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (indexOfAlphabet(candidate.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static BigInteger randomValue(long timestamp) {
        byte[] randomness = new byte[RANDOMNESS_BITS / Byte.SIZE];
        RANDOM.nextBytes(randomness);
        return BigInteger.valueOf(timestamp).shiftLeft(RANDOMNESS_BITS)
                .or(new BigInteger(1, randomness));
    }

    private static BigInteger awaitNextMillisecond(long saturatedTimestamp) {
        long timestamp = saturatedTimestamp;
        while (timestamp == saturatedTimestamp) {
            LockSupport.parkNanos(100_000L);
            timestamp = System.currentTimeMillis();
        }
        lastTimestamp = timestamp;
        return randomValue(timestamp);
    }

    private static String encode(BigInteger value) {
        char[] chars = new char[LENGTH];
        BigInteger remaining = value;
        for (int i = LENGTH - 1; i >= 0; i--) {
            BigInteger[] division = remaining.divideAndRemainder(RADIX);
            chars[i] = ALPHABET[division[1].intValue()];
            remaining = division[0];
        }
        return new String(chars);
    }

    private static int indexOfAlphabet(char c) {
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
