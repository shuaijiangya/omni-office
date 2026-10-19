package com.yw.module.calculation.indicatorCalculation.strategy.weight.impl;

import com.google.common.collect.Lists;
import com.yw.common.utils.MyNumberUtil;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.domain.dto.EvaluationCalculationDTO;
import com.yw.module.calculation.domain.entity.AlgorithmParamEntity;
import com.yw.module.calculation.domain.entity.IndicatorWeightEntity;
import com.yw.module.calculation.domain.entity.WeightModelEntity;
import com.yw.module.calculation.indicatorCalculation.point.DecimalPointRetention;
import com.yw.module.calculation.indicatorCalculation.service.impl.CalculateDataServiceImpl;
import com.yw.module.calculation.indicatorCalculation.strategy.weight.IWeightALG;
import com.yw.module.equnode.domain.dto.SSLEvaluationCalculationDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @Author admin
 * @Description: 加权求和算法
 * @Date: 2020/12/10 15:39
 */
@Service("WEIGHTED_SUM")
@Slf4j
public class WeightSumALG implements IWeightALG {

    @Autowired
    private DecimalPointRetention decimalPointRetention;

    //@Override
    public List<IndicatorValuesEntity> doCalculate(String sceneId, String indicatorName, List<IndicatorWeightEntity> indicatorWeightEntities,
                                                   Collection<IndicatorValuesEntity> indicatorValuesEntityList) {
        if (CollectionUtils.isEmpty(indicatorWeightEntities)
                || CollectionUtils.isEmpty(indicatorValuesEntityList)) {
            throw new BusinessException("权重计算" + indicatorName + " 指标权重算法无输入");
        }
        List<IndicatorValuesEntity> currenCacheValue = Lists.newArrayList(indicatorValuesEntityList);
        List<IndicatorValuesEntity> indicatorValuesAdd = Lists.newArrayListWithExpectedSize(indicatorWeightEntities.size());

        indicatorWeightEntities.forEach(weight -> {
            currenCacheValue
                    .stream()
                    .filter(f -> weight.getIndicatorId().equals(f.getIndicatorId()) && f.getSceneId().equals(sceneId))
                    .forEach(value -> {
                        if (!MyNumberUtil.isNumber(new BigDecimal(value.getNormalizedValue()).toPlainString())) {
                            throw new BusinessException("权重计算" + indicatorName + " " + value.getNormalizedValue().replaceAll(",", " ") + " 不是数字类别，无法进行权重计算");
                        }

                        IndicatorValuesEntity indicatorValue = new IndicatorValuesEntity();
                        indicatorValue.setIndicatorId(weight.getIndicatorId());
                        indicatorValue.setSid(weight.getSid());
                        indicatorValue.setSceneId(sceneId);
                        BigDecimal result = BigDecimal.valueOf(weight.getWeight())
                                .multiply(new BigDecimal(value.getNormalizedValue()).setScale(10,BigDecimal.ROUND_UP))
                                .setScale(3, BigDecimal.ROUND_UP);
                        //todo 暂时默认归一值未归一值都一致
                        indicatorValue.setNormalizedValue(result.toString());
                        indicatorValue.setUnNormalizedValue(result.toString());
                        indicatorValuesAdd.add(indicatorValue);
                        log.info(weight.getWeight() + " * " + value.getNormalizedValue() + " = " + result.toString());
                    });
        });
        return indicatorValuesAdd;
    }

    @Override
    public EvaluationCalculationDTO calculateIndicatorWeightV2(EvaluationCalculationDTO bo, Map<String, AlgorithmParamEntity> paramEntityMap, String type, List<Float> treeParamList, List<String> alreadySetList) {
        return calculateIndicatorWeight(bo);
    }

    @Override
    public SSLEvaluationCalculationDTO calculateIndicatorWeightSSL(SSLEvaluationCalculationDTO bo, Map<String, AlgorithmParamEntity> paramEntityMap, String type, List<Float> treeParamList, List<String> alreadySetList) {
        return doCalculateIndicatorWeightSSL(bo);
    }

    private SSLEvaluationCalculationDTO doCalculateIndicatorWeightSSL(SSLEvaluationCalculationDTO bo) {
        //当前指标
        IndicatorEntity indicatorEntity = bo.getCurrentIndicator();
        log.info("指标[" + indicatorEntity.getName() + "]开始执行权重算法");
        //权重模型
        WeightModelEntity weightModelEntity = bo.getWeightModelMapByIndicatorId().get(indicatorEntity.getId());
        if (weightModelEntity == null) {
            throw new BusinessException("指标[" + indicatorEntity.getName() + "]未设置权重算法");
        }
        //子指标权重
        List<IndicatorWeightEntity> indicatorWeightEntities = bo.getGlistIndicatorWeightMapByWeightModelId().get(weightModelEntity.getId());
        //返回，计算当前节点子节点的值×权重
        List<IndicatorValuesEntity> indicatorValuesEntities = doCalculate(
                bo.getSslResultEntity().getId(), indicatorEntity.getName(), indicatorWeightEntities, bo.getIndicatorValuesEntityMap().values());
        List<IndicatorValuesEntity> indicatorSet = indicatorValuesEntities;
        BigDecimal sum = new BigDecimal("0.0");

        //计算当前节点值
        for (IndicatorValuesEntity indicatorValuesEntity : indicatorSet) {
            //bo.getIndicatorValuesEntityMap().put(indicatorValuesEntity, indicatorValuesEntity);
            sum = new BigDecimal(indicatorValuesEntity.getNormalizedValue()).add(sum).setScale(3, BigDecimal.ROUND_UP);
        }
        String value = decimalPointRetention.setScale(sum,"");
        log.info("权重计算结束计算结果：" + sum.toString());
        //添加需要保存至数据库的指标值
        IndicatorValuesEntity indicatorValuesEntity = new IndicatorValuesEntity();
        indicatorValuesEntity.setIndicatorId(indicatorEntity.getId());
        indicatorValuesEntity.setSid(bo.getSslResultEntity().getSceneScenarioId());
        indicatorValuesEntity.setSceneId(bo.getSslResultEntity().getId());
        //todo 暂时默认归一值未归一值都一致
        indicatorValuesEntity.setNormalizedValue(value);
        indicatorValuesEntity.setUnNormalizedValue(null);
        CalculateDataServiceImpl.tranceStandardLabelValue(bo.getSchemaEntity().getSchemaEvaluationType(), bo.getSslResultEntity().getId(), bo.getIndicatorStandardMapByIndicatorId(),
                bo.getStandardLabelsEntityList(), indicatorEntity.getId(), value, indicatorValuesEntity);
        bo.getIndicatorValuesEntityListNeedUpdate().add(indicatorValuesEntity);

        //记录当前节点最新值，用于后续计算
        bo.getIndicatorValuesEntityMap().put(indicatorValuesEntity, indicatorValuesEntity);
        return bo;
    }

    @Override
    public EvaluationCalculationDTO calculateIndicatorWeight(EvaluationCalculationDTO bo) {
        //当前指标
        IndicatorEntity indicatorEntity = bo.getCurrentIndicator();
        log.info("指标[" + indicatorEntity.getName() + "]开始执行权重算法");
        //权重模型
        WeightModelEntity weightModelEntity = bo.getWeightModelMapByIndicatorId().get(indicatorEntity.getId());
        if (weightModelEntity == null) {
            throw new BusinessException("指标[" + indicatorEntity.getName() + "]未设置权重算法");
        }
        //子指标权重
        List<IndicatorWeightEntity> indicatorWeightEntities = bo.getGlistIndicatorWeightMapByWeightModelId().get(weightModelEntity.getId());
        //返回，计算当前节点子节点的值×权重
        List<IndicatorValuesEntity> indicatorValuesEntities = doCalculate(
                bo.getSceneEntity().getId(), indicatorEntity.getName(), indicatorWeightEntities, bo.getIndicatorValuesEntityMap().values());
        List<IndicatorValuesEntity> indicatorSet = indicatorValuesEntities;
        BigDecimal sum = new BigDecimal("0.0");

        //计算当前节点值
        for (IndicatorValuesEntity indicatorValuesEntity : indicatorSet) {
            //bo.getIndicatorValuesEntityMap().put(indicatorValuesEntity, indicatorValuesEntity);
            sum = new BigDecimal(indicatorValuesEntity.getNormalizedValue()).add(sum).setScale(3, BigDecimal.ROUND_UP);
        }
        String value = decimalPointRetention.setScale(sum,"");
        log.info("权重计算结束计算结果：" + sum.toString());
        //添加需要保存至数据库的指标值
        IndicatorValuesEntity indicatorValuesEntity = new IndicatorValuesEntity();
        indicatorValuesEntity.setIndicatorId(indicatorEntity.getId());
        indicatorValuesEntity.setSid(bo.getSceneEntity().getSid());
        indicatorValuesEntity.setSceneId(bo.getSceneEntity().getId());
        //todo 暂时默认归一值未归一值都一致
        indicatorValuesEntity.setNormalizedValue(value);
        indicatorValuesEntity.setUnNormalizedValue(null);
        CalculateDataServiceImpl.tranceStandardLabelValue(bo.getSchemaEntity().getSchemaEvaluationType(), bo.getSceneEntity().getId(), bo.getIndicatorStandardMapByIndicatorId(),
                bo.getStandardLabelsEntityList(), indicatorEntity.getId(), value, indicatorValuesEntity);
        bo.getIndicatorValuesEntityListNeedUpdate().add(indicatorValuesEntity);

        //记录当前节点最新值，用于后续计算
        bo.getIndicatorValuesEntityMap().put(indicatorValuesEntity, indicatorValuesEntity);
        return bo;
    }
}
