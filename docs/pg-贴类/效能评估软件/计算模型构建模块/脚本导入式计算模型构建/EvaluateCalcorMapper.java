package com.yw.module.calculation.indicatorCalculation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.calculation.indicatorCalculation.domain.dto.EvaluateCalcorDTO;
import com.yw.module.calculation.indicatorCalculation.domain.entity.EvaluateCalcorEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 算子数据交互层
 */
@Mapper
public interface EvaluateCalcorMapper extends BaseMapper<EvaluateCalcorEntity> {

    List<EvaluateCalcorEntity> list(EvaluateCalcorDTO dto);
}
