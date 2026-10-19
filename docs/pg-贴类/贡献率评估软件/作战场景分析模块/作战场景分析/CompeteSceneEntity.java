package com.yw.module.missionScene.domain.entity;
/*
 *@ClassName CompeteScene
 *@Description TODO
 *@Author ww
 *@Dete 2023/4/6  19:48
 *@version 1.0
 */

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yw.framework.common.base.api.R;
import com.yw.framework.common.module.login.util.DateUtils;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Builder
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@ToString
//@Entity
//@Table(name = "yw_toses_compete_scene")
@TableName("yw_toses_compete_scene")
public class CompeteSceneEntity {
    //@Id
    //@Column(name = "ID")
    private String id;

    /**
     * 场景id
     **/
    //@Column(name = "NAME")
    @ApiModelProperty(value = "场景名称", name = "name")
    private String name;

    /**
     * 想定名称
     **/
    //@Column(name = "TASK_COUNT")
    @ApiModelProperty(value = "场景关联任务个数", name = "taskCount")
    private Integer taskCount;

    /**
     * 装备名称
     **/
    //@Column(name = "EQUIPMENT_COUNT")
    @ApiModelProperty(value = "装备个数", name = "equipmentCount")
    private Integer equipmentCount;

    /**
     * 装备id
     **/
    //@Column(name = "DESCS")
    @ApiModelProperty(value = "场景描述", name = "desc")
    private String descs;

    /**
     * 装备id
     **/
    //@Column(name = "CREATE_TIME")
    @ApiModelProperty(value = "创建时间", name = "createTime")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    private LocalDateTime createTime;

    /**
     * 装备id
     **/
    //@Column(name = "SID")
    @ApiModelProperty(value = "方案id", name = "sid")
    private String sid;

    /**
     * 使命任务id集合
     **/
    //@Column(name = "TASK_IDS")
    @ApiModelProperty(value = "使命任务集合", name = "taskIds")
    private String taskIds;

    /**
     * 作战时间
     */
    //@Column(name = "FIGHTER_TIME")
    @ApiModelProperty(value = "作战时间", name = "fighterTime")
    private String fighterTime;

    /**
     * 作战对手
     */
    //@Column(name = "ENEMY")
    @ApiModelProperty(value = "作战对手", name = "enemy")
    private String enemy;

    /**
     * 作战区域
     */
    //@Column(name = "FIGHTER_AREA")
    @ApiModelProperty(value = "作战区域", name = "fighterArea")
    private String fighterArea;

    /**
     * 任务样式 如果为1代表任务样式 如果等于0代表场景
     */
    //@Column(name = "TASK_MODEL")
    @ApiModelProperty(value = "任务样式", name = "taskModel")
    private String taskModel;

    /**
     *
     */
    //@Column(name = "COL_NAME")
    @ApiModelProperty(value = "导入字段名称")
    private String colName;

    //@Column(name = "COL_CNAME")
    @ApiModelProperty(value = "中文别名")
    private String colCname;

    //@Column(name ="SOURCE_ID")
    @ApiModelProperty(value = "sourceID")
    private String sourceId;
//
//    /**
//     * 使命任务id集合
//     **/
//    //@Column(name = "TASK_ID")
//    @ApiModelProperty(value = "关联使命任务", name = "missionTaskId")
//    private String missionTaskId;

    //@Transient
    @TableField(exist = false)
    private Map<String, List<EquipmentSceneEntity>> equipmentMap;

    //@Transient
    @TableField(exist = false)
    private Map<String, String> taskIdAndNameMap;

    //当前场景下所有的想定集合
    //@Transient
    @TableField(exist = false)
    private List<SceneScenarioEntity> sceneScenarioList;

    @ApiModelProperty("所有使命任务集合列表")
    @TableField(exist = false)
    private transient List<String> taskIdLists;

    public List<String> getTaskIdLists() {
        if (StringUtils.isNotEmpty(taskIds)) {
            return JSON.parseArray(taskIds, String.class);
        }
        return taskIdLists;
    }

    public void setTaskIdsList(String taskIds) {
        List<String> taskIdLists;
        if (StrUtil.isNotBlank(this.taskIds) && JSONUtil.isJsonArray(this.taskIds)) {
            taskIdLists = JSONUtil.toList(JSONUtil.parseArray(this.taskIds), String.class);
        } else {
            taskIdLists = Lists.newArrayList();
        }
        taskIdLists.add(taskIds);
        this.taskIds = JSONUtil.toJsonStr(taskIdLists);
    }

    public void setTaskIdLists(List<String> taskIdLists) {
        if (StringUtils.isNotEmpty(taskIds)) {
            this.taskIdLists = JSON.parseArray(taskIds, String.class);
        } else {
            this.taskIdLists = taskIdLists;
        }
    }
}
