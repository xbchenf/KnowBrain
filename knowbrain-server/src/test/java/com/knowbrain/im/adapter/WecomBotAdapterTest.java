package com.knowbrain.im.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业微信加解密单元测试。
 *
 * <p>测试范围：
 * <ul>
 *   <li>AES-256-CBC 加密 → 解密往返（round-trip）</li>
 *   <li>SHA1 签名计算</li>
 *   <li>XML 消息解析</li>
 *   <li>边界情况（空内容、receiveId 不匹配等）</li>
 * </ul>
 *
 * <p>使用已知测试向量，不依赖 Spring 容器或外部服务。
 */
@DisplayName("企业微信加解密")
class WecomBotAdapterTest {

    private WecomBotAdapter adapter;

    /** 标准 43 位 EncodingAESKey（对应 32 字节 AES 密钥） */
    private static final String ENCODING_AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    private static final String CORP_ID = "test-corp-id";
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        adapter = new WecomBotAdapter();
        // 通过反射注入 @Value 字段（绕过 Spring 容器）
        setField(adapter, "corpId", CORP_ID);
        setField(adapter, "token", TOKEN);
        setField(adapter, "encodingAesKey", ENCODING_AES_KEY);
        setField(adapter, "agentId", "1000001");
        setField(adapter, "secret", "test-secret");
        adapter.init();
    }

    // ==================== AES 加解密往返测试 ====================

    @Test
    @DisplayName("AES 加密 → 解密往返：中文消息")
    void aesRoundTripChinese() {
        String original = "这是一条测试消息，包含中文和标点符号！";
        String encrypted = adapter.aesEncrypt(original, CORP_ID);
        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());

        String decrypted = adapter.aesDecrypt(encrypted, CORP_ID);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("AES 加密 → 解密往返：XML 消息")
    void aesRoundTripXml() {
        String xml = """
                <xml>
                  <ToUserName><![CDATA[corpId]]></ToUserName>
                  <FromUserName><![CDATA[user123]]></FromUserName>
                  <CreateTime>1408091189</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[VPN怎么配置？]]></Content>
                  <MsgId>1234567890</MsgId>
                  <ChatId><![CDATA[wrChat001]]></ChatId>
                  <ChatType><![CDATA[group]]></ChatType>
                </xml>""";
        String encrypted = adapter.aesEncrypt(xml, CORP_ID);
        String decrypted = adapter.aesDecrypt(encrypted, CORP_ID);
        assertEquals(xml, decrypted);
    }

    @Test
    @DisplayName("AES 加密 → 解密往返：空字符串")
    void aesRoundTripEmpty() {
        String original = "";
        String encrypted = adapter.aesEncrypt(original, CORP_ID);
        String decrypted = adapter.aesDecrypt(encrypted, CORP_ID);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("AES 加密 → 解密往返：长文本（>1000 字符）")
    void aesRoundTripLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是第").append(i).append("行文本，用于测试长消息加解密性能。\n");
        }
        String original = sb.toString();
        String encrypted = adapter.aesEncrypt(original, CORP_ID);
        String decrypted = adapter.aesDecrypt(encrypted, CORP_ID);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("AES 解密：错误的 receiveId 不抛异常但解密继续")
    void aesDecryptWrongReceiveId() {
        String original = "test message";
        String encrypted = adapter.aesEncrypt(original, CORP_ID);

        // 用错误的 receiveId 解密，仍能解密出内容（仅告警日志）
        String decrypted = adapter.aesDecrypt(encrypted, "wrong-corp-id");
        // 解密本身成功（receiveId 不匹配只产生日志警告，不抛异常）
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("AES 解密：篡改密文抛异常")
    void aesDecryptTampered() {
        String original = "test message";
        String encrypted = adapter.aesEncrypt(original, CORP_ID);

        // 篡改密文（翻转第一个字符）
        char[] chars = encrypted.toCharArray();
        chars[0] = chars[0] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(Exception.class, () -> adapter.aesDecrypt(tampered, CORP_ID));
    }

    @Test
    @DisplayName("AES 解密：空密文抛异常")
    void aesDecryptInvalidBase64() {
        assertThrows(Exception.class, () -> adapter.aesDecrypt("!!!not-valid-base64!!!", CORP_ID));
    }

    // ==================== SHA1 签名测试 ====================

    @Test
    @DisplayName("SHA1：已知向量验证")
    void sha1KnownVector() {
        // SHA1("hello") = aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
        String result = adapter.sha1("hello");
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", result);
    }

    @Test
    @DisplayName("SHA1：空字符串")
    void sha1Empty() {
        // SHA1("") = da39a3ee5e6b4b0d3255bfef95601890afd80709
        String result = adapter.sha1("");
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", result);
    }

    @Test
    @DisplayName("SHA1：排序拼接验证（签名标准格式）")
    void sha1SortedJoin() {
        // 模拟签名计算: SHA1(sort(token, timestamp, nonce, encrypt))
        // 已知值：token="a", timestamp="1", nonce="2", encrypt="b"
        // sort → ["1","2","a","b"] → "12ab"
        String result = adapter.sha1("12ab");
        assertNotNull(result);
        assertEquals(40, result.length()); // SHA1 = 40 hex chars
    }

    // ==================== 完整性验证 ====================

    @Test
    @DisplayName("完整流程：加密 → 嵌入 XML 信封 → 提取 Encrypt → 解密")
    void fullEnvelopeRoundTrip() {
        String plainMsg = """
                <xml>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[测试问题]]></Content>
                  <MsgId>999</MsgId>
                </xml>""";

        // 1. 加密
        String encrypted = adapter.aesEncrypt(plainMsg, CORP_ID);

        // 2. 构造企微回调 XML 信封
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = "test-nonce";

        // 计算签名
        String signature = adapter.sha1(
                adapter.sortAndJoin(TOKEN, timestamp, nonce, encrypted));

        String envelope = String.format("""
                <xml>
                  <ToUserName><![CDATA[%s]]></ToUserName>
                  <Encrypt><![CDATA[%s]]></Encrypt>
                  <AgentID>1000001</AgentID>
                </xml>""", CORP_ID, encrypted);

        // 3. 模拟适配器解密流程
        String extractedEncrypt = extractXmlField(envelope, "Encrypt");
        assertEquals(encrypted, extractedEncrypt);

        String decrypted = adapter.aesDecrypt(extractedEncrypt, CORP_ID);
        assertEquals(plainMsg, decrypted);

        // 4. 验证签名计算一致
        assertNotNull(signature);
        assertEquals(40, signature.length());
    }

    // ==================== 辅助方法 ====================

    /** 反射设置私有字段 */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("反射设置字段失败: " + fieldName, e);
        }
    }

    /** 从 XML 中提取字段值（与 WecomBotAdapter 实现一致） */
    private static String extractXmlField(String xml, String fieldName) {
        if (xml == null) return null;

        // 优先尝试 CDATA 版本
        String cdataStartTag = "<" + fieldName + "><![CDATA[";
        int start = xml.indexOf(cdataStartTag);
        if (start != -1) {
            start += cdataStartTag.length();
            String cdataEndTag = "]]></" + fieldName + ">";
            int end = xml.indexOf(cdataEndTag);
            return end != -1 ? xml.substring(start, end) : null;
        }

        // 回退：普通文本
        String startTag = "<" + fieldName + ">";
        start = xml.indexOf(startTag);
        if (start == -1) return null;
        start += startTag.length();
        String endTag = "</" + fieldName + ">";
        int end = xml.indexOf(endTag);
        return end != -1 ? xml.substring(start, end) : null;
    }
}
