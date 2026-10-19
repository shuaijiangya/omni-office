package com.yw.module.indicator.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yw.module.indicator.domain.dto.StandardInsertDTO;
import com.yw.framework.common.base.support.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.springframework.util.StringUtils;



/**
 * @Author: 刘灿莲
 * @Description:pg等级表
 * @Date: 2021/01/26 10:58
 */
@Builder
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@ToString
//@Entity
//@Table(name = "yw_toses_standard_labels")
@TableName("yw_toses_standard_labels")
@ApiModel(value = "pg等级实体")
public class StandardLabelsEntity extends BaseEntity {
    //@Id
    //@Column(name = "id")
    @ApiModelProperty(value = "主键", name = "id")
    private String id;

    @ApiModelProperty(value = "等级名称", name = "name")
    //@Column(name = "name")
    private String name;

    @ApiModelProperty(value = "描述", name = "describes")
    //@Column(name = "describes")
    private String describes;

    @ApiModelProperty(value = "等级取值", name = "value")
    //@Column(name = "value")
    private String value;

    @ApiModelProperty(value = "编码", name = "code")
    //@Column(name = "code")
    private String code;

    @ApiModelProperty(value = "排序字段", name = "sortno")
    //@Column(name = "sortno")
    private Integer sortno;

    @ApiModelProperty(value = "自定义标识 0表示默认, 1表示自定义 , 2表示脚本方式", name = "flag")
    //@Column(name = "FLAG")
    private int flag;

    @ApiModelProperty(value = "脚本路径", name = "scriptPath")
    //@Column(name = "SCRIPT_PATH")
    private String scriptPath;



    public static StandardLabelsEntity valueOf(String standardId, StandardInsertDTO s) {
        StandardLabelsEntity entity = new StandardLabelsEntity();
        entity.id = standardId;
        entity.name = s.getName();
        String value = s.getValue();
        if (StringUtils.isEmpty(value)) {
            entity.value = 1 + "";
        } else {
            entity.value = value;
        }
        entity.sortno = s.getSortno();
        entity.flag = s.getFlag();
        entity.scriptPath = s.getScriptPath();
        return entity;
    }
}
