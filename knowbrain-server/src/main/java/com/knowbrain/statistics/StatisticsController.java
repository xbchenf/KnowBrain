package com.knowbrain.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 使用统计 API — Dashboard 数据
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class StatisticsController {

    private final SearchLogMapper searchLogMapper;

    /**
     * 综合统计摘要
     */
    @GetMapping
    public Result<Map<String, Object>> stats() {
        // 总查询数
        long totalQueries = searchLogMapper.selectCount(null);

        // FAQ 命中数和命中率
        long faqHits = searchLogMapper.selectCount(
                new LambdaQueryWrapper<SearchLog>().eq(SearchLog::getFaqMatched, 1));
        double faqHitRate = totalQueries > 0 ? Math.round(faqHits * 1000.0 / totalQueries) / 10.0 : 0;

        // 置信度分布
        long highCount = searchLogMapper.selectCount(
                new LambdaQueryWrapper<SearchLog>().eq(SearchLog::getConfidence, "high"));
        long mediumCount = searchLogMapper.selectCount(
                new LambdaQueryWrapper<SearchLog>().eq(SearchLog::getConfidence, "medium"));
        long lowCount = searchLogMapper.selectCount(
                new LambdaQueryWrapper<SearchLog>().eq(SearchLog::getConfidence, "low"));

        // 每日趋势（近 30 天）
        List<Map<String, Object>> dailyTrend = searchLogMapper.dailyTrend(30);

        // 热门问题 Top 10
        List<Map<String, Object>> topQuestions = searchLogMapper.topQuestions(10);

        // 热门文档 Top 10（从 source_titles 聚合）
        List<Map<String, Object>> topSources = computeTopSources();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalQueries", totalQueries);
        result.put("faqHits", faqHits);
        result.put("faqHitRate", faqHitRate);
        result.put("confidenceDist", Map.of("high", highCount, "medium", mediumCount, "low", lowCount));
        result.put("dailyTrend", dailyTrend);
        result.put("topQuestions", topQuestions);
        result.put("topSources", topSources);
        return Result.ok(result);
    }

    /**
     * 从搜索日志的 source_titles 字段聚合热门文档 Top 10
     */
    private List<Map<String, Object>> computeTopSources() {
        // 取近期的搜索日志
        var wrapper = new LambdaQueryWrapper<SearchLog>()
                .isNotNull(SearchLog::getSourceTitles)
                .ne(SearchLog::getSourceTitles, "")
                .orderByDesc(SearchLog::getCreateTime)
                .last("LIMIT 500");
        List<SearchLog> logs = searchLogMapper.selectList(wrapper);

        Map<String, Integer> titleCount = new LinkedHashMap<>();
        for (SearchLog log : logs) {
            if (log.getSourceTitles() != null) {
                for (String title : log.getSourceTitles().split(",")) {
                    String t = title.trim();
                    if (!t.isEmpty()) {
                        titleCount.merge(t, 1, Integer::sum);
                    }
                }
            }
        }

        return titleCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("title", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
