package com.yw.module.calculation.indicatorCalculation.strategy.algParser.impl;

import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.yw.common.utils.UUIDGenerator;
import com.yw.module.calculation.domain.entity.IndicatorWeightEntity;
import com.yw.module.calculation.domain.entity.WeightModelEntity;
import com.yw.module.calculation.indicatorCalculation.point.DecimalPointRetention;
import com.yw.module.equnode.domain.dto.SSLEvaluationCalculationDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.domain.dto.EvaluationCalculationDTO;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.indicatorCalculation.service.ITIndicatorWeightService;
import com.yw.module.calculation.indicatorCalculation.service.IWeightModelService;
import com.yw.module.calculation.indicatorCalculation.service.impl.CalculateDataServiceImpl;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.AbstractAlgInputParser;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.entity.ANPEntity;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author superman
 * 网络分析法解析器
 */
@Component
public class ANPParser extends AbstractAlgInputParser<ANPEntity> {


    @Autowired
    private DecimalPointRetention decimalPointRetention;

    @Autowired
    private IWeightModelService weightModelService;

    @Autowired
    private ITIndicatorWeightService indicatorWeightService;

    @Override
    public ANPEntity prepareEntity(EvaluationCalculationDTO bo, Collection<IndicatorEntity> indicatorList, IndicatorEntity entity,
                                   Map<String, IndicatorValuesEntity> indicatorValueMap, double value, double weight, List<Float> paramList) {
        ANPEntity anpEntity = ANPEntity.valueOf();
        if (prepareCheckAndUpdate(anpEntity, entity, value, paramList)) {
            try {
                anpEntity.setTheory_min(paramList.get(0));
                anpEntity.setTheory_max(paramList.get(1));
            } catch (Exception e) {
                throw new BusinessException("缺失指标参数设置:" + entity.getName());
            }
        }
        if (entity.getPid().equals("0")) {
            return anpEntity;
        }
        List<WeightModelEntity> weightModelEntityList = weightModelService.find(WeightModelEntity.builder().indicatorId(entity.getPid()).build());
        if (weightModelEntityList.size() != 1) {
            throw new BusinessException("指标权重异常, 指标:" + entity.getName());
        }
        WeightModelEntity weightModelEntity = weightModelEntityList.get(0);
        List<IndicatorWeightEntity> indicatorWeightEntities = indicatorWeightService.find(IndicatorWeightEntity.builder()
                .weightModelId(weightModelEntity.getId()).build());
        double weightValue = 0d;
        for (IndicatorWeightEntity indicatorWeightEntity : indicatorWeightEntities) {
            if (indicatorWeightEntity.getIndicatorId().equals(entity.getId())) {
                weightValue = indicatorWeightEntity.getWeight();
                break;
            }
        }
        anpEntity.setWeight(weightValue);
        return anpEntity;
    }

    @Override
    public ANPEntity prepareEntitySSL(SSLEvaluationCalculationDTO bo, Collection<IndicatorEntity> indicatorList, IndicatorEntity entity,
                                      Map<String, IndicatorValuesEntity> indicatorValueMap, double value, double weight, List<Float> paramList) {
        return prepareEntity(null, indicatorList, entity, indicatorValueMap, value, weight, paramList);
    }

    @Override
    public String prepareString(List<Float> treeParam, String sid) {
        return JSON.toJSONString(treeParam);
    }

    @Override
    protected EvaluationType getType() {
        return EvaluationType.ANP_MEANS;
    }

    @Override
    public void extend(Object... var) {
        // 网络分析法子节点模拟
        Collection<ANPEntity> targetList = (Collection<ANPEntity>) var[0];
        Collection<IndicatorEntity> indicatorEntities = (Collection<IndicatorEntity>) var[1];
        Collection<ANPEntity> addList = Lists.newArrayList();
        Map<String, IndicatorEntity> indicatorEntitiesMap = Maps.newHashMap();
        Map<String, ANPEntity> anpEntitiesMap = Maps.newHashMap();
        indicatorEntities.forEach(e -> indicatorEntitiesMap.put(e.getId(), e));
        targetList.forEach(entity -> anpEntitiesMap.put(entity.getId(), entity));
        targetList.forEach(entity -> {
            List<WeightModelEntity> weightModelEntityList = weightModelService.find(WeightModelEntity.builder().indicatorId(entity.getId()).build());
            if (weightModelEntityList.size() != 1) {
                return;
            }
            WeightModelEntity weightModelEntity = weightModelEntityList.get(0);
            List<IndicatorWeightEntity> indicatorWeightEntities = indicatorWeightService.find(IndicatorWeightEntity.builder()
                    .weightModelId(weightModelEntity.getId()).build());
            IndicatorEntity indicatorEntity = indicatorEntitiesMap.get(entity.getId());
            if (indicatorEntity == null) {
                throw new BusinessException("指标实体不存在请检查数据:" + entity.getName());
            }
            List<String> relationIndicatorList = indicatorEntity.getRelationIndicators();
            if (relationIndicatorList.isEmpty()) {
                return;
            }
            Map<String, IndicatorWeightEntity> indicatorWeightMaps = Maps.newHashMap();
            indicatorWeightEntities.forEach(e -> indicatorWeightMaps.put(e.getIndicatorId(), e));
            if (relationIndicatorList.retainAll(Lists.newArrayList(indicatorWeightMaps.keySet()))) {
                throw new BusinessException("指标权重异常, 指标:" + entity.getName());
            }
            // 处理网络关联指标
            relationIndicatorList.forEach(id -> {
                IndicatorWeightEntity indicatorWeightEntity = indicatorWeightMaps.get(id);
                ANPEntity anpEntity = anpEntitiesMap.get(id);
                if (indicatorWeightEntity == null || anpEntity == null) {
                    throw new BusinessException("指标权重实体参数异常, 指标:" + entity.getName());
                }
                ANPEntity newAnpEntity = ANPEntity.valueOf();
                BeanUtils.copyProperties(anpEntity, newAnpEntity);
                // 设置虚拟值
                String newId = UUIDGenerator.getUUID();
                newAnpEntity.setId(newId);
                newAnpEntity.setPid(entity.getId());
                newAnpEntity.setWeight(indicatorWeightEntity.getWeight());
                // 设置和自己相同的指标
                Set<String> set = (Set<String>) anpEntity.getIndicatorList();
                set.add(anpEntity.getId());
                set.add(newId);
                newAnpEntity.setIndicatorList(anpEntity.getIndicatorList());
                addList.add(newAnpEntity);
            });
        });
        targetList.addAll(addList);
        // 除去当前指标下关联指标列表自己的指标id
        targetList.forEach(entity -> {
            Set<String> set = Sets.newHashSet((Set<String>) entity.getIndicatorList());
            if (set.isEmpty()) {
                return;
            }
            set.remove(entity.getId());
            StringBuilder stringBuilder = new StringBuilder();
            boolean flag = false;
            for (String s : set) {
                if (flag) {
                    stringBuilder.append("|");
                }
                stringBuilder.append(s);
                flag = true;
            }
            entity.setIndicatorList(stringBuilder.toString());
        });
    }

    @Override
    public void parserResult(EvaluationCalculationDTO bo, String indicatorId, String result) {
        // 处理anp返回值
        List<String> indicatorList = bo.getGlistIndicatorMapByPid().values().stream().flatMap(Collection::stream)
                .collect(Collectors.toList()).stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        Set<IndicatorValuesEntity> set = Sets.newHashSet(bo.getIndicatorValuesEntityListNeedUpdate());
        String[] results = result.split(",");
        for (String valueStr : results) {
            if (StringUtils.isEmpty(valueStr)) {
                continue;
            }
            String[] values = valueStr.split(":");
            if (values.length != 2) {
                continue;
            }
            String valueId = values[0].trim();
            if (!indicatorList.contains(valueId)) {
                continue;
            }

            String value = values[1].trim();
            IndicatorValuesEntity indicatorValuesEntity = new IndicatorValuesEntity();
            indicatorValuesEntity.setIndicatorId(valueId.trim());
            indicatorValuesEntity.setSid(bo.getSceneEntity().getSid());
            indicatorValuesEntity.setSceneId(bo.getSceneEntity().getId());
            indicatorValuesEntity.setNormalizedValue(value);
            indicatorValuesEntity.setUnNormalizedValue(value);
            CalculateDataServiceImpl.tranceStandardLabelValue(bo.getSchemaEntity().getSchemaEvaluationType(), bo.getSceneEntity().getId(), bo.getIndicatorStandardMapByIndicatorId(),
                    bo.getStandardLabelsEntityList(), valueId.trim(), value, indicatorValuesEntity);
            set.add(indicatorValuesEntity);
        }
        // 指标值更新
        bo.setIndicatorValuesEntityListNeedUpdate(Lists.newArrayList(set));

    }

    @Override
    public void parserResultV2(EvaluationCalculationDTO bo, String indicatorId, String result, List<String> alreadySetList) {
        // 处理anp返回值
        List<String> indicatorList = bo.getGlistIndicatorMapByPid().values().stream().flatMap(Collection::stream)
                .collect(Collectors.toList()).stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        Set<IndicatorValuesEntity> set = Sets.newHashSet(bo.getIndicatorValuesEntityListNeedUpdate());
        String[] results = result.split(",");
        for (String valueStr : results) {
            if (StringUtils.isEmpty(valueStr)) {
                continue;
            }
            String[] values = valueStr.split(":");
            if (values.length != 2) {
                continue;
            }
            String valueId = values[0].trim();
            if (!indicatorList.contains(valueId)) {
                continue;
            }
            if (!CollectionUtils.isEmpty(alreadySetList) && alreadySetList.contains(valueId)) {
                continue;
            }
            String value = values[1].trim();
            IndicatorValuesEntity indicatorValuesEntity = new IndicatorValuesEntity();
            indicatorValuesEntity.setIndicatorId(valueId.trim());
            indicatorValuesEntity.setSid(bo.getSceneEntity().getSid());
            indicatorValuesEntity.setSceneId(bo.getSceneEntity().getId());
            value = decimalPointRetention.setScale(new BigDecimal(value), "");
            indicatorValuesEntity.setNormalizedValue(value);
            indicatorValuesEntity.setUnNormalizedValue(value);
            CalculateDataServiceImpl.tranceStandardLabelValue(bo.getSchemaEntity().getSchemaEvaluationType(), bo.getSceneEntity().getId(), bo.getIndicatorStandardMapByIndicatorId(),
                    bo.getStandardLabelsEntityList(), valueId.trim(), value, indicatorValuesEntity);
            set.add(indicatorValuesEntity);
        }
        // 指标值更新
        bo.setIndicatorValuesEntityListNeedUpdate(Lists.newArrayList(set));

    }

    @Override
    public String getPythonCalcResult(EvaluationCalculationDTO bo, Collection<?> entityList) {
        return null;
    }

    @Override
    public String getPythonCalcResultSSL(SSLEvaluationCalculationDTO bo, Collection<?> entityList) {
        return null;
    }

    @Override
    public String getHttpResultAndProcess(HttpResponse execute) {
        return null;
    }
}
