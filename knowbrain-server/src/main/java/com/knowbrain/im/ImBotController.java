package com.knowbrain.im;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * IM Bot 回调端点 — 仅接收企业微信的 HTTP 回调消息推送。
 *
 * <p>钉钉和飞书已迁移至 WebSocket 长连接模式
 *（{@code DingtalkStreamBotHandler} / {@code FeishuStreamBotHandler}），
 * 不再使用 HTTP 回调。
 *
 * <h3>安全说明</h3>
 * 此端点不走 {@code AuthInterceptor} 鉴权（企微没有 KnowBrain JWT Token）。
 * 安全由企微的 SHA1 签名 + AES-256-CBC 消息加密保证。
 */
@Tag(name = "IM 集成", description = "企业微信 Bot 回调")
@Slf4j
@RestController
@RequestMapping("/api/v1/im")
@RequiredArgsConstructor
public class ImBotController {

    private final ImBotDispatcher dispatcher;

    /**
     * 企业微信回调端点。
     *
     * <ul>
     *   <li>GET：URL 验证（首次配置接收消息 URL 时企微发起的验证请求）</li>
     *   <li>POST：消息回调（用户在企微中 @机器人 或发消息时触发）</li>
     * </ul>
     */
    @Operation(summary = "企业微信回调", description = "GET: URL 验证, POST: 消息回调")
    @RequestMapping(value = "/wecom/callback", method = {RequestMethod.GET, RequestMethod.POST})
    public String wecomCallback(HttpServletRequest request,
                                 @RequestBody(required = false) String body) {
        log.info("[企微] 收到回调: method={}, bodyLen={}, remoteIP={}",
                request.getMethod(), body != null ? body.length() : 0, request.getRemoteAddr());
        return dispatcher.handle("wecom", request, body != null ? body : "");
    }
}
