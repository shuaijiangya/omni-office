package com.yw.module.indicator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yw.framework.common.base.support.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;


import java.util.Objects;

/**
 * @Author admin
 * @Description: 指标计算值
 * @Date: 2020/12/23 11:33
 */
@AllArgsConstructor
@Builder
@Getter
@Setter
@NoArgsConstructor
@ToString
@TableName("YW_TOSES_INDICATOR_VALUES")
public class IndicatorValuesEntity extends BaseEntity {

    @TableId(value = "ID",type = IdType.ASSIGN_UUID)
    private String id;

    @ApiModelProperty(value = "方案id", name = "sid")
    //@Id
    //@Column(name = "sid")
    @TableField(value = "SID")
    private String sid;

    @ApiModelProperty(value = "指标id", name = "indicatorId")
    //@Id
    //@Column(name = "indicator_id")
    @TableField(value = "indicator_id")
    private String indicatorId;

    /** 样本--杀伤链id*/
    @ApiModelProperty(value = "场景id")
    //@Id
    //@Column(name = "scene_id")
    @TableField(value = "scene_id")
    private String sceneId;

    @ApiModelProperty(value = "归一值", name = "normalizedValue")
    //@Column(name = "NORMALIZED_VALUE")
    @TableField("NORMALIZED_VALUE")
    private String normalizedValue;

    @ApiModelProperty(value = "未归一值", name = "unNormalizedValue")
    //@Column(name = "UN_NORMALIZED_VALUE")
    @TableField("UN_NORMALIZED_VALUE")
    private String unNormalizedValue;

    @ApiModelProperty(value = "指标评价值", name = "standardLabelValue")
    //@Column(name = "STANDARD_LABEL_VALUE")
    @TableField("STANDARD_LABEL_VALUE")
    private String standardLabelValue;

    @ApiModelProperty(value = "指标评价名称", name = "standardLabelName")
    //@Column(name = "STANDARD_LABEL_NAME")
    @TableField("STANDARD_LABEL_NAME")
    private String standardLabelName;

    //    NATURE_VALUE
    @ApiModelProperty(value = "率性指标值", name = "natureValue")
    //@Column(name = "NATURE_VALUE")
    @TableField("NATURE_VALUE")
    private String natureValue;

    @ApiModelProperty(value = "正理想解值", name = "dplus")
    @TableField("DPLUS")
    private String dplus;

    @ApiModelProperty(value = "负理想解值", name = "dminus")
    @TableField("DMINUS")
    private String dminus;

    @ApiModelProperty(value = "主成分分析值", name = "pclValue")
    @TableField("PCL_VALUE")
    private String pclValue;

    @ApiModelProperty("杀伤链计算指标错误信息")
    @TableField("SSL_ERROR_MSG")
    private String sslErrorMsg;

    //二维数组字符串
    @JsonIgnore
    @TableField(exist = false)
    //@Transient
    private String arrayStr;

    /**
     * 重写hashcode和equals方法在做map的key中可以实现覆盖
     **/
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndicatorValuesEntity that = (IndicatorValuesEntity) o;
        return sid.equals(that.sid) &&
                indicatorId.equals(that.indicatorId) &&
                sceneId.equals(that.sceneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sid, indicatorId, sceneId);
    }
}
