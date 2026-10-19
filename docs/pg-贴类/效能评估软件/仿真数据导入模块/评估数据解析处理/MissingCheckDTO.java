package com.yw.module.datasource.domain.dto;/*
 *@ClassName MissingCheckDTO
 *@Description TODO
 *@Author yc
 *@Dete 2023/7/26  16:05
 *@version 1.0
 */

import com.yw.framework.backMgr.dbTableGenerator.domain.dto.ExerciseInfoDTO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
public class MissingCheckDTO {

    @ApiModelProperty("数据清洗的操作 1：缺失项检查 2：清洗归一化")
    private int type;

    @ApiModelProperty(value = "数据集ID")
    private Integer customId;

    @ApiModelProperty(value = "数据源ID")
    private String dataSrcId;

    @ApiModelProperty(value = "表名")
    private String tableName;

    @ApiModelProperty(value = "roundFilterList")
    private List<ExerciseInfoDTO.RoundFilter> roundFilterList;

    @ApiModelProperty("预留条件字段 轮次标识：轮次Id")
    private String var1;
}
