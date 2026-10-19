package com.yw.module.calculation.indicatorCalculation.strategy.algParser.entity;

import com.yw.module.calculation.indicatorCalculation.strategy.algParser.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author superman
 * SEA算法参数实体
 */
@Getter
@Setter
public class SEAEntity extends BaseEntity {
    /**
     * 使命需求值
     */
    private float mission_req;
    /**
     * 使命需求的状态
     */
    private float mission_req_status;
    /**
     * 理论最小值
     */
    private float theory_min;
    /**
     * 理论最大值
     */
    private float theory_max;

    public static SEAEntity valueOf() {
        SEAEntity entity = new SEAEntity();
        entity.initEntity();
        return entity;
    }

    @Override
    protected void initEntity(float initValue) {
        this.value = initValue;
        this.mission_req_status = initValue;
        this.theory_min = initValue;
        this.theory_max = initValue;
    }
}
