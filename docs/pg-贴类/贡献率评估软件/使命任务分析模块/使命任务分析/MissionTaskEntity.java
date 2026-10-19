package com.yw.module.missionScene.domain.entity;
/*
 *@ClassName MissionTaskEntity
 *@Description TODO
 *@Author ww
 *@Dete 2023/4/6  19:46
 *@version 1.0
 */


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yw.framework.common.module.login.util.DateUtils;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.naming.Name;

import java.time.LocalDateTime;
import java.util.Date;

@Builder
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@ToString
//@Entity
//@Table(name = "YW_TOSES_MISSION_TASK")
@TableName("YW_TOSES_MISSION_TASK")
@Data
public class MissionTaskEntity {

    //@Id
    //@Column(name = "ID")
    private String id;

    /**
     * 使命任务名称
     **/
    //@Column(name = "NAME")
    @ApiModelProperty(value = "使命任务名称", name = "name")
    private String name;

    /**
     * 说明
     **/
    //@Column(name = "DESCS")
    @ApiModelProperty(value = "说明", name = "desc")
    private String descs;

    /**
     * 父id
     **/
    //@Column(name = "PID")
    @ApiModelProperty(value = "父id", name = "pid")
    private String pid;


    //@Column(name = "FLAG")
    @ApiModelProperty(value = "1：父任务 2：子任务", name = "flag")
    private Integer flag;

    /**
     * 创建时间
     **/
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    //@Column(name = "CREATE_TIME")
    @ApiModelProperty(value = "创建时间", name = "createTime")
    private LocalDateTime createTime;

    /**
     * 方案id
     **/
    //@Column(name = "SID")
    @ApiModelProperty(value = "方案id", name = "sid")
    private String sid;

    //@Column(name = "TASK_TYPE")
    @ApiModelProperty(value = "任务类型")
    private String taskType;

    //@Column(name = "TASK_GOAL")
    @ApiModelProperty(value = "任务目标")
    private String taskGoal;

    /**
     * 作战背景
     **/
    //@Column(name = "BACK_GROUND")
    @ApiModelProperty(value = "作战背景", name = "backGround")
    private String backGround;

    //@Column(name = "SOURCE_ID")
    @ApiModelProperty(value = "原始ID")
    private String sourceId;

    //@Column(name = "COMPETE_ID")
    @ApiModelProperty(value = "场景ID")
    private String competeId;



}
