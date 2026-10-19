package com.yw.module.calculation.domain.dto;


import com.yw.module.calculation.domain.entity.CalcModuleEntity;
import com.yw.module.calculation.domain.entity.CalcorEntity;
import com.yw.module.calculation.domain.entity.CalCorValuesEntity;
import com.yw.module.calculation.domain.entity.IndicatorStandardValuesEntity;
import com.yw.module.plan.domain.entity.SceneEntity;
import com.yw.module.plan.domain.entity.SceneTableEntity;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorStandardEntity;
import com.yw.module.indicator.domain.entity.StandardLabelsEntity;
import com.yw.gather.dbinfomgr.entity.DatasrcEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author admin
 * @Description: 算子计算bo
 * @Date: 2020/12/9 16:20
 */
@Getter
@Setter
@ToString
public class CalcorCalculateDTO {

    //率性指标值
    private String natureValue;

    private String ArrayStr;//数组计算字符串

    private Object singleValue;//单个指标节点算子计算值

    private Object unNormalizedValue;//记录未归一值

    /**
     * {key:别名, value:该数据源下勾选的表字段结构}
     */
    private final Map<String, SceneTableEntity> sceneTableEntityMap;

    /**
     * 数据源map，k 数据源id,v 数据源实例
     */
    private final Map<String, DatasrcEntity> datasrcEntityMapById;

    /**
     * 当前场景实例
     */
    private final SceneEntity sceneEntity;

    /**
     * 需要更新的算子集合
     */
    private List<CalCorValuesEntity> updateEntityList;

    private List<IndicatorStandardValuesEntity> standardValuesNeedUpdate;

    /**
     * 指标标准map
     */
    private final Map<String, List<IndicatorStandardEntity>> glistIndicatorStandard;

    /**
     * 等级
     */
    private final List<StandardLabelsEntity> standardLabelsEntityList;

    /**
     * 需要进行模糊综合的指标集合
     */
    private final String evaluateALG;

    /**
     * 算子集合,k pid,v 算子集合
     */
    private final Map<String, List<CalcorEntity>> calcorEntityMapByPid;

    private final IndicatorEntity indicatorEntity;

    // todo 额外算法所需数据
    private CalcModuleEntity calcModuleEntity;
    private List<CalcorEntity> calcorEntities;

    public CalcorCalculateDTO(Map<String, SceneTableEntity> sceneTableEntityMap, Map<String, DatasrcEntity> datasrcEntityMapById,
                              SceneEntity sceneEntity, Map<String, List<CalcorEntity>> calcorEntityMapByPid, Map<String, List<IndicatorStandardEntity>> glistIndicatorStandard,
                              List<StandardLabelsEntity> standardLabelsEntityList, String evaluateALG, IndicatorEntity indicatorEntity) {
        this.sceneTableEntityMap = sceneTableEntityMap;
        this.datasrcEntityMapById = datasrcEntityMapById;
        this.sceneEntity = sceneEntity;
        this.updateEntityList = new ArrayList<>();
        this.calcorEntityMapByPid = calcorEntityMapByPid;
        this.glistIndicatorStandard = glistIndicatorStandard;
        this.standardLabelsEntityList = standardLabelsEntityList;
        this.evaluateALG = evaluateALG;
        this.indicatorEntity = indicatorEntity;
    }
}
