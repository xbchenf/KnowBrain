package com.knowbrain.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowbrain.evaluation.entity.EvaluationQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测问题 Mapper
 */
@Mapper
public interface EvaluationQuestionMapper extends BaseMapper<EvaluationQuestion> {
}
