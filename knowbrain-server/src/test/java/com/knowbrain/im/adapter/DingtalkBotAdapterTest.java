package com.knowbrain.im.adapter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 钉钉 Bot 适配器单元测试。
 *
 * <p>测试范围：
 * <ul>
 *   <li>HmacSHA256 签名验证（已知向量 + 随机生成）</li>
 *   <li>时间戳窗口校验（过期拒绝、正常通过）</li>
 *   <li>消息 JSON 解析（文本消息、非文本消息、空消息）</li>
 *   <li>sessionWebhook 处理</li>
 * </ul>
 */
@DisplayName("钉钉签名与消息解析")
class DingtalkBotAdapterTest {

    private DingtalkBotAdapter adapter;

    private static final String APP_KEY = "ding-test-app-key";
    private static final String APP_SECRET = "test-app-secret-123";
    private static final String ROBOT_CODE = "ding-test-robot-code";

    @BeforeEach
    void setUp() {
        adapter = new DingtalkBotAdapter();
        setField(adapter, "appKey", APP_KEY);
        setField(adapter, "appSecret", APP_SECRET);
        setField(adapter, "robotCode", ROBOT_CODE);
        adapter.init();
    }

    // ==================== 签名验证 ====================

    @Test
    @DisplayName("验签：正确签名通过验证")
    void verifySignatureValid() throws Exception {
        long now = System.currentTimeMillis();
        String timestamp = String.valueOf(now);
        String sign = hmacSha256(timestamp + "\n" + APP_SECRET, APP_SECRET);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("timestamp")).thenReturn(timestamp);
        when(request.getHeader("sign")).thenReturn(sign);

        assertTrue(adapter.verifySignature(request, "{}"));
    }

    @Test
    @DisplayName("验签：错误签名被拒绝")
    void verifySignatureInvalid() throws Exception {
        long now = System.currentTimeMillis();
        String timestamp = String.valueOf(now);
        String wrongSign = hmacSha256(timestamp + "\n" + "wrong-secret", "wrong-secret");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("timestamp")).thenReturn(timestamp);
        when(request.getHeader("sign")).thenReturn(wrongSign);

        assertFalse(adapter.verifySignature(request, "{}"));
    }

    @Test
    @DisplayName("验签：过期时间戳被拒绝（超过 1 小时）")
    void verifySignatureExpiredTimestamp() {
        long expiredTime = System.currentTimeMillis() - 4_000_000L; // 超过 1 小时前
        String timestamp = String.valueOf(expiredTime);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("timestamp")).thenReturn(timestamp);
        when(request.getHeader("sign")).thenReturn("any-sign");

        assertFalse(adapter.verifySignature(request, "{}"));
    }

    @Test
    @DisplayName("验签：未来时间戳被拒绝")
    void verifySignatureFutureTimestamp() {
        long futureTime = System.currentTimeMillis() + 4_000_000L; // 超过 1 小时后
        String timestamp = String.valueOf(futureTime);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("timestamp")).thenReturn(timestamp);
        when(request.getHeader("sign")).thenReturn("any-sign");

        assertFalse(adapter.verifySignature(request, "{}"));
    }

    @Test
    @DisplayName("验签：缺少 Header 参数返回 false")
    void verifySignatureMissingHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("timestamp")).thenReturn(null);
        when(request.getHeader("sign")).thenReturn(null);

        assertFalse(adapter.verifySignature(request, "{}"));
    }

    @Test
    @DisplayName("验签：非法时间戳格式返回 false")
    void verifySignatureInvalidTimestampFormat() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("timestamp")).thenReturn("not-a-number");
        when(request.getHeader("sign")).thenReturn("any-sign");

        assertFalse(adapter.verifySignature(request, "{}"));
    }

    // ==================== URL 验证 ====================

    @Test
    @DisplayName("URL 验证：钉钉无此机制，返回 null")
    void handleUrlVerificationReturnsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertNull(adapter.handleUrlVerification(request, "{}"));
    }

    // ==================== 消息解析 ====================

    @Test
    @DisplayName("解析：标准文本消息")
    void parseTextMessage() {
        String body = """
                {
                  "msgId": "msg-001",
                  "senderId": "user-zhangsan",
                  "conversationId": "cid-001",
                  "conversationType": "2",
                  "text": {
                    "content": "@KnowBrain VPN怎么配置"
                  },
                  "msgtype": "text",
                  "sessionWebhook": "https://oapi.dingtalk.com/robot/send?access_token=abc",
                  "sessionWebhookExpiredTime": 2000000000000,
                  "createAt": 1700000000000
                }""";
        HttpServletRequest request = mock(HttpServletRequest.class);

        var msg = adapter.parse(request, body);
        assertNotNull(msg);
        assertEquals("dingtalk", msg.getPlatform());
        assertEquals("msg-001", msg.getMessageId());
        assertEquals("user-zhangsan", msg.getFromUserId());
        assertEquals("cid-001", msg.getChatId());
        assertEquals("group", msg.getChatType()); // conversationType=2 → group
        assertEquals("VPN怎么配置", msg.getContent()); // @前缀已去除
        assertEquals("https://oapi.dingtalk.com/robot/send?access_token=abc", msg.getReplyTarget());
        assertEquals(2000000000000L, msg.getReplyTargetExpiry());
        assertEquals(1700000000000L, msg.getTimestamp());
    }

    @Test
    @DisplayName("解析：单聊消息（conversationType=1）")
    void parseSingleChatMessage() {
        String body = """
                {
                  "msgId": "msg-002",
                  "senderId": "user-lisi",
                  "conversationId": "cid-002",
                  "conversationType": "1",
                  "text": { "content": "年假有几天" },
                  "msgtype": "text",
                  "sessionWebhook": "https://oapi.dingtalk.com/robot/send?access_token=def",
                  "createAt": 1700000001000
                }""";
        HttpServletRequest request = mock(HttpServletRequest.class);

        var msg = adapter.parse(request, body);
        assertNotNull(msg);
        assertEquals("single", msg.getChatType());
        assertEquals("年假有几天", msg.getContent());
    }

    @Test
    @DisplayName("解析：非文本消息返回 null")
    void parseNonTextMessage() {
        String body = """
                {
                  "msgId": "msg-003",
                  "msgtype": "picture",
                  "senderId": "user-wangwu"
                }""";
        HttpServletRequest request = mock(HttpServletRequest.class);

        var msg = adapter.parse(request, body);
        assertNull(msg);
    }

    @Test
    @DisplayName("解析：空消息体返回 null")
    void parseEmptyBody() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertNull(adapter.parse(request, ""));
        assertNull(adapter.parse(request, null));
    }

    @Test
    @DisplayName("解析：仅 @机器人 无实质内容返回 null")
    void parseAtOnlyNoContent() {
        String body = """
                {
                  "msgId": "msg-004",
                  "senderId": "user-zhao",
                  "msgtype": "text",
                  "text": { "content": "@KnowBrain " }
                }""";
        HttpServletRequest request = mock(HttpServletRequest.class);

        var msg = adapter.parse(request, body);
        // @机器人 + 空格 = 去除后为空 → null
        assertNull(msg);
    }

    @Test
    @DisplayName("解析：无 sessionWebhook 时 replyTarget 为 null")
    void parseNoWebhook() {
        String body = """
                {
                  "msgId": "msg-005",
                  "senderId": "user-sun",
                  "msgtype": "text",
                  "text": { "content": "你好" }
                }""";
        HttpServletRequest request = mock(HttpServletRequest.class);

        var msg = adapter.parse(request, body);
        assertNotNull(msg);
        assertNull(msg.getReplyTarget());
    }

    // ==================== 平台标识 ====================

    @Test
    @DisplayName("platform() 返回 dingtalk")
    void platformReturnsDingtalk() {
        assertEquals("dingtalk", adapter.platform());
    }

    // ==================== 辅助方法 ====================

    /** HmacSHA256 → Base64（与 DingtalkBotAdapter 内部实现一致） */
    private static String hmacSha256(String data, String key) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] signData = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(signData);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("反射设置字段失败: " + fieldName, e);
        }
    }
}
