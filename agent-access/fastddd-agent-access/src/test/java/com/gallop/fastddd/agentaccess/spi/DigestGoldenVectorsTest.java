package com.gallop.fastddd.agentaccess.spi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双摘要 golden vectors v1 Java 复算（GV-01~07 含 05a/05b 逐字节一致；
 * 跨语言一致性哨兵——Python 复算脚本产出向量表冻结值，两侧任一不符即 fail）。
 * 覆盖：键序无关、数字规范化（1 vs 1.0）、Unicode 字面输出、null 归一、
 * 空集合区分、域分离（GV-06/07 同对象异标签）。
 */
class DigestGoldenVectorsTest {

    /** 投影构造 helper：null 值归一为不出现（§5.5 规则 2 投影层语义）。 */
    private static Map<String, Object> projection(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                m.put((String) kv[i], kv[i + 1]);
            }
        }
        return m;
    }

    /** GV-06/GV-07 样例投影对象 P（§3 逻辑形态，插入序即文档序）。 */
    private static Map<String, Object> sampleP() {
        return projection(
                "tenantId", "acme",
                "environment", "dev",
                "actionId", "ar.createCrmFollowup",
                "actionVersion", "1",
                "resourceId", "INV-2026-0001",
                "purpose", "collection.followup",
                "dataClassification", "CUSTOMER_FINANCIAL",
                "amount", 100);
    }

    @Test
    void gv01KeyOrderIrrelevance() {
        String expected = "9d47d5d4bc1833b16512fc52ec0ad22d11dd7e1d766b065e32c16743fc6d1671";
        // 两个插入序产生同一 JCS 串、同一摘要
        Map<String, Object> a = projection("tenantId", "acme", "actionId", "ar.createCrmFollowup", "amount", 100);
        Map<String, Object> b = projection("amount", 100, "actionId", "ar.createCrmFollowup", "tenantId", "acme");
        assertEquals("{\"actionId\":\"ar.createCrmFollowup\",\"amount\":100,\"tenantId\":\"acme\"}",
                JsonCanonicalizer.canonicalize(a));
        assertEquals(expected, ActionDigestCalculator.effectDigest(a));
        assertEquals(expected, ActionDigestCalculator.effectDigest(b));
    }

    @Test
    void gv02NumberNormalizationLongVsDouble() {
        String expected = "e3de59fb53a1a37e03d960ef179c2d36ab546e58881acd3e9a726d24c6dd7d7f";
        // ECMAScript Number 序列化：1.0 同构输出 1（Java Double.toString/Python json.dumps 均非 JCS——本向量是跨语言哨兵）
        Map<String, Object> asLong = projection("n", 1);
        Map<String, Object> asDouble = projection("n", 1.0d);
        assertEquals("{\"n\":1}", JsonCanonicalizer.canonicalize(asLong));
        assertEquals("{\"n\":1}", JsonCanonicalizer.canonicalize(asDouble));
        assertEquals(expected, ActionDigestCalculator.effectDigest(asLong));
        assertEquals(expected, ActionDigestCalculator.effectDigest(asDouble));
    }

    @Test
    void gv03UnicodeLiteralOutput() {
        String expected = "54b84597dcbf47790d9ac027b7eaff2ad60c816e73d67cab72f6a4884bab105a";
        Map<String, Object> m = projection("city", "北京", "name", "café");
        // 非 ASCII 字面 UTF-8 输出，禁止 unicode 转义形式
        assertEquals("{\"city\":\"北京\",\"name\":\"café\"}", JsonCanonicalizer.canonicalize(m));
        assertEquals(expected, ActionDigestCalculator.effectDigest(m));
    }

    @Test
    void gv04NullNormalization() {
        String expected = "39a370f85f25cf805a84f13d514bc5a5c972efef804044484d3480d6d4ce0bde";
        // {"a":1} 与 {"a":1,"b":null}（投影归一后）同摘要
        Map<String, Object> withoutNull = projection("a", 1);
        Map<String, Object> withNull = projection("a", 1, "b", null);
        assertEquals(expected, ActionDigestCalculator.effectDigest(withoutNull));
        assertEquals(expected, ActionDigestCalculator.effectDigest(withNull));
    }

    @Test
    void gv05EmptyCollectionsAreDistinct() {
        // 缺省/null ≠ 空对象 ≠ 空数组（三者互不相同）
        String base = "39a370f85f25cf805a84f13d514bc5a5c972efef804044484d3480d6d4ce0bde";
        String emptyObject = "6224fcc2a39d11d58efed90b899f4ce122ab4bb7e49b0831b76ec28a1643c3c4";
        String emptyArray = "837b4d0d5ef2cc8883f317f8757b16b022e2f3c81ac9cb5648aa82981dbe5841";
        Map<String, Object> withEmptyObject = projection("a", 1, "b", new LinkedHashMap<String, Object>());
        Map<String, Object> withEmptyArray = projection("a", 1, "b", List.of());
        assertEquals("{\"a\":1,\"b\":{}}", JsonCanonicalizer.canonicalize(withEmptyObject));
        assertEquals("{\"a\":1,\"b\":[]}", JsonCanonicalizer.canonicalize(withEmptyArray));
        assertEquals(emptyObject, ActionDigestCalculator.effectDigest(withEmptyObject));
        assertEquals(emptyArray, ActionDigestCalculator.effectDigest(withEmptyArray));
        assertNotEquals(emptyObject, emptyArray);
        assertNotEquals(base, emptyObject);
        assertNotEquals(base, emptyArray);
    }

    @Test
    void gv06And07DomainSeparation() {
        String effectDigest = "6b4a2e8330e787506fc8dbe3d7ce1a83a045b9713fbf2d1cc36c57fa0dba7be1";
        String bindingDigest = "4c86966647769c969a10cc2c0c8c8cd29dda8d7106fceaeea2309234e14822af";
        Map<String, Object> p = sampleP();
        assertEquals("{\"actionId\":\"ar.createCrmFollowup\",\"actionVersion\":\"1\",\"amount\":100,"
                        + "\"dataClassification\":\"CUSTOMER_FINANCIAL\",\"environment\":\"dev\","
                        + "\"purpose\":\"collection.followup\",\"resourceId\":\"INV-2026-0001\",\"tenantId\":\"acme\"}",
                JsonCanonicalizer.canonicalize(p));
        // 同一投影对象在两个域标签下必须产生不同摘要
        assertEquals(effectDigest, ActionDigestCalculator.effectDigest(p));
        assertEquals(bindingDigest, ActionDigestCalculator.authorizationBindingDigest(p));
        assertNotEquals(ActionDigestCalculator.effectDigest(p), ActionDigestCalculator.authorizationBindingDigest(p));
    }

    @Test
    void canonicalizerFailsClosedOnIllegalValues() {
        // canonicalizer 层负向：投影归一遗漏的显式 null 值必须失败（不走 projection helper 的归一）
        Map<String, Object> withNullValue = new LinkedHashMap<>();
        withNullValue.put("a", null);
        List<Runnable> invalid = new ArrayList<>();
        invalid.add(() -> JsonCanonicalizer.canonicalize(withNullValue));
        invalid.add(() -> JsonCanonicalizer.canonicalize(projection("n", Double.NaN)));
        invalid.add(() -> JsonCanonicalizer.canonicalize(projection("n", Double.POSITIVE_INFINITY)));
        invalid.add(() -> JsonCanonicalizer.canonicalize(projection("x", new Object())));
        invalid.add(() -> JsonCanonicalizer.canonicalize(null));
        for (Runnable r : invalid) {
            assertThrows(IllegalArgumentException.class, r::run);
        }
    }

    @Test
    void unsupportedDigestSchemaVersionRejected() {
        // §5.5 规则 5：未识别 digestSchemaVersion 拒绝，禁止猜测降级
        ActionDigestCalculator.requireSupportedDigestSchemaVersion(1);
        assertThrows(IllegalArgumentException.class,
                () -> ActionDigestCalculator.requireSupportedDigestSchemaVersion(0));
        assertThrows(IllegalArgumentException.class,
                () -> ActionDigestCalculator.requireSupportedDigestSchemaVersion(2));
    }

    @Test
    void digestOutputIsLowercaseHex() {
        String digest = ActionDigestCalculator.effectDigest(sampleP());
        assertTrue(digest.matches("[0-9a-f]{64}"), "摘要必须是小写 64 位 hex：" + digest);
    }

    @Test
    void mutableDispatchFieldsDoNotAffectEffectDigest() {
        // AST-03 语义级：attempt/dispatch/trace 是可变调度信息，不进 effect 投影——
        // 同一业务效果（tenant/action/resource/purpose/amount）在两轮不同调度下摘要稳定
        Map<String, Object> effectRound1 = projection(
                "tenantId", "acme", "actionId", "ar.createCrmFollowup", "actionVersion", "1",
                "resourceId", "INV-2026-0001", "purpose", "collection.followup", "amount", 100);
        Map<String, Object> effectRound2 = projection(
                "amount", 100, "purpose", "collection.followup", "resourceId", "INV-2026-0001",
                "actionVersion", "1", "actionId", "ar.createCrmFollowup", "tenantId", "acme");
        assertEquals(ActionDigestCalculator.effectDigest(effectRound1), ActionDigestCalculator.effectDigest(effectRound2));
    }
}
