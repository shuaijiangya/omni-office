package com.yw.module.calculation.contributionCalculation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.framework.common.defines.constant.IntegerPool;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.contributionCalculation.domain.vo.IndicatorCombineCredibilityVO;
import com.yw.module.calculation.contributionCalculation.service.IIndicatorCombineCredibilityService;
import com.yw.module.calculation.contributionCalculation.service.IIndicatorContributionCalculationService;
import com.yw.module.calculation.contributionCalculation.service.IIndicatorValueInfoService;
import com.yw.module.calculation.contributionCalculation.utils.CalculationUtil;
import com.yw.module.calculation.contributionRate.service.ContributionRateCalculatedValueService;
import com.yw.module.calculation.contributionRate.service.ContributionRateResultService;
import com.yw.module.calculation.domain.entity.IndicatorWeightEntity;
import com.yw.module.calculation.domain.enums.ContributionRateType;
import com.yw.module.calculation.domain.vo.ContributionRateTypeVO;
import com.yw.module.calculation.indicatorCalculation.service.ITIndicatorWeightService;
import com.yw.module.indicator.domain.dto.IndicatorValuesWithNameDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.service.IIndicatorService;
import com.yw.module.indicator.service.ITIndicatorValueService;
import com.yw.module.missionScene.domain.entity.ContributionRateAlgorithmEntity;
import com.yw.module.missionScene.domain.entity.ContributionRateCalculatedValueEntity;
import com.yw.module.missionScene.domain.entity.SceneScenarioEntity;
import com.yw.module.missionScene.service.ContributionRateAlgorithmService;
import com.yw.module.missionScene.service.SceneScenarioService;
import com.yw.module.plan.service.ISceneService;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 贡献率基础想定多样本计算服务
 *
 * @author xiangyichen
 * @date 2024/4/28 13:57
 */
@Service
public class IndicatorContributionCalculationServiceImpl implements IIndicatorContributionCalculationService {

    /**
     * 满足度配置
     */
    @Resource
    private IIndicatorCombineCredibilityService indicatorCombineCredibilityService;
    /**
     * 想定服务
     */
    @Resource
    private SceneScenarioService sceneScenarioService;
    /**
     * 想定样本关联服务
     */
    @Resource
    private ContributionRateAlgorithmService contributionRateAlgorithmService;
    /**
     * 指标服务
     */
    @Resource
    private IIndicatorService indicatorService;
    /**
     * 指标值服务
     */
    @Resource
    private ITIndicatorValueService indicatorValueService;
    /**
     * 样本服务
     */
    @Resource
    private ISceneService sceneService;
    /**
     * 指标权重服务
     */
    @Resource
    private ITIndicatorWeightService indicatorWeightService;

    /**
     * 获取基础想定下的所有样本id
     *
     * @param schemaId      方案
     * @param missionTaskId 使命任务
     * @return
     */
    @Override
    public List<String> listBenchmarkSceneIds(String schemaId, String missionTaskId) {
        // 想定 获取所有
        List<SceneScenarioEntity> sceneScenarioEntities = sceneScenarioService.list(
                new LambdaQueryWrapper<SceneScenarioEntity>()
                        .eq(SceneScenarioEntity::getSid, schemaId));
        // 构建 scenarioChildrenMap key pid value children
        Map<String, List<SceneScenarioEntity>> scenarioChildrenMap = createScenarioChildrenMap(sceneScenarioEntities, missionTaskId);

        if (!scenarioChildrenMap.containsKey("0") || scenarioChildrenMap.get("0").isEmpty()) {
            throw new BusinessException("无法找到基础想定，计算失败");
        }
        // 想定排序
        List<SceneScenarioEntity> rootChildren = new ArrayList<>();
        // 有子想定情况
        if (scenarioChildrenMap.size() > 1) {
            SceneScenarioEntity root = scenarioChildrenMap.get("0").get(0);
            if (!CollectionUtils.isEmpty(scenarioChildrenMap.get(root.getId()))) {
                rootChildren = scenarioChildrenMap.get(root.getId())
                        .stream()
                        .sorted(Comparator.comparing(SceneScenarioEntity::getId))
                        .collect(Collectors.toList());
            } else {
                rootChildren = scenarioChildrenMap.get("0");
            }
        } else {
            rootChildren = scenarioChildrenMap.get("0");
        }
        // 定出基础想定
        SceneScenarioEntity benchmarkScenario = rootChildren.get(0);
        // 基础想定下的所有样本
        List<ContributionRateAlgorithmEntity> algorithmEntities = contributionRateAlgorithmService.list(
                new LambdaQueryWrapper<ContributionRateAlgorithmEntity>()
                        .eq(ContributionRateAlgorithmEntity::getSceneScenarioId, benchmarkScenario.getId()));

        // 如果想定下子想定个数小于2 无法计算贡献率
        if (algorithmEntities.isEmpty() || rootChildren.size() < 2) {
            return null;
        }

        return algorithmEntities.stream().map(ContributionRateAlgorithmEntity::getSceneId).collect(Collectors.toList());
    }

    /**
     * 证据推理
     *
     * @param benchmarkSceneIds 基础想定
     * @param indicatorTreeId   指标树
     * @return
     */
    @Override
    public List<ContributionRateCalculatedValueEntity> calc(List<String> benchmarkSceneIds, String indicatorTreeId) {
        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }


        // 获取样本
        String schemaId = leaves.get(0).getSid();
        List<String> sceneIdList = sceneService.findIdsBySid(schemaId)
                .stream().sorted().collect(Collectors.toList());
        List<IndicatorEntity> allNodes = indicatorService.findByTreeId(indicatorTreeId);

        // id
        List<String> allIds = allNodes.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        List<String> leafNodeIds = leaves.stream().map(IndicatorEntity::getId).collect(Collectors.toList());

        // 获取权重
        List<IndicatorWeightEntity> indicatorWeightEntities = indicatorWeightService.listByIndicatorIds(allIds);
        // key indicatorId value weight
        Map<String, Double> weightMap = indicatorWeightEntities.stream().collect(Collectors.toMap(IndicatorWeightEntity::getIndicatorId, IndicatorWeightEntity::getWeight));

        // 获取想定关联数据
        List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findBySceneIds(sceneIdList)
                .stream()
                .sorted(Comparator.comparing(ContributionRateAlgorithmEntity::getSceneId))
                .collect(Collectors.toList());
        if (contributionRateAlgorithmEntities.isEmpty()) {
            throw new BusinessException("计算失败,无样本");
        }
        // key sceneId
        Map<String, ContributionRateAlgorithmEntity> contributionRateAlgorithmEntityMap = contributionRateAlgorithmEntities.stream().collect(Collectors.toMap(ContributionRateAlgorithmEntity::getSceneId, v -> v));

        // 计算
        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = new ArrayList<>();

        List<String> otherSceneIds = sceneIdList.stream().filter(s -> !benchmarkSceneIds.contains(s)).collect(Collectors.toList());
        // 基础想定id
        for (String benchmarkSceneId : benchmarkSceneIds) {


            // 叶子节点贡献率计算
            // 获取满足度
            List<IndicatorCombineCredibilityVO> leafCredibilityVOs = indicatorCombineCredibilityService.listByIndicators(leafNodeIds);
            if (leafCredibilityVOs.isEmpty()) {
                throw new BusinessException("请先计算满足度");
            }
            Map<String, List<IndicatorCombineCredibilityVO>> sceneMap = leafCredibilityVOs.stream().collect(Collectors.groupingBy(IndicatorCombineCredibilityVO::getSceneId));
            for (int i = 0; i < otherSceneIds.size(); i++) {
                String currentSceneId = otherSceneIds.get(i);
                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                if (ObjectUtils.isEmpty(algorithmEntity)) {
                    continue;
                }
                // 基础想定满足度
                List<IndicatorCombineCredibilityVO> benchmarkCredibilityVOs = sceneMap.get(benchmarkSceneId);
                // 样本满足度
                List<IndicatorCombineCredibilityVO> credibilityVOS = sceneMap.get(currentSceneId);
                //
                for (int j = 0; j < benchmarkCredibilityVOs.size(); j++) {
                    ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                    double value = CalculationUtil.getLeafNodeContributionRate(benchmarkCredibilityVOs.get(j).getMatchRate(), credibilityVOS.get(j).getMatchRate());
                    calculatedValue.setIndicatorId(benchmarkCredibilityVOs.get(j).getIndicatorId());
                    calculatedValue.setCalculatedValue(String.valueOf(value));
                    calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                    calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                    calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                    calculatedValue.setSceneId(currentSceneId);
                    calculatedValue.setBenchmarkSceneId(benchmarkSceneId);
                    calculatedValue.setType(IntegerPool.ONE);

                    calculatedValueEntities.add(calculatedValue);
                }
            }

            // 叶子节点父级贡献率计算
            // key pid value List<IndicatorEntity>
            Map<String, List<IndicatorEntity>> map = leaves.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
            // 获取上一层的指标id，根据叶子指标pid查询
            List<String> fatherIds = new ArrayList<>(map.keySet());
            List<IndicatorCombineCredibilityVO> fatherCredibilityVOs = indicatorCombineCredibilityService.listByIndicators(fatherIds);

            for (int i = 0; i < otherSceneIds.size(); i++) {
                String currentSceneId = otherSceneIds.get(i);
                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                if (ObjectUtils.isEmpty(algorithmEntity)) {
                    continue;
                }
                // 基础想定满足度
                List<IndicatorCombineCredibilityVO> benchmarkCredibilityVOs = sceneMap.get(benchmarkSceneId);
                Map<String, Double> childrenBenchmarkMatchRateMap = benchmarkCredibilityVOs.stream().collect(Collectors.toMap(IndicatorCombineCredibilityVO::getIndicatorId, IndicatorCombineCredibilityVO::getMatchRate));
                // 样本满足度
                List<IndicatorCombineCredibilityVO> credibilityVOS = sceneMap.get(currentSceneId);
                Map<String, Double> childrenMatchRateMap = credibilityVOS.stream().collect(Collectors.toMap(IndicatorCombineCredibilityVO::getIndicatorId, IndicatorCombineCredibilityVO::getMatchRate));
                map.forEach((key, children) -> {
                    Double sumB = 0D;
                    Double sumC = 0D;
                    for (IndicatorEntity indicator : children) {
                        String id = indicator.getId();
                        sumB += (1 - childrenBenchmarkMatchRateMap.get(id));
                        sumC += (1 - childrenMatchRateMap.get(id));
                    }

                    ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                    double value = CalculationUtil.getLeafNodeContributionRate(sumB, sumC);
                    calculatedValue.setIndicatorId(key);
                    calculatedValue.setCalculatedValue(String.valueOf(value));
                    calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                    calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                    calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                    calculatedValue.setSceneId(currentSceneId);
                    calculatedValue.setBenchmarkSceneId(benchmarkSceneId);
                    calculatedValue.setType(IntegerPool.ONE);

                    calculatedValueEntities.add(calculatedValue);
                });
            }


            // key pid value list
            Map<String, List<IndicatorEntity>> pidMap = allNodes.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));

            // 其他节点
            List<String> otherNodeIds = allNodes.stream()
                    .map(IndicatorEntity::getId)
                    .filter(id -> !leafNodeIds.contains(id))
                    .filter(id -> !fatherIds.contains(id))
                    .collect(Collectors.toList());
            if (!otherNodeIds.isEmpty()) {

                // 获取计算结果
                List<IndicatorValuesWithNameDTO> schemaIndicatorValuesBoList = indicatorValueService
                        .findWithNameBySidIndicatorIdSceneId(schemaId, allIds, sceneIdList);
                Map<String, List<IndicatorValuesWithNameDTO>> sceneMapOther = schemaIndicatorValuesBoList.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getSceneId));

                for (int i = 0; i < otherSceneIds.size(); i++) {
                    String currentSceneId = otherSceneIds.get(i);
                    ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                    if (ObjectUtils.isEmpty(algorithmEntity)) {
                        continue;
                    }
                    // 基础想定满足度
                    List<IndicatorValuesWithNameDTO> benchmarkBOs = sceneMapOther.get(benchmarkSceneId);
                    Map<String, List<IndicatorValuesWithNameDTO>> pidBenchmarkBOMap = benchmarkBOs.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getPid));
                    // 样本满足度
                    List<IndicatorValuesWithNameDTO> bos = sceneMapOther.get(currentSceneId);
                    Map<String, List<IndicatorValuesWithNameDTO>> currentBOMap = bos.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getPid));
                    //
                    for (String indicatorId : otherNodeIds) {

//                        String indicatorId = benchmarkBOs.get(j).getIndicatorId();
                        List<IndicatorEntity> indicatorEntities = pidMap.get(indicatorId);
                        if (ObjectUtils.isEmpty(indicatorEntities)) {
                            continue;
                        }
                        // children
                        List<IndicatorValuesWithNameDTO> benchmark = pidBenchmarkBOMap.get(indicatorId);
                        List<IndicatorValuesWithNameDTO> current = currentBOMap.get(indicatorId);
                        Double sumB = 0D;
                        Double sumC = 0D;
                        for (int k = 0; k < benchmark.size(); k++) {
                            sumB += Double.parseDouble(benchmark.get(k).getNormalizedValue()) * weightMap.get(benchmark.get(k).getIndicatorId());
                            sumC += Double.parseDouble(current.get(k).getNormalizedValue()) * weightMap.get(current.get(k).getIndicatorId());
                        }
                        ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                        double value = CalculationUtil.getLeafNodeContributionRate(sumB, sumC);
                        calculatedValue.setIndicatorId(indicatorId);
                        calculatedValue.setCalculatedValue(String.valueOf(value));
                        calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                        calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                        calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                        calculatedValue.setSceneId(currentSceneId);
                        calculatedValue.setBenchmarkSceneId(benchmarkSceneId);
                        calculatedValue.setType(IntegerPool.ONE);

                        calculatedValueEntities.add(calculatedValue);
                    }
                }
            }
        }
        return calculatedValueEntities;
    }

    /**
     * 查询贡献率计算类型
     */
    @Override
    public List<ContributionRateTypeVO> findContributionRateType() {
        List<ContributionRateTypeVO> contributionRateTypeVOS = Lists.newArrayList();
        for (ContributionRateType value : ContributionRateType.values()) {
            if (ContributionRateType.NONE == value) {
                continue;
            }
            ContributionRateTypeVO contributionRateTypeVO = new ContributionRateTypeVO();
            contributionRateTypeVO.setCode(value.getCode());
            contributionRateTypeVO.setMsg(value.getMsg());
            contributionRateTypeVOS.add(contributionRateTypeVO);
        }
        return contributionRateTypeVOS;
    }

    /**
     * 计算基础想定多样本贡献率（加权综合）
     *
     * @param benchmarkSceneIds 基础想定下的所有样本id
     * @param indicatorTreeId   指标树id
     * @return
     */
    @Override
    public List<ContributionRateCalculatedValueEntity> calcMultiBenchmarkSceneContribution(List<String> benchmarkSceneIds, String indicatorTreeId) {


        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }


        // 获取样本
        String schemaId = leaves.get(0).getSid();
        List<String> sceneIdList = sceneService.findIdsBySid(schemaId)
                .stream().sorted().collect(Collectors.toList());
        List<IndicatorEntity> allNodes = indicatorService.findByTreeId(indicatorTreeId);

        // id
        List<String> allIds = allNodes.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        List<String> leafNodeIds = leaves.stream().map(IndicatorEntity::getId).collect(Collectors.toList());

        // 获取权重
        List<IndicatorWeightEntity> indicatorWeightEntities = indicatorWeightService.listByIndicatorIds(allIds);
        // key indicatorId value weight
        Map<String, Double> weightMap = indicatorWeightEntities.stream().collect(Collectors.toMap(IndicatorWeightEntity::getIndicatorId, IndicatorWeightEntity::getWeight));

        // 获取想定关联数据
        List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findBySceneIds(sceneIdList)
                .stream()
                .sorted(Comparator.comparing(ContributionRateAlgorithmEntity::getSceneId))
                .collect(Collectors.toList());
        if (contributionRateAlgorithmEntities.isEmpty()) {
            throw new BusinessException("计算失败,无样本");
        }
        // key sceneId
        Map<String, ContributionRateAlgorithmEntity> contributionRateAlgorithmEntityMap = contributionRateAlgorithmEntities.stream().collect(Collectors.toMap(ContributionRateAlgorithmEntity::getSceneId, v -> v));

        // 计算
        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = new ArrayList<>();

        List<String> otherSceneIds = sceneIdList.stream().filter(s -> !benchmarkSceneIds.contains(s)).collect(Collectors.toList());
        // 基础想定id
        for (String benchmarkSceneId : benchmarkSceneIds) {


            // 获取叶子节点计算结果
            List<IndicatorValuesWithNameDTO> schemaIndicatorValuesBoList = indicatorValueService
                    .findWithNameBySidIndicatorIdSceneId(schemaId, leafNodeIds, sceneIdList);
            Map<String, List<IndicatorValuesWithNameDTO>> sceneMapLeaves = schemaIndicatorValuesBoList.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getSceneId));
            for (int i = 0; i < otherSceneIds.size(); i++) {
                String currentSceneId = otherSceneIds.get(i);
                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                if (ObjectUtils.isEmpty(algorithmEntity)) {
                    continue;
                }
                // 基础想定满足度
                List<IndicatorValuesWithNameDTO> benchmarkBOs = sceneMapLeaves.get(benchmarkSceneId);
                // 样本满足度
                List<IndicatorValuesWithNameDTO> bos = sceneMapLeaves.get(currentSceneId);
                //
                for (int j = 0; j < benchmarkBOs.size(); j++) {
                    ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                    double value = CalculationUtil.getLeafNodeContributionRate(Double.parseDouble(benchmarkBOs.get(j).getNormalizedValue()), Double.parseDouble(bos.get(j).getNormalizedValue()));
                    calculatedValue.setIndicatorId(benchmarkBOs.get(j).getIndicatorId());
                    calculatedValue.setCalculatedValue(String.valueOf(value));
                    calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                    calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                    calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                    calculatedValue.setBenchmarkSceneId(benchmarkSceneId);
                    calculatedValue.setSceneId(currentSceneId);
                    calculatedValue.setType(IntegerPool.ONE);

                    calculatedValueEntities.add(calculatedValue);
                }
            }

            // key pid value list
            Map<String, List<IndicatorEntity>> pidMap = allNodes.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));

            // 其他节点
            List<String> otherNodeIds = allNodes.stream()
                    .map(IndicatorEntity::getId)
                    .filter(id -> !leafNodeIds.contains(id))
                    .collect(Collectors.toList());
            // 获取其他节点计算结果
            List<IndicatorValuesWithNameDTO> allNodeBoList = indicatorValueService
                    .findWithNameBySidIndicatorIdSceneId(schemaId, allIds, sceneIdList);
            Map<String, List<IndicatorValuesWithNameDTO>> sceneMapAll = allNodeBoList.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getSceneId));
            for (int i = 0; i < otherSceneIds.size(); i++) {
                String currentSceneId = otherSceneIds.get(i);
                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                if (ObjectUtils.isEmpty(algorithmEntity)) {
                    continue;
                }
                // 基础想定满足度
                List<IndicatorValuesWithNameDTO> benchmarkBOs = sceneMapAll.get(benchmarkSceneId);
                Map<String, List<IndicatorValuesWithNameDTO>> pidBenchmarkBOMap = benchmarkBOs.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getPid));
                // 样本满足度
                List<IndicatorValuesWithNameDTO> bos = sceneMapAll.get(currentSceneId);
                Map<String, List<IndicatorValuesWithNameDTO>> currentBOMap = bos.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getPid));
                //
                for (int j = 0; j < benchmarkBOs.size(); j++) {
                    String indicatorId = benchmarkBOs.get(j).getIndicatorId();
                    List<IndicatorEntity> indicatorEntities = pidMap.get(indicatorId);
                    if (ObjectUtils.isEmpty(indicatorEntities)) {
                        continue;
                    }
                    // children
                    List<IndicatorValuesWithNameDTO> benchmark = pidBenchmarkBOMap.get(indicatorId);
                    List<IndicatorValuesWithNameDTO> current = currentBOMap.get(indicatorId);
                    Double sumB = 0D;
                    Double sumC = 0D;
                    for (int k = 0; k < benchmark.size(); k++) {
                        sumB += Double.parseDouble(benchmark.get(k).getNormalizedValue()) * weightMap.get(benchmark.get(k).getIndicatorId());
                        sumC += Double.parseDouble(current.get(k).getNormalizedValue()) * weightMap.get(current.get(k).getIndicatorId());
                    }
                    ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                    double value = CalculationUtil.getLeafNodeContributionRate(sumB, sumC);
                    calculatedValue.setIndicatorId(benchmarkBOs.get(j).getIndicatorId());
                    calculatedValue.setCalculatedValue(String.valueOf(value));
                    calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                    calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                    calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                    calculatedValue.setBenchmarkSceneId(benchmarkSceneId);
                    calculatedValue.setSceneId(currentSceneId);
                    calculatedValue.setType(IntegerPool.ONE);

                    calculatedValueEntities.add(calculatedValue);
                }
            }
        }

        return calculatedValueEntities;
    }


    /**
     * 构建 scenarioChildrenMap key pid value children
     *
     * @param entities      想定集合
     * @param missionTaskId 任务主键
     * @return
     */
    private Map<String, List<SceneScenarioEntity>> createScenarioChildrenMap(List<SceneScenarioEntity> entities, String missionTaskId) {

        Map<String, SceneScenarioEntity> scenarioMap = entities.stream().collect(Collectors.toMap(SceneScenarioEntity::getId, s -> s));

        Map<String/*pid*/, List<SceneScenarioEntity> /*想定*/> scenarioUnderTheChildMap = Maps.newHashMap();

        for (SceneScenarioEntity scenario : entities) {
            if (StringUtils.isEmpty(scenario.getPid())) {
                scenario.setPid("0");
            }
            if (scenario.getTaskId() == null || !scenario.getTaskId().equals(missionTaskId)) {
                continue;
            }
            // 实验工程
            if (StringUtils.isEmpty(scenario.getType())) {
                continue;
            }
            SceneScenarioEntity parentSceneScenario = scenarioMap.get(scenario.getPid());
            if (parentSceneScenario != null && StringUtils.isEmpty(parentSceneScenario.getType())) {
                // 试验工程
                scenario.setPid(parentSceneScenario.getPid());
            }
            if (!scenarioUnderTheChildMap.containsKey(scenario.getPid())) {
                scenarioUnderTheChildMap.put(scenario.getPid(), Lists.newArrayList());
            }
            scenarioUnderTheChildMap.get(scenario.getPid()).add(scenario);
        }
        return scenarioUnderTheChildMap;
    }
}
