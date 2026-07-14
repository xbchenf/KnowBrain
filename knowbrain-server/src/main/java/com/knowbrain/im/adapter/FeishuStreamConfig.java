package com.knowbrain.im.adapter;

import com.lark.oapi.Client;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书 Stream 模式 — WebSocket 长连接客户端配置。
 *
 * <p>通过 App ID + App Secret 建立 WebSocket 连接，无需公网回调地址。
 * 消息由 {@link FeishuStreamBotHandler} 处理。
 *
 * <h3>启用条件</h3>
 * {@code im.feishu.app-id} 不为空（判空在 @Bean 方法内完成，不使用 @ConditionalOn* 以规避
 * application.yml 占位符与 Spring 条件评估的时序问题）。
 *
 * <h3>生命周期</h3>
 * Spring 容器启动时自动连接，关闭时自动断开。SDK 内置重连机制。
 *
 * @see <a href="https://open.feishu.cn/document/server-docs/event-subscription-guide/overview">飞书事件订阅</a>
 */
@Slf4j
@Configuration
public class FeishuStreamConfig {

    @Value("${im.feishu.app-id:}")
    private String appId;

    @Value("${im.feishu.app-secret:}")
    private String appSecret;

    private final FeishuStreamBotHandler handler;

    public FeishuStreamConfig(FeishuStreamBotHandler handler) {
        this.handler = handler;
    }

    private boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }

    /**
     * 飞书 REST API 客户端 — 用于回复消息、调用通讯录 API 等。
     *
     * <p>只有配置了 app-id + app-secret 时才真正创建；
     * 否则将 null 注入 Handler，后续 API 调用安全降级为空列表。
     */
    @Bean
    public Client feishuApiClient() {
        if (!isConfigured()) {
            log.info("[飞书Stream] 未配置 app-id/app-secret，跳过 REST API 客户端初始化。"
                    + "管理后台的飞书部门拉取等功能将不可用。");
            handler.setFeishuClient(null);
            return null;
        }
        log.info("[飞书Stream] 初始化 REST API 客户端: appId={}", appId);
        Client client = Client.newBuilder(appId, appSecret)
                .logReqAtDebug(true)
                .build();
        handler.setFeishuClient(client);
        return client;
    }

    /**
     * 飞书 WebSocket 长连接客户端 — 接收事件推送。
     *
     * <p>通过 initMethod=start 在 Spring 启动后自动连接，
     * destroyMethod=close 在关闭时断开。
     *
     * <p>未配置 app-id 时返回 null（不建立连接）。
     */
    @Bean(initMethod = "start", destroyMethod = "close")
    public com.lark.oapi.ws.Client feishuStreamClient() {
        if (!isConfigured()) {
            log.info("[飞书Stream] 未配置 app-id/app-secret，跳过 WebSocket 长连接。");
            return null;
        }
        // 注册消息事件处理器
        EventDispatcher eventDispatcher = EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        handler.handleMessageEvent(event);
                    }
                })
                .build();

        log.info("[飞书Stream] 初始化 WebSocket 长连接: appId={}", appId);
        return new com.lark.oapi.ws.Client.Builder(appId, appSecret)
                .eventHandler(eventDispatcher)
                .autoReconnect(true)
                .build();
    }
}
