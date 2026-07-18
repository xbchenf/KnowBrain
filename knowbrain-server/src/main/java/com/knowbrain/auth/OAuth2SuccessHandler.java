package com.knowbrain.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2/OIDC 登录成功处理器 — 查找或创建本地用户 → 签发 JWT → 重定向到前端回调页。
 *
 * <p>前端回调页 <code>/login/callback</code> 从 URL query 参数中提取 token 并完成登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2UserService oAuth2UserService;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend-url:http://localhost}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String platform = token.getAuthorizedClientRegistrationId();
        OAuth2User oauth2User = token.getPrincipal();

        // 提取 IdP 用户信息
        String platformUid = oauth2User.getName(); // OIDC sub / 钉钉 openId
        String name = oauth2User.getAttribute("name");
        String mobile = oauth2User.getAttribute("mobile");

        log.info("[OIDC] 登录成功: platform={}, platformUid={}, name={}", platform, platformUid, name);

        // 查找或创建本地用户
        SysUser user;
        try {
            user = oAuth2UserService.lookupOrCreate(platform, platformUid, name, mobile);
        } catch (IllegalStateException e) {
            // 用户被禁用等业务异常 → 重定向到前端错误页
            response.sendRedirect(frontendUrl + "/login?error="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            return;
        }

        // 签发自有 JWT
        String jwt = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), user.getUsername(), user.getRole());

        // 重定向到前端回调页，通过 URL query 参数传递 token
        String redirectUrl = String.format(
                "%s/login/callback?token=%s&refreshToken=%s&name=%s&role=%s",
                frontendUrl,
                URLEncoder.encode(jwt, StandardCharsets.UTF_8),
                URLEncoder.encode(refreshToken, StandardCharsets.UTF_8),
                URLEncoder.encode(user.getName() != null ? user.getName() : "", StandardCharsets.UTF_8),
                URLEncoder.encode(user.getRole(), StandardCharsets.UTF_8));

        response.sendRedirect(redirectUrl);
    }
}
