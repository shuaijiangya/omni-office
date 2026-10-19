package com.yw.module.plan.domain.dto;/*
 *@ClassName ContributionRateExportDTO
 *@Description TODO
 *@Author ww
 *@Dete 2023/5/29  10:16
 *@version 1.0
 */


import io.swagger.annotations.ApiModelProperty;
import lombok.*;


import java.io.Serializable;

/**
 * 体系贡献率导出报告DTO
 */
@Data
public class ContributionRateExportDTO implements Serializable {

    private static final long serialVersionUID = -3887272806544272997L;


    //武器系统使命任务 是否显示 0 不显示 1 显示
    @ApiModelProperty(value = "武器系统使命任务",notes = "0 不显示, 1 显示")
    private Integer weaponsSystemIsShow;

    //对抗场景设置 是否显示 0 不显示 1 显示
    @ApiModelProperty(value = "对抗场景设置",notes = "1 显示")
    private Integer competeSceneIsShow;

    //对抗场景多种情况分析 是否显示 0 不显示 1 显示
    @ApiModelProperty(value = "对抗场景多种情况设置",notes = "1 显示")
    private Integer manyCompeteSceneIsShow;

    //评估方案描述 是否显示 0 不显示 1 显示
    @ApiModelProperty(value = "评估方案描述",notes = "1 显示")
    
    private Integer pgSchemeIsShow;

    //评估指标体系 是否显示 0 不显示 1 显示
    @ApiModelProperty(value = "评估指标体系",notes = "1 显示")
    
    private Integer pgIndicatorIsShow;

    //评估样本结构 是否显示 0 不显示 1 显示
    @ApiModelProperty(value = "评估样本结构",notes = "1 显示")
    
    private Integer pgSampleStructureIsShow;

    //评估结果与结论 是否显示 0 不显示 1 显示
    @ApiModelProperty(value = "评估结果与结论",notes = "1 显示")
    
    private Integer pgResultIsShow;

    @ApiModelProperty(value = "系统类型")
    
    private String type;

    @ApiModelProperty(value = "方案ID")
    
    private String sid;

    @ApiModelProperty(value = "场景ID")
    
    private String sceneId;

    @ApiModelProperty(value = "导出类型")
    private String exportType;

    @ApiModelProperty(value = "评估结论")
    private String summary;
}
