package com.yw.module.calculation.indicatorCalculation.strategy.calcorAlg.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.common.defines.enums.CalModuleType;
import com.yw.common.defines.enums.EvaluateALGEnum;
import com.yw.common.defines.enums.ModuleType;
import com.yw.common.utils.Array2dUtil;
import com.yw.common.utils.MyNumberUtil;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.domain.dto.CalcorCalculateDTO;
import com.yw.module.calculation.domain.dto.IndicatorStandCalBO;
import com.yw.module.calculation.domain.entity.CalCorValuesEntity;
import com.yw.module.calculation.domain.entity.CalcModuleEntity;
import com.yw.module.calculation.domain.entity.IndicatorStandardValuesEntity;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.indicatorCalculation.service.ICalcModuleService;
import com.yw.module.calculation.indicatorCalculation.strategy.calcorAlg.ICalculationALG;
import com.yw.module.calculation.indicatorCalculation.strategy.calcorAlg.ValueParam;
import com.yw.module.calculation.indicatorCalculation.strategy.standard.StandardHelper;
import com.yw.module.equnode.domain.dto.SSLCalcorCalculateDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorStandardEntity;
import com.yw.module.indicator.domain.entity.StandardLabelsEntity;
import com.yw.module.indicator.service.IIndicatorService;
import com.yw.module.calculation.indicatorCalculation.point.DecimalPointRetention;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.ujmp.core.Matrix;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author admin
 * @Description: 模糊综合算子
 * @Date: 2021/7/2 16:28
 */
@Service("NORM")
@Slf4j
public class FuzzyALG implements ICalculationALG {
    @Autowired
    private DecimalPointRetention decimalPointRetention;

    @Autowired
    private IIndicatorService indicatorService;

    @Autowired
    private ICalcModuleService calcModuleService;

    private CalcModuleEntity parentIndicatorCalcModule(String indicatorId) {
        Optional<IndicatorEntity> indicatorEntity = indicatorService.findById(indicatorId);
        if (indicatorEntity.isPresent()) {
            IndicatorEntity currentIndicator = indicatorEntity.get();
            List<CalcModuleEntity> calcModuleEntities = calcModuleService.find(CalcModuleEntity.builder().indicatorId(currentIndicator.getId()).calcType(ModuleType.METHOD_MODEL.id).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build());
            if (CollUtil.isNotEmpty(calcModuleEntities)) {
                CalcModuleEntity calcModuleEntity = calcModuleEntities.get(0);
                if (calcModuleEntity.getCalcType() == 2) {
                    return calcModuleEntity;
                }
            }
            String pid = currentIndicator.getPid();
            List<CalcModuleEntity> calcModuleEntitiesTwo = calcModuleService.find(CalcModuleEntity.builder().indicatorId(pid).calcType(ModuleType.METHOD_MODEL.id).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build());
            if (CollUtil.isNotEmpty(calcModuleEntitiesTwo)) {
                CalcModuleEntity calcModuleEntity = calcModuleEntitiesTwo.get(0);
                if (calcModuleEntity.getCalcType() == 2) {
                    return calcModuleEntity;
                }
            }
            return parentIndicatorCalcModule(pid);
        }
        return null;
    }

    @Override
    public Object doCalculate(ValueParam param, String calId, CalcorCalculateDTO bo) {
        checkIndicatorStandardLabels(bo.getStandardLabelsEntityList());

        List<Object> values = param.parseValueByType();

        if (CollectionUtils.isEmpty(values)) {
            throw new BusinessException("模糊综合算子模糊综合算子计算值不能为空");
        }
        values.forEach(f -> {
            if (!MyNumberUtil.isNumber(f.toString())) {
                throw new BusinessException("模糊综合算子该值不是数字类别" + f.toString());
            }
        });
        //当前指标项实体
        IndicatorEntity indicatorEntity = bo.getIndicatorEntity();
        CalcModuleEntity calcModuleEntity = parentIndicatorCalcModule(indicatorEntity.getPid());
        if (calcModuleEntity != null) {
            //判断是否需要模糊综合计算
            String indicatorId = calcModuleEntity.getIndicatorId();
            Optional<IndicatorEntity> indicatorEntityOptional = indicatorService.findById(indicatorId);
            IndicatorEntity entity = indicatorEntityOptional.get();
            if (entity.getId().equals(indicatorId) && !calcModuleEntity.getMethodId().equals(EvaluationType.WEIGHT_NORM.getName())) {
                if (!(EvaluationType.getTypeByName(bo.getEvaluateALG()) == EvaluationType.WEIGHT_NORM)) {
                    throw new BusinessException("模糊综合算子使用模糊综合算子需将指标的pg算法设置为模糊综合");
                }
            }
            if (!entity.getId().equals(indicatorId) && !EvaluateALGEnum.FUZZY_COMPREHENSIVE.getCode().equals(calcModuleEntity.getMethodId())) {
//                throw new BusinessException("模糊综合算子使用模糊综合算子需将指标的pg算法设置为模糊综合");

            }


        }
        //初始化等级map
        Map<String, Integer> levelMap = initLevelMap(bo.getStandardLabelsEntityList());
        //获取指标等级map
        List<IndicatorStandCalBO> indicatorStandCalBOList = getIndicatorStandardBO(indicatorEntity, bo.getGlistIndicatorStandard());
        //等级比例
        Map<String, Double> levelPercentageMap = StandardHelper.calculatePercentage(levelMap, indicatorStandCalBOList, values);
        //获取等级数组并按照等级序号排序
        double[][] levelArray = parseLevelArray(levelPercentageMap, bo.getStandardLabelsEntityList());
        //归一化后向量(在计算levelPercentageMap等级比例时已经进行过归一化)
        Matrix norm = Matrix.Factory.importFromArray(levelArray);
        double maxFromNormArray = norm.getMaxValue();
        maxFromNormArray = decimalPointRetention.setScale(BigDecimal.valueOf(maxFromNormArray), 0d);
        //未归一化值
        String arrayStr = Array2dUtil.arrayToString(norm.toDoubleArray());
        //设置返回值
        bo.setUnNormalizedValue(parseArrayStr(norm.toDoubleArray()));//未归一值[1,2,3]
        bo.setArrayStr(arrayStr);
//        bo.setSingleValue(maxFromNormArray);
        CalCorValuesEntity calcorEntity = setCalcorValuesEntity(bo.getSceneEntity().getId(), indicatorEntity, calId, norm.toDoubleArray());
        List<IndicatorStandardValuesEntity> standardValuesNeedUpdates = setIndicatorStandardValues(bo.getSceneEntity().getId(), indicatorEntity.getId(), indicatorEntity.getSid(), levelPercentageMap);
        bo.getStandardValuesNeedUpdate().addAll(standardValuesNeedUpdates);
        bo.getUpdateEntityList().add(calcorEntity);
        log.info("模糊综合算子，单值：{}；向量：{}", maxFromNormArray, norm.toDoubleArray());
//        return maxFromNormArray;
        double totalValue = values.stream().mapToDouble(v -> Double.parseDouble(v.toString())).sum();
        totalValue = decimalPointRetention.setScale(BigDecimal.valueOf(totalValue), 0d);
        return StandardHelper.getSingleValueForScore(totalValue, calcModuleEntity.getTreeParamList(), levelArray);//归一值未归一值与比例相乘
    }

    @Override
    public Object doCalculateSSL(ValueParam param, String calId, SSLCalcorCalculateDTO bo) {
        checkIndicatorStandardLabels(bo.getStandardLabelsEntityList());

        List<Object> values = param.parseValueByType();

        if (CollectionUtils.isEmpty(values)) {
            throw new BusinessException("模糊综合算子模糊综合算子计算值不能为空");
        }
        values.forEach(f -> {
            if (!MyNumberUtil.isNumber(f.toString())) {
                throw new BusinessException("模糊综合算子该值不是数字类别" + f.toString());
            }
        });
        //当前指标项实体
        IndicatorEntity indicatorEntity = bo.getIndicatorEntity();
        CalcModuleEntity calcModuleEntity = parentIndicatorCalcModule(indicatorEntity.getPid());
        if (calcModuleEntity != null) {
            //判断是否需要模糊综合计算
            String indicatorId = calcModuleEntity.getIndicatorId();
            Optional<IndicatorEntity> indicatorEntityOptional = indicatorService.findById(indicatorId);
            IndicatorEntity entity = indicatorEntityOptional.get();
            if (entity.getId().equals(indicatorId) && !calcModuleEntity.getMethodId().equals(EvaluationType.WEIGHT_NORM.getName())) {
                if (!(EvaluationType.getTypeByName(bo.getEvaluateALG()) == EvaluationType.WEIGHT_NORM)) {
                    throw new BusinessException("模糊综合算子使用模糊综合算子需将指标的pg算法设置为模糊综合");
                }
            }
            if (!entity.getId().equals(indicatorId) && !EvaluateALGEnum.FUZZY_COMPREHENSIVE.getCode().equals(calcModuleEntity.getMethodId())) {
//                throw new BusinessException("模糊综合算子使用模糊综合算子需将指标的pg算法设置为模糊综合");

            }


        }
        //初始化等级map
        Map<String, Integer> levelMap = initLevelMap(bo.getStandardLabelsEntityList());
        //获取指标等级map
        List<IndicatorStandCalBO> indicatorStandCalBOList = getIndicatorStandardBO(indicatorEntity, bo.getGlistIndicatorStandard());
        //等级比例
        Map<String, Double> levelPercentageMap = StandardHelper.calculatePercentage(levelMap, indicatorStandCalBOList, values);
        //获取等级数组并按照等级序号排序
        double[][] levelArray = parseLevelArray(levelPercentageMap, bo.getStandardLabelsEntityList());
        //归一化后向量(在计算levelPercentageMap等级比例时已经进行过归一化)
        Matrix norm = Matrix.Factory.importFromArray(levelArray);
        double maxFromNormArray = norm.getMaxValue();
        maxFromNormArray = decimalPointRetention.setScale(BigDecimal.valueOf(maxFromNormArray), 0d);
        //未归一化值
        String arrayStr = Array2dUtil.arrayToString(norm.toDoubleArray());
        //设置返回值
        bo.setUnNormalizedValue(parseArrayStr(norm.toDoubleArray()));//未归一值[1,2,3]
        bo.setArrayStr(arrayStr);
//        bo.setSingleValue(maxFromNormArray);
        CalCorValuesEntity calcorEntity = setCalcorValuesEntity(bo.getSslResultEntity().getId(), indicatorEntity, calId, norm.toDoubleArray());
        List<IndicatorStandardValuesEntity> standardValuesNeedUpdates = setIndicatorStandardValues(bo.getSslResultEntity().getId(), indicatorEntity.getId(), indicatorEntity.getSid(), levelPercentageMap);
        bo.getStandardValuesNeedUpdate().addAll(standardValuesNeedUpdates);
        bo.getUpdateEntityList().add(calcorEntity);
        log.info("模糊综合算子，单值：{}；向量：{}", maxFromNormArray, norm.toDoubleArray());
//        return maxFromNormArray;
        double totalValue = values.stream().mapToDouble(v -> Double.parseDouble(v.toString())).sum();
        totalValue = decimalPointRetention.setScale(BigDecimal.valueOf(totalValue), 0d);
        return StandardHelper.getSingleValueForScore(totalValue, calcModuleEntity.getTreeParamList(), levelArray);//归一值未归一值与比例相乘
    }

    /**
     * 模糊综合计算叶子节点指标准则要和配置项的评语项名称保持一致
     * @param standardLabelsEntityList
     */
    private void checkIndicatorStandardLabels(List<StandardLabelsEntity> standardLabelsEntityList) {
        Set<String> names = new HashSet<>(Arrays.asList("优","良","中","差"));
        for (StandardLabelsEntity standardLabelsEntity : standardLabelsEntityList) {
            names.add(standardLabelsEntity.getName());
        }
        if (names.size()>4){
            throw new BusinessException("本方法要求所有指标的评估准则名称与配置评语项一致，请检查准则名称设置");
        }
    }

//    private String getSingleValue(List<StandardLabelsEntity> standardLabelsEntityList, Map<String, Double> levelPercentageMap) {
//        BigDecimal b3 = new BigDecimal("0");
//        for (StandardLabelsEntity standard : standardLabelsEntityList) {
//            BigDecimal b1 = new BigDecimal(standard.getValue());
//            BigDecimal labelProportion = new BigDecimal(levelPercentageMap.getOrDefault(standard.getId(), 0D));
//            BigDecimal b2 = b1.multiply(labelProportion);
//            b3 = b2.add(b3);
//            //resSum = resSum + b2.doubleValue();
//        }
//        BigDecimal b4 = b3.setScale(3, BigDecimal.ROUND_HALF_UP);
//        return b4.toString();
//    }

    /**
     * 设置标准百分比值
     */
    private List<IndicatorStandardValuesEntity> setIndicatorStandardValues(String sceneId, String indicatorId, String sid, Map<String, Double> levelPercentageMap) {
        List<IndicatorStandardValuesEntity> indicatorStandardValuesEntities = Lists.newArrayListWithExpectedSize(levelPercentageMap.size());

        levelPercentageMap.forEach((k, v) -> {
            IndicatorStandardValuesEntity indicatorStandardValuesEntity = new IndicatorStandardValuesEntity();
            indicatorStandardValuesEntities.add(indicatorStandardValuesEntity);

            indicatorStandardValuesEntity.setIndicatorId(indicatorId);
            indicatorStandardValuesEntity.setLabelId(k);
            indicatorStandardValuesEntity.setLabelValue(v);
            indicatorStandardValuesEntity.setSceneId(sceneId);
            indicatorStandardValuesEntity.setSid(sid);
        });

        return indicatorStandardValuesEntities;
    }

    /**
     * 将数组第一行转为字符串{{1，2，3}} -> [1.2.3]
     */
    private String parseArrayStr(double[][] levelNorm) {
        List<String> str = new ArrayList<>();
        for (int i = 0; i < levelNorm[0].length; i++) {
            str.add(BigDecimal.valueOf(levelNorm[0][i]).setScale(3, 4).toString());
        }

        return "[" + StringUtils.join(str, ",") + "]";
    }

    /**
     * 设置算子值
     */
    private CalCorValuesEntity setCalcorValuesEntity(String sceneId, IndicatorEntity indicator, String calId, double[][] levelNorm) {

        CalCorValuesEntity calcorValuesEntity = new CalCorValuesEntity();
        calcorValuesEntity.setIndicatorId(indicator.getId());
        calcorValuesEntity.setCalcorId(calId);
        calcorValuesEntity.setSceneId(sceneId);
        calcorValuesEntity.setValue(parseArrayStr(levelNorm));
        return calcorValuesEntity;
    }

    /**
     * 初始化等级Map
     */
    private Map<String, Integer> initLevelMap(List<StandardLabelsEntity> standardLabelsEntityList) {
        Map<String, Integer> levelMap = Maps.newHashMapWithExpectedSize(standardLabelsEntityList.size());

        standardLabelsEntityList.forEach(f -> {
            levelMap.put(f.getId(), 0);
        });
        return levelMap;
    }

    /**
     * 获取指标标准bo
     */
    private List<IndicatorStandCalBO> getIndicatorStandardBO(IndicatorEntity indicatorEntity, Map<String, List<IndicatorStandardEntity>> glistIndicatorStandard) {
        String indicatorId = indicatorEntity.getId();
        List<IndicatorStandardEntity> indicatorStandardEntityList = glistIndicatorStandard.get(indicatorId);
        if (CollectionUtils.isEmpty(indicatorStandardEntityList))
            throw new BusinessException("“" + indicatorEntity.getName() + "”指标未设置准则，请先设置再计算");
        List<IndicatorStandCalBO> standCalBoList = indicatorStandardEntityList
                .stream()
                .map(m -> new IndicatorStandCalBO(m))
                .collect(Collectors.toList());
        return standCalBoList;
    }

    private double[][] parseLevelArray(Map<String, Double> levelPercentageMap, List<StandardLabelsEntity> standardLabelsEntityList) {
        double[][] levelArray = new double[1][levelPercentageMap.size()];

        List<StandardLabelsEntity> standardLabelsEntities = standardLabelsEntityList
                .stream()
                .sorted(Comparator.comparing(StandardLabelsEntity::getSortno))
                .collect(Collectors.toList());
        for (int i = 0; i < standardLabelsEntities.size(); i++) {
            levelArray[0][i] = levelPercentageMap.get(standardLabelsEntities.get(i).getId());
        }

        return levelArray;
    }

    //构造一个Map 等级id，数量，并初始化
    //计算该指标下，优良中差比例，生成一个二维数组按等级排序并归一化[[0.2,0.2,0.3,0.3]]
    //指标归一化值默认都是单个数，取最大的一个，生成的向量放到为归一化存储
}
