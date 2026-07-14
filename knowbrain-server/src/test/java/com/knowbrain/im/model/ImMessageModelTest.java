package com.knowbrain.im.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一消息模型单元测试。
 */
@DisplayName("IM 消息模型")
class ImMessageModelTest {

    // ==================== ImIncomingMessage ====================

    @Test
    @DisplayName("IncomingMessage：Builder 构建")
    void incomingMessageBuilder() {
        ImIncomingMessage msg = ImIncomingMessage.builder()
                .platform("wecom")
                .messageId("msg-001")
                .fromUserId("user-zhangsan")
                .fromUserName("张三")
                .chatId("wrChat001")
                .chatType("group")
                .content("VPN怎么配")
                .timestamp(1700000000000L)
                .replyTarget("https://webhook.example.com")
                .replyTargetExpiry(1800000000000L)
                .build();

        assertEquals("wecom", msg.getPlatform());
        assertEquals("msg-001", msg.getMessageId());
        assertTrue(msg.isGroupChat());
        assertFalse(msg.isSingleChat());
        assertTrue(msg.hasValidReplyTarget());
    }

    @Test
    @DisplayName("IncomingMessage：hasValidReplyTarget - 未过期")
    void hasValidReplyTargetNotExpired() {
        ImIncomingMessage msg = ImIncomingMessage.builder()
                .replyTarget("https://hook.example.com")
                .replyTargetExpiry(System.currentTimeMillis() + 3600_000)
                .build();
        assertTrue(msg.hasValidReplyTarget());
    }

    @Test
    @DisplayName("IncomingMessage：hasValidReplyTarget - 已过期")
    void hasValidReplyTargetExpired() {
        ImIncomingMessage msg = ImIncomingMessage.builder()
                .replyTarget("https://hook.example.com")
                .replyTargetExpiry(System.currentTimeMillis() - 1000) // 1 秒前过期
                .build();
        assertFalse(msg.hasValidReplyTarget());
    }

    @Test
    @DisplayName("IncomingMessage：hasValidReplyTarget - 无过期时间（永远有效）")
    void hasValidReplyTargetNoExpiry() {
        ImIncomingMessage msg = ImIncomingMessage.builder()
                .replyTarget("https://hook.example.com")
                .replyTargetExpiry(0) // 企微/飞书无过期时间
                .build();
        assertTrue(msg.hasValidReplyTarget());
    }

    @Test
    @DisplayName("IncomingMessage：hasValidReplyTarget - null")
    void hasValidReplyTargetNull() {
        ImIncomingMessage msg = ImIncomingMessage.builder()
                .replyTarget(null)
                .build();
        assertFalse(msg.hasValidReplyTarget());
    }

    @Test
    @DisplayName("IncomingMessage：isGroupChat / isSingleChat")
    void chatTypeChecks() {
        assertTrue(ImIncomingMessage.builder().chatType("group").build().isGroupChat());
        assertTrue(ImIncomingMessage.builder().chatType("GROUP").build().isGroupChat());
        assertTrue(ImIncomingMessage.builder().chatType("single").build().isSingleChat());
        assertFalse(ImIncomingMessage.builder().chatType("single").build().isGroupChat());
    }

    // ==================== ImOutgoingMessage 工厂方法 ====================

    @Test
    @DisplayName("OutgoingMessage：single() — 非流式单条回复")
    void outgoingSingle() {
        ImOutgoingMessage msg = ImOutgoingMessage.single("你好，这是回答", "msg-001");

        assertEquals("你好，这是回答", msg.getContent());
        assertFalse(msg.isStreaming());
        assertEquals(0, msg.getStreamSeq());
        assertTrue(msg.isStreamEnd());
        assertEquals("msg-001", msg.getReplyToMessageId());
        assertTrue(msg.getSourceLinks().isEmpty());
    }

    @Test
    @DisplayName("OutgoingMessage：streamFirst() — 流式首帧")
    void outgoingStreamFirst() {
        ImOutgoingMessage msg = ImOutgoingMessage.streamFirst("正在检索…", "msg-002");

        assertEquals("正在检索…", msg.getContent());
        assertTrue(msg.isStreaming());
        assertEquals(0, msg.getStreamSeq());
        assertFalse(msg.isStreamEnd());
    }

    @Test
    @DisplayName("OutgoingMessage：streamSegment() — 流式中间帧")
    void outgoingStreamSegment() {
        ImOutgoingMessage msg = ImOutgoingMessage.streamSegment("部分内容…", 3, "msg-003");

        assertTrue(msg.isStreaming());
        assertEquals(3, msg.getStreamSeq());
        assertFalse(msg.isStreamEnd());
    }

    @Test
    @DisplayName("OutgoingMessage：streamFinal() — 流式末帧含溯源")
    void outgoingStreamFinal() {
        List<String> sources = List.of("员工手册.pdf", "考勤制度.docx");
        ImOutgoingMessage msg = ImOutgoingMessage.streamFinal("完整回答", sources, 5, "msg-004");

        assertFalse(msg.isStreaming());
        assertTrue(msg.isStreamEnd());
        assertEquals(5, msg.getStreamSeq());
        assertEquals("完整回答", msg.getContent());
        assertEquals(2, msg.getSourceLinks().size());
        assertEquals("员工手册.pdf", msg.getSourceLinks().get(0));
    }

    @Test
    @DisplayName("OutgoingMessage：error() — 错误回复")
    void outgoingError() {
        ImOutgoingMessage msg = ImOutgoingMessage.error("系统繁忙，请稍后重试", "msg-005");

        assertEquals("系统繁忙，请稍后重试", msg.getContent());
        assertFalse(msg.isStreaming());
        assertTrue(msg.isStreamEnd());
        assertEquals("msg-005", msg.getReplyToMessageId());
    }
}
