package com.knowbrain.feedback;

import com.knowbrain.auth.JwtUtil;
import com.knowbrain.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 答案反馈 — 用户对 AI 回答 👍/👎
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackMapper feedbackMapper;
    private final JwtUtil jwtUtil;

    @PostMapping("/feedback")
    public Result<Void> submit(@RequestBody Map<String, Object> body,
                                HttpServletRequest request) {
        Feedback feedback = new Feedback();
        feedback.setQuestion((String) body.getOrDefault("question", ""));
        feedback.setAnswer((String) body.getOrDefault("answer", ""));
        feedback.setRating((String) body.getOrDefault("rating", "useful"));
        feedback.setComment((String) body.getOrDefault("comment", null));

        // 从 JWT Token 提取真实 userId（不信任请求体）
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            Map<String, Object> claims = jwtUtil.verifyToken(authHeader.substring(7));
            if (claims != null && claims.get("userId") != null) {
                feedback.setUserId(((Number) claims.get("userId")).longValue());
            }
        }

        feedbackMapper.insert(feedback);
        log.info("反馈已记录: rating={}", feedback.getRating());
        return Result.ok("感谢反馈", null);
    }
}
