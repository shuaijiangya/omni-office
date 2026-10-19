package com.yw.module.calculation.domain.entity;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.yw.common.defines.enums.CalModuleType;
import com.yw.common.utils.UUIDGenerator;
import com.yw.framework.common.module.login.util.DateUtils;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * @Author admin
 * @Description: 指标计算模型
 * @Date: 2020/12/2 10:27
 */
@Builder
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@ToString
//@Entity
//@Table(name = "yw_toses_calc_module")
@ApiModel(value = "指标计算模型")
@TableName("yw_toses_calc_module")
@Slf4j
public class CalcModuleEntity {

    /**
     * 主键
     */
    //@Id
    //@Column(name = "id")
    @TableId(value = "id")
    private String id;
    /**
     * 所属方案id
     */
    @ApiModelProperty(value = "所属方案id", name = "sid")
    //@Column(name = "sid")
    @TableField("sid")
    private String sid;
    /**
     * 名称
     */
    @ApiModelProperty(value = "名称", name = "name")
    //@Column(name = "name")
    @TableField("name")
    private String name;
    /**
     * 所属指标id
     */
    @ApiModelProperty(value = "所属指标id", name = "indicatorId")
    //@Column(name = "indicator_id")
    @TableField("indicator_id")
    private String indicatorId;
    /**
     * 计算模型json
     */
    @ApiModelProperty(value = "计算模型json", name = "json")
    //@Column(name = "json")
    @TableField("json")
    private String json;
    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    //@Column(name = "CREATE_TIME")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    @TableField("CREATE_TIME")
    private LocalDateTime createTime;
    /**
     * 默认计算模型
     */
    @ApiModelProperty("默认计算模型")
    //@Column(name = "DEFAULT_CAL_MODULE")
    @TableField("DEFAULT_CAL_MODULE")
    private Integer defaultCalModule;
    /**
     * 算法类型
     */
    @ApiModelProperty("算法类型")
    //@Column(name = "CALC_TYPE")
    @TableField("CALC_TYPE")
    private Integer calcType;
    /**
     * 算法参数配置
     */
    @ApiModelProperty("算法参数配置")
    //@Column(name = "ALG_PARAM")
    @TableField("ALG_PARAM")
    private String algParam;
    /**
     * 算法模型ID
     */
    //@Column(name = "METHOD_ID")
    @TableField("METHOD_ID")
    private String methodId;

    @TableField("ALGORITHM_PARSER")
    private String algorithmParser;
    /**
     * 算法参数
     */
    //@Transient
    @TableField(exist = false)
    private List<Float> treeParamList;

    public static CalcModuleEntity valueOf(String sid, String treeId, String evaluationMethod, String chName) {
        CalcModuleEntity entity = new CalcModuleEntity();
        entity.id = UUIDGenerator.getUUID();
        entity.sid = sid;
        entity.indicatorId = treeId;
        entity.methodId = evaluationMethod;
        entity.name = chName;
        entity.defaultCalModule = CalModuleType.DEFAULT_MODEL.id;
        entity.createTime = LocalDateTime.now();
        return entity;
    }

    public void setTreeParamList(List<Float> treeParamList) {
        algParam = JSON.toJSONString(treeParamList);
    }

    public List<Float> getTreeParamList() {
        try {
            return JSON.parseArray(algParam, Float.class);
        }catch (Exception e){
            log.error("获取参数失败");
        }
        return Lists.newArrayList();
    }
}


