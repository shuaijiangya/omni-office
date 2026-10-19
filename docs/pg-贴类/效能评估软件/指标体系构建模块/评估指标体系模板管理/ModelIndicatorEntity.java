package com.yw.module.indicator.domain.entity;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yw.framework.common.module.login.util.DateUtils;
import com.yw.module.plan.domain.dto.ExcelDTO;
import com.yw.framework.common.base.support.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;


import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author admin
 * @Description: 指标模板实体
 * @Date: 2021/1/29 15:31
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
//@Entity
@TableName(value = "YW_TOSES_MODEL_INDICATOR")
public class ModelIndicatorEntity extends BaseEntity {

    /**
     * 主键
     */
    //@Id
    private String id;
    /**
     * 指标名称
     */
    @TableField("NAME")
    private String name;
    /**
     * 指标树ID
     */
    @TableField("TREE_ID")
    private String treeId;
    /**
     * 父节点
     */
    @TableField("PID")
    private String pid;
    /**
     * 描述
     */
    @TableField("DESCRIBES")
    private String describes;
    /**
     * 级别
     */
    @TableField("LEVEL_VAL")
    private Integer level;
    /**
     * 创建时间
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    @TableField("CREATE_TIME")
    private LocalDateTime createDate;
    /**
     * 创建人
     */
    @TableField("CREATOR")
    private String creator;
    /**
     * 修改时间
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    @TableField("UPDATE_TIME")
    private LocalDateTime updateDate;
    /**
     * 方案标记
     */
    @TableField("SID")
    @ApiModelProperty("方案id")
    private String sid;
    /**
     * 业务记录
     */
    @TableField("APPRANCE_ID")
    private String appranceId;

    /**
     * 量纲
     */
    @TableField("DIMENSION")
    @ApiModelProperty("必须（量纲）")
    private String dimension;

    /**
     * 最大值
     */
    @TableField("MAX_VALUE")
    @ApiModelProperty("必须（最大值）")
    private String maxValue;

    /**
     * 最小值
     */
    @TableField("MIN_VALUE")
    @ApiModelProperty("必须（最小值）")
    private String minValue;

    /**
     * 指标内涵
     */
    @TableField("CONNOTATION")
    @ApiModelProperty("必须（指标内涵）")
    private String connotation;

    /**
     * 指标方法
     */
    @TableField("METHOD")
    @ApiModelProperty("必须（指标方法）")
    private String method;

    /**
     * 关联指标
     */
    @TableField("RELATION_INDICATORS")
    @ApiModelProperty("关联指标")
    private String relationIndicators;
    /**
     * 原始指标
     */
    @TableField("SOURCE_INDICATOR")
    private String sourceIndicator;


    public static ModelIndicatorEntity valueOf(String uuid, ExcelDTO s, String treeId, String pid, int level) {
        ModelIndicatorEntity entity = new ModelIndicatorEntity();
        entity.id = uuid;
        entity.creator = "admin";
        entity.name = s.getName();
        entity.treeId = treeId;
        entity.createDate = LocalDateTime.now();
        entity.pid = pid;
        entity.level = level;
        entity.dimension = s.getDimension();
        entity.maxValue = s.getMaxValue();
        entity.minValue = s.getMinValue();
        entity.connotation = s.getConnotation();
        entity.method = s.getMethod();
        return entity;
    }

    //@Transient
    @TableField(exist = false)
    private List<String> relationIndicatorList;

    public void setRelationIndicatorList(List<String> relationIndicatorList) {
        relationIndicators = JSON.toJSONString(relationIndicatorList);
        this.relationIndicatorList = relationIndicatorList;
    }

    public List<String> getRelationIndicatorList() {
        return JSON.parseArray(relationIndicators, String.class);
    }
}
