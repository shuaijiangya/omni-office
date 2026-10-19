package com.yw.module.calculation.indicatorCalculation.strategy.algParser.entity;

import com.yw.module.calculation.indicatorCalculation.strategy.algParser.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author superman
 * VTF算法参数实体
 */
@Getter
@Setter
public class VTFEntity extends BaseEntity {
    /**
     * 原始输入权重
     */
    private float weight_origin;
    /**
     * 理论最小值
     */
    private float theory_min;
    /**
     * 理论最大值
     */
    private float theory_max;

    public static VTFEntity valueOf() {
        VTFEntity entity = new VTFEntity();
        entity.initEntity();
        return entity;
    }

    @Override
    protected void initEntity(float initValue) {
        this.value = initValue;
        this.weight_origin = initValue;
        this.theory_min = initValue;
        this.theory_max = initValue;
    }
}
