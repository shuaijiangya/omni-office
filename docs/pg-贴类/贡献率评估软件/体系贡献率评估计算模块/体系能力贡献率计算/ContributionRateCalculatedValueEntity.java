package com.yw.module.missionScene.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yw.common.utils.UUIDGenerator;
import com.yw.framework.common.base.support.BaseEntity;
import com.yw.framework.common.defines.constant.IntegerPool;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@ToString
//@Entity
@NoArgsConstructor
//@Table(name = "YW_TOSES_CONTRIBUTION_RATE_CALCULATED_VALUE")
@TableName("YW_TOSES_CONTRIBUTION_RATE_CALCULATED_VALUE")
public class ContributionRateCalculatedValueEntity extends BaseEntity {

    //@Id
    //@Column(name = "ID")
    private String id;

    //@Column(name = "COMPETE_SCENE_ID")
    private String competeSceneId;

    //@Column(name = "MISSION_TASK_ID")
    private String missionTaskId;

    //@Column(name = "SCENE_SCENARIO_ID")
    private String sceneScenarioId;

    //@Column(name = "INDICATOR_ID")
    private String indicatorId;

    //@Column(name = "CALCULATED_VALUE")
    private String calculatedValue;

    // 结果值基础 综合值
    private String sumB;
    // 结果值对比 综合值
    private String sumC;

    //样本id
    private String sceneId;
    private String benchmarkSceneId;

    /**
     * 贡献率类型（1-提升度的体系贡献率算法，2-满足度的体系贡献率算法，3-加权综合体系贡献率算法）
     * ContributionRateType
     */
    @ApiModelProperty("贡献率类型（1-提升度的体系贡献率算法，2-满足度的体系贡献率算法，3-加权综合体系贡献率算法）")
    @TableField(value = "TYPE")
    private Integer type;

    public static ContributionRateCalculatedValueEntity valueOf(String missionTask, String sceneScenarioId, String competeSceneId, String indicatorId, Double resultValue) {
        ContributionRateCalculatedValueEntity entity = new ContributionRateCalculatedValueEntity();
        entity.missionTaskId = missionTask;
        entity.competeSceneId = competeSceneId;
        entity.sceneScenarioId = sceneScenarioId;
        entity.indicatorId = indicatorId;
        entity.calculatedValue = resultValue + "";
        entity.setId(UUIDGenerator.getUUID());
        return entity;
    }
}
