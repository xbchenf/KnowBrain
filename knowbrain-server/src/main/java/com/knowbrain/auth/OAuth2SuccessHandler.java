package com.knowbrain.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * OAuth2/OIDC 登录成功处理器。
 *
 * <p>流程：
 * <ol>
 *   <li>从 IdP 获取用户身份 → 查找或创建本地用户 → 签发 JWT</li>
 *   <li>将 token 存储到 Redis（临时 code，60 秒 TTL）</li>
 *   <li>重定向到前端回调页，URL 只携带一次性 code（不暴露 token）</li>
 *   <li>前端用 code 调 /auth/oidc-exchange 换取 token</li>
 * </ol>
 *
 * <p>Token 不经过 URL query 参数和浏览器历史，防止泄露到服务器日志和 Referer 头。
 */
@Slf4j
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final String OIDC_CODE_PREFIX = "kb:oidc:code:";
    private static final Duration CODE_TTL = Duration.ofSeconds(60);

    private final OAuth2UserService oAuth2UserService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    @Value("${app.frontend-url:http://localhost}")
    private String frontendUrl;

    public OAuth2SuccessHandler(OAuth2UserService oAuth2UserService,
                                JwtUtil jwtUtil,
                                StringRedisTemplate redis) {
        this.oAuth2UserService = oAuth2UserService;
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String platform = token.getAuthorizedClientRegistrationId();
        OAuth2User oauth2User = token.getPrincipal();

        // 提取 IdP 用户信息
        String platformUid = oauth2User.getName();
        String name = oauth2User.getAttribute("name");
        String mobile = oauth2User.getAttribute("mobile");

        log.info("[OIDC] 登录成功: platform={}, platformUid={}, name={}", platform, platformUid, name);

        // 查找或创建本地用户
        SysUser user;
        try {
            user = oAuth2UserService.lookupOrCreate(platform, platformUid, name, mobile);
        } catch (IllegalStateException e) {
            response.sendRedirect(frontendUrl + "/login?error="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            return;
        }

        // 签发 JWT
        String jwt = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), user.getUsername(), user.getRole());

        // 生成一次性交换码，将 token 存入 Redis
        String code = UUID.randomUUID().toString().replace("-", "");
        String redisKey = OIDC_CODE_PREFIX + code;
        String redisValue = jwt + "\n" + refreshToken + "\n"
                + (user.getName() != null ? user.getName() : "")
                + "\n" + user.getRole();
        redis.opsForValue().set(redisKey, redisValue, CODE_TTL);

        // 重定向到前端回调页，URL 仅携带一次性 code
        String redirectUrl = frontendUrl + "/login/callback?code=" + code;
        response.sendRedirect(redirectUrl);
    }
}
