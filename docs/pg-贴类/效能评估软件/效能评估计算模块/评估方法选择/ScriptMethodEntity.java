package com.yw.module.calculation.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yw.framework.common.base.support.BaseEntity;
import com.yw.framework.common.module.login.util.DateUtils;
import com.yw.module.calculation.domain.dto.ScriptMethodDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Date;


/**
 * @author lj
 * @description 脚本算法实体类
 * @date 2021-10-25 16:09
 */
@Data
@TableName(value = "yw_toses_script_method")
@AllArgsConstructor
public class ScriptMethodEntity extends BaseEntity {
    /**
     * 主键id
     */
    @TableId(value = "ID")
    private String id;
    /**
     * 脚本名称
     */
    @TableField(value = "SCRIPT_NAME")
    private String scriptName;
    /**
     * 脚本信息
     */
    @TableField(exist = false)
    private String scriptInfo;
    /**
     * 脚本描述
     */
    @TableField(value = "SCRIPT_DESC")
    private String scriptDesc;
    /**
     * 脚本输入参数
     */
    @TableField(value = "SCRIPT_INPUT")
    private String scriptInput;
    /**
     * 脚本创建时间
     */
    @TableField(value = "CREATE_TIME")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    private LocalDateTime createTime;
    /**
     * 脚本修改时间
     */
    @TableField(value = "UPDATE_TIME")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.FMT_LONG_DATE)
    private LocalDateTime updateTime;
    /**
     * 是否可以被修改
     */
    @TableField(exist = false)
    private int whetherEdit;

    /**
     * 算法名称
     */
    @TableField(value = "SCRIPT_PATH")
    private String scriptPath;

    /**
     * 脚本来源：1算子脚本 2模型脚本
     */
    @TableField(value = "CALCOR_OR_MODEL")
    private Integer calcorOrModel;

    public ScriptMethodEntity() {
    }

    public static ScriptMethodEntity valueOf(ScriptMethodDTO dto) {
        ScriptMethodEntity entity = new ScriptMethodEntity();
        entity.id = dto.getId();
        entity.scriptName = dto.getScriptName();
        entity.scriptDesc = dto.getScriptDesc();
        entity.scriptInput = dto.getScriptInput();
        entity.scriptPath = dto.getScriptInfo();
        entity.calcorOrModel = dto.getCalcorOrModel();
        return entity;
    }

    public static ScriptMethodEntity valueOf(String id, String chName, String calculationScript) {
        ScriptMethodEntity entity = new ScriptMethodEntity();
        entity.id = id;
        entity.scriptName = chName;
        entity.scriptPath = calculationScript;
        return entity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getScriptInfo() {
        return scriptInfo;
    }

    public void setScriptInfo(String scriptInfo) {
        this.scriptInfo = scriptInfo;
    }

    public String getScriptDesc() {
        return scriptDesc;
    }

    public void setScriptDesc(String scriptDesc) {
        this.scriptDesc = scriptDesc;
    }

    public String getScriptInput() {
        return scriptInput;
    }

    public void setScriptInput(String scriptInput) {
        this.scriptInput = scriptInput;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public int getWhetherEdit() {
        return whetherEdit;
    }

    public void setWhetherEdit(int whetherEdit) {
        this.whetherEdit = whetherEdit;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public Integer getCalcorOrModel() {
        return calcorOrModel;
    }

    public void setCalcorOrModel(Integer calcorOrModel) {
        this.calcorOrModel = calcorOrModel;
    }
}
