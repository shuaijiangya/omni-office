package com.yw.module.calculation.indicatorCalculation.strategy.algParser.impl;

import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.domain.dto.EvaluationCalculationDTO;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.AbstractAlgInputParser;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.entity.SEAEntity;
import com.yw.module.equnode.domain.dto.SSLEvaluationCalculationDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author superman
 * sea分析法解析类
 * <p>
 * eg:
 * [{"id":"999","mission_req":0.0,"mission_req_status":0.0,"name":"指标999","pid":"0","theory_max":0.0,"theory_min":0.0,"value":2.0},
 * {"id":"1","mission_req":0.48972714,"mission_req_status":0.0,"name":"指标1","pid":"999","theory_max":1.0,"theory_min":-1.0,"value":33.0},
 * {"id":"2","mission_req":0.30062324,"mission_req_status":0.0,"name":"指标2","pid":"999","theory_max":1.0,"theory_min":-1.0,"value":29.0},
 * {"id":"3","mission_req":0.8400956,"mission_req_status":0.0,"name":"指标3","pid":"1","theory_max":1.0,"theory_min":-1.0,"value":92.0},
 * {"id":"4","mission_req":0.9464231,"mission_req_status":0.0,"name":"指标4","pid":"2","theory_max":1.0,"theory_min":-1.0,"value":3.0},
 * {"id":"5","mission_req":0.65572447,"mission_req_status":0.0,"name":"指标5","pid":"1","theory_max":1.0,"theory_min":-1.0,"value":55.0},
 * {"id":"6","mission_req":0.8277694,"mission_req_status":0.0,"name":"指标6","pid":"2","theory_max":1.0,"theory_min":-1.0,"value":8.0},
 * {"id":"7","mission_req":0.4666689,"mission_req_status":0.0,"name":"指标7","pid":"1","theory_max":1.0,"theory_min":-1.0,"value":67.0},
 * {"id":"8","mission_req":0.5432932,"mission_req_status":0.0,"name":"指标8","pid":"2","theory_max":1.0,"theory_min":-1.0,"value":96.0},
 * {"id":"9","mission_req":0.07950,"mission_req_status":0.0,"name":"指标9","pid":"1","theory_max":1.0,"theory_min":-1.0,"value":37.0}]
 */
@Component
public class SEAParser extends AbstractAlgInputParser<SEAEntity> {

    @Override
    public SEAEntity prepareEntity(EvaluationCalculationDTO bo, Collection<IndicatorEntity> indicatorList, IndicatorEntity indicatorEntity,
                                   Map<String, IndicatorValuesEntity> indicatorValueMap, double value, double weight, List<Float> paramList) {
        SEAEntity seaEntity = new SEAEntity();
        if (prepareCheckAndUpdate(seaEntity, indicatorEntity, value, paramList)) {
            try {
                seaEntity.setMission_req(paramList.get(0));
                seaEntity.setMission_req_status(paramList.get(1));
                seaEntity.setTheory_min(paramList.get(2));
                seaEntity.setTheory_max(paramList.get(3));
            } catch (Exception e) {
                throw new BusinessException("缺失指标参数设置:" + indicatorEntity.getName());
            }
        } else {
            if (!indicatorEntity.getLevel().equals(0)) {
                throw new BusinessException("缺失指标参数设置:" + indicatorEntity.getName());
            }
        }
        return seaEntity;
    }

    public SEAEntity prepareEntitySSL(SSLEvaluationCalculationDTO bo, Collection<IndicatorEntity> indicatorList, IndicatorEntity indicatorEntity,
                                   Map<String, IndicatorValuesEntity> indicatorValueMap, double value, double weight, List<Float> paramList) {
        return prepareEntity(null, indicatorList, indicatorEntity, indicatorValueMap, value, weight, paramList);
    }

    @Override
    public String prepareString(List<Float> treeParam, String sid) {
        return JSON.toJSONString(treeParam);
    }

    @Override
    protected EvaluationType getType() {
        return EvaluationType.SEA_MEANS;
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
