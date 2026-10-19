package com.yw.module.calculation.indicatorCalculation.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yw.framework.common.module.login.util.DateUtils;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;


import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 算子信息实体类
 *
 * @author yc
 */

@Data
@TableName("YW_TOSES_EVALUATE_CALCOR")
public class EvaluateCalcorEntity {

    @TableId(value = "ID", type = IdType.ASSIGN_UUID)
    private String id;

    @ApiModelProperty("算子名称")
    @TableField("NAME")
    private String name;

    @ApiModelProperty("算子代号")
    @TableField("CALCOR_CODE")
    private String calcorCode;

    @ApiModelProperty("算子描述")
    @TableField("REMAKE")
    private String remake;

    @ApiModelProperty("开发单位ID")
    @TableField("UNIT_ID")
    private String unitId;

    @ApiModelProperty("开发单位名称")
    @TableField("UNIT_NAME")
    private String unitName;

    @ApiModelProperty("作者ID")
    @TableField("AUTHOR_ID")
    private String authorId;

    @ApiModelProperty("算子开发作者名称")
    @TableField("AUTHOR")
    private String author;

    @ApiModelProperty("创建时间")
    @TableField("CREATE_TIME")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    private LocalDateTime createTime;

    @ApiModelProperty("开发时间")
    @TableField("DEVELOPMENT_TIME")
    private String developmentTime;

    @ApiModelProperty("图片")
    @TableField("IMAGE")
    private String image;

    @ApiModelProperty("评估方法")
    @TableField("METHOD")
    private String method;

    @ApiModelProperty("请求地址")
    @TableField("ADDRESS")
    private String address;

    @ApiModelProperty("算子请求类型")
    @TableField("TYPE")
    private String type;

    @ApiModelProperty("算子类型: 0内置算法1外部算法")
    @TableField("ENTITY_TYPE")
    private Integer entityType;

    @ApiModelProperty("调用类型：1 外部算子 2 python脚本 3 so动态链接库")
    @TableField("INVOKE_TYPE")
    private Integer invokeType;

    @ApiModelProperty("脚本路径")
    @TableField("SCRIPT_PATH")
    private String scriptPath;

    //参数信息
    private transient List<InputParamEntity> paramList;

    private transient List<String> deleteParamId;

}
