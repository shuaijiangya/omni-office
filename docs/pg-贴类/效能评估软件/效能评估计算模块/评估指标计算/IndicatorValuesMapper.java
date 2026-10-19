package com.yw.module.indicator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.indicator.domain.dto.IndicatorValuesWithNameDTO;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import com.yw.module.analysis.domain.vo.IndicatorValueWeightVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @Author admin
 * @Description: 指标值mapper
 * @Date: 2021/1/4 9:45
 */
@Mapper
public interface IndicatorValuesMapper extends BaseMapper<IndicatorValuesEntity> {

    /**
     * @param sid          方案id 必传
     * @param indicatorIds 指标id集合
     * @Author 刘乐法
     * @Description 通过分类id和指标id查询vo（标值id，指标值，指标名称，分类id）集合
     * @Return List<IndicatorValuesWithNameBO>
     * @Date 2021/1/4 14:41
     */
    List<IndicatorValuesWithNameDTO> findWithNameBySidIndicatorIdSceneId(@Param("sid") String sid, @Param("indicatorIds") List<String> indicatorIds,
                                                                         @Param("sceneIdList") List<String> sceneIdList);

    /**
     * @param sid          方案id 必传
     * @param indicatorIds 指标id集合
     * @param SSLIds 杀伤链id
     * @Description 通过分类id和指标id查询vo（标值id，指标值，指标名称，分类id）集合
     * @Return List<IndicatorValuesWithNameBO>
     */
    List<IndicatorValuesWithNameDTO> findWithNameBySidIndicatorIdSSLId(@Param("sid") String sid, @Param("indicatorIds") List<String> indicatorIds,
                                                                         @Param("SSLIds") List<String> SSLIds);

    /**
     * @param sid     方案id
     * @param sceneId 场景id
     * @Author 刘乐法
     * @Description 通过方案id、场景id查询
     * @Return List<IndicatorValuesEntity>
     * @Date 2021/2/24 14:53
     */
    List<IndicatorValuesEntity> findBySidSceneId(@Param("sid") String sid, @Param("sceneId") String sceneId);

    /**
     * @param sceneId 场景id
     * @param pid     指标父id
     * @Author 刘乐法
     * @Description 通过场景id、指标pid查询
     * @Return List<IndicatorValuesWithNameBO>
     * @Date 2021/2/24 14:53
     */
    List<IndicatorValuesWithNameDTO> findBySceneIdPid(@Param("sceneId") String sceneId, @Param("pid") String pid);

    List<IndicatorValueWeightVo> findIndicatorValueWeightVoBySceneId(@Param("sceneId") String sceneId);

    List<Map<String, Object>> getIndicatorValue(@Param("indicatorIds") List<String> indicatorIds);

    /**
     * 指标id获取对应值
     *
     * @param indicatorId
     * @return
     * @author lj
     */
    String findByIndicatorId(@Param("indicatorId") String indicatorId);

    /**
     * 获取指标值
     *
     * @param sceneId       sceneId
     * @param rootIndicator rootIndicator
     */
    String findByIndicatorIdAndSceneId(@Param("indicatorId") String rootIndicator, @Param("sceneId") String sceneId);

    /**
     * 查询指标的指标权重
     *
     * @param indicatorId
     * @return
     */
    IndicatorValueWeightVo findIndicatorValueWeightVoByIndicator(@Param("indicatorId") String indicatorId);
    /**
     * 查询指标树所有样本计算结果
     * @param sid          方案id 必传
     * @param indicatorTreeId 指标树id
     * @Description
     * @Date 2029/9/9
     */
    List<IndicatorValuesWithNameDTO> findWithNameBySidIndicatorTreeId(@Param("sid")String sid, @Param("indicatorTreeId")String indicatorTreeId);
    /**
     * 查询指标树单样本计算结果
     * @param sid          方案id 必传
     * @param indicatorTreeId 指标树id
     * @Description
     * @Date 2029/9/9
     */
    List<IndicatorValuesWithNameDTO> findWithNameBySidIndicatorTreeIdSceneId(@Param("sid")String sid, @Param("indicatorTreeId")String indicatorTreeId, @Param("sceneId")String sceneId);
}
