package com.knowbrain.im.adapter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.knowbrain.common.RateLimiter;
import com.knowbrain.im.ImMessageDedup;
import com.knowbrain.im.ImUserMapping;
import com.knowbrain.im.model.ImIncomingMessage;
import com.knowbrain.im.model.ImOutgoingMessage;
import com.knowbrain.permission.PermissionService;
import com.knowbrain.retrieval.engine.RAGService;
import com.knowbrain.retrieval.engine.SearchResult;
import com.knowbrain.retrieval.engine.StreamContext;
import com.lark.oapi.Client;
import com.lark.oapi.service.contact.v3.model.*;
import com.lark.oapi.service.im.v1.model.*;
import com.lark.oapi.service.im.v1.model.ext.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 飞书 Stream 模式 Bot 消息处理器 + API 工具。
 *
 * <h3>架构</h3>
 * 类似 {@link DingtalkStreamBotHandler}，WebSocket 长连接接收事件：
 * <ol>
 *   <li>提取文本内容（仅处理 text 消息）</li>
 *   <li>消息去重</li>
 *   <li>IM 用户 → KB 用户映射</li>
 *   <li>限流检查</li>
 *   <li>异步 RAG + 回复</li>
 * </ol>
 *
 * <h3>API 工具方法</h3>
 * 同时提供 {@link #fetchDepartmentList()}、{@link #fetchUserName(String)}、
 * {@link #fetchUserDepartments(String)} 等供管理后台使用。
 *
 * @see <a href="https://open.feishu.cn/document/server-docs/event-subscription-guide/overview">飞书事件订阅</a>
 */
@Slf4j
@Component
public class FeishuStreamBotHandler {

    private final RAGService ragService;
    private final PermissionService permissionService;
    private final RateLimiter rateLimiter;
    private final ImUserMapping userMapping;
    private final ImMessageDedup dedup;

    /** 由 FeishuStreamConfig 注入 */
    private volatile Client feishuClient;

    private static final int DEPT_PAGE_SIZE = 50;

    public FeishuStreamBotHandler(RAGService ragService,
                                  PermissionService permissionService,
                                  RateLimiter rateLimiter,
                                  ImUserMapping userMapping,
                                  ImMessageDedup dedup) {
        this.ragService = ragService;
        this.permissionService = permissionService;
        this.rateLimiter = rateLimiter;
        this.userMapping = userMapping;
        this.dedup = dedup;
    }

    /** 由 FeishuStreamConfig 设置 */
    void setFeishuClient(Client client) {
        this.feishuClient = client;
    }

    // ==================== 消息处理（P2MessageReceiveV1 Handler） ====================

    /**
     * 处理飞书消息事件。由 {@code EventDispatcher} 回调。
     */
    public void handleMessageEvent(P2MessageReceiveV1 event) {
        // ---- Step 1: 提取数据 ----
        P2MessageReceiveV1Data data = event.getEvent();
        if (data == null) return;

        EventMessage message = data.getMessage();
        if (message == null) return;

        // 仅处理文本消息
        if (!"text".equals(message.getMessageType())) {
            log.debug("[飞书Stream] 非文本消息忽略: msgType={}", message.getMessageType());
            return;
        }

        // 解析 content（飞书 content 是 JSON 字符串：{"text":"hello"}）
        String contentRaw = message.getContent();
        if (contentRaw == null || contentRaw.isBlank()) return;

        String text;
        try {
            JSONObject contentJson = JSONUtil.parseObj(contentRaw);
            text = contentJson.getStr("text");
        } catch (Exception e) {
            log.warn("[飞书Stream] content 解析失败: length={}", contentRaw != null ? contentRaw.length() : 0);
            return;
        }
        if (text == null || text.isBlank()) return;

        // 去除 @机器人 提及（飞书格式: <at user_id="xxx">名字</at>）
        text = text.replaceAll("<at\\s+user_id=\"[^\"]*\">[^<]*</at>", "").trim();
        if (text.isEmpty()) return;

        // ---- Step 2: 消息去重 ----
        String eventId = event.getHeader() != null ? event.getHeader().getEventId() : message.getMessageId();
        if (eventId == null || !dedup.markAndCheck("feishu", eventId)) {
            return;
        }

        // ---- Step 3: 提取用户信息 ----
        String userId = null;
        EventSender sender = data.getSender();
        if (sender != null && sender.getSenderId() != null) {
            com.lark.oapi.service.im.v1.model.UserId senderId = sender.getSenderId();
            // 优先级: open_id > user_id > union_id
            userId = senderId.getOpenId();
            if (userId == null || userId.isBlank()) {
                userId = senderId.getUserId();
            }
        }
        if (userId == null || userId.isBlank()) {
            log.warn("[飞书Stream] 无法提取用户 ID");
            return;
        }

        // ---- Step 4: 会话信息 ----
        String chatId = message.getChatId();
        String chatType = "p2p".equals(message.getChatType()) ? "single" : "group";

        // ---- Step 5: IM 用户 → KB 用户 ----
        String userName = null; // 飞书回调不含显示名，后续 API 获取
        Long kbUserId = userMapping.resolveUserId("feishu", userId, userName);

        // ---- Step 6: 限流 ----
        if (!rateLimiter.tryAcquire(kbUserId)) {
            replyQuick(message.getMessageId(), "提问太频繁了，请稍后再试 ⏳");
            return;
        }

        // ---- Step 7: 构建入站消息（供异步处理使用） ----
        ImIncomingMessage incoming = ImIncomingMessage.builder()
                .platform("feishu")
                .messageId(eventId)
                .fromUserId(userId)
                .fromUserName(userName)
                .chatId(chatId)
                .chatType(chatType)
                .content(text)
                .timestamp(System.currentTimeMillis())
                .raw(Map.of("messageId", message.getMessageId(), "chatId", chatId))
                .build();

        // ---- Step 8: 异步 RAG + 回复 ----
        List<Long> spaceIds = permissionService.getAccessibleSpaceIds(kbUserId);
        CompletableFuture.runAsync(() -> executeRagAndReply(incoming, spaceIds));
    }

    // ==================== RAG 执行 ====================

    private void executeRagAndReply(ImIncomingMessage msg, List<Long> spaceIds) {
        try {
            StreamContext ctx = ragService.chatStream(msg.getContent(), spaceIds);
            List<String> allTokens = ctx.tokens().collectList().block(Duration.ofSeconds(60));
            if (allTokens == null || allTokens.isEmpty()) {
                sendFeishuReply("抱歉，系统处理出错了。", msg);
                return;
            }

            String fullAnswer = String.join("", allTokens);
            List<SearchResult> sources = ctx.sources();

            if (allTokens.size() == 1) {
                // 非流式：FAQ/兜底
                String md = fullAnswer;
                if (sources != null && !sources.isEmpty()) {
                    md += buildSourceLinks(sources);
                }
                sendFeishuReply(md, msg);
            } else {
                // 流式：先发"思考中"，等全部完成后发完整结果
                sendFeishuReply("> 🔍 正在检索知识库…\n\n> ⏳ 回答生成中…", msg);

                StringBuilder buffer = new StringBuilder();
                for (String token : allTokens) {
                    buffer.append(token);
                }

                String finalMd = buffer.toString();
                if (sources != null && !sources.isEmpty()) {
                    finalMd += buildSourceLinks(sources);
                }
                sendFeishuReply(finalMd, msg);
            }

            log.info("[飞书Stream] RAG 完成: msgId={}, answerLen={}",
                    msg.getMessageId(), fullAnswer.length());
        } catch (Exception e) {
            log.error("[飞书Stream] RAG 失败: msgId={}", msg.getMessageId(), e);
            sendFeishuReply("抱歉，系统处理出错了，请稍后重试。", msg);
        }
    }

    // ==================== 消息回复 ====================

    /**
     * 通过飞书 API 回复消息（post 类型 + md 标签）。
     */
    private void sendFeishuReply(String markdownContent, ImIncomingMessage msg) {
        if (feishuClient == null) {
            log.warn("[飞书Stream] feishuClient 未初始化，跳过回复");
            return;
        }

        String replyToMsgId = null;
        if (msg.getRaw() != null) {
            replyToMsgId = (String) msg.getRaw().get("messageId");
        }
        if (replyToMsgId == null || replyToMsgId.isBlank()) {
            log.warn("[飞书Stream] 无可用 message_id");
            return;
        }

        try {
            // 构造 post 内容
            String postJson = buildPostContent(markdownContent);

            ReplyMessageReq req = ReplyMessageReq.newBuilder()
                    .messageId(replyToMsgId)
                    .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                            .msgType("post")
                            .content(postJson)
                            .build())
                    .build();

            ReplyMessageResp resp = feishuClient.im().v1().message().reply(req);
            if (resp.getCode() != 0) {
                log.warn("[飞书Stream] 消息发送失败: code={}, msg={}", resp.getCode(), resp.getMsg());
            }
        } catch (Exception e) {
            log.error("[飞书Stream] 消息发送异常: messageId={}", replyToMsgId, e);
        }
    }

    /** 快捷回复（限流等场景） */
    private void replyQuick(String messageId, String text) {
        if (feishuClient == null || messageId == null) return;
        try {
            String postJson = buildPostContent(text);
            ReplyMessageReq req = ReplyMessageReq.newBuilder()
                    .messageId(messageId)
                    .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                            .msgType("post")
                            .content(postJson)
                            .build())
                    .build();
            feishuClient.im().v1().message().reply(req);
        } catch (Exception e) {
            log.debug("[飞书Stream] 快捷回复失败: {}", e.toString());
        }
    }

    /**
     * 构造飞书 post 消息内容（含 md 标签）。
     * content 字段为 JSON 字符串。
     */
    private String buildPostContent(String markdown) {
        Map<String, Object> post = Map.of(
                "zh_cn", Map.of(
                        "title", "KnowBrain 回答",
                        "content", List.of(
                                List.of(Map.of("tag", "md", "text", markdown))
                        )
                )
        );
        return JSONUtil.toJsonStr(post);
    }

    // ==================== 用户信息 API（供管理后台使用） ====================

    /**
     * 通过飞书 API 获取用户显示名。
     *
     * @param userId 飞书用户 ID（open_id）
     * @return 用户显示名，失败返回 null
     */
    public String fetchUserName(String userId) {
        if (feishuClient == null) return null;
        try {
            GetUserReq req = GetUserReq.newBuilder()
                    .userId(userId)
                    .userIdType("open_id")
                    .build();
            GetUserResp resp = feishuClient.contact().v3().user().get(req);
            if (resp.getCode() != 0) return null;
            return resp.getData().getUser() != null
                    ? resp.getData().getUser().getName() : null;
        } catch (Exception e) {
            log.debug("[飞书Stream] 获取用户名称失败: userId={}, error={}", userId, e.toString());
            return null;
        }
    }

    /**
     * 通过飞书 API 获取用户所属部门 ID 列表。
     *
     * @param userId 飞书用户 ID（open_id）
     * @return 部门 ID 列表，失败返回空列表
     */
    public List<String> fetchUserDepartments(String userId) {
        if (feishuClient == null) return List.of();
        try {
            GetUserReq req = GetUserReq.newBuilder()
                    .userId(userId)
                    .userIdType("open_id")
                    .build();
            GetUserResp resp = feishuClient.contact().v3().user().get(req);
            if (resp.getCode() != 0) return List.of();
            String[] deptIds = resp.getData().getUser() != null
                    ? resp.getData().getUser().getDepartmentIds() : null;
            return deptIds != null ? Arrays.asList(deptIds) : List.of();
        } catch (Exception e) {
            log.warn("[飞书Stream] 获取用户部门失败: userId={}, error={}", userId, e.toString());
            return List.of();
        }
    }

    /**
     * 通过飞书 API 获取用户手机号（跨平台匹配用）。
     *
     * @param userId 飞书用户 ID（open_id）
     * @return 手机号，失败返回 null
     */
    public String fetchUserMobile(String userId) {
        if (feishuClient == null) return null;
        try {
            GetUserReq req = GetUserReq.newBuilder()
                    .userId(userId)
                    .userIdType("open_id")
                    .build();
            GetUserResp resp = feishuClient.contact().v3().user().get(req);
            if (resp.getCode() != 0) return null;
            return resp.getData().getUser() != null
                    ? resp.getData().getUser().getMobile() : null;
        } catch (Exception e) {
            log.debug("[飞书Stream] 获取用户手机号失败: userId={}, error={}", userId, e.toString());
            return null;
        }
    }

    // ==================== 部门列表 API（供管理后台使用） ====================

    /**
     * 获取飞书完整部门列表。
     *
     * <p>使用 contact v3 list API 获取全量部门。
     *
     * @return 部门列表，每个元素包含 id / name / parentId
     */
    public List<Map<String, Object>> fetchDepartmentList() {
        if (feishuClient == null) {
            log.warn("[飞书Stream] fetchDepartmentList: feishuClient 为 null，跳过");
            return List.of();
        }
        log.info("[飞书Stream] 开始获取部门列表...");
        List<Map<String, Object>> all = new ArrayList<>();
        try {
            // 优先尝试 list + fetch_child=true（一次拉取全量）
            if (!tryListAll(all)) {
                // list 不可用 → 降级为递归 children
                log.info("[飞书Stream] list API 不可用，降级为递归 children");
                Map<String, Object> root = fetchRootDepartment();
                if (root != null) all.add(root);
                fetchSubDepartments("0", all);
            }
            log.info("[飞书Stream] 获取部门列表完成: count={}", all.size());
        } catch (Exception e) {
            log.warn("[飞书Stream] 获取部门列表失败: error={}", e.toString());
        }
        return all;
    }

    /**
     * 尝试用 list API（fetch_child=true）一次性拉取全量部门。
     *
     * @return true 表示成功拉取，false 表示不可用（需降级）
     */
    private boolean tryListAll(List<Map<String, Object>> collector) {
        try {
            String pageToken = null;
            int totalItems = 0;
            do {
                ListDepartmentReq.Builder reqBuilder = ListDepartmentReq.newBuilder()
                        .pageSize(DEPT_PAGE_SIZE)
                        .departmentIdType("open_department_id")
                        .fetchChild(true);  // 关键：递归返回子部门
                if (pageToken != null) {
                    reqBuilder.pageToken(pageToken);
                }
                ListDepartmentResp resp = feishuClient.contact().v3().department()
                        .list(reqBuilder.build());
                if (resp.getCode() != 0) {
                    log.warn("[飞书Stream] list API 失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                    return false;
                }
                ListDepartmentRespBody data = resp.getData();
                if (data != null && data.getItems() != null) {
                    for (Department dept : data.getItems()) {
                        String deptId = effectiveStr(dept.getOpenDepartmentId());
                        if (deptId == null) deptId = effectiveStr(dept.getDepartmentId());
                        if (deptId == null) continue;

                        String parentId = effectiveStr(dept.getParentDepartmentId());
                        if (parentId == null) parentId = "0";

                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", deptId);
                        String name = dept.getName();
                        item.put("name", name != null && !name.isBlank() ? name
                                : "0".equals(deptId) ? "公司" : "");
                        item.put("parentId", parentId);
                        collector.add(item);
                        totalItems++;
                    }
                }
                pageToken = data != null ? data.getPageToken() : null;
                if (pageToken != null && pageToken.isEmpty()) pageToken = null;
                if (data == null || !Boolean.TRUE.equals(data.getHasMore())) break;
            } while (pageToken != null);
            // 如果只返回了 1 条且是根部门空壳，视为不可用
            return totalItems > 1 || (totalItems == 1 && !isShellRoot(collector.get(0)));
        } catch (Exception e) {
            log.warn("[飞书Stream] list API 异常: error={}", e.toString());
            return false;
        }
    }

    /** 判断是否为仅包含根部门空壳（name 为空且 id=0） */
    private boolean isShellRoot(Map<String, Object> dept) {
        return "0".equals(dept.get("id"))
                && (dept.get("name") == null || dept.get("name").toString().isBlank());
    }

    /** 获取根部门信息（id=0） */
    private Map<String, Object> fetchRootDepartment() {
        try {
            GetDepartmentResp resp = feishuClient.contact().v3().department()
                    .get(GetDepartmentReq.newBuilder()
                            .departmentId("0")
                            .departmentIdType("open_department_id")
                            .build());
            if (resp.getCode() != 0) {
                log.debug("[飞书Stream] 获取根部门失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                return null;
            }
            Department dept = resp.getData() != null ? resp.getData().getDepartment() : null;
            if (dept == null) return null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "0");
            String name = dept.getName();
            item.put("name", name != null && !name.isBlank() ? name : "公司");
            item.put("parentId", "0");
            return item;
        } catch (Exception e) {
            log.debug("[飞书Stream] 获取根部门异常: error={}", e.toString());
            return null;
        }
    }

    /** 递归拉取子部门（降级方案） */
    private void fetchSubDepartments(String parentOpenId, List<Map<String, Object>> collector) {
        try {
            String pageToken = null;
            do {
                ChildrenDepartmentReq.Builder reqBuilder = ChildrenDepartmentReq.newBuilder()
                        .departmentId(parentOpenId)
                        .pageSize(DEPT_PAGE_SIZE)
                        .departmentIdType("open_department_id");
                if (pageToken != null) {
                    reqBuilder.pageToken(pageToken);
                }
                ChildrenDepartmentResp resp = feishuClient.contact().v3().department()
                        .children(reqBuilder.build());
                if (resp.getCode() != 0) {
                    if (resp.getCode() != 99992357) {
                        log.warn("[飞书Stream] children({}) 失败: code={}, msg={}",
                                parentOpenId, resp.getCode(), resp.getMsg());
                    }
                    break;
                }
                ChildrenDepartmentRespBody data = resp.getData();
                if (data != null && data.getItems() != null) {
                    for (Department dept : data.getItems()) {
                        String deptId = effectiveStr(dept.getOpenDepartmentId());
                        if (deptId == null) deptId = effectiveStr(dept.getDepartmentId());
                        if (deptId == null) continue;

                        String parentDeptId = effectiveStr(dept.getParentDepartmentId());
                        if (parentDeptId == null) parentDeptId = parentOpenId;

                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", deptId);
                        item.put("name", dept.getName() != null ? dept.getName() : "");
                        item.put("parentId", parentDeptId);
                        collector.add(item);

                        fetchSubDepartments(deptId, collector);
                    }
                }
                pageToken = data != null ? data.getPageToken() : null;
                if (pageToken != null && pageToken.isEmpty()) pageToken = null;
                if (data == null || !Boolean.TRUE.equals(data.getHasMore())) break;
            } while (pageToken != null);
        } catch (Exception e) {
            log.warn("[飞书Stream] children({}) 异常: error={}", parentOpenId, e.toString());
        }
    }

    // ==================== 辅助方法 ====================

    /** 将 "0" 或 "" 或 null 统一视为无效值，返回 null */
    private static String effectiveStr(String s) {
        if (s == null || s.isBlank() || "0".equals(s)) return null;
        return s;
    }

    private String buildSourceLinks(List<SearchResult> sources) {
        StringBuilder sb = new StringBuilder("\n\n---\n**📖 参考来源**  \n");
        for (int i = 0; i < Math.min(sources.size(), 5); i++) {
            SearchResult s = sources.get(i);
            sb.append(i + 1).append(". **").append(s.getDocumentTitle())
                    .append("**（").append(Math.round(s.getScore() * 100)).append("%）  \n");
        }
        return sb.toString();
    }
}
