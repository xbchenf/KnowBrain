package com.knowbrain.im.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一入站消息 — 屏蔽企微/钉钉/飞书的消息字段差异。
 *
 * <h3>各平台关键字段映射</h3>
 * <table>
 *   <tr><th>本字段</th><th>企微 (XML)</th><th>钉钉 (JSON)</th><th>飞书 (JSON v2.0)</th></tr>
 *   <tr><td>messageId</td><td>MsgId</td><td>msgId</td><td>header.event_id</td></tr>
 *   <tr><td>fromUserId</td><td>FromUserName</td><td>senderId</td><td>event.sender.sender_id</td></tr>
 *   <tr><td>chatId</td><td>ChatId</td><td>conversationId</td><td>event.message.chat_id</td></tr>
 *   <tr><td>chatType</td><td>ChatType(single/group)</td><td>conversationType(1=单聊,2=群聊)</td><td>event.message.chat_type</td></tr>
 *   <tr><td>content</td><td>Content</td><td>text.content</td><td>event.message.content</td></tr>
 *   <tr><td>timestamp</td><td>CreateTime(秒)</td><td>createAt(毫秒)</td><td>event.message.create_time(毫秒)</td></tr>
 *   <tr><td>replyTarget</td><td>—（通过 API 回复）</td><td>sessionWebhook（临时）</td><td>—（通过 API 回复）</td></tr>
 * </table>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImIncomingMessage {

    /** 平台标识: "wecom" / "dingtalk" / "feishu" */
    private String platform;

    /** 平台原生消息 ID（用于去重，各平台会在 7-8 小时内重试） */
    private String messageId;

    /** 发送者 IM 用户 ID */
    private String fromUserId;

    /** 发送者显示名 */
    private String fromUserName;

    /** 会话 ID：群 ID（群聊）或 用户 ID（单聊） */
    private String chatId;

    /** 会话类型: "group" / "single" */
    private String chatType;

    /** 消息文本（已由适配器去除 @机器人 前缀） */
    private String content;

    /**
     * 消息时间戳（统一为毫秒）。
     * 企微原始为秒级，适配器负责 ×1000 转换。
     */
    private long timestamp;

    /**
     * 回复目标地址（平台相关）。
     *
     * <ul>
     *   <li>钉钉：消息体中的 sessionWebhook（临时地址，有过期时间）</li>
     *   <li>企微：null（通过 /cgi-bin/message/send 主动发送）</li>
     *   <li>飞书：null（通过 /open-apis/im/v1/messages/{id}/reply 主动发送）</li>
     * </ul>
     */
    private String replyTarget;

    /**
     * 回复目标过期时间（毫秒时间戳，仅钉钉 sessionWebhook 有此概念）。
     * 0 表示无过期限制（企微/飞书）。
     */
    private long replyTargetExpiry;

    /**
     * 平台原始数据（扩展用）。
     *
     * <p>由适配器自行决定放入哪些平台特有字段，供后续处理步骤使用。
     * Dispatcher 不直接依赖此字段内容。
     */
    private Map<String, Object> raw;

    // ========== 便捷方法 ==========

    /** 是否为群聊消息 */
    public boolean isGroupChat() {
        return "group".equalsIgnoreCase(chatType);
    }

    /** 是否为单聊消息 */
    public boolean isSingleChat() {
        return "single".equalsIgnoreCase(chatType);
    }

    /** replyTarget 是否有效（未过期且非空） */
    public boolean hasValidReplyTarget() {
        if (replyTarget == null || replyTarget.isBlank()) {
            return false;
        }
        if (replyTargetExpiry > 0) {
            return System.currentTimeMillis() <= replyTargetExpiry;
        }
        return true; // 无过期时间 = 一直有效
    }
}
