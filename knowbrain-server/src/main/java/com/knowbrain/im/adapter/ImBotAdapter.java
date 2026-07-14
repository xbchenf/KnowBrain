package com.knowbrain.im.adapter;

import com.knowbrain.im.model.ImIncomingMessage;
import com.knowbrain.im.model.ImOutgoingMessage;
import jakarta.servlet.http.HttpServletRequest;

/**
 * IM Bot 平台适配器接口 — 屏蔽企微/钉钉/飞书在签名位置、加密方式、
 * URL 验证、回复机制上的差异。
 *
 * <p>所有方法接收 {@link HttpServletRequest} 原始请求，由各实现自行提取
 * 平台相关参数（签名可能来自 Query String 或 HTTP Header）。
 *
 * <h3>平台差异速查</h3>
 * <table>
 *   <tr><th>维度</th><th>企业微信</th><th>钉钉</th><th>飞书</th></tr>
 *   <tr><td>签名位置</td><td>URL Query（msg_signature）</td><td>HTTP Header（timestamp + sign）</td><td>可选 Encrypt Key</td></tr>
 *   <tr><td>消息加密</td><td>AES-256-CBC（强制）</td><td>明文</td><td>明文（可选）</td></tr>
 *   <tr><td>URL 验证</td><td>GET — 解密 EchoStr</td><td>无</td><td>POST — 返回 challenge</td></tr>
 *   <tr><td>被动回复</td><td>5s 内同步返回</td><td>不支持</td><td>不支持</td></tr>
 *   <tr><td>主动回复</td><td>POST API（access_token）</td><td>POST sessionWebhook（临时地址）</td><td>POST API（tenant_access_token）</td></tr>
 * </table>
 *
 * <h3>扩展新平台</h3>
 * 实现此接口并注册为 Spring Bean，{@code ImBotDispatcher} 自动发现并路由。
 */
public interface ImBotAdapter {

    /**
     * 平台标识，路径中用于路由。
     *
     * @return 小写平台名，如 "wecom"、"dingtalk"、"feishu"
     */
    String platform();

    /**
     * 验证回调请求签名，防止伪造请求。
     *
     * <ul>
     *   <li>企微：从 Query String 取 msg_signature + timestamp + nonce，SHA1 校验</li>
     *   <li>钉钉：从 HTTP Header 取 timestamp + sign，HmacSHA256 校验</li>
     *   <li>飞书：未配 Encrypt Key 时无需验签，直接返回 true</li>
     * </ul>
     *
     * @param request HTTP 原始请求（不同平台参数位置不同）
     * @param body    原始请求体
     * @return true = 合法请求
     */
    boolean verifySignature(HttpServletRequest request, String body);

    /**
     * URL 验证 — 首次配置回调地址时平台调用此方法验证服务可用性。
     *
     * <ul>
     *   <li>企微：GET 请求，Query 含 echostr → 解密后返回明文</li>
     *   <li>飞书：POST 请求，body 为 {"type":"url_verification","challenge":"xxx"} → 返回 {"challenge":"xxx"}</li>
     *   <li>钉钉：无 URL 验证环节 → 返回 null</li>
     * </ul>
     *
     * @param request HTTP 原始请求
     * @param body    原始请求体
     * @return URL 验证响应体；非验证请求或无需验证时返回 null
     */
    String handleUrlVerification(HttpServletRequest request, String body);

    /**
     * 解密并解析请求体为统一消息模型。
     *
     * <ul>
     *   <li>企微：AES-256-CBC 解密 XML → {@link ImIncomingMessage}</li>
     *   <li>钉钉：明文 JSON → {@link ImIncomingMessage}</li>
     *   <li>飞书：配了 Encrypt Key 则解密，否则直接解析 JSON</li>
     * </ul>
     *
     * @param request HTTP 原始请求
     * @param body    原始请求体（密文或明文）
     * @return 统一入站消息；非消息类回调（如 URL 验证、心跳）返回 null
     */
    ImIncomingMessage parse(HttpServletRequest request, String body);

    /**
     * 发送回复消息 — 全部走异步主动推送。
     *
     * <ul>
     *   <li>企微：POST /cgi-bin/message/send（需 access_token）</li>
     *   <li>钉钉：POST msg.replyTarget（sessionWebhook 临时地址）</li>
     *   <li>飞书：POST /open-apis/im/v1/messages/{message_id}/reply（需 tenant_access_token）</li>
     * </ul>
     *
     * @param reply       统一出站回复
     * @param originalMsg 原始入站消息（从中提取回复目标地址）
     */
    void sendReply(ImOutgoingMessage reply, ImIncomingMessage originalMsg);
}
