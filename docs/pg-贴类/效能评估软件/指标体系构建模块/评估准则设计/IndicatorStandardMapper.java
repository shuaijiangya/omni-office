package com.yw.module.indicator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.indicator.domain.entity.IndicatorStandardEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author admin
 * @Description: 指标标准mapper
 * @Date: 2021/2/4 11:32
 */
@Mapper
public interface IndicatorStandardMapper extends BaseMapper<IndicatorStandardEntity> {

    /**
     * @param sid
     * @Author 刘乐法
     * @Description 通过方案id删除指标标准
     * @Return
     * @Date 2021/2/24 14:52
     */
    void deleteBySid(@Param("sid") String sid);

    /**
     * @param indicatorList
     * @Author 刘乐法
     * @Description 通过指标id集合删除指标标准
     * @Return
     * @Date 2021/2/24 14:52
     */
    void deleteByIndicatorId(@Param("indicatorList") List<String> indicatorList);

    /**
     * 根据指标id进行删除
     * @param indicatorId
     */
    void deleteIndicatorId(@Param("indicatorId") String indicatorId);

    /**
     * 根据指标id获取信息
     * @param indicatorId
     * @return
     */
    List<IndicatorStandardEntity> findByIndicatorId(String indicatorId);

    /**
     * 根据指标id获取信息
     * @param indicatorId
     * @return
     */
    List<IndicatorStandardEntity> findByIndicatorIdAndFlag(String indicatorId, @Param("flag") Integer flag);

    /**
     * 根据方案ID获取所有信息
     * @param sid
     * @return
     */
    List<IndicatorStandardEntity> findBySid(@Param("sid") String sid);
}
