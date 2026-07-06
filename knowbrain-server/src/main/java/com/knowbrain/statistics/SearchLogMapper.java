package com.knowbrain.statistics;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SearchLogMapper extends BaseMapper<SearchLog> {

    /** 每日问答趋势（近 N 天） */
    @Select("SELECT DATE(create_time) as date, COUNT(*) as count FROM kb_search_log " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> dailyTrend(int days);

    /** Top N 热门问题 */
    @Select("SELECT question, COUNT(*) as count FROM kb_search_log " +
            "GROUP BY question ORDER BY count DESC LIMIT #{n}")
    List<Map<String, Object>> topQuestions(int n);
}
