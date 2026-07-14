package com.knowbrain.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowbrain.evaluation.entity.EvaluationRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测运行记录 Mapper
 */
@Mapper
public interface EvaluationRunMapper extends BaseMapper<EvaluationRun> {
}
