package com.yw.module.calculation.indicatorCalculation.domain.dto;

import com.yw.framework.common.defines.exception.custom.BusinessException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author admin
 * @Description: 层次分析法bo
 * @Date: 2020/12/21 13:40
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AnalyticHierarchyDTO {

    private List<Double[]> featureVectors;//特征向量集合（准则层+方案层）按顺序

    private Double[] armNormSpecial;//目标层+准则层

    /**
     * @Author 刘乐法
     * @Description 计算每个方案-目标层的权值 featureVectors每个数组的
     * @Return java.lang.Double[]
     * @Date 2020/12/22 16:44
     */
    public Double[] getArmWeight() {
        if (CollectionUtils.isEmpty(featureVectors) || armNormSpecial == null)
            throw new BusinessException("特征向量为空无法计算");
        if (armNormSpecial.length != featureVectors.size())
            throw new BusinessException("准则层~方案层特征向量与目标层~准则层特征向量不匹配，请重新输入");

        Double[] sumPlan = new Double[featureVectors.get(0).length];
        for (int i = 0; i < featureVectors.get(0).length; i++) {
            for (int j = 0; j < featureVectors.size(); j++) {
                BigDecimal value = BigDecimal.valueOf(featureVectors.get(j)[i]).multiply(BigDecimal.valueOf(armNormSpecial[j]));
                sumPlan[i] = sumPlan[i] == null ? value.doubleValue() : BigDecimal.valueOf(sumPlan[i]).add(value).doubleValue();
            }
        }

        return sumPlan;
    }

}
