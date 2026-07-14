package com.knowbrain.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowbrain.evaluation.entity.EvaluationResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单题评测结果 Mapper
 */
@Mapper
public interface EvaluationResultMapper extends BaseMapper<EvaluationResult> {
}
