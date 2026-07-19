package com.knowbrain.auth;

import jakarta.servlet.http.Cookie;
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

        String platformUid = oauth2User.getName();
        String name = oauth2User.getAttribute("name");
        String mobile = oauth2User.getAttribute("mobile");

        log.info("[OIDC] 登录成功: platform={}, platformUid={}, name={}", platform, platformUid, name);

        // 分支 1: 关联账号流程（Cookie 中有 kb_link_token）
        String linkToken = getCookie(request, "kb_link_token");
        if (linkToken != null && !linkToken.isBlank()) {
            handleAccountLinking(response, linkToken, platform, platformUid, name, mobile);
            return;
        }

        // 分支 2: 无手机号 → 重定向到绑定页
        if (mobile == null || mobile.isBlank()) {
            String bindCode = UUID.randomUUID().toString().replace("-", "");
            redis.opsForValue().set("kb:oidc:bind:" + bindCode,
                    platform + "\n" + platformUid + "\n" + (name != null ? name : ""),
                    Duration.ofMinutes(5));
            response.sendRedirect(frontendUrl + "/login/bind?code=" + bindCode);
            return;
        }

        // 分支 3: 正常流程（有手机号）
        completeLogin(response, platform, platformUid, name, mobile);
    }

    /** 正常 OAuth2 登录完成（有手机号） */
    private void completeLogin(HttpServletResponse response,
                               String platform, String platformUid, String name, String mobile) throws IOException {
        SysUser user;
        try {
            user = oAuth2UserService.lookupOrCreate(platform, platformUid, name, mobile);
        } catch (IllegalStateException e) {
            response.sendRedirect(frontendUrl + "/login?error="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            return;
        }

        String jwt = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), user.getUsername(), user.getRole());

        String code = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(OIDC_CODE_PREFIX + code,
                jwt + "\n" + refreshToken + "\n"
                        + (user.getName() != null ? user.getName() : "")
                        + "\n" + user.getRole(),
                CODE_TTL);

        response.sendRedirect(frontendUrl + "/login/callback?code=" + code);
    }

    /** 关联账号流程：从 Redis 读取 userId，绑定 OAuth2 身份 */
    private void handleAccountLinking(HttpServletResponse response,
                                       String linkToken, String platform,
                                       String platformUid, String name, String mobile) throws IOException {
        String redisKey = "kb:link:" + linkToken;
        String value = redis.opsForValue().get(redisKey);
        // 立即删除 Redis key + 清除 Cookie
        redis.delete(redisKey);
        Cookie clearCookie = new Cookie("kb_link_token", "");
        clearCookie.setPath("/");
        clearCookie.setMaxAge(0);
        response.addCookie(clearCookie);

        if (value == null) {
            response.sendRedirect(frontendUrl + "/settings?link_error=expired");
            return;
        }

        String[] parts = value.split("\n", 2);
        if (parts.length < 2) {
            response.sendRedirect(frontendUrl + "/settings?link_error=invalid");
            return;
        }

        Long userId = Long.parseLong(parts[0]);
        String expectedPlatform = parts[1];

        if (!platform.equals(expectedPlatform)) {
            response.sendRedirect(frontendUrl + "/settings?link_error=platform_mismatch");
            return;
        }

        oAuth2UserService.linkIdentity(userId, platform, platformUid, name, mobile);
        log.info("[OIDC] 关联账号成功: userId={}, platform={}, platformUid={}", userId, platform, platformUid);
        response.sendRedirect(frontendUrl + "/settings?linked=" + platform);
    }

    private String getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
