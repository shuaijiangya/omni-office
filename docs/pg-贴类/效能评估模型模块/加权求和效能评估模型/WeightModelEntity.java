package com.yw.module.calculation.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.yw.framework.common.base.support.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yw.framework.common.module.login.util.DateUtils;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;


import java.time.LocalDateTime;
import java.util.Date;

/**
 * @Author admin
 * @Description: 权重算法模型
 * @Date: 2020/12/2 10:27
 */
@AllArgsConstructor
@Builder
@Getter
@Setter
@NoArgsConstructor
@ToString
//@Entity
//@Table(name = "yw_toses_weight_model")
@TableName("yw_toses_weight_model")
@ApiModel(value = "权重算法模型")
public class WeightModelEntity extends BaseEntity {
    /**
     * 主键
     */
    //@Id
    //@Column(name = "id")
    private String id;
    /**
     * 所属方案id
     */
    @ApiModelProperty(value = "所属方案id", name = "sid")
    //@Column(name = "sid")
    private String sid;
    /**
     * 指标id
     */
    @ApiModelProperty(value = "指标id", name = "indicatorId")
    //@Column(name = "indicator_id")
    private String indicatorId;
    /**
     * 名称
     */
    @ApiModelProperty(value = "名称", name = "name")
    //@Column(name = "name")
    private String name;
    /**
     * 算法编号
     */
    @ApiModelProperty(value = "算法编号", name = "code")
    //@Column(name = "code")
    private String code;
    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间", name = "createDate")
    //@Column(name = "create_date")
    @TableField(value = "create_time")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    private LocalDateTime createDate;
    /**
     * 创建人
     */
    @ApiModelProperty(value = "创建人", name = "creator")
    //@Column(name = "creator")
    private String creator;
    /**
     * 权重算法json
     */
    @ApiModelProperty(value = "权重算法json", name = "algorithmJson")
    //@Column(name = "algorithm_json")
    private String algorithmJson;
    /**
     * 更新时间
     */
    @ApiModelProperty(value = "更新时间", name = "updateDate")
    //@Column(name = "update_date")
    @TableField(value = "update_time")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    private LocalDateTime updateDate;
    /**
     * 算法模型ID
     */
    @ApiModelProperty("算法模型ID")
    //@Column(name = "CALC_MODULE_ID")
    private String calcModuleId;
    /**
     * 专家个数
     */
    @ApiModelProperty("专家个数")
    //@Column(name = "SCORE_NUMBER")
    private Integer scoreNumber;
    /**
     * 最大值
     */
    @ApiModelProperty("最大值")
    //@Column(name = "MIN")
    private String min;
    /**
     * 最小值
     */
    @ApiModelProperty("最小值")
    //@Column(name = "MAX")
    private String max;
}
