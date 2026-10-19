package com.yw.module.indicator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yw.module.indicator.domain.dto.StandardInsertDTO;
import com.yw.framework.common.base.support.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

/**
 * @Author admin
 * @Description:
 * @Date: 2021/1/29 15:31
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
//@Entity
//@Table(name = "YW_TOSES_INDICATOR_STANDARD")
@TableName("YW_TOSES_INDICATOR_STANDARD")
public class IndicatorStandardEntity extends BaseEntity {

    //@Id
    //@Column(name = "ID")
    @ApiModelProperty(value = "id", name = "id")
    @TableId(value = "id",type = IdType.ASSIGN_UUID)
    private String id;

    @ApiModelProperty(value = "方案id", name = "sid")
    //@Column(name = "SID")
    private String sid;

    @ApiModelProperty(value = "指标id", name = "indicatorId")
    //@Column(name = "INDICATOR_ID")
    private String indicatorId;

    @ApiModelProperty(value = "等级id", name = "labelId")
    //@Column(name = "LABEL_ID")
    private String labelId;

    @ApiModelProperty(value = "最小值", name = "min")
    //@Column(name = "MIN")
    private String min;

    @ApiModelProperty(value = "最大值", name = "max")
    //@Column(name = "MAX")
    private String max;

    @ApiModelProperty(value = "最小值符号", name = "minSymbol")
    //@Column(name = "MIN_SYMBOL")
    private String minSymbol;

    @ApiModelProperty(value = "最大值符号", name = "maxSymbol")
    //@Column(name = "MAX_SYMBOL")
    private String maxSymbol;

    @ApiModelProperty(value = "脚本地址", name = "scriptPath")
    //@Column(name = "SCRIPT_PATH")
    private String scriptPath;

    @ApiModelProperty(value = "准则状态", name = "status")
    //@Column(name = "STATUS")
    private Integer status;

    @ApiModelProperty(value = "线性函数", name = "func")
    //@Column(name = "FUNC")
    private String func;

    @ApiModelProperty(value = "准则名称", name = "name")
    //@Column(name = "INDICATOR_STAN_NAME")
    private String indicatorStanName;

    @ApiModelProperty(value = "是否打开最终结果值参与向上综合计算", name = "scriptPath")
    //@Column(name = "OPEN_FLAG")
    private Integer openFlag;

    public static IndicatorStandardEntity valueOf(String indicatorId, String standardId, String sid, StandardInsertDTO s) {
        String indicatorStanId = s.getIndicatorStanId();
        if (StringUtils.isEmpty(indicatorStanId)) {
            indicatorStanId = UUID.randomUUID().toString().replace("-", "");
        }
        IndicatorStandardEntity entity = new IndicatorStandardEntity();
        entity.id = indicatorStanId;
        entity.sid = sid;
        entity.labelId = standardId;
        String max = s.getMax();
        String min = s.getMin();
        if (StringUtils.isEmpty(max) || max.equals("+")) {
            entity.max = "+";
        } else {
            entity.max = s.getMax();
        }
        if (StringUtils.isEmpty(min) || min.equals("-")) {
            entity.min = "-";
        } else {
            entity.min = s.getMin();
        }
        entity.minSymbol = s.getMinSymbol();
        entity.maxSymbol = s.getMaxSymbol();
        entity.indicatorId = indicatorId;
        entity.scriptPath = s.getScriptPath();
        entity.func = s.getFunc();
        entity.status = s.getStatus();
        entity.setIndicatorStanName(s.getIndicatorStanName());
        return entity;
    }

    public static IndicatorStandardEntity valueOf(String indicatorId) {
        IndicatorStandardEntity entity = new IndicatorStandardEntity();
        entity.indicatorId = indicatorId;
        return entity;
    }

}
