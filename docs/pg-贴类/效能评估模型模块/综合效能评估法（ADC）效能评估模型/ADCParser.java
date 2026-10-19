package com.yw.module.calculation.indicatorCalculation.strategy.algParser.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.common.utils.UUIDGenerator;
import com.yw.module.calculation.domain.dto.EvaluationCalculationDTO;
import com.yw.module.calculation.domain.entity.AlgorithmConfigureEntity;
import com.yw.module.calculation.domain.entity.AlgorithmResultEntity;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.domain.vo.CompRateInfoVO;
import com.yw.module.calculation.indicatorCalculation.service.ICalcModuleService;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.AbstractAlgInputParser;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.entity.ADCEntity;
import com.yw.module.calculation.mapper.AlgorithmResultMapper;
import com.yw.module.calculation.service.ScriptConfigureService;
import com.yw.module.equnode.domain.dto.SSLEvaluationCalculationDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import com.yw.module.indicator.service.IIndicatorService;
import com.yw.module.plan.domain.entity.SceneEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author superman
 * adc分析法解析类
 * <p>
 * eg:
 * "[{'id':'999','name':'我是指标999','pid':'0','value':'-1','pr_norm':'-1','pr_abn':'-1'},"
 * "{'id':'1','name':'我是指标1','pid':'999','value':'-1','pr_norm':'0.6','pr_abn':'0.4'},"
 * "{'id':'2','name':'我是指标2','pid':'999','value':'-1','pr_norm':'0.4','pr_abn':'0.4'},"
 * "{'id':'3','name':'我是指标3','pid':'1','value':'300','pr_norm':'0.8','pr_abn':'0.2'},"
 * "{'id':'4','name':'我是指标4','pid':'2','value':'400','pr_norm':'0.3','pr_abn':'0.7'},"
 * "{'id':'5','name':'我是指标5','pid':'1','value':'500','pr_norm':'0.7','pr_abn':'0.3'},"
 * "{'id':'6','name':'我是指标6','pid':'2','value':'600','pr_norm':'0.8','pr_abn':'0.2'},"
 * "{'id':'7','name':'我是指标7','pid':'1','value':'700','pr_norm':'0.6','pr_abn':'0.4'},"
 * "{'id':'8','name':'我是指标8','pid':'2','value':'800','pr_norm':'0.7','pr_abn':'0.3'},"
 * "{'id':'9','name':'我是指标9','pid':'1','value':'900','pr_norm':'0.5','pr_abn':'0.5'}]
 */
@Component
public class ADCParser extends AbstractAlgInputParser<ADCEntity> {

    @Autowired
    private ScriptConfigureService scriptConfigureService;

    @Autowired
    private IIndicatorService indicatorService;
    @Autowired
    private AlgorithmResultMapper algorithmResultMapper;
    @Autowired
    private ICalcModuleService calcModuleService;
    @Override
    public ADCEntity prepareEntity(EvaluationCalculationDTO bo, Collection<IndicatorEntity> indicatorList, IndicatorEntity indicatorEntity,
                                   Map<String, IndicatorValuesEntity> indicatorValueMap, double value, double weight, List<Float> paramList) {
        ADCEntity adcEntity = ADCEntity.valueOf();
        adcEntity.basePrepare(indicatorEntity, value);
        adcEntity.setWeight(weight);
        if (getLeafNode(indicatorList).contains(indicatorEntity.getId())){
            adcEntity.setFlag(1);
        }
        return adcEntity;
    }
    @Override
    public ADCEntity prepareEntitySSL(SSLEvaluationCalculationDTO bo, Collection<IndicatorEntity> indicatorList, IndicatorEntity indicatorEntity,
                                   Map<String, IndicatorValuesEntity> indicatorValueMap, double value, double weight, List<Float> paramList) {
        ADCEntity adcEntity = ADCEntity.valueOf();
        adcEntity.basePrepare(indicatorEntity, value);
        adcEntity.setWeight(weight);
        if (getLeafNode(indicatorList).contains(indicatorEntity.getId())){
            adcEntity.setFlag(1);
        }
        return adcEntity;
    }

    @Override
    public void extend(Object... var) {
        Collection<ADCEntity> targetList = (Collection<ADCEntity>) var[0];
        List<ADCEntity> adcList = Lists.newArrayList();
        //叶子指标设置各状态空间下的概率值
        String sid =  (String) var[1];
        Map<String, List<Double>> matrixIndicatorValueMap = scriptConfigureService.showMatrixIndicatorInfo(sid);
        //获取组成部件个数
        int count = scriptConfigureService.showCompCount(sid);
        for (Iterator<ADCEntity> iterator = targetList.iterator(); iterator.hasNext(); ) {
            ADCEntity adcEntity = iterator.next();
            String id = adcEntity.getId();
            List<Double> doubles = matrixIndicatorValueMap.get(id);
            if (CollUtil.isEmpty(doubles) && count > 0) {
                //非叶子指标设置为-1
                doubles = Lists.newArrayList();
                for (int i = 0; i < count; i++) {
                    doubles.add(-1d);
                }
            }else {
                //-1补充为指标归一化值
                doubles = doubles.stream().map(item -> {
                    if (item == -1.0) {
                        return adcEntity.getValue();
                    }
                    return item;
                }).collect(Collectors.toList());
            }
            adcEntity.setStatus(doubles);
            adcList.add(adcEntity);
            iterator.remove();
        }
        targetList.addAll(adcList);
    }

    @Override
    protected EvaluationType getType() {
        return EvaluationType.ADC_MEANS;
    }

    @Override
    public String prepareString(List<Float> treeParam, String sid) {
        //adc算法配置信息;
        /**
         * eg:{
         *      compInfo: [{combId:1,combName:idc-1,failureRate:0.5,repairRate:0.5},{combId:1,combName:idc-2,failureRate:0.5,repairRate:0.5}],
         *      statusList: {ids-1:[0,0,0], ids-2:[1,0,0], ids-3:[0,1,0]},
         *      executionTime: 10
         *    }
         */
        List<CompRateInfoVO> compRateInfoVOS = scriptConfigureService.showCompInfosBySid(sid); //部件修复率以及故障率
        AlgorithmConfigureEntity entity = scriptConfigureService.showTaskExecutionTime(sid);
        Double executionTime = entity.getExecutionTime(); //部件完成任务时间
//        Optional<CalcModuleEntity> calcModuleEntity = calcModuleRepository.findById(sid);
//        Optional<CalcModuleEntity> calcModuleEntity = Optional.ofNullable(calcModuleService.getOne(new QueryWrapper<CalcModuleEntity>().eq("SID", sid)));
//        if (!calcModuleEntity.isPresent()){
//            return "";
//        }
//        CalcModuleEntity moduleEntity = calcModuleEntity.get();
//        String indicatorId = moduleEntity.getIndicatorId();
////        Optional<IndicatorEntity> indicatorEntity = indicatorRepository.findById(indicatorId);
//        Optional<IndicatorEntity> indicatorEntity =indicatorService.findById(indicatorId);
//        if (!indicatorEntity.isPresent()){
//            return "";
//        }

        //部件状态空间值
        Map<String, List<Integer>> statusList = scriptConfigureService.showMatrixConfigInfoStatus(sid);
        Map<String, Object> map = Maps.newLinkedHashMap();
        map.put("compInfo", compRateInfoVOS);
        map.put("statusList", statusList);
        map.put("executionTime", executionTime);
        float judge_repair = 1; //是否可修复 是：1 否： 0
        float judge_weight = 1; //是否考虑权重
        if (CollUtil.isNotEmpty(treeParam)) {
            judge_repair = treeParam.get(0);
            judge_weight = treeParam.get(1);
        }
        map.put("judge_repair", judge_repair);
        map.put("judge_weight", judge_weight);
        return JSON.toJSONString(map);
    }

    @Override
    public void parserResult(EvaluationCalculationDTO bo, String indicatorId, String result) {
        StringBuilder stringBuilder = new StringBuilder();
        while (true) {
            int endIndex = result.indexOf(" ");
            if (endIndex < 0) {
                stringBuilder.append(" " + result);
                break;
            }
            stringBuilder.append(" " + result.substring(0, endIndex));
            result = result.substring(endIndex).trim();
        }
        result = stringBuilder.toString();
        String[] resultItem = result.split(";");
        String A_matrix = resultItem[0].trim();
        String D_matrix = resultItem[1].trim();
        String C_matrix = resultItem[2].trim();
        String indicatorValues = resultItem[3].trim();
        /**
         * A矩阵结构:
         * [0.19999999999999998, 0.19999999999999998, 0.09999999999999999, 0.1333333333333333]
         *
         *
         * D矩阵结构:{
         *  "空间状态-1":{"空间状态-1":"0.018316","空间状态-2":"0.031471","空间状态-3":"0.031471","空间状态-4":"0.117020"}
         *  ,"空间状态-2":{"空间状态-1":"0.031471","空间状态-2":"0.018316","空间状态-3":"0.054077","空间状态-4":"0.201073"}
         *  ,"空间状态-3":{"空间状态-1":"0.043049","空间状态-2":"0.073971","空间状态-3":"0.006738","空间状态-4"":"0.275043"}
         *  ,"空间状态-4":{"空间状态-1":"0.128597","空间状态-2":"0.220966","空间状态-3":"0.220966","空间状态-4":"0.006738"}
         *  }
         *
         *
         * C矩阵结构:{
         *  指标id-1: {'空间状态-1': 1.0, '空间状态-2': 0.6000000000000001, '空间状态-3': 0.6, '空间状态-4': 0.5}
         *  ,指标id-2: {'空间状态-1': 1.0, '空间状态-2': 0.6000000000000001, '空间状态-3': 0.6, '空间状态-4': 0.5}
         *  ,指标id-3: {'空间状态-1': 1.0, '空间状态-2': 0.6000000000000001, '空间状态-3': 0.6, '空间状态-4': 0.5}
         * }
         */
        //存储结果值
        SceneEntity sceneEntity = bo.getSceneEntity();
        String sceneId = sceneEntity.getId();
//        AlgorithmResultEntity resultEntity = iAlgorithmResultRepository.findBySceneId(sceneId);
        AlgorithmResultEntity resultEntity = algorithmResultMapper.selectOne(new QueryWrapper<AlgorithmResultEntity>().eq("SCENE_ID",sceneId));
        if (resultEntity == null) {
            resultEntity = AlgorithmResultEntity.valueOf(UUIDGenerator.getUUID(), sceneId, A_matrix, D_matrix, C_matrix);
        } else {
            resultEntity.setAMatrix(A_matrix);
            resultEntity.setDMatrix(D_matrix);
            resultEntity.setCMatrix(C_matrix);
        }
//        iAlgorithmResultRepository.saveAndFlush(resultEntity);
        if (algorithmResultMapper.selectCount(new QueryWrapper<AlgorithmResultEntity>().setEntity(resultEntity))>0) {
            algorithmResultMapper.updateById(resultEntity);
        }else{
            algorithmResultMapper.insert(resultEntity);
        }
        //4. 指标值解析 存储指标结果值
        super.parserResult(bo, indicatorId, indicatorValues);
    }

    @Override
    public void parserResultV2(EvaluationCalculationDTO bo, String indicatorId, String result, List<String> alreadySetList) {
        StringBuilder stringBuilder = new StringBuilder();
        while (true) {
            int endIndex = result.indexOf(" ");
            if (endIndex < 0) {
                stringBuilder.append(" " + result);
                break;
            }
            stringBuilder.append(" " + result.substring(0, endIndex));
            result = result.substring(endIndex).trim();
        }
        result = stringBuilder.toString();
        String[] resultItem = result.split(";");
        String A_matrix = resultItem[0].trim();
        String D_matrix = resultItem[1].trim();
        String C_matrix = resultItem[2].trim();
        String indicatorValues = resultItem[3].trim();
        /**
         * A矩阵结构:
         * [0.19999999999999998, 0.19999999999999998, 0.09999999999999999, 0.1333333333333333]
         *
         *
         * D矩阵结构:{
         *  "空间状态-1":{"空间状态-1":"0.018316","空间状态-2":"0.031471","空间状态-3":"0.031471","空间状态-4":"0.117020"}
         *  ,"空间状态-2":{"空间状态-1":"0.031471","空间状态-2":"0.018316","空间状态-3":"0.054077","空间状态-4":"0.201073"}
         *  ,"空间状态-3":{"空间状态-1":"0.043049","空间状态-2":"0.073971","空间状态-3":"0.006738","空间状态-4"":"0.275043"}
         *  ,"空间状态-4":{"空间状态-1":"0.128597","空间状态-2":"0.220966","空间状态-3":"0.220966","空间状态-4":"0.006738"}
         *  }
         *
         *
         * C矩阵结构:{
         *  指标id-1: {'空间状态-1': 1.0, '空间状态-2': 0.6000000000000001, '空间状态-3': 0.6, '空间状态-4': 0.5}
         *  ,指标id-2: {'空间状态-1': 1.0, '空间状态-2': 0.6000000000000001, '空间状态-3': 0.6, '空间状态-4': 0.5}
         *  ,指标id-3: {'空间状态-1': 1.0, '空间状态-2': 0.6000000000000001, '空间状态-3': 0.6, '空间状态-4': 0.5}
         * }
         */
        //存储结果值
        SceneEntity sceneEntity = bo.getSceneEntity();
        String sceneId = sceneEntity.getId();
//        AlgorithmResultEntity resultEntity = iAlgorithmResultRepository.findBySceneId(sceneId);
        AlgorithmResultEntity resultEntity =algorithmResultMapper.selectOne(new QueryWrapper<AlgorithmResultEntity>().eq("SCENE_ID",sceneId));
        if (resultEntity == null) {
            resultEntity = AlgorithmResultEntity.valueOf(UUIDGenerator.getUUID(), sceneId, A_matrix, D_matrix, C_matrix);
        } else {
            resultEntity.setAMatrix(A_matrix);
            resultEntity.setDMatrix(D_matrix);
            resultEntity.setCMatrix(C_matrix);
        }
//        iAlgorithmResultRepository.saveAndFlush(resultEntity);
        if (algorithmResultMapper.selectCount(new LambdaQueryWrapper<AlgorithmResultEntity>().eq(AlgorithmResultEntity::getId,resultEntity.getId()))>0) {
            algorithmResultMapper.updateById(resultEntity);
        }else{
            algorithmResultMapper.insert(resultEntity);
        }
        //4. 指标值解析 存储指标结果值
        super.parserResultV2(bo, indicatorId, indicatorValues,alreadySetList);
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

//    public static void main(String[] args) {
//        String p1 = "{compInfo:[{compId:4833c91afb,compName:组件4,failureRate:0.2,repairRate:0.5},{compId:86e34b49d3,compName:组件3,failureRate:0.2,repairRate:0.3},{compId:1751f0ddef,compName:组件2,failureRate:0.3,repairRate:0.8},{compId:42d41d229e,compName:组件1,failureRate:0.35,repairRate:0.2}],statusList:{4dedcaaa91:[0,0,1,0],008f6c9b8d:[1,0,0,0],lfb58420:[1,0,1,0],eb8c709350:[0,0,0,1],2c829463d9:[0,0,0,0]},executionTime:56.0,judge_repair:1.0,judge_weight:1.0}";
//        String p2 = "[{flag:0,id:jea6fe199e,level:0,name:能力指标,pid:0,pr_abn:-1.0,pr_norm:-1.0,status:-1.0_-1.0_-1.0_-1.0_-1.0,value:-1.0,weight:-1.0},{flag:0,id:af1aa0ee4e,level:1,name:子指标1-3,pid:jea6fe199e,pr_abn:-1.0,pr_norm:-1.0,status:-1.0_-1.0_-1.0_-1.0_-1.0,value:-1.0,weight:-1.0},{flag:0,id:a5c6b5ca92,level:1,name:子指标1-2,pid:jea6fe199e,pr_abn:-1.0,pr_norm:-1.0,status:-1.0_-1.0_-1.0_-1.0_-1.0,value:-1.0,weight:-1.0},{flag:0,id:ad12ea85c7,level:1,name:子指标1-1,pid:jea6fe199e,pr_abn:-1.0,pr_norm:-1.0,status:-1.0_-1.0_-1.0_-1.0_-1.0,value:-1.0,weight:-1.0},{flag:1,id:af47a3ddcd,level:2,name:子指标2-6,pid:af1aa0ee4e,pr_abn:-1.0,pr_norm:-1.0,status:0.5_0.2_0.1_0.4_0.087,value:1.3,weight:-1.0},{flag:1,id:aee8b317f4,level:2,name:子指标2-4,pid:af1aa0ee4e,pr_abn:-1.0,pr_norm:-1.0,status:0.5_0.5_0.5_0.5_0.2,value:1.3,weight:-1.0},{flag:1,id:a9659db701,level:2,name:子指标2-5,pid:a5c6b5ca92,pr_abn:-1.0,pr_norm:-1.0,status:0.5_0.1_0.3_0.7_0.1,value:1.3,weight:-1.0},{flag:1,id:afda67d5ea,level:2,name:子指标2-3,pid:a5c6b5ca92,pr_abn:-1.0,pr_norm:-1.0,status:0.2_0.22_0.5_0.5_0.12,value:1.3,weight:-1.0},{flag:1,id:a24d6bb529,level:2,name:子指标2-2,pid:ad12ea85c7,pr_abn:-1.0,pr_norm:-1.0,status:0.6_0.5_0.4_0.4_0.6,value:1.3,weight:-1.0},{flag:1,id:afe5f4c5ce,level:2,name:子指标2-1,pid:ad12ea85c7,pr_abn:-1.0,pr_norm:-1.0,status:0.1_0.2_0.7_0.1_0.2,value:1.2,weight:-1.0}]";
//        ADCParser parser = new ADCParser();
//        String result = PyScriptALG.execScript("pythonFile/ADC.py", p1, p2);
//        parser.parserResult(null, null, result);
//    }
}
