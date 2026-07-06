package com.knowbrain.feedback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.auth.JwtUtil;
import com.knowbrain.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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

    // ==================== 管理端 API ====================

    /**
     * 反馈统计汇总
     */
    @GetMapping("/admin/feedback/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LambdaQueryWrapper<Feedback> wrapper = buildTimeFilter(startDate, endDate);

        long total = feedbackMapper.selectCount(wrapper);
        long useful = feedbackMapper.selectCount(
                buildTimeFilter(startDate, endDate).eq(Feedback::getRating, "useful"));
        long useless = feedbackMapper.selectCount(
                buildTimeFilter(startDate, endDate).eq(Feedback::getRating, "useless"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("useful", useful);
        result.put("useless", useless);
        result.put("usefulRate", total > 0 ? Math.round(useful * 1000.0 / total) / 10.0 : 0);
        return Result.ok(result);
    }

    /**
     * 反馈列表（分页 + 筛选）
     */
    @GetMapping("/admin/feedback/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LambdaQueryWrapper<Feedback> wrapper = buildTimeFilter(startDate, endDate);

        if (rating != null && !rating.isBlank()) {
            wrapper.eq(Feedback::getRating, rating);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Feedback::getQuestion, keyword)
                            .or().like(Feedback::getAnswer, keyword));
        }
        wrapper.orderByDesc(Feedback::getCreateTime);

        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(page, size), wrapper);

        // 列表场景截断过长的答案字段
        result.getRecords().forEach(f -> {
            if (f.getAnswer() != null && f.getAnswer().length() > 300) {
                f.setAnswer(f.getAnswer().substring(0, 300) + "...");
            }
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());
        return Result.ok(data);
    }

    private LambdaQueryWrapper<Feedback> buildTimeFilter(String startDate, String endDate) {
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (startDate != null && !startDate.isBlank()) {
            wrapper.ge(Feedback::getCreateTime, LocalDateTime.parse(startDate + " 00:00:00", fmt));
        }
        if (endDate != null && !endDate.isBlank()) {
            wrapper.le(Feedback::getCreateTime, LocalDateTime.parse(endDate + " 23:59:59", fmt));
        }
        return wrapper;
    }
}
