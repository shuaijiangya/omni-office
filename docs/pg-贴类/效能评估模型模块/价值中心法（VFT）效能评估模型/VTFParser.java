package com.yw.module.calculation.indicatorCalculation.strategy.algParser.impl;

import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.spire.doc.Document;
import com.spire.doc.FileFormat;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.domain.dto.EvaluationCalculationDTO;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.indicatorCalculation.service.impl.CalculateDataServiceImpl;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.AbstractAlgInputParser;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.entity.VTFEntity;
import com.yw.module.equnode.domain.dto.SSLEvaluationCalculationDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author superman
 * vtf分析法解析类
 */
@Component
public class VTFParser extends AbstractAlgInputParser<VTFEntity> {

    @Override
    public VTFEntity prepareEntity(EvaluationCalculationDTO bo, Collection<IndicatorEntity> indicatorList, IndicatorEntity indicatorEntity,
                                   Map<String, IndicatorValuesEntity> indicatorValueMap, double value, double weight, List<Float> paramList) {
        VTFEntity vtfEntity = VTFEntity.valueOf();
        if (!indicatorEntity.getLevel().equals(0) && weight == -1) {
            throw new BusinessException("请先给当前指标设置权重,指标名称: " + indicatorEntity.getName());
        }
        vtfEntity.setWeight_origin((float) weight);
        if (prepareCheckAndUpdate(vtfEntity, indicatorEntity, value, paramList)) {
            try {
                IndicatorValuesEntity indicatorValuesEntity = indicatorValueMap.get(indicatorEntity.getId());
                if (indicatorValuesEntity == null) {
                    return vtfEntity;
                }
                CalculateDataServiceImpl.tranceStandardLabelValue(bo.getSchemaEntity().getSchemaEvaluationType(), bo.getSceneEntity().getId(), bo.getIndicatorStandardMapByIndicatorId(),
                        bo.getStandardLabelsEntityList(), indicatorEntity.getId(), value, indicatorValuesEntity);
                double targetValue = Double.parseDouble(indicatorValuesEntity.getStandardLabelValue());
                if (targetValue < 0 || targetValue > 100) {
                    throw new BusinessException("指标pg结果超出0-100范围, 名称:" + indicatorEntity.getName());
                }
            } catch (Exception e) {
                throw new BusinessException(e.getMessage());
            }
        }
        return vtfEntity;
    }

    @Override
    public VTFEntity prepareEntitySSL(SSLEvaluationCalculationDTO bo, Collection<IndicatorEntity> indicatorList, IndicatorEntity indicatorEntity,
                                      Map<String, IndicatorValuesEntity> indicatorValueMap, double value, double weight, List<Float> paramList) {
        VTFEntity vtfEntity = VTFEntity.valueOf();
        if (!indicatorEntity.getLevel().equals(0) && weight == -1) {
            throw new BusinessException("请先给当前指标设置权重,指标名称: " + indicatorEntity.getName());
        }
        vtfEntity.setWeight_origin((float) weight);
        if (prepareCheckAndUpdate(vtfEntity, indicatorEntity, value, paramList)) {
            try {
                IndicatorValuesEntity indicatorValuesEntity = indicatorValueMap.get(indicatorEntity.getId());
                if (indicatorValuesEntity == null) {
                    return vtfEntity;
                }
                CalculateDataServiceImpl.tranceStandardLabelValue(bo.getSchemaEntity().getSchemaEvaluationType(), bo.getSslResultEntity().getId(), bo.getIndicatorStandardMapByIndicatorId(),
                        bo.getStandardLabelsEntityList(), indicatorEntity.getId(), value, indicatorValuesEntity);
                double targetValue = Double.parseDouble(indicatorValuesEntity.getStandardLabelValue());
                if (targetValue < 0 || targetValue > 100) {
                    throw new BusinessException("指标pg结果超出0-100范围, 名称:" + indicatorEntity.getName());
                }
            } catch (Exception e) {
                throw new BusinessException(e.getMessage());
            }
        }
        return vtfEntity;
    }

    public static void main(String[] args) {
        Document document = new Document();
        document.createParagraph().setText("123123");
        document.saveToFile("d://a.docx", FileFormat.Docx_2010);
    }

    @Override
    public String prepareString(List<Float> treeParam, String sid) {
        return JSON.toJSONString(treeParam);
    }

    @Override
    protected EvaluationType getType() {
        return EvaluationType.VTF_MEANS;
    }

    @Override
    public String getHttpResultAndProcess(HttpResponse execute) {
        return null;
    }

    @Override
    public String getPythonCalcResult(EvaluationCalculationDTO bo, Collection<?> entityList) {
        return null;
    }
    @Override
    public String getPythonCalcResultSSL(SSLEvaluationCalculationDTO bo, Collection<?> entityList) {
        return null;
    }
}
