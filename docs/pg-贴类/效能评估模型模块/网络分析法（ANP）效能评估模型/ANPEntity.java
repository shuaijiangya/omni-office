package com.yw.module.calculation.indicatorCalculation.strategy.algParser.entity;

import com.google.common.collect.Sets;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author superman
 * ANP算法参数实体
 */
@Getter
@Setter
public class ANPEntity extends BaseEntity {
    /**
     * 当前指标权重
     */
    private double weight;
    /**
     * 本指标相同指标的ID
     */
    private Object indicatorList;
    /**
     * 理论最小值
     */
    private float theory_min;
    /**
     * 理论最大值
     */
    private float theory_max;

    public static ANPEntity valueOf() {
        ANPEntity entity = new ANPEntity();
        entity.initEntity();
        return entity;
    }

    @Override
    protected void initEntity(float initValue) {
        this.value = initValue;
        indicatorList = Sets.newHashSet();
    }
}
