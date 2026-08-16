package com.gallop.fastddd.agentaccess.spi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * RFC 8785 JCS（JSON Canonicalization Scheme）子集实现（纯 JDK；
 * Contract §5.5 canonical 规则 1、golden vectors v1 输入规范）。
 *
 * <p>支持值域（投影 Map 的值）：String、Boolean、Integer/Long（精确整数输出）、
 * Double/Float（ECMAScript Number 序列化）、{@code Map<String, Object>}（递归）、
 * {@code List<Object>}（递归）。不支持嵌套 null 值——「显式 null 与字段缺省
 * 归一为不出现」发生在<b>投影层</b>（构造投影 Map 时省略无值字段），本序列化器
 * 对 Map 值 null 抛 IAE（防御：投影层漏归一即失败，不静默跳过）。空对象 {}
 * 与空数组 [] 是有值数据，保留输出且互不相同（§5.5 规则 2）。</p>
 *
 * <p>规则要点：</p>
 * <ul>
 *   <li>对象键按 UTF-16 码元升序（{@code String.compareTo} 恰为该序，含增补
 *       平面代理对按码元比较——RFC 8785 §3.2.3）；</li>
 *   <li>字符串仅转义 {@code "}、{@code \} 与 U+0000..U+001F 控制字符
 *       （退格/制表/换行/换页/回车简写转义，其余控制字符用小写十六进制
 *       unicode 转义序列）；非 ASCII 字符保持字面（不做 unicode 转义，
 *       输出 UTF-8）；</li>
 *   <li>数字：NaN/±Infinity 抛 IAE（JSON 无此值，fail-closed）；Double 按
 *       ECMAScript Number::toString 记法（整数值无小数点、{@code 1e-7}/
 *       {@code 1e+21} 边界切换科学记法、-0 输出 {@code 0}）；</li>
 *   <li>输出无空白。</li>
 * </ul>
 *
 * <p><b>对齐范围如实标注</b>：浮点序列化以 {@code BigDecimal.valueOf}（即
 * {@code Double.toString} 的最短往返表示）为数字源，与 ECMAScript 最短表示
 * 在常规区间一致；golden vectors v1 只使用整数（向量表 §4.1 明示避免浮点
 * 歧义），浮点全区间与 UTF-16 增补平面排序边界的一致性向量由 Contract Kit
 * （FDD-AA-15）补充，未验证前不宣称全区间逐字节对齐。Integer/Long 超出
 * I-JSON 双精度可表示域（|x| &gt; 2^53）时直接精确输出——授权投影的整数
 * 标识/计数值需要精确性，域约束由投影构造方负责。</p>
 */
public final class JsonCanonicalizer {

    private JsonCanonicalizer() {
    }

    /**
     * 投影对象 → JCS 串（UTF-16 层拼接；调用方按 UTF-8 取字节参与摘要）。
     *
     * @param projection 已归一（无 null 值）的投影对象
     * @return canonical JSON 文本
     * @throws IllegalArgumentException 投影含 null 值、不支持的值类型或 NaN/Infinity
     */
    public static String canonicalize(Map<String, Object> projection) {
        if (projection == null) {
            throw new IllegalArgumentException("projection 必选");
        }
        StringBuilder out = new StringBuilder();
        writeObject(projection, out);
        return out.toString();
    }

    private static void writeObject(Map<String, Object> value, StringBuilder out) {
        for (String key : value.keySet()) {
            if (key == null) {
                throw new IllegalArgumentException("投影对象键不得为 null");
            }
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : value.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(e.getKey(), out);
            out.append(':');
            writeValue(e.getValue(), out);
        }
        out.append('}');
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            // 投影层归一遗漏：显式 null 不允许进入摘要对象（§5.5 规则 2）
            throw new IllegalArgumentException("投影值不得为 null：显式 null 必须在投影层归一为不出现");
        }
        if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b ? "true" : "false");
        } else if (value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            out.append(serializeEsNumber(((Number) value).doubleValue()));
        } else if (value instanceof Map<?, ?> m) {
            writeTypedObject(m, out);
        } else if (value instanceof List<?> l) {
            writeArray(l, out);
        } else {
            throw new IllegalArgumentException("投影值类型不受支持：" + value.getClass().getName()
                    + "（允许：String/Boolean/Integer/Long/Double/Float/Map/List）");
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeTypedObject(Map<?, ?> m, StringBuilder out) {
        for (Object key : m.keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("投影对象键必须是 String：" + key);
            }
        }
        writeObject((Map<String, Object>) m, out);
    }

    private static void writeArray(List<?> value, StringBuilder out) {
        out.append('[');
        for (int i = 0; i < value.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(value.get(i), out);
        }
        out.append(']');
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /**
     * ECMAScript Number::toString 记法（RFC 8785 §3.2.2.3）：
     * 十进制条件 {@code -6 < n <= 21}（n=小数点相对位置），否则科学记法
     * {@code d[.ddd]e±x}；整数值无小数点；-0 与 0 同输出 "0"。
     */
    private static String serializeEsNumber(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("NaN/Infinity 不是合法 JSON 数字（fail-closed）");
        }
        if (d == 0.0) {
            return "0";
        }
        BigDecimal bd = BigDecimal.valueOf(d).stripTrailingZeros();
        String digits = bd.unscaledValue().abs().toString();
        int k = digits.length();
        int n = bd.precision() - bd.scale();
        StringBuilder out = new StringBuilder();
        if (d < 0) {
            out.append('-');
        }
        if (-6 < n && n <= 21) {
            if (k <= n) {
                // 整数形式：digits 后补 (n-k) 个零
                out.append(digits).append("0".repeat(n - k));
            } else if (n > 0) {
                out.append(digits, 0, n).append('.').append(digits, n, k);
            } else {
                out.append("0.").append("0".repeat(-n)).append(digits);
            }
        } else {
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits, 1, k);
            }
            int exponent = n - 1;
            out.append('e').append(exponent >= 0 ? "+" : "-").append(Math.abs(exponent));
        }
        return out.toString();
    }
}
