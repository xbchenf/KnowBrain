package com.knowbrain.im.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一出站回复 — 屏蔽各平台回复格式差异。
 *
 * <p>各平台适配器负责将此类转为平台特定的消息格式：
 * <ul>
 *   <li>企微：转为 {"msgtype":"markdown","markdown":{"content":"..."}}</li>
 *   <li>钉钉：转为 {"msgtype":"markdown","markdown":{"title":"...","text":"..."}}</li>
 *   <li>飞书：转为 {"msg_type":"interactive","content":"..."}</li>
 * </ul>
 *
 * <h3>流式分段回复</h3>
 * 流式场景下，同一条回答会以多次 {@code sendReply()} 发送，
 * 每次的 {@code streamSeq} 递增。IM 客户端看到的是消息被逐步更新。
 * 适配器根据平台能力选择实现方式：
 * <ul>
 *   <li>支持编辑消息的平台 → 首次发送后记录 msgId，后续调编辑接口更新内容</li>
 *   <li>不支持的平台 → 每次发送新消息，streamEnd 时标注"回答完成"</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImOutgoingMessage {

    /** Markdown 格式回复内容 */
    private String content;

    /** 溯源文档链接列表（仅 streamEnd=true 时填充） */
    @Builder.Default
    private List<String> sourceLinks = new ArrayList<>();

    /** 是否流式分段发送（true = 此消息内容可能被后续消息更新） */
    private boolean streaming;

    /** 流式分段序号（0-based，非流式时为 0） */
    private int streamSeq;

    /** 是否为流式最后一帧（非流式时为 true） */
    private boolean streamEnd;

    /** 回复目标消息 ID（表示此回复针对哪条用户消息） */
    private String replyToMessageId;

    // ========== 工厂方法 ==========

    /** 创建非流式单条回复 */
    public static ImOutgoingMessage single(String content, String replyToMessageId) {
        return ImOutgoingMessage.builder()
                .content(content)
                .streaming(false)
                .streamSeq(0)
                .streamEnd(true)
                .replyToMessageId(replyToMessageId)
                .build();
    }

    /** 创建流式首帧（"思考中…"） */
    public static ImOutgoingMessage streamFirst(String content, String replyToMessageId) {
        return ImOutgoingMessage.builder()
                .content(content)
                .streaming(true)
                .streamSeq(0)
                .streamEnd(false)
                .replyToMessageId(replyToMessageId)
                .build();
    }

    /** 创建流式中间帧 */
    public static ImOutgoingMessage streamSegment(String content, int seq, String replyToMessageId) {
        return ImOutgoingMessage.builder()
                .content(content)
                .streaming(true)
                .streamSeq(seq)
                .streamEnd(false)
                .replyToMessageId(replyToMessageId)
                .build();
    }

    /** 创建流式末帧（含溯源链接） */
    public static ImOutgoingMessage streamFinal(String content, List<String> sourceLinks,
                                                 int seq, String replyToMessageId) {
        return ImOutgoingMessage.builder()
                .content(content)
                .sourceLinks(sourceLinks != null ? sourceLinks : List.of())
                .streaming(false)
                .streamSeq(seq)
                .streamEnd(true)
                .replyToMessageId(replyToMessageId)
                .build();
    }

    /** 创建错误回复 */
    public static ImOutgoingMessage error(String errorMessage, String replyToMessageId) {
        return ImOutgoingMessage.builder()
                .content(errorMessage)
                .streaming(false)
                .streamSeq(0)
                .streamEnd(true)
                .replyToMessageId(replyToMessageId)
                .build();
    }
}
