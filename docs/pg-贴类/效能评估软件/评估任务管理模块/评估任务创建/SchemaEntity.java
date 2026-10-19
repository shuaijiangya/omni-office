package com.yw.module.plan.domain.entity;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import com.yw.framework.common.module.login.util.DateUtils;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.TypeDiscriminator;
import org.apache.ibatis.type.ClobTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.format.annotation.DateTimeFormat;


import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Author admin
 * @Description:方案表
 * @Date: 2020/11/17 11:01
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@ApiModel(value = "方案实体")
@TableName("yw_toses_schema")
public class SchemaEntity {
    @TableId(value = "id",type = IdType.ASSIGN_UUID)
    private String id;

    @ApiModelProperty(value = "名称", name = "name")
    @TableField(value = "S_NAME")
    private String name;

    @ApiModelProperty(value = "编号", name = "code")
    private String code;

    @ApiModelProperty(value = "pg对象", name = "assessObject")
    private String assessObject;

    @ApiModelProperty(value = "pg目的", name = "assessTarget")
    private String assessTarget;

    @ApiModelProperty(value = "任务背景", name = "background")
    private String background;

    @ApiModelProperty(value = "说明", name = "describes")
    private String describes;

    @ApiModelProperty(value = "创建时间", name = "createDate")
    @TableField(value = "CREATE_TIME")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    private LocalDateTime createDate;

    @ApiModelProperty(value = "创建人", name = "creator")
    private String creator;

    @ApiModelProperty(value = "更新时间", name = "updateDate")
    @TableField(value = "UPDATE_TIME")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    private LocalDateTime updateDate;

    @ApiModelProperty(value = "任务来源", name = "taskSource")
    private String taskSource;

    @ApiModelProperty(value = "装备预览", name = "equipmentOverview")
    private String equipmentOverview;

    @ApiModelProperty(value = "装备关键指标", name = "equipmentKey")
    private String equipmentKey;

    @ApiModelProperty(value = "任务id", name = "taskId")
    private String taskId;

    @ApiModelProperty(value = "版本号", name = "version")
    private String version;

    @ApiModelProperty(value = "任务描述", name = "taskDescribe")
    private String taskDescribe;

    @ApiModelProperty(value = "型号", name = "model")
    private String model;

    @ApiModelProperty(value = "试验方案id", name = "experimentId")
    private String experimentId;

    @ApiModelProperty(value = "是否手动创建", name = "isManualCreate")
    private Integer isManualCreate;

    @ApiModelProperty(value = "下达任务id", name = "issuedTaskId")
    private String issuedTaskId;

    @ApiModelProperty(value = "装备id", name = "equipmentId")
    private Integer equipmentId;

    @ApiModelProperty(value = "装备体系id", name = "equipmentSysId")
    private Integer equipmentSysId;

    @ApiModelProperty(value = "综合分析方法", name = "evaluationMethod")
    private String evaluationMethod;

    @ApiModelProperty(value = "综合方法类型", name = "evaluationType")
    private Integer evaluationType;

    @ApiModelProperty(value = "pg对象", name = "evaluationObject",notes = "需求评估项目中改为存储体系部项目ID")
    private String evaluationObject;

    @ApiModelProperty(value = "pg任务时间", name = "evaluationDate", example = "字符串:yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date evaluationDate;

    @ApiModelProperty(value = "任务分类", name = "schemaCata")
    private String schemaCata;

    @ApiModelProperty(value = "pg结论", name = "summary")
    private String summary;

    private Integer schemaCheck;

    @ApiModelProperty(value = "pg类型（1：能力，2：效能，3：贡献率）", name = "schemaEvaluationType")
    private Integer schemaEvaluationType;

    @ApiModelProperty(value = "目录ID",name = "dataDirectoryId")
    private String dataDirectoryId;

    @ApiModelProperty(value = "目录名称",name = "dataDirectoryName")
    private String dataDirectoryName;

    @ApiModelProperty(value = "用户ID",name = "userId")
    private String userId;

    @ApiModelProperty(value = "评估单位",name = "unit")
    private String unit;



    @ApiModelProperty("扩展字段(评估场景类型)")
    @TableField("F1")
    private String f1;

    @ApiModelProperty("扩展字段(场景研究目标）")
    @TableField("F2")
    private String f2;

    @ApiModelProperty("扩展字段(数据源id)")
    @TableField("F3")
    private String f3;

    @ApiModelProperty("评估状态1:待开始 2:已完成")
    @TableField("STATE")
    private String state;



    @ApiModelProperty(value = "项目ID",name = "与体系部关联所用")
    @TableField("PROJECT_ID")
    private String projectId;

    //@Transient
    @JSONField(serialize = false)
    @TableField(exist = false)
    private Map<String, List<String>> algParamMap;

    //场景想定
    @TableField(exist = false)
    private List<CmSchemaScenario> schemaScenarioList;
    //场景仿真变量
    @TableField(exist = false)
    private List<CmSimulationVariables> simulationVariablesList;

    public Map<String, List<String>> getAlgParamMap() {
        if (algParamMap == null) {
            try {
//                algParamMap = JSON.parseObject(algParam, ConcurrentMap.class);
            } catch (JSONException e) {
                algParamMap = Maps.newConcurrentMap();
            }
        }
        return algParamMap;
    }

    /**
     * 更新pg方法脚本所需参数
     *
     * @param algType
     * @param param
     */
    public void updateAlgParam(String algType, List<String> param) {
        if (algParamMap == null) {
            algParamMap = Maps.newConcurrentMap();
        }
        if (param != null) {
            algParamMap.put(algType, param);
        }
    }
}
