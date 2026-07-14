package com.knowbrain.im.adapter;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 钉钉 Stream 模式 — WebSocket 长连接客户端配置。
 *
 * <p>通过 AppKey + AppSecret 建立 WebSocket 连接，无需公网回调地址。
 * 消息由 {@link DingtalkStreamBotHandler} 处理。
 *
 * <h3>启用条件</h3>
 * {@code DINGTALK_ENABLED=true} 且 {@code DINGTALK_APP_KEY} 不为空。
 *
 * <h3>生命周期</h3>
 * Spring 容器启动时自动连接，关闭时自动断开。SDK 内置重连机制。
 *
 * @see <a href="https://open.dingtalk.com/document/resourcedownload/introduction-to-stream-mode">Stream 模式介绍</a>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "im.dingtalk.enabled", havingValue = "true")
public class DingtalkStreamConfig {

    @Value("${im.dingtalk.app-key}")
    private String appKey;

    @Value("${im.dingtalk.app-secret}")
    private String appSecret;

    private final DingtalkStreamBotHandler handler;

    public DingtalkStreamConfig(DingtalkStreamBotHandler handler) {
        this.handler = handler;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public OpenDingTalkClient dingtalkStreamClient() {
        log.info("[钉钉Stream] 初始化 WebSocket 连接: appKey={}", appKey);
        return OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(appKey, appSecret))
                .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, handler)
                .build();
    }
}
