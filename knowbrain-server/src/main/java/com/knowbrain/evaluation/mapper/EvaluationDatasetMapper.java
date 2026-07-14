package com.knowbrain.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowbrain.evaluation.entity.EvaluationDataset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测数据集 Mapper
 */
@Mapper
public interface EvaluationDatasetMapper extends BaseMapper<EvaluationDataset> {
}
