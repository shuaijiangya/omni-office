package com.yw.module.indicator.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yw.framework.common.module.login.util.DateUtils;
import com.yw.module.indicator.domain.enums.IndicatorCriteriaType;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDateTime;

/**
 * @Description:指标准则实体类
 * @version: v1.0
 * @author: superman
 * @time: 2023-04-12 15:15:28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
//@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@Table(name = "YW_TOSES_INDICATOR_CRITERIA")
@TableName("YW_TOSES_INDICATOR_CRITERIA")
public class IndicatorCriteria {

    private static final long serialVersionUID = 1681283728064L;

    /**
     * 最大值
     */
    //@Column(name = "MAX_VALUE")
    @ApiModelProperty(name = "maxValue", value = "最大值")
    @TableField("MAX_VALUE")
    private String maxValue;

    /**
     * 场景id
     */
    //@Column(name = "SCENE_ID")
    @ApiModelProperty(name = "sceneId", value = "场景id")
    @TableField("SCENE_ID")
    private String sceneId;

    /**
     * 创建时间
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    //@Column(name = "CREATE_DATE")
    @ApiModelProperty(name = "createDate", value = "创建时间")
    @TableField("CREATE_TIME")
    private LocalDateTime createDate;

    /**
     * 指标id
     */
    //@Column(name = "INDICATOR_ID")
    @ApiModelProperty(name = "indicatorId", value = "指标id")
    @TableField("INDICATOR_ID")
    private String indicatorId;

    /**
     * 任务id
     */
    //@Column(name = "TASK_ID")
    @ApiModelProperty(name = "taskId", value = "任务id")
    @TableField("TASK_ID")
    private String taskId;

    /**
     * 更新时间
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    //@Column(name = "UPDATE_DATE")
    @ApiModelProperty(name = "updateDate", value = "更新时间")
    @TableField("UPDATE_TIME")
    private LocalDateTime updateDate;

    /**
     * 准则类型，1：优，2：良，3：中，4：差
     */
    //@Column(name = "TYPE")
    @ApiModelProperty(name = "type", value = "准则类型，1：优，2：良，3：中，4：差")
    @TableField("TYPE")
    private Integer type;

    /**
     * 最小值
     */
    //@Column(name = "MIN_VALUE")
    @ApiModelProperty(name = "minValue", value = "最小值")
    @TableField("MIN_VALUE")
    private String minValue;

    /**
     * 主键
     */
    //@Id
    //@Column(name = "ID")
    @ApiModelProperty(name = "id", value = "主键")
    private String id;


    @ApiModelProperty("原始指标准则标志")
    //@Column(name = "SOURCE_INDICATOR_STANDARD")
    @TableField("SOURCE_INDICATOR_STANDARD")
    private String sourceIndicatorStandard;

    @ApiModelProperty("指标准则输出值")
    @TableField("FUNC")
    @Length(max = 50, message = "输出值最长为50个字符")
    private String func;

    @ApiModelProperty(name = "typeCode", value = "准则类型描述")
    public String getTypeCode() {
        IndicatorCriteriaType[] values = IndicatorCriteriaType.values();
        for (IndicatorCriteriaType value : values) {
            if (value.getCode() == this.type) {
                return value.getDesc();
            }
        }
        return null;
    }

    /**
     * 优良中差--从大到小校验
     *
     * @param other
     * @return false：代表区间无冲突
     */
    public boolean isOverlappingLargeToSmall(IndicatorCriteria other) {



        String thisMinValue = this.minValue;
        String otherMaxValue = other.maxValue;

        if (thisMinValue == null) {
            thisMinValue = "-";
        }
        if (otherMaxValue == null) {
            otherMaxValue ="+";
        }

        try {
            /**
             * this.minValue < other.maxValue
             */
            return /*Double.compare(thisMinValue, otherMaxValue) == 0 ||*/ Double.compare(Double.parseDouble(thisMinValue), Double.parseDouble(otherMaxValue)) == -1;
        } catch (NumberFormatException e) {
            //e.printStackTrace();
            return false;
        }
    }

    /**
     * 优良中差--从小到大校验
     *
     * @param other
     * @return false：代表区间无冲突
     */
    public boolean isOverlappingSmallToLarge(IndicatorCriteria other) {


        String thisMaxValue = this.maxValue;
        String otherMinValue = other.minValue;

        if (thisMaxValue == null) {
            thisMaxValue = "+";
        }
        if (otherMinValue == null) {
            otherMinValue = "-";
        }

        /**
         * this.thisMaxValue > other.otherMinValue
         */

        try {
            return /*Double.compare(thisMaxValue, otherMinValue) == 0 ||*/ Double.compare(Double.parseDouble(thisMaxValue), Double.parseDouble(otherMinValue)) == 1;
        } catch (NumberFormatException e) {
            //e.printStackTrace();
            return false;
        }
    }

    /**
     * 区间存在未设置的值
     * @param other
     * @return false：代表区间无冲突
     */
   /* public boolean isOverlapping(IndicatorCriteria other) {
       if (null == this.minValue && null != this.maxValue && (null != other.minValue || null != other.maxValue)) {
            return true;
        } else if (null == other.maxValue && null != other.minValue && (null != this.minValue || null != this.maxValue)) {
            return true;
        }
        return false;
    }*/

}
