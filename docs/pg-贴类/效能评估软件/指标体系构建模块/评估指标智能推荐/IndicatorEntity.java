package com.yw.module.indicator.domain.entity;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.yw.framework.common.base.support.BaseEntity;
import com.yw.framework.common.module.login.util.DateUtils;
import com.yw.module.datasource.domain.dto.SceneTableDTO;
import com.yw.framework.backMgr.dbTableGenerator.domain.dto.TableColsDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.springframework.util.StringUtils;


import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * @Author admin
 * @Description:指标表
 * @Date: 2020/11/17 11:01
 */
@Builder
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@ToString
//@Entity
//@Table(name = "yw_toses_indicator")
@ApiModel(value = "指标项实体")
@TableName("yw_toses_indicator")
public class IndicatorEntity extends BaseEntity {

    //@Id
    //@Column(name = "id")
    @TableId(value = "id",type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 指标名称
     **/
    @ApiModelProperty(value = "指标名称", name = "name")
    //@Column(name = "name")
    @TableField("INDICATOR_NAME")
    private String name;

    /**
     * 所属指标树
     **/
    @ApiModelProperty(value = "所属指标树", name = "treeId")
    //@Column(name = "tree_id")
    @TableField("tree_id")
    private String treeId;

    /**
     * 父id
     **/
    @ApiModelProperty(value = "父id", name = "pid")
    //@Column(name = "pid")
    @TableField("pid")
    private String pid;

    /**
     * 描述
     **/
    @ApiModelProperty(value = "描述", name = "describes")
    //@Column(name = "describes")
    @TableField("describes")
    private String describes;

    /**
     * 当前层级
     **/
    @ApiModelProperty(value = "当前层级", name = "level")
    //@Column(name = "level")
    @TableField("INDICATOR_LEVEL")
    private Integer level;

    /**
     * 创建时间
     **/
    @ApiModelProperty(value = "创建时间", name = "createDate")
    //@Column(name = "create_date")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    @TableField("create_time")
    private LocalDateTime createDate;

    /**
     * 创建人
     **/
    @ApiModelProperty(value = "创建人", name = "creator")
    //@Column(name = "creator")
    @TableField("creator")
    private String creator;

    /**
     * 更新时间
     **/
    @ApiModelProperty(value = "更新时间", name = "updateDate")
    //@Column(name = "update_date")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    @TableField("update_time")
    private LocalDateTime updateDate;

    @ApiModelProperty(value = "方案id", name = "sid")
    //@Column(name = "sid")
    @TableField("sid")
    private String sid;

    @ApiModelProperty(value = "排序字段", name = "sortno")
    //@Column(name = "sortno")
    @TableField("sortno")
    private Integer sortno;

    @ApiModelProperty(value = "是否使用计算模型模板", name = "isUsedCalcorTemplate")
    //@Column(name = "IS_USED_CALCOR_TEMPLATE")
    @TableField("IS_USED_CALCOR_TEMPLATE")
    private String isUsedCalcorTemplate;

    //@Column(name = "ALG_PARAM")
    @ApiModelProperty(value = "分析算法参数", name = "algParam")
    @TableField("ALG_PARAM")
    private String algParam;


    /**
     * 量纲
     */
    //@Column(name = "DIMENSION")
    @ApiModelProperty("必须（量纲）")
    @TableField("DIMENSION")
    private String dimension;

    /**
     * 最大值
     */
    //@Column(name = "MAX_VALUE")
    @ApiModelProperty("必须（最大值）")
    @TableField("MAX_VALUE")
    private String maxValue;

    /**
     * 最小值
     */
    //@Column(name = "MIN_VALUE")
    @ApiModelProperty("必须（最小值）")
    @TableField("MIN_VALUE")
    private String minValue;

    /**
     * 指标内涵
     */
    //@Column(name = "CONNOTATION")
    @ApiModelProperty("必须（指标内涵）")
    @TableField("CONNOTATION")
    private String connotation;

    /**
     * 指标方法
     */
    //@Column(name = "METHOD")
    @ApiModelProperty("必须（指标方法）")
    @TableField("INDICATOR_METHOD")
    private String method;

    //@Column(name = "INDICATOR_SCORE")
    @ApiModelProperty("专家打分值")
    @TableField("INDICATOR_SCORE")
    private String indicatorScore;

    /**
     * 关联指标
     */
    //@Column(name = "RELATION_INDICATORS")
    @ApiModelProperty("关联指标")
    @TableField("RELATION_INDICATORS")
    private String relationIndicatorStr;

    //@Column(name = "DIRECTORY_ID")
    @ApiModelProperty("目录ID")
    @TableField("DIRECTORY_ID")
    private String directoryId;

    //@Column(name = "TABLE_NAME")
    @ApiModelProperty("表名")
    @TableField("TABLE_NAME")
    private String tableName;

    @ApiModelProperty("指标类型 1-效益 2-成本 3-适中")
    @TableField("INDICATOR_TYPE")
    private Integer indicatorType;

    @ApiModelProperty("指标来源 1：来源与任务 2：手动新建")
    @TableField("INDICATOR_SOURCE")
    private Integer indicatorSource;

    @ApiModelProperty("指标来源ID")
    @TableField("INDICATOR_SOURCE_ID")
    private String indicatorSourceId;

    @ApiModelProperty("叶子指标算法名（只在 抗毁性页面使用）")
    @TableField("ALG_PARAM_CODE")
    private String algParamCode;

    //表字段集合
    //@Transient
    @TableField(exist = false)
    private List<TableColsDTO> tableCols;

    //数据源id
    //@Transient
    @TableField(exist = false)
    private String dsid;

    //数据库名称
    //@Transient
    @TableField(exist = false)
    private String dbName;

    //@Transient
    @TableField(exist = false)
    private String methodId;


    //@Transient
    @TableField(exist = false)
    private List<Float> paramList;

    //@Transient
    @TableField(exist = false)
    private List<String> relationIndicators;

    //@Transient
    @TableField(exist = false)
    private List<String> indicatorScores;

    //@Transient
    @TableField(exist = false)
    private SceneTableDTO sceneTableDTO;

    //@Transient
    @TableField(exist = false)
    private List<String> childPointer;

    @ApiModelProperty("指标遴选的指标值")
    @TableField("INDICATOR_VALUE")
    private String value;

    public void setParamList(List<Float> paramList) {
        algParam = JSON.toJSONString(paramList);
        this.paramList = paramList;
    }

    public List<Float> getParamList() {
        return JSON.parseArray(algParam, Float.class);
    }

    public void setRelationIndicators(List<String> relationIndicators) {
        relationIndicatorStr = JSON.toJSONString(relationIndicators);
        this.relationIndicators = relationIndicators;
    }

    public List<String> getRelationIndicators() {
        return JSON.parseArray(relationIndicatorStr, String.class);
    }

    public List<String> getIndicatorScores() {
        if (!StringUtils.isEmpty(indicatorScore)) {
            return JSON.parseArray(indicatorScore, String.class);
        }
        return Lists.newArrayList();
    }

    public void setIndicatorScores(List<String> indicatorScores) {
        indicatorScore = JSON.toJSONString(indicatorScores);
        this.indicatorScores = indicatorScores;
    }
}
