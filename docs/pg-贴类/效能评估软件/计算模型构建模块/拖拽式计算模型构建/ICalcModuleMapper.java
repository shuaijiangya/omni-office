package com.yw.module.calculation.indicatorCalculation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.calculation.domain.entity.CalcModuleEntity;
import com.yw.module.calculation.domain.vo.CalcModuleVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author x l
 * @description 指标算法管理
 * @date 2021-11-13 9:58
 */
@Mapper
public interface ICalcModuleMapper extends BaseMapper<CalcModuleEntity> {
    /**
     * 获取所有指标算法
     *
     * @return
     */
    List<CalcModuleVo> findAll();

    /**
     * 获取默认方法模型id
     * @param sid
     * @return
     */
    String findDefaultCalcModuleId(@Param("sid") String sid);

    /**
     * 根据方案id获取当下所有指标算法信息
     * @param sid
     * @return
     */
    List<CalcModuleVo> showMethodCalc(@Param("sid") String sid);
}
