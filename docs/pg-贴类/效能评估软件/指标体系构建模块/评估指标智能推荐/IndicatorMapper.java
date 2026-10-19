package com.yw.module.indicator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.indicator.domain.dto.IndicatorDTO;
import com.yw.module.calculation.domain.dto.ChildWeightResult;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author zl
 * @date 2023/4/12 16:21
 * @Description: 指标项实体mapper
 */
@Mapper
public interface IndicatorMapper extends BaseMapper<IndicatorEntity> {
    List<IndicatorDTO> indicatorList();
    List<IndicatorDTO> indicatorTestList();

    List<ChildWeightResult> selectChildWeight(@Param("indicatorId") String indicatorId);

    @Select(value = "update YW_TOSES_INDICATOR YTI SET YTI.TABLE_NAME = :tableName , YTI.DIRECTORY_ID = :directoryId WHERE id = :id")
    void saveTbAndDid(String tableName, String directoryId, String id);
}
