package com.yw.module.calculation.contributionCalculation.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.common.base.BaseTableHeaderBody;
import com.yw.common.base.BaseTableResponse;
import com.yw.common.defines.enums.IndicatorTypeEnum;
import com.yw.framework.common.base.api.R;
import com.yw.framework.common.defines.annotation.SchemaOperationTimeAnnotation;
import com.yw.framework.common.defines.constant.IntegerPool;
import com.yw.framework.common.defines.constant.StringPool;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.contributionCalculation.domain.dto.FindSatisfactionContributionRateDTO;
import com.yw.module.calculation.contributionCalculation.domain.entity.IndicatorCombineCredibility;
import com.yw.module.calculation.contributionCalculation.domain.entity.IndicatorValueInfo;
import com.yw.module.calculation.contributionCalculation.domain.vo.*;
import com.yw.module.calculation.contributionCalculation.service.IContributionWeightService;
import com.yw.module.calculation.contributionCalculation.service.IIndicatorCombineCredibilityService;
import com.yw.module.calculation.contributionCalculation.service.IIndicatorContributionCalculationService;
import com.yw.module.calculation.contributionCalculation.service.IIndicatorValueInfoService;
import com.yw.module.calculation.contributionCalculation.utils.CalculationUtil;
import com.yw.module.calculation.contributionRate.service.ContributionRateCalculatedValueService;
import com.yw.module.calculation.domain.entity.IndicatorWeightEntity;
import com.yw.module.calculation.domain.enums.ContributionRateType;
import com.yw.module.calculation.domain.vo.ContributionRateTypeVO;
import com.yw.module.calculation.indicatorCalculation.service.ICalcModuleService;
import com.yw.module.calculation.indicatorCalculation.service.ITIndicatorWeightService;
import com.yw.module.indicator.domain.dto.IndicatorValuesWithNameDTO;
import com.yw.module.indicator.domain.entity.*;
import com.yw.module.indicator.domain.enums.IndicatorTreeType;
import com.yw.module.indicator.service.*;
import com.yw.module.missionScene.domain.entity.ContributionRateAlgorithmEntity;
import com.yw.module.missionScene.domain.entity.ContributionRateCalculatedValueEntity;
import com.yw.module.missionScene.domain.entity.MissionTaskEntity;
import com.yw.module.missionScene.domain.entity.SceneScenarioEntity;
import com.yw.module.missionScene.service.ContributionRateAlgorithmService;
import com.yw.module.missionScene.service.IMissionTaskService;
import com.yw.module.missionScene.service.SceneScenarioService;
import com.yw.module.plan.domain.entity.SceneEntity;
import com.yw.module.plan.domain.entity.SchemaEntity;
import com.yw.module.plan.service.ISceneService;
import com.yw.module.plan.service.ISchemaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 贡献率计算控制器
 *
 * @author xiangyichen
 * @date 2024/3/7 10:00
 */
@RestController
@RequestMapping("/contributionCalculate")
@Api(tags = "贡献率计算controller")
@Slf4j
public class IndicatorContributionCalculationController {
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
     * 指标值信息服务
     */
    @Resource
    private IIndicatorValueInfoService indicatorValueInfoService;
    /**
     * 指标满足度配置服务
     */
    @Resource
    private IIndicatorCombineCredibilityService indicatorCombineCredibilityService;
    /**
     * 指标权重服务
     */
    @Resource
    private ITIndicatorWeightService indicatorWeightService;
    /**
     * 场景任务样本关联服务
     */
    @Resource
    private ContributionRateAlgorithmService contributionRateAlgorithmService;
    /**
     * 体系贡献率结果服务
     */
    @Resource
    private ContributionRateCalculatedValueService contributionRateCalculatedValueService;

    /**
     * 想定服务
     */
    @Resource
    private SceneScenarioService sceneScenarioService;

    /**
     * 体系贡献率计算服务
     */
    @Resource
    private IIndicatorContributionCalculationService indicatorContributionCalculationService;


    /**
     * 任务服务
     */
    @Resource
    private IMissionTaskService missionTaskService;

    /**
     * 方案服务
     */
    @Autowired
    private ISchemaService schemaService;
    /**
     * 计算模型service接口
     */
    @Resource
    private ICalcModuleService calcModuleService;
    /**
     * 计算模型service接口
     */
    @Resource
    private IIndicatorTreeService indicatorTreeService;

    /**
     * 指标准则service
     */
    @Resource
    private ITIndicatorStandardService indicatorStandardService;

    /**
     * 指标准则等级service
     */
    @Resource
    private IStandardLabelsService standardLabelsService;

    /**
     * 贡献率结果权重信息Service
     */
    @Resource
    private IContributionWeightService contributionWeightService;

    /**
     * 格式化对象
     */
    private static DecimalFormat decimalFormat = new DecimalFormat("#.###");

    /**
     * 初始化数据设置
     *
     * @param indicatorTreeId 指标树主键
     * @return
     */
    @GetMapping("/initValueInfo")
    @ApiOperation("初始化数据设置")
    public R<List<IndicatorValueInfoWithCredibilityRateVO>> initValueInfo(@RequestParam String indicatorTreeId) {

        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }

        String schemaId = leaves.get(0).getSid();
        List<String> sceneIdList = sceneService.findIdsBySid(schemaId);
        List<String> indicatorIds = leaves.stream().map(IndicatorEntity::getId).collect(Collectors.toList());

        // 获取计算结果
        List<IndicatorValuesWithNameDTO> schemaIndicatorValuesBoList = indicatorValueService
                .findWithNameBySidIndicatorIdSceneId(schemaId, indicatorIds, sceneIdList);
        if (ObjectUtils.isEmpty(schemaIndicatorValuesBoList)) {
            throw new BusinessException("指标未计算");
        }

        // 拼接样本、结果到vo
        Map<String, IndicatorEntity> entityMap = leaves.stream().collect(Collectors.toMap(IndicatorEntity::getId, v -> v));
        List<IndicatorValueInfoWithCredibilityRateVO> vos = new ArrayList<>();
        for (IndicatorValuesWithNameDTO bo : schemaIndicatorValuesBoList) {
            IndicatorValueInfoWithCredibilityRateVO vo = BeanUtil.toBean(bo, IndicatorValueInfoWithCredibilityRateVO.class);
            String formatValue = decimalFormat.format(Double.parseDouble(vo.getNormalizedValue()));
            vo.setNormalizedValue(formatValue);
            vo.setId(null);
            vo.setIndicatorName(bo.getName());
            vo.setSchemaId(bo.getSid());
            vo.setValueUnit(entityMap.get(vo.getIndicatorId()).getDimension());
            // 样本名称修改
            String fullName = bo.getSceneName();
            String[] names = fullName.split("/");
            List<String> newNames = Lists.newArrayList();
            //避免names中数据没有包含"-"分隔符报错
            for (String name : names) {
                if (name.contains("-")) {
                    newNames.add(name.split("-")[1]);
                } else {
                    newNames.add(name);
                }
            }
            vo.setSceneName(String.join("/", newNames));
            vos.add(vo);
        }
        return R.data(vos);
    }

    /**
     * 编辑数据设置
     *
     * @param vos 编辑数据
     */
    @PostMapping("/saveOrUpdateBatchValueInfo")
    @ApiOperation("编辑数据设置")
    @Transactional
    public R saveOrUpdateBatchValueInfo(@RequestBody List<IndicatorValueInfoWithCredibilityRateVO> vos) {

        String indicatorId = isValid(vos);
        if (ObjectUtils.isNotEmpty(indicatorId)) {
            return R.data(500, indicatorId, "数据配置不合法，请检查指标");
        }


        // 需要保存的IndicatorValueInfo
        List<IndicatorValueInfo> infos = new ArrayList<>();
        List<String> indicatorIds = new ArrayList<>();
        // 指标数据设置值
        // key indicatorId value List<IndicatorValueInfoWithCredibilityRateVO>
        Map<String, List<IndicatorValueInfoWithCredibilityRateVO>> voMap = vos.stream()
                .collect(Collectors.groupingBy(IndicatorValueInfoWithCredibilityRateVO::getIndicatorId));
        voMap.forEach((key, value) -> {
            IndicatorValueInfoWithCredibilityRateVO vo = value.get(0);
            IndicatorValueInfo info = BeanUtil.toBean(vo, IndicatorValueInfo.class);
            info.setId(null);
            infos.add(info);
            indicatorIds.add(key);
        });
        indicatorValueInfoService.deleteByIndicatorIds(indicatorIds);
        indicatorValueInfoService.saveOrUpdateBatch(infos);
        return R.data("");
    }

    /**
     * 查询数据设置
     *
     * @param indicatorTreeId 指标树主键
     * @return
     */
    @GetMapping("/listIndicatorValueInfoWithCredibilityRate")
    @ApiOperation("查询数据设置")
    public R<List<IndicatorValueInfoWithCredibilityRateVO>> listIndicatorValueInfoWithCredibilityRate(@RequestParam String indicatorTreeId) {

        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }

        String schemaId = leaves.get(0).getSid();
        List<String> sceneIdList = sceneService.findIdsBySid(schemaId);
        List<String> indicatorIds = leaves.stream().map(IndicatorEntity::getId).collect(Collectors.toList());

        // 获取计算结果
        List<IndicatorValuesWithNameDTO> schemaIndicatorValuesBoList = indicatorValueService
                .findWithNameBySidIndicatorIdSceneId(schemaId, indicatorIds, sceneIdList);
        if (ObjectUtils.isEmpty(schemaIndicatorValuesBoList)) {
            throw new BusinessException("指标未计算");
        }

        List<IndicatorValueInfoWithCredibilityRateVO> vos = new ArrayList<>();
        List<IndicatorValueInfoVO> valueInfoVOS = indicatorValueInfoService.listByIndicatorId(indicatorIds);
        List<IndicatorCombineCredibilityVO> credibilityVOS = indicatorCombineCredibilityService.listByIndicators(indicatorIds);

        Map<String, IndicatorValueInfoVO> valueInfoVOMap = valueInfoVOS.stream().collect(Collectors.toMap(IndicatorValueInfoVO::getIndicatorId, v -> v));
        Map<String, List<IndicatorEntity>> indicatorParentAndChildMap = leaves.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        for (Map.Entry<String, List<IndicatorEntity>> pidAndChildIndicatorValue : indicatorParentAndChildMap.entrySet()) {
            for (IndicatorEntity indicatorEntity : pidAndChildIndicatorValue.getValue()) {
                List<IndicatorValueInfoWithCredibilityRateVO> indicatorValues = Lists.newArrayList();
                for (IndicatorValuesWithNameDTO bo : schemaIndicatorValuesBoList) {
                    if (!bo.getIndicatorId().equals(indicatorEntity.getId())) {
                        continue;
                    }
                    IndicatorValueInfoWithCredibilityRateVO vo = BeanUtil.toBean(bo, IndicatorValueInfoWithCredibilityRateVO.class);
                    vo.setIndicatorName(bo.getName());
                    vo.setSchemaId(bo.getSid());
                    vo.setValueUnit(valueInfoVOMap.get(bo.getIndicatorId()).getValueUnit());
                    vo.setCredibilityRate(valueInfoVOMap.get(bo.getIndicatorId()).getCredibilityRate());
                    vo.setIdealValue(valueInfoVOMap.get(bo.getIndicatorId()).getIdealValue());
                    vo.setMinValue(valueInfoVOMap.get(bo.getIndicatorId()).getMinValue());
                    vo.setIndicatorType(valueInfoVOMap.get(bo.getIndicatorId()).getIndicatorValueType());
                    for (IndicatorCombineCredibilityVO credibilityVO : credibilityVOS) {
                        if (Objects.equals(vo.getIndicatorId(), credibilityVO.getIndicatorId()) && Objects.equals(vo.getSceneId(), credibilityVO.getSceneId())) {
                            vo.setMatchRate(credibilityVO.getMatchRate());
                        }
                    }
                    indicatorValues.add(vo);
                }
                indicatorValues.sort(Comparator.comparing(IndicatorValueInfoWithCredibilityRateVO::getSceneName));
                vos.addAll(indicatorValues);
            }
        }

        return R.data(vos);
    }

    /**
     * 查询配置是否存在
     *
     * @param indicatorTreeId 指标树id
     * @return
     */
    @GetMapping("/isConfig")
    @ApiOperation("查询是否存在配置")
    public R<Boolean> isConfig(@RequestParam String indicatorTreeId) {

        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }
        List<String> indicatorIds = leaves.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        List<IndicatorValueInfoVO> valueInfoVOS = indicatorValueInfoService.listByIndicatorId(indicatorIds);
        return R.data(ObjectUtils.isNotEmpty(valueInfoVOS));
    }

    /**
     * 满足度计算
     *
     * @param indicatorTreeId 指标树
     * @return
     */
    @GetMapping("/calc1")
    @ApiOperation("计算满足度信度")
    @Deprecated
    public R<List<IndicatorCombineCredibilityVO>> calc1(@RequestParam String indicatorTreeId) {

        // 计算后得到的上层节点IndicatorCombineCredibilitylsit
        List<IndicatorCombineCredibility> resultCredibilities = new ArrayList<>();

        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }

        // 获取满足度信度的计算参数 匹配度 和 实际可信度
        List<String> indicatorIds = leaves.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        List<IndicatorCombineCredibilityVO> credibilities = indicatorCombineCredibilityService.listByIndicators(indicatorIds);
        List<IndicatorValueInfoVO> valueInfoVOS = indicatorValueInfoService.listByIndicatorId(indicatorIds);

        // key pid value List<IndicatorEntity>
        Map<String, List<IndicatorEntity>> map = leaves.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        // key IndicatorId value IndicatorEntity
        Map<String, IndicatorValueInfoVO> valueInfoVOMap = valueInfoVOS.stream().collect(Collectors.toMap(IndicatorValueInfoVO::getIndicatorId, v -> v));
        // key sceneId value List<IndicatorCombineCredibilityVO>
        Map<String, List<IndicatorCombineCredibilityVO>> sceneMap = credibilities.stream().collect(Collectors.groupingBy(IndicatorCombineCredibilityVO::getSceneId));

        // 获取上一层的指标id，根据叶子指标pid查询
        List<String> fatherIds = map.keySet().stream().collect(Collectors.toList());
        List<IndicatorEntity> fathers = indicatorService.listByIds(fatherIds);

        // 计算每个样本下每个叶子指标的满足度信度
        // 遍历样本
        for (Map.Entry<String, List<IndicatorCombineCredibilityVO>> e : sceneMap.entrySet()) {
            // key indicatorId value 满足度信度
            Map<String, double[]> resultMap = new HashMap<>();
            // 遍历指标
            for (IndicatorCombineCredibilityVO i : e.getValue()) {
                Double matchRate = i.getMatchRate();
                Double credibilityRate = valueInfoVOMap.get(i.getIndicatorId()).getCredibilityRate();
                double[] result = CalculationUtil.getCombineCredibilityRate(matchRate, credibilityRate);
                resultMap.put(i.getIndicatorId(), result);
            }
            // 获取上一层的指标id，根据叶子指标pid查询
            // 计算叶子节点父节点的满足度
            for (IndicatorEntity i : fathers) {
                Double fatherMatchRate = 1D;
                List<IndicatorEntity> children = map.get(i.getId());

                IndicatorCombineCredibility credibility = new IndicatorCombineCredibility();

                for (int k = 0; k < children.size(); k++) {
                    String indicatorId = children.get(k).getId();
                    fatherMatchRate *= (1 - valueInfoVOMap.get(indicatorId).getCredibilityRate());
                }

                // new
                String prefix = "m(" + i.getName() + "=1|";
                List<List<Integer>> combine = generateCombinations(children.size());
                for (List<Integer> c : combine) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(prefix);
                    double combineMatchRate = 1D;
                    for (int j = 0; j < children.size(); j++) {
                        IndicatorEntity currentIndicator = children.get(j);
                        double[] combineCredibilityRate = resultMap.get(currentIndicator.getId());
                        int index = c.get(j);
//                        fatherMatchRate *= (combineCredibilityRate[0] + combineCredibilityRate[1]);
                        combineMatchRate *= combineCredibilityRate[index];
                        sb.append(currentIndicator.getName()).append("=").append(index).append("^");
                    }
                    sb.delete(sb.length() - 1, sb.length());
                    // 组装条件规则
                    if (StringUtils.isEmpty(credibility.getCombineCredibilityParam())) {
                        credibility.setCombineCredibilityParam(sb.toString() + ")=" + decimalFormat.format(combineMatchRate));
                    } else {
                        String tmp = credibility.getCombineCredibilityParam() + sb.toString() + ")=" + decimalFormat.format(combineMatchRate);
                        credibility.setCombineCredibilityParam(tmp);
                    }
                    credibility.setCombineCredibilityParam(credibility.getCombineCredibilityParam() + ";");
                }

                credibility.setMatchRate(fatherMatchRate);
                credibility.setSceneId(e.getKey());
                credibility.setIndicatorId(i.getId());
                credibility.setIndicatorName(i.getName());
                credibility.setCombineCredibilityNumber(combine.size());
                credibility.setSchemaId(i.getSid());
                resultCredibilities.add(credibility);
            }
        }
        // 计算信度参数
        indicatorCombineCredibilityService.deleteByIndicatorIds(fatherIds);
        indicatorCombineCredibilityService.saveBatch(resultCredibilities);

        // 组装样本名称
        List<IndicatorCombineCredibilityVO> vos = resultCredibilities.stream().map(IndicatorCombineCredibility::toVO).collect(Collectors.toList());
        List<String> sceneIds = new ArrayList<>(sceneMap.keySet());
        List<SceneEntity> sceneEntities = sceneService.findByIdIn(sceneIds);
        Map<String, String> sceneEntityMap = sceneEntities.stream().collect(Collectors.toMap(SceneEntity::getId, SceneEntity::getName));
        vos.forEach(vo -> {
            String fullName = sceneEntityMap.get(vo.getSceneId());
            String[] names = fullName.split("/");
            List<String> newNames = Arrays.stream(names).map(name -> {
                if (name.split("-").length > 1) {
                    return name.split("-")[1];
                } else {
                    return name;
                }
            }).collect(Collectors.toList());
            vo.setSceneName(String.join("/", newNames));
        });
        return R.data(vos);
    }

    /**
     * 查询满足度信度及信度参数
     *
     * @param indicatorTreeId 指标树
     * @return
     */
    @GetMapping("/listIndicatorCombineCredibility")
    @ApiOperation("查询满足度信度及信度参数")
    public R<List<IndicatorCombineCredibilityVO>> listIndicatorCombineCredibility(@RequestParam String indicatorTreeId) {
        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }
        // key pid value List<IndicatorEntity>
        Map<String, List<IndicatorEntity>> map = leaves.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        // 获取上一层的指标id，根据叶子指标pid查询
        List<String> fatherIds = map.keySet().stream().collect(Collectors.toList());
        // 组装样本名称
        List<IndicatorCombineCredibilityVO> vos = indicatorCombineCredibilityService.listByIndicators(fatherIds);
        List<SceneEntity> sceneEntities = sceneService.findBySchemeId(leaves.get(0).getSid());
        Map<String, String> sceneEntityMap = sceneEntities.stream().collect(Collectors.toMap(SceneEntity::getId, SceneEntity::getName));
        vos.forEach(vo -> {
            String fullName = sceneEntityMap.get(vo.getSceneId());
            if (StringUtils.isEmpty(fullName)) {
                return;
            }
            String[] names = fullName.split("/");
            List<String> newNames = Arrays.stream(names).map(name -> {
                if (name.split("-").length > 1) {
                    return name.split("-")[1];
                } else {
                    return name;
                }
            }).collect(Collectors.toList());
            vo.setSceneName(String.join("/", newNames));
        });
        return R.data(vos);
    }

    /**
     * 计算贡献率（证据推理）
     *
     * @param indicatorTreeId 指标树id
     * @return
     */
    @SchemaOperationTimeAnnotation
    @GetMapping("/calcContributionRate")
    @ApiOperation("计算贡献率（证据推理）")
    public R calcContributionRate(@RequestParam String indicatorTreeId) {
        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }


        // 获取样本
        String schemaId = leaves.get(0).getSid();
        List<String> sceneIdList = sceneService.findIdsBySid(schemaId);
        if (ObjectUtils.isEmpty(sceneIdList)) {
            throw new BusinessException("样本不存在");
        }
        if (sceneIdList.size() < 2) {
            throw new BusinessException("证据推理计算至少需要两个样本");
        }
        sceneIdList = sceneIdList.stream().sorted().collect(Collectors.toList());
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
        // 叶子节点贡献率计算
        // 获取满足度
        calc(indicatorTreeId);
        List<IndicatorCombineCredibilityVO> leafCredibilityVOs = indicatorCombineCredibilityService.listByIndicators(leafNodeIds);
//        if (leafCredibilityVOs.isEmpty()) {
//            //先计算再查询结果
//            calc(indicatorTreeId);
//            leafCredibilityVOs = indicatorCombineCredibilityService.listByIndicators(leafNodeIds);
//            //            throw new BusinessException("请先计算满足度");
//        }

        Map<String, List<IndicatorCombineCredibilityVO>> sceneMap = leafCredibilityVOs.stream().collect(Collectors.groupingBy(IndicatorCombineCredibilityVO::getSceneId));
        // 基础想定id
        List<SceneScenarioEntity> sceneScenarioBySid = sceneScenarioService.findSceneScenarioBySid(schemaId);
        Map<String, List<SceneScenarioEntity>> listMap = sceneScenarioBySid.stream().filter(s -> StringUtils.isNotEmpty(s.getTaskId())).collect(Collectors.groupingBy(SceneScenarioEntity::getTaskId));
        for (Map.Entry<String, List<SceneScenarioEntity>> missionTaskAndSceneScenarioChildMap : listMap.entrySet()) {
            List<SceneScenarioEntity> sceneScenarioChildValues = missionTaskAndSceneScenarioChildMap.getValue();
            // 子想定 or 想定
            // 基础想定id
            SceneScenarioEntity baseSceneScenario = null;
            int doesIncludeSubScenarios = 0;
            for (SceneScenarioEntity sceneScenarioChildValue : sceneScenarioChildValues) {
                if (!"0".equals(sceneScenarioChildValue.getPid())) {
                    doesIncludeSubScenarios = 1;
                    break;
                }
            }
            if (doesIncludeSubScenarios == 0) {
                baseSceneScenario = sceneScenarioChildValues.get(0);
                for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioChildValues) {
                    if (sceneScenarioEntity.getBaseScenarioType() != null && sceneScenarioEntity.getBaseScenarioType() == 1) {
                        baseSceneScenario = sceneScenarioEntity;
                        break;
                    }
                }
            } else {
                for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioChildValues) {
                    // 先保证一个有
                    if (baseSceneScenario == null && !"0".equals(sceneScenarioEntity.getPid())) {
                        baseSceneScenario = sceneScenarioEntity;
                    }
                    if (sceneScenarioEntity.getBaseScenarioType() != null && sceneScenarioEntity.getBaseScenarioType() == 1) {
                        baseSceneScenario = sceneScenarioEntity;
                        break;
                    }
                }
            }
            if (baseSceneScenario == null) {
                continue;
            }
            // 基础想定的样本id
            String benchmarkSceneId = null;
            for (ContributionRateAlgorithmEntity contributionRateAlgorithmEntity : contributionRateAlgorithmEntities) {
                if (contributionRateAlgorithmEntity.getSceneScenarioId().equals(baseSceneScenario.getId())) {
                    benchmarkSceneId = contributionRateAlgorithmEntity.getSceneId();
                }
            }

            for (String currentSceneId : sceneIdList) {
                if (currentSceneId.equals(benchmarkSceneId)) {
                    continue;
                }
                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                if (ObjectUtils.isEmpty(algorithmEntity)) {
                    continue;
                }
                if (!algorithmEntity.getMissionTaskId().equals(missionTaskAndSceneScenarioChildMap.getKey())) {
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
                    calculatedValue.setType(ContributionRateType.ENHANCEMENT.getCode());

                    calculatedValueEntities.add(calculatedValue);
                }
            }
            // 叶子节点父级贡献率计算
            // 获取上一层的指标id，根据叶子指标pid查询
            Map<String, List<IndicatorEntity>> pidMap = allNodes.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
            for (String currentSceneId : sceneIdList) {
                if (currentSceneId.equals(benchmarkSceneId)) {
                    continue;
                }
                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                if (ObjectUtils.isEmpty(algorithmEntity)) {
                    continue;
                }
                if (!algorithmEntity.getMissionTaskId().equals(missionTaskAndSceneScenarioChildMap.getKey()) || algorithmEntity.getSceneScenarioId().equals(baseSceneScenario.getId())) {
                    continue;
                }
                // 获取根指标结果
                List<IndicatorEntity> indicatorEntities = pidMap.get("0");
                IndicatorEntity indicatorEntity = indicatorEntities.get(0);

                // 基础想定满足度
                List<IndicatorCombineCredibilityVO> benchmarkCredibilityVOs = sceneMap.get(benchmarkSceneId);
                Map<String, Double> childrenBenchmarkMatchRateMap = benchmarkCredibilityVOs.stream().collect(Collectors.toMap(IndicatorCombineCredibilityVO::getIndicatorId, IndicatorCombineCredibilityVO::getMatchRate));
                // 样本满足度
                List<IndicatorCombineCredibilityVO> credibilityVOS = sceneMap.get(currentSceneId);
                Map<String, Double> childrenMatchRateMap = credibilityVOS.stream().collect(Collectors.toMap(IndicatorCombineCredibilityVO::getIndicatorId, IndicatorCombineCredibilityVO::getMatchRate));
                aggregate(indicatorEntity, childrenBenchmarkMatchRateMap, childrenMatchRateMap, pidMap, calculatedValueEntities, algorithmEntity, weightMap);
            }
        }
        if (contributionRateCalculatedValueService.saveValues(calculatedValueEntities, ContributionRateType.ENHANCEMENT.getCode())) {
            Optional<SchemaEntity> schemaEntityOptional = schemaService.findById(schemaId);
            if (schemaEntityOptional.isPresent()) {
                updateSchema(indicatorTreeId, schemaEntityOptional, "EVIDENCE_REASONING");
            }
            return R.success();
        } else {
            return R.fail("因设置的想定不在同一个任务下无法计算");
        }
    }

    /**
     * 递归计算指标结果
     *
     * @param indicatorEntity               当前指标
     * @param childrenBenchmarkMatchRateMap 基础想定
     * @param childrenMatchRateMap          对比想定
     * @param pidMap                        父亲指标节点
     * @param calculatedValueEntities       计算值保存
     * @param algorithmEntity               想定场景任务ID标志
     * @param weightMap                     指标权重
     */
    private void aggregate(IndicatorEntity indicatorEntity, Map<String, Double> childrenBenchmarkMatchRateMap,
                           Map<String, Double> childrenMatchRateMap, Map<String, List<IndicatorEntity>> pidMap,
                           List<ContributionRateCalculatedValueEntity> calculatedValueEntities, ContributionRateAlgorithmEntity algorithmEntity,
                           Map<String, Double> weightMap) {
        // 叶子节点不处理
        if (!pidMap.containsKey(indicatorEntity.getId())) {
            return;
        }
        String currentId = indicatorEntity.getId();
        List<IndicatorEntity> indicatorEntities = pidMap.get(currentId);
        // 先递归处理所有叶子节点
        for (IndicatorEntity entity : indicatorEntities) {
            aggregate(entity, childrenBenchmarkMatchRateMap, childrenMatchRateMap, pidMap, calculatedValueEntities, algorithmEntity, weightMap);
        }
        // 计算当前指标的贡献率值
        double sumB = 0D;
        double sumC = 0D;
        for (IndicatorEntity entity : indicatorEntities) {
            if (weightMap.get(entity.getId())==null){
                throw new BusinessException("指标暂未设置权重："+entity.getName());
            }
            if (childrenBenchmarkMatchRateMap.get(entity.getId())==null){
                throw new BusinessException("指标未计算:"+entity.getName());
            }
            sumB += (childrenBenchmarkMatchRateMap.get(entity.getId())) * weightMap.get(entity.getId());
            sumC += (childrenMatchRateMap.get(entity.getId())) * weightMap.get(entity.getId());
        }
        sumB = BigDecimal.valueOf(sumB).setScale(3, RoundingMode.HALF_UP).doubleValue();
        sumC = BigDecimal.valueOf(sumC).setScale(3, RoundingMode.HALF_UP).doubleValue();
        childrenBenchmarkMatchRateMap.put(currentId, sumB);
        childrenMatchRateMap.put(currentId, sumC);
        ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
        double value = CalculationUtil.getLeafNodeContributionRate(sumB, sumC);
        calculatedValue.setSumB(sumB + "");
        calculatedValue.setSumC(sumC + "");
        calculatedValue.setIndicatorId(indicatorEntity.getId());
        calculatedValue.setCalculatedValue(String.valueOf(value));
        calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
        calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
        calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
        calculatedValue.setSceneId(algorithmEntity.getSceneId());
        calculatedValue.setType(ContributionRateType.ENHANCEMENT.getCode());
        calculatedValueEntities.add(calculatedValue);
    }

    /**
     * 构建排列组合01
     *
     * @param n 指数
     * @return
     */
    // 构建排列组合01
    private List<List<Integer>> generateCombinations(int n) {
        List<List<Integer>> result = new ArrayList<>();
        // 所有组合数
        int totalCombinations = 1 << n;

        for (int i = 0; i < totalCombinations; i++) {
            List<Integer> combine = new ArrayList<>();
            for (int j = n - 1; j >= 0; j--) {
                combine.add((i >> j) & 1);
            }
            result.add(combine);
        }
        return result;
    }

    /**
     * 查询贡献率计算类型
     */
    @GetMapping("/findContributionRateType")
    @ApiOperation("查询贡献率计算类型")
    public R<List<ContributionRateTypeVO>> findContributionRateType() {
        return R.data(indicatorContributionCalculationService.findContributionRateType());
    }


    //1、基于提升度的体系贡献率计算（原贡献率计算）
    //	a：取基础想定（基础想定不计算）
    //	b：（其他样本值-基础想定样本值） / 基础想定样本值
    //	c：结论：评判基于基础想定提示/下降多少
    //2、基于满足度的体系贡献率计算
    //	a：取准则优差值计算
    //	b：所有样本都有贡献率值
    //	c：没有基础想定概念，没有对比结论
    //3、加权综合体系贡献率算法
    //	a：设置基于提升度的体系贡献率、基于满足度的体系贡献率权重
    //	b：由于基于提升度的体系贡献率存在基础想定样本没有值情况，所以加权综合体系贡献率算法对应的基础想定值为0
    //	c：没有基础想定概念，没有对比结论

    /**
     * 计算贡献率（加权综合）
     *
     * @param indicatorTreeId 指标树
     * @return
     */
    @SchemaOperationTimeAnnotation
    @GetMapping("/calcContributionRate2")
    @ApiOperation("计算贡献率（加权综合）")
    public R calcContributionRate2(@RequestParam String indicatorTreeId) {

        long startTime = System.currentTimeMillis();
        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }


        // 获取样本
        String schemaId = leaves.get(0).getSid();
        List<String> sceneIdList = sceneService.findIdsBySid(schemaId).stream().sorted().collect(Collectors.toList());
        //增加计算样本判断
        if (sceneIdList.size() <= 1) {
            throw new BusinessException("参与贡献率计算，要求已评估计算样本必须大于等于2个");
        }
        List<IndicatorEntity> allNodes = indicatorService.findByTreeId(indicatorTreeId);

        // id
        List<String> allIds = allNodes.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        List<String> leafNodeIds = leaves.stream().map(IndicatorEntity::getId).collect(Collectors.toList());

        // 获取权重
        List<IndicatorWeightEntity> indicatorWeightEntities = indicatorWeightService.listByIndicatorIds(allIds);
        // key indicatorId value weight
        Map<String, Double> weightMap = indicatorWeightEntities.stream().collect(Collectors.toMap(IndicatorWeightEntity::getIndicatorId, IndicatorWeightEntity::getWeight));

        // 获取想定关联数据
        List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findBySceneIds(sceneIdList);
//                .stream()
//                .sorted(Comparator.comparing(ContributionRateAlgorithmEntity::getSceneId))
//                .collect(Collectors.toList());
        if (contributionRateAlgorithmEntities.isEmpty()) {
            throw new BusinessException("计算失败,无样本");
        }
        // key sceneId
        Map<String, ContributionRateAlgorithmEntity> contributionRateAlgorithmEntityMap = contributionRateAlgorithmEntities.stream().collect(Collectors.toMap(ContributionRateAlgorithmEntity::getSceneId, v -> v));

        // 计算
        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = new ArrayList<>();
        List<SceneScenarioEntity> sceneScenarioBySid = sceneScenarioService.findSceneScenarioBySid(schemaId);
        Map<String, List<SceneScenarioEntity>> listMap = sceneScenarioBySid.stream().filter(s -> StringUtils.isNotEmpty(s.getTaskId())).collect(Collectors.groupingBy(SceneScenarioEntity::getTaskId));
        for (Map.Entry<String, List<SceneScenarioEntity>> missionTaskAndSceneScenarioChildMap : listMap.entrySet()) {
//            List<SceneScenarioEntity> sceneScenarioChildValues = missionTaskAndSceneScenarioChildMap.getValue();
            // 子想定 or 想定
            // 基础想定id
//            SceneScenarioEntity baseSceneScenario = contributionRateCalculatedValueService.getBaseSceneScenario(sceneScenarioChildValues);


            // 基础想定的样本id
//            String benchmarkSceneId = null;
//            for (ContributionRateAlgorithmEntity contributionRateAlgorithmEntity : contributionRateAlgorithmEntities) {
//                if (contributionRateAlgorithmEntity.getSceneScenarioId().equals(baseSceneScenario.getId())) {
//                    benchmarkSceneId = contributionRateAlgorithmEntity.getSceneId();
//                    break;
//                }
//            }

            String benchmarkSceneId = sceneService.findBasicScene(schemaId).getId();

            // 获取叶子节点计算结果
            List<IndicatorValuesWithNameDTO> schemaIndicatorValuesBoList = indicatorValueService
                    .findWithNameBySidIndicatorIdSceneId(schemaId, leafNodeIds, sceneIdList);
            if (CollUtil.isEmpty(schemaIndicatorValuesBoList)) {
                throw new BusinessException("指标未计算，请先计算");
            }
            Map<String, List<IndicatorValuesWithNameDTO>> sceneMapLeaves = schemaIndicatorValuesBoList.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getSceneId));
            for (int i = 0; i < sceneIdList.size(); i++) {
                String currentSceneId = sceneIdList.get(i);
                if (currentSceneId.equals(benchmarkSceneId)) {
                    continue;
                }
                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                if (ObjectUtils.isEmpty(algorithmEntity)) {
                    continue;
                }
                // 不属于当前任务的不参与计算
                if (!algorithmEntity.getMissionTaskId().equals(missionTaskAndSceneScenarioChildMap.getKey())) {
                    continue;
                }
//                if (algorithmEntity.getSceneScenarioId().equals(baseSceneScenario.getId())) {
//                    continue;
//                }
                // 基础想定满足度
                List<IndicatorValuesWithNameDTO> benchmarkBOs = sceneMapLeaves.get(benchmarkSceneId);
                // 样本满足度
                List<IndicatorValuesWithNameDTO> bos = sceneMapLeaves.get(currentSceneId);
                //
                if (CollectionUtils.isEmpty(benchmarkBOs) || CollectionUtils.isEmpty(bos)) {
                    continue;
                }
                for (int j = 0; j < benchmarkBOs.size(); j++) {
                    ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                    double value = CalculationUtil.getLeafNodeContributionRate(Double.parseDouble(benchmarkBOs.get(j).getNormalizedValue()), Double.parseDouble(bos.get(j).getNormalizedValue()));
                    calculatedValue.setIndicatorId(benchmarkBOs.get(j).getIndicatorId());
                    calculatedValue.setCalculatedValue(String.valueOf(value));
                    calculatedValue.setSceneId(currentSceneId);
                    calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                    calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                    calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                    calculatedValue.setType(ContributionRateType.ENHANCEMENT.getCode());

                    calculatedValueEntities.add(calculatedValue);
                }
            }
            Map<String, List<IndicatorEntity>> pidMap = allNodes.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));

            for (String currentSceneId : sceneIdList) {
                if (currentSceneId.equals(benchmarkSceneId)) {
                    continue;
                }
                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
                if (ObjectUtils.isEmpty(algorithmEntity)) {
                    continue;
                }
//                if (!algorithmEntity.getMissionTaskId().equals(missionTaskAndSceneScenarioChildMap.getKey()) || algorithmEntity.getSceneScenarioId().equals(baseSceneScenario.getId())) {
//                    continue;
//                }
                // 获取根指标结果
                List<IndicatorEntity> indicatorEntities = pidMap.get("0");
                IndicatorEntity indicatorEntity = indicatorEntities.get(0);

                // 基础想定满足度
                List<IndicatorValuesWithNameDTO> benchmarkCredibilityVOs = sceneMapLeaves.get(benchmarkSceneId);
                if (CollectionUtils.isEmpty(benchmarkCredibilityVOs)) {
                    continue;
                }
                Map<String, Double> childrenBenchmarkMatchRateMap = benchmarkCredibilityVOs.stream().collect(Collectors.toMap(IndicatorValuesWithNameDTO::getIndicatorId, s -> Double.parseDouble(s.getNormalizedValue())));
                // 样本满足度
                List<IndicatorValuesWithNameDTO> credibilityVOS = sceneMapLeaves.get(currentSceneId);
                if (CollectionUtils.isEmpty(credibilityVOS)) {
                    continue;
                }
                Map<String, Double> childrenMatchRateMap = credibilityVOS.stream().collect(Collectors.toMap(IndicatorValuesWithNameDTO::getIndicatorId, s -> Double.parseDouble(s.getNormalizedValue())));
                aggregate(indicatorEntity, childrenBenchmarkMatchRateMap, childrenMatchRateMap, pidMap, calculatedValueEntities, algorithmEntity, weightMap);
            }
            // 获取其他节点计算结果
//            List<IndicatorValuesWithNameBO> allNodeBoList = indicatorValueService
//                    .findWithNameBySidIndicatorIdSceneId(schemaId, allIds, sceneIdList);
//            Map<String, List<IndicatorValuesWithNameBO>> sceneMapAll = allNodeBoList.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameBO::getSceneId));
//            for (int i = 0; i < contributionRateAlgorithmEntities.size(); i++) {
//                String currentSceneId = contributionRateAlgorithmEntities.get(i).getSceneId();
//                if (currentSceneId.equals(benchmarkSceneId)) {
//                    continue;
//                }
//                ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(currentSceneId);
//                if (ObjectUtils.isEmpty(algorithmEntity)) {
//                    continue;
//                }
//                // 不属于当前任务的不参与计算
//                if (!algorithmEntity.getMissionTaskId().equals(missionTaskAndSceneScenarioChildMap.getKey())) {
//                    continue;
//                }
//                if (algorithmEntity.getSceneScenarioId().equals(baseSceneScenario.getId())) {
//                    continue;
//                }
//                // 基础想定满足度
//                List<IndicatorValuesWithNameBO> benchmarkBOs = sceneMapAll.get(benchmarkSceneId);
//                Map<String, List<IndicatorValuesWithNameBO>> pidBenchmarkBOMap = benchmarkBOs.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameBO::getPid));
//                // 样本满足度
//                List<IndicatorValuesWithNameBO> bos = sceneMapAll.get(currentSceneId);
//                Map<String, List<IndicatorValuesWithNameBO>> currentBOMap = bos.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameBO::getPid));
//                //
//                for (int j = 0; j < benchmarkBOs.size(); j++) {
//                    String indicatorId = benchmarkBOs.get(j).getIndicatorId();
//                    List<IndicatorEntity> indicatorEntities = pidMap.get(indicatorId);
//                    if (ObjectUtils.isEmpty(indicatorEntities)) {
//                        continue;
//                    }
//                    IndicatorEntity currentIndicator = null;
//                    for (IndicatorEntity allNode : allNodes) {
//                        if (allNode.getId().equals(indicatorId)) {
//                            currentIndicator = allNode;
//                            break;
//                        }
//                    }
//                    if (currentIndicator == null) {
//                        continue;
//                    }
//                    // children
//                    List<IndicatorValuesWithNameBO> benchmark = pidBenchmarkBOMap.get(currentIndicator.getPid());
//                    List<IndicatorValuesWithNameBO> current = currentBOMap.get(currentIndicator.getPid());
//                    double sumB = 0D;
//                    double sumC = 0D;
//                    //防空判断
//                    if (!CollectionUtils.isEmpty(benchmark)) {
//                        for (int k = 0; k < benchmark.size(); k++) {
//                            if (benchmark.get(k).getIndicatorId().equals(indicatorId)) {
//                                sumB = decimalPointRetention.setScale(new BigDecimal(benchmark.get(k).getNormalizedValue()), 0d);
//                                break;
//                            }
//
//                        }
//                        for (int k = 0; k < current.size(); k++) {
//                            if (current.get(k).getIndicatorId().equals(indicatorId)) {
//                                sumC = decimalPointRetention.setScale(new BigDecimal(current.get(k).getNormalizedValue()), 0d);
//                                break;
//                            }
//                        }
////                    for (int k = 0; k < benchmark.size(); k++) {
////
////                        sumB += decimalPointRetention.setScale(new BigDecimal(benchmark.get(k).getNormalizedValue()).multiply(BigDecimal.valueOf(Objects.isNull(weightMap.get(benchmark.get(k).getIndicatorId())) ? 0 :
////                                weightMap.get(benchmark.get(k).getIndicatorId()))),0.0);
////                        sumC += decimalPointRetention.setScale(new BigDecimal(current.get(k).getNormalizedValue()).multiply(BigDecimal.valueOf(Objects.isNull(weightMap.get(current.get(k).getIndicatorId())) ? 0 :
////                                weightMap.get(current.get(k).getIndicatorId()))),0.0);
////                    }
//                    }
//                    ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
//                    double value = CalculationUtil.getLeafNodeContributionRate(sumB, sumC);
//                    calculatedValue.setIndicatorId(benchmarkBOs.get(j).getIndicatorId());
//                    calculatedValue.setCalculatedValue(String.valueOf(value));
//                    calculatedValue.setCompeteSceneId(contributionRateAlgorithmEntities.get(i).getCompeteSceneId());
//                    calculatedValue.setMissionTaskId(contributionRateAlgorithmEntities.get(i).getMissionTaskId());
//                    calculatedValue.setSceneScenarioId(contributionRateAlgorithmEntities.get(i).getSceneScenarioId());
//
//                    calculatedValueEntities.add(calculatedValue);
//                }
//            }
        }

        //判断方案是否在存在
        Optional<SchemaEntity> schemaEntityOptional = schemaService.findById(schemaId);
        if (schemaEntityOptional.isPresent()) {
            updateSchema(indicatorTreeId, schemaEntityOptional, "CONTRIBUTION_METHOD_WEIGHTED_SUM");
        }

        if (contributionRateCalculatedValueService.saveValues(calculatedValueEntities, ContributionRateType.ENHANCEMENT.getCode())) {
            long endTime = System.currentTimeMillis();
            return R.success("计算完成! 耗时: " + (endTime - startTime) + "毫秒");
        } else {
            return R.fail("因设置的想定不在同一个任务下无法计算");
        }
    }


    /**
     * 计算贡献率（基于满足度的体系贡献率算法）
     *
     * @param indicatorTreeId 指标树
     * @return 提示信息
     */
    @SchemaOperationTimeAnnotation
    @GetMapping("/calcContributionRateByDemand")
    @ApiOperation("计算贡献率（基于满足度的体系贡献率算法）")
    public R<String> calcContributionRateByDemand(@RequestParam String indicatorTreeId) {
        TimeInterval timer = DateUtil.timer();

        log.info("根据指标树ID-{}，获取指标信息", indicatorTreeId);
        List<IndicatorEntity> indicatorEntities = indicatorService.findByTreeId(indicatorTreeId);
        //获取叶子指标
        List<IndicatorEntity> leafNode = getLeafNode(indicatorEntities);
        List<String> leafNodeIdList = leafNode.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        //获取方案ID
        String sid = indicatorEntities.get(0).getSid();

        log.info("获取指标计算结果");
        List<String> indicatorIdList = indicatorEntities.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        List<IndicatorValuesEntity> indicatorValuesEntities = indicatorValueService.list(new LambdaQueryWrapper<IndicatorValuesEntity>()
                .in(IndicatorValuesEntity::getIndicatorId, indicatorIdList)
                .eq(IndicatorValuesEntity::getSid, sid));
        if (CollUtil.isEmpty(indicatorValuesEntities)) {
            log.error("指标未计算，请先计算");
            throw new BusinessException("指标未计算，请先计算");
        }

        // 获取想定关联数据
        List<String> sceneIdList = sceneService.findIdsBySid(sid).stream().sorted().collect(Collectors.toList());
        List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findBySceneIds(sceneIdList);
        if (contributionRateAlgorithmEntities.isEmpty()) {
            throw new BusinessException("计算失败,无样本");
        }
        // key sceneId
        Map<String, ContributionRateAlgorithmEntity> contributionRateAlgorithmEntityMap = contributionRateAlgorithmEntities.stream().collect(Collectors.toMap(ContributionRateAlgorithmEntity::getSceneId, v -> v));


        log.info("获取指标准则");
        //获取指标id、准则优最差值map
        Map<String, String> indicatorStandardMap = getIndicatorStandardMap(indicatorEntities, "优");
        Map<String, String> badStandardMap = getIndicatorStandardMap(indicatorEntities, "差");

        log.info("获取指标权重");
        // 获取权重
        List<IndicatorWeightEntity> indicatorWeightEntities = indicatorWeightService.listByIndicatorIds(indicatorIdList);
        // key indicatorId value weight
        Map<String, Double> weightMap = indicatorWeightEntities.stream().collect(Collectors.toMap(IndicatorWeightEntity::getIndicatorId, IndicatorWeightEntity::getWeight));
        if (CollUtil.isEmpty(weightMap)) {
            log.error("指标未设置权重");
            throw new BusinessException("指标未设置权重，请先设置权重");
        }

        log.info("初始化指标计算数据");
        Map<String, List<IndicatorValuesEntity>> groupingBySceneId = indicatorValuesEntities.stream().collect(Collectors.groupingBy(IndicatorValuesEntity::getSceneId));
        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = new ArrayList<>();

        //计算每一个样本的贡献率
        for (Map.Entry<String, List<IndicatorValuesEntity>> entry : groupingBySceneId.entrySet()) {
            //获取叶子指标计算参数
            Map<String, IndicatorValueInfo> indicatorValueInfoMap = initializeIndicatorValueInfoMap(entry.getValue(), indicatorEntities, indicatorStandardMap, badStandardMap);
            ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(entry.getKey());
            if (ObjectUtil.isNull(algorithmEntity)) {
                continue;
            }
            double max = 0d;

            //计算叶子基于需求的体系贡献率
            for (IndicatorValuesEntity indicatorValuesEntity : entry.getValue()) {
                if (!leafNodeIdList.contains(indicatorValuesEntity.getIndicatorId()) || !indicatorValueInfoMap.containsKey(indicatorValuesEntity.getIndicatorId())) {
                    continue;
                }
                if (StringUtils.isEmpty(indicatorValuesEntity.getNormalizedValue()) || "无".equals(indicatorValuesEntity.getNormalizedValue())) {
                    throw new BusinessException("指标计算结果不能为空");
                }
                IndicatorValueInfo indicatorValueInfo = indicatorValueInfoMap.get(indicatorValuesEntity.getIndicatorId());
                Double idealValue = indicatorValueInfo.getIdealValue();
                Double minValue = indicatorValueInfo.getMinValue();
                int indicatorType = indicatorValueInfo.getIndicatorType();
                double matchRate = CalculationUtil.getMatchRate(idealValue,
                        minValue,
                        Double.parseDouble(indicatorValuesEntity.getNormalizedValue()), indicatorType, max, null);
                ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                calculatedValue.setIndicatorId(indicatorValuesEntity.getIndicatorId());
                calculatedValue.setCalculatedValue(NumberUtil.round(matchRate, 3).toString());
                calculatedValue.setSceneId(entry.getKey());
                calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                calculatedValue.setType(ContributionRateType.SATISFACTION.getCode());

                calculatedValueEntities.add(calculatedValue);
            }

            // 向上综合
            upwardSynthesis(indicatorEntities, calculatedValueEntities, weightMap, algorithmEntity, entry.getKey(), ContributionRateType.SATISFACTION.getCode());
        }


        Boolean b = contributionRateCalculatedValueService.saveValues(calculatedValueEntities, ContributionRateType.SATISFACTION.getCode());

        return b ? R.success("计算完成! 耗时：" + timer.interval() + "毫秒") : R.fail("计算失败");
    }


    /**
     * 计算贡献率（基于需求的体系贡献率算法）
     *
     * @param indicatorTreeId 指标树
     * @return 提示信息
     */
    @SchemaOperationTimeAnnotation
    @GetMapping("/calcContributionRateByWeighted")
    @ApiOperation("计算贡献率（加权综合体系贡献率算法）")
    public R<String> calcContributionRateByWeighted(@RequestParam String indicatorTreeId) {
        TimeInterval timer = DateUtil.timer();

        log.info("根据指标树ID-{}，获取指标信息", indicatorTreeId);
        List<IndicatorEntity> indicatorEntities = indicatorService.findByTreeId(indicatorTreeId);
        List<String> indicatorIdList = indicatorEntities.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        //获取叶子指标
        List<IndicatorEntity> leafNode = getLeafNode(indicatorEntities);
        List<String> leafNodeIdList = leafNode.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        //获取方案ID
        String sid = indicatorEntities.get(0).getSid();

        log.info("获取贡献率结果权重设置信息");
        ContributionWeightVO contributionWeightVO = contributionWeightService.findWeight(sid, indicatorTreeId);
        if (ObjectUtil.isNull(contributionWeightVO.getLiftDegreeWeight()) || ObjectUtil.isNull(contributionWeightVO.getSatisfactionWeight())) {
            log.error("贡献率结果权重占比未null");
            throw new BusinessException("请先设置贡献率结果权重占比");
        }

        log.info("获取指标权重");
        // 获取权重
        List<IndicatorWeightEntity> indicatorWeightEntities = indicatorWeightService.listByIndicatorIds(indicatorIdList);
        // key indicatorId value weight
        Map<String, Double> weightMap = indicatorWeightEntities.stream().collect(Collectors.toMap(IndicatorWeightEntity::getIndicatorId, IndicatorWeightEntity::getWeight));
        if (CollUtil.isEmpty(weightMap)) {
            log.error("指标未设置权重");
            throw new BusinessException("指标未设置权重，请先设置权重");
        }

        // 获取想定关联数据
        List<String> sceneIdList = sceneService.findIdsBySid(sid).stream().sorted().collect(Collectors.toList());
        List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findBySceneIds(sceneIdList);
        if (contributionRateAlgorithmEntities.isEmpty()) {
            throw new BusinessException("计算失败,无样本");
        }
        // key sceneId
        Map<String, ContributionRateAlgorithmEntity> contributionRateAlgorithmEntityMap = contributionRateAlgorithmEntities.stream().collect(Collectors.toMap(ContributionRateAlgorithmEntity::getSceneId, v -> v));

        log.info("获取基于提升度的体系贡献率 与 基于满足度的体系贡献率 结果");
        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = contributionRateCalculatedValueService.list(new LambdaQueryWrapper<ContributionRateCalculatedValueEntity>()
                .in(ContributionRateCalculatedValueEntity::getIndicatorId, leafNodeIdList));
        //根据样本ID分组
        Map<String, List<ContributionRateCalculatedValueEntity>> groupingBySceneId = calculatedValueEntities.stream().collect(Collectors.groupingBy(ContributionRateCalculatedValueEntity::getSceneId));
        if (CollUtil.isEmpty(groupingBySceneId)) {
            log.error("基于提升度的体系贡献率 与 基于满足度的体系贡献率，计算结果为null");
            throw new BusinessException("请先计算基于提升度的体系贡献率 与 基于满足度的体系贡献率");
        }
        List<ContributionRateCalculatedValueEntity> result = new ArrayList<>();
        for (Map.Entry<String, List<ContributionRateCalculatedValueEntity>> entry : groupingBySceneId.entrySet()) {
            ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmEntityMap.get(entry.getKey());
            //获取当前样本下的贡献率计算结果
            List<ContributionRateCalculatedValueEntity> crValue = entry.getValue();
            //根据贡献率算法类型分组
            Map<Integer, List<ContributionRateCalculatedValueEntity>> groupingByType = crValue.stream().collect(Collectors.groupingBy(ContributionRateCalculatedValueEntity::getType));
            //获取基于提升度的体系贡献率结果
            List<ContributionRateCalculatedValueEntity> liftDegreeCalculatedValue = groupingByType.get(ContributionRateType.ENHANCEMENT.getCode());
            Map<String, String> liftDegreeCalculatedValueMap = Maps.newHashMap();
            if (CollUtil.isNotEmpty(liftDegreeCalculatedValue)) {
//                log.error("基于提升度的体系贡献率结果为null");
//                throw new BusinessException("请先计算基于提升度的体系贡献率");
                //存在基础样本是没有提升度贡献率结果的
                liftDegreeCalculatedValueMap = liftDegreeCalculatedValue.stream().collect(Collectors.toMap(ContributionRateCalculatedValueEntity::getIndicatorId, ContributionRateCalculatedValueEntity::getCalculatedValue));
            }
            //获取基于满足度的体系贡献率结果
            List<ContributionRateCalculatedValueEntity> satisfactionCalculatedValue = groupingByType.get(ContributionRateType.SATISFACTION.getCode());
            if (CollUtil.isEmpty(satisfactionCalculatedValue)) {
                log.error("样本-{}，基于满足度的体系贡献率结果为null", entry.getKey());
//                throw new BusinessException("请先计算基于满足度的体系贡献率");
                continue;
            }
            Map<String, String> satisfactionCalculatedValueMap = satisfactionCalculatedValue.stream().collect(Collectors.toMap(ContributionRateCalculatedValueEntity::getIndicatorId, ContributionRateCalculatedValueEntity::getCalculatedValue));
            //计算叶子指标加权综合体系贡献率算法
            for (IndicatorEntity indicatorEntity : leafNode) {
                double value = getCalculatedValue(indicatorEntity, liftDegreeCalculatedValueMap, satisfactionCalculatedValueMap, contributionWeightVO);

                ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                calculatedValue.setIndicatorId(indicatorEntity.getId());
                calculatedValue.setCalculatedValue(NumberUtil.round(value, 3).toString());
                calculatedValue.setSceneId(entry.getKey());
                calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                calculatedValue.setType(ContributionRateType.WEIGHTED_SYNTHESIS.getCode());

                result.add(calculatedValue);
            }

            // 向上综合
            upwardSynthesis(indicatorEntities, result, weightMap, algorithmEntity, entry.getKey(), ContributionRateType.WEIGHTED_SYNTHESIS.getCode());

        }

        Boolean b = contributionRateCalculatedValueService.saveValues(result, ContributionRateType.WEIGHTED_SYNTHESIS.getCode());
        return b ? R.success("计算完成! 耗时：" + timer.interval() + "毫秒") : R.fail("计算失败");
    }


    /**
     * 任务满足度贡献率计算
     *
     * @param sid  方案ID
     * @param type 贡献率计算类型
     * @return 提示信息
     */
    @GetMapping("/taskSatisfactionContributionRate")
    @ApiOperation("任务满足度贡献率计算")
    public R<String> taskSatisfactionContributionRate(@RequestParam String sid, @RequestParam Integer type) {
        TimeInterval timer = DateUtil.timer();
        List<ContributionRateCalculatedValueEntity> result = new ArrayList<>();

        log.info("获取贡献率结果权重设置信息");
        ContributionWeightVO contributionWeightVO = contributionWeightService.findWeight(sid, null);
        if (ObjectUtil.isNull(contributionWeightVO.getMissionCompletionRate()) || ObjectUtil.isNull(contributionWeightVO.getOperationalEffectivenessWeight())) {
            log.error("贡献率结果权重占比为null");
            throw new BusinessException("请先设置贡献率结果权重占比");
        }
        //根据方案ID获取指标树信息
        List<IndicatorTreeEntity> indicatorTreeEntityList = indicatorTreeService.list(new LambdaQueryWrapper<IndicatorTreeEntity>()
                .eq(IndicatorTreeEntity::getSid, sid));
        if (CollUtil.isEmpty(indicatorTreeEntityList)) {
            log.error("根据方案ID-{},未查询到指标树信息", sid);
            throw new BusinessException("未查询方案相关指标树信息");
        }
        Map<String, IndicatorTreeEntity> indicatorTreeMap = indicatorTreeEntityList.stream().collect(Collectors.toMap(IndicatorTreeEntity::getId, Function.identity()));

        //根据方案ID获取根指标
        log.info("根据方案ID-{}获取根指标", sid);
        List<IndicatorEntity> rootIndicatorEntityList = indicatorService.list(new LambdaQueryWrapper<IndicatorEntity>()
                .eq(IndicatorEntity::getSid, sid)
                .eq(IndicatorEntity::getPid, StringPool.ZERO));
        if (CollUtil.isEmpty(rootIndicatorEntityList)) {
            log.error("根据方案ID-{}没有查询到根指标信息", sid);
            throw new BusinessException("根据方案ID-" + sid + "没有查询到根指标信息");
        }
        //获取指标权重map
        Map<String, Double> weightMap = Maps.newHashMap();
        for (IndicatorEntity indicatorEntity : rootIndicatorEntityList) {
            //获取对应的指标树信息
            IndicatorTreeEntity indicatorTreeEntity = indicatorTreeMap.get(indicatorEntity.getTreeId());
            if (IndicatorTreeType.ABILITY_EVALUATE.id == indicatorTreeEntity.getIndicatorTreeType()) {
                weightMap.put(indicatorEntity.getId(), contributionWeightVO.getMissionCompletionRate());
            } else if (IndicatorTreeType.EFFICACY_EVALUATE.id == indicatorTreeEntity.getIndicatorTreeType()) {
                weightMap.put(indicatorEntity.getId(), contributionWeightVO.getOperationalEffectivenessWeight());
            } else {
                log.warn("存在不合理的指标树：{}，指标树类型为：{}", indicatorTreeEntity.getName(), indicatorTreeEntity.getIndicatorTreeType());
            }
        }

        //获取根指标贡献率结果
        String typeMsg = ContributionRateType.getType(type).getMsg();
        log.info("查询根指标贡献率结果，贡献率类型：{}", typeMsg);
        //获取指标ID
        List<String> indicatorEntityIdList = rootIndicatorEntityList.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = contributionRateCalculatedValueService.list(new LambdaQueryWrapper<ContributionRateCalculatedValueEntity>()
                .in(ContributionRateCalculatedValueEntity::getIndicatorId, indicatorEntityIdList)
                .eq(ContributionRateCalculatedValueEntity::getType, type));
        if (CollUtil.isEmpty(calculatedValueEntities)) {
            log.error("未查询到相关指标{}贡献率结果", typeMsg);
            throw new BusinessException("请先计算" + typeMsg);
        }

        //根据样本分组
        Map<String, List<ContributionRateCalculatedValueEntity>> cRResultBySceneIdMap = calculatedValueEntities.stream().collect(Collectors.groupingBy(ContributionRateCalculatedValueEntity::getSceneId));
        for (Map.Entry<String, List<ContributionRateCalculatedValueEntity>> entry : cRResultBySceneIdMap.entrySet()) {
            //获取该样本的贡献率结果
            List<ContributionRateCalculatedValueEntity> calculatedValueEntitiesByScene = entry.getValue();
            //计算加权贡献率结果值
            double value = 0;
            for (ContributionRateCalculatedValueEntity contributionRateCalculatedValueEntity : calculatedValueEntitiesByScene) {
                //获取指标结果权重
                Double weight = weightMap.get(contributionRateCalculatedValueEntity.getIndicatorId());
                if (ObjectUtil.isNull(weight)) {
                    weight = 0.0;
                    log.warn("指标-{}贡献率结果权限为null", contributionRateCalculatedValueEntity.getIndicatorId());
                }
                value += roundValue(Double.parseDouble(contributionRateCalculatedValueEntity.getCalculatedValue()), weight);
            }
            ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
            calculatedValue.setIndicatorId(sid);
            calculatedValue.setCalculatedValue(NumberUtil.round(value, 3).toString());
            calculatedValue.setSceneId(entry.getKey());
            calculatedValue.setCompeteSceneId(calculatedValueEntitiesByScene.get(0).getCompeteSceneId());
            calculatedValue.setMissionTaskId(calculatedValueEntitiesByScene.get(0).getMissionTaskId());
            calculatedValue.setSceneScenarioId(calculatedValueEntitiesByScene.get(0).getSceneScenarioId());
            calculatedValue.setType(calculatedValueEntitiesByScene.get(0).getType());

            result.add(calculatedValue);
        }


        Boolean b = contributionRateCalculatedValueService.saveValues(result, type);
        return b ? R.success("计算完成! 耗时：" + timer.interval() + "毫秒") : R.fail("计算失败");
    }

    /**
     * 查询任务满足度贡献率结果
     */
    @PostMapping("/findSatisfactionContributionRate")
    @ApiOperation("查询任务满足度贡献率结果")
    public R<SatisfactionContributionRateVO> findSatisfactionContributionRate(@RequestBody FindSatisfactionContributionRateDTO rateDTO) {
        if (StrUtil.isBlank(rateDTO.getSid())) {
            log.error("方案ID不能为空");
            throw new BusinessException("请先打开方案");
        }

        //返回结果对象
        SatisfactionContributionRateVO satisfactionContributionRateVO = new SatisfactionContributionRateVO();
        BaseTableResponse<Map<String, Object>> tableResponse = new BaseTableResponse<>();
        satisfactionContributionRateVO.setTableResponse(tableResponse);

        List<String> missionTaskIdList = rateDTO.getMissionTaskIdList();
        if (CollectionUtil.isEmpty(missionTaskIdList)){
            List<SceneScenarioEntity> sceneScenarioEntityList = sceneScenarioService.list(new LambdaQueryWrapper<SceneScenarioEntity>().in(SceneScenarioEntity::getId, rateDTO.getSceneScenarioId()));
            missionTaskIdList = sceneScenarioEntityList.stream().filter(t->t.getTaskId()!=null).map(t -> t.getTaskId()).collect(Collectors.toList());

        }
        if (CollUtil.isEmpty(missionTaskIdList)) {
            log.warn("未指定具体需要查询的使命任务");
            return R.data(satisfactionContributionRateVO);
        }
        List<String> sceneIdList = rateDTO.getSceneIdList();
        if (CollUtil.isEmpty(sceneIdList)) {
            log.warn("未指定具体需求查询的样本");
            return R.data(satisfactionContributionRateVO);
        }

        log.info("查询方案ID-{}，查询任务满足度贡献率结果", rateDTO.getSid());
        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = contributionRateCalculatedValueService.list(new LambdaQueryWrapper<ContributionRateCalculatedValueEntity>()
                .eq(ContributionRateCalculatedValueEntity::getIndicatorId, rateDTO.getSid())
                .eq(ContributionRateCalculatedValueEntity::getType, rateDTO.getType())
                .in(ContributionRateCalculatedValueEntity::getSceneId, sceneIdList)
                .in(ContributionRateCalculatedValueEntity::getMissionTaskId, missionTaskIdList));

        if (CollUtil.isEmpty(calculatedValueEntities)) {
            log.warn("未查询到贡献率结果");
            return R.data(satisfactionContributionRateVO);
        }

        log.info("构建表头");
        List<BaseTableHeaderBody> tableHeader = buildTableHeader(calculatedValueEntities);
        tableResponse.setTableHeader(tableHeader);

        log.info("构建表数据");
        Map<String, Object> rowData = calculatedValueEntities.stream().collect(Collectors.toMap(ContributionRateCalculatedValueEntity::getSceneId, ContributionRateCalculatedValueEntity::getCalculatedValue, (a, b) -> a));
        tableResponse.setTableData(CollUtil.toList(rowData));

        log.info("返回前端表格数据");
        return R.data(satisfactionContributionRateVO);
    }


    /**
     * 查询贡献率结论
     *
     * @param sid             方案ID
     * @param indicatorTreeId 指标树ID
     */
    @PostMapping("/findContributionRateConclusion")
    @ApiOperation("查询贡献率结论")
    public R<String> findContributionRateConclusion(@RequestParam String sid, @RequestParam(required = false) String indicatorTreeId,
                                                    @RequestParam(defaultValue = "1") Integer type, @RequestParam String modeName) {
        StrBuilder conclusion = new StrBuilder();
        //获取贡献率结果
        String rootIndicatorId = getRootIndicatorId(sid, indicatorTreeId);

        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = contributionRateCalculatedValueService.list(new LambdaQueryWrapper<ContributionRateCalculatedValueEntity>()
                .eq(ContributionRateCalculatedValueEntity::getIndicatorId, rootIndicatorId)
                .eq(ContributionRateCalculatedValueEntity::getType, type));
        if (CollUtil.isEmpty(calculatedValueEntities)) {
            return R.data("请先计算贡献率结果");
        }

        //获取贡献率结果map
        Map<String, String> calculatedValueMap = calculatedValueEntities.stream().collect(Collectors.toMap(ContributionRateCalculatedValueEntity::getSceneId, ContributionRateCalculatedValueEntity::getCalculatedValue, (a, b) -> a));

        //获取样本ID
        List<String> sceneIdList = calculatedValueEntities.stream().map(ContributionRateCalculatedValueEntity::getSceneId).collect(Collectors.toList());
        //查询样本信息
        List<SceneEntity> sceneEntities = sceneService.listByIds(sceneIdList);
        //查询指标计算结果信息
        Map<String, String> valueMap = indicatorValueService.list(new LambdaQueryWrapper<IndicatorValuesEntity>()
                        .eq(IndicatorValuesEntity::getIndicatorId, rootIndicatorId)
                        .in(IndicatorValuesEntity::getSceneId, sceneIdList))
                .stream().collect(Collectors.toMap(IndicatorValuesEntity::getSceneId, IndicatorValuesEntity::getNormalizedValue, (a, b) -> a));


        //构建结论
        if (CollUtil.isNotEmpty(valueMap)) {
            conclusion.append(sceneEntities.size()).append("个仿真样本的").append(modeName).append("评估结果分别为：");
            for (int i = 0; i < sceneEntities.size(); i++) {
                SceneEntity sceneEntity = sceneEntities.get(i);
                String value = valueMap.get(sceneEntity.getId());
                conclusion.append(getSceneName(sceneEntity.getName())).append("(").append(value).append(")");
                if (i == sceneEntities.size() - 1) {
                    conclusion.append("。");
                } else {
                    conclusion.append("、");
                }
            }
        }
        //贡献率说明
        conclusion.append("各仿真样本的").append(modeName).append("贡献率分别为：");
        for (int i = 0; i < sceneEntities.size(); i++) {
            SceneEntity sceneEntity = sceneEntities.get(i);
            String calculatedValue = calculatedValueMap.get(sceneEntity.getId());
            conclusion.append(getSceneName(sceneEntity.getName())).append("(").append(NumberUtil.round(Double.parseDouble(calculatedValue) * 100, 3)).append("%)");
            if (i == sceneEntities.size() - 1) {
                conclusion.append("。");
            } else {
                conclusion.append("、");
            }
        }

        Map.Entry<String, Double> maxValue = getCalculatedMaxValue(calculatedValueEntities);

        //获取子方案名称
        SceneScenarioEntity scenarioEntity = sceneScenarioService.getById(maxValue.getKey());
        if (ObjectUtil.isNull(scenarioEntity)) {
            throw new BusinessException("未查询到子方案信息，联系管理员排查");
        }
        conclusion.append("其中，").append(scenarioEntity.getScenarioName()).append("样本整体表现最优，").append(modeName).append("贡献率均值为").append(NumberUtil.round(maxValue.getValue() * 100, 3)).append("%。");

        return R.data(conclusion.toString());
    }


    /**
     * 查询贡献率结论
     *
     * @param sid             方案ID
     * @param indicatorTreeId 指标树ID
     */
    @PostMapping("/findContributionRateConclusionV2")
    @ApiOperation("查询贡献率结论（需求版本V0.18）")
    public R<String> findContributionRateConclusionV2(@RequestParam String sid,
                                                      @RequestParam String indicatorTreeId,
                                                      @RequestParam(defaultValue = "1") Integer type) {
        StrBuilder conclusion = new StrBuilder();
        //获取指标信息map
        Map<String, String> indicatorEntitieMap = indicatorService.list(new LambdaQueryWrapper<IndicatorEntity>().eq(IndicatorEntity::getTreeId, indicatorTreeId))
                .stream().collect(Collectors.toMap(IndicatorEntity::getId, IndicatorEntity::getName));
        if (CollUtil.isEmpty(indicatorEntitieMap)) {
            return R.data("未查询到指标信息");
        }

        //获取贡献率结果
        List<ContributionRateCalculatedValueEntity> calculatedValueEntities = contributionRateCalculatedValueService.list(new LambdaQueryWrapper<ContributionRateCalculatedValueEntity>()
                .in(ContributionRateCalculatedValueEntity::getIndicatorId, indicatorEntitieMap.keySet())
                .eq(ContributionRateCalculatedValueEntity::getType, type));
        if (CollUtil.isEmpty(calculatedValueEntities)) {
            return R.data("请先计算贡献率结果");
        }

        //根据任务分组贡献率结果
        Map<String, List<ContributionRateCalculatedValueEntity>> groupingByMissionTask = calculatedValueEntities.stream().collect(Collectors.groupingBy(ContributionRateCalculatedValueEntity::getMissionTaskId));
        //查询任务信息map
        if (CollUtil.isEmpty(groupingByMissionTask.keySet())) {
            return R.data("未查询到样本关联任务信息");
        }
        Map<String, String> missionTaskIdNameMap = missionTaskService.listByIds(groupingByMissionTask.keySet()).stream().collect(Collectors.toMap(MissionTaskEntity::getId, MissionTaskEntity::getName));
        if (CollUtil.isEmpty(missionTaskIdNameMap)) {
            return R.data("未查询到样本关联任务信息");
        }
        //查询方案信息map
        List<String> sceneScenarioIdList = calculatedValueEntities.stream().map(ContributionRateCalculatedValueEntity::getSceneScenarioId).collect(Collectors.toList());
        if (CollUtil.isEmpty(sceneScenarioIdList)) {
            return R.data("未查询到样本关联方案信息");
        }
        Map<String, String> sceneScenarioMap = sceneScenarioService.listByIds(sceneScenarioIdList).stream().collect(Collectors.toMap(SceneScenarioEntity::getId, SceneScenarioEntity::getScenarioName));
        if (CollUtil.isEmpty(sceneScenarioMap)) {
            return R.data("未查询到样本关联方案信息");
        }
        //查询样本信息map
        List<String> sceneIdList = calculatedValueEntities.stream().map(ContributionRateCalculatedValueEntity::getSceneId).collect(Collectors.toList());
        Map<String, String> sceneEntitieMap = sceneService.listByIds(sceneIdList).stream().collect(Collectors.toMap(SceneEntity::getId, SceneEntity::getName));
        if (CollUtil.isEmpty(sceneEntitieMap)) {
            return R.data("未查询到样本信息");
        }

        int num = 1;
        for (Map.Entry<String, List<ContributionRateCalculatedValueEntity>> entry : groupingByMissionTask.entrySet()) {
            //结论模板如下：
            //1、在【任务名称】中，【方案名称】的【样本名称】综合贡献率最大，其中【指标名称1】提升xxx、【指标名称2】提升xxx（找到提升最大的3个）
            //2、...
            String missionTaskId = entry.getKey();
            List<ContributionRateCalculatedValueEntity> taskList = entry.getValue();

            // 计算每个样本(sceneId)的综合贡献率（累加和）
            Map<String, BigDecimal> sceneComprehensiveMap = new LinkedHashMap<>();
            for (ContributionRateCalculatedValueEntity entity : taskList) {
                String sceneId = entity.getSceneId();
                if (sceneId == null) continue;
                BigDecimal val = safeParseBigDecimal(entity.getCalculatedValue());
                sceneComprehensiveMap.merge(sceneId, val, BigDecimal::add);
            }
            if (sceneComprehensiveMap.isEmpty()) continue;

            // 找出综合贡献率最大的样本
            Map.Entry<String, BigDecimal> maxSceneEntry = sceneComprehensiveMap.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            if (maxSceneEntry == null) continue;

            String bestSceneId = maxSceneEntry.getKey();
            // 获取该样本关联的方案sceneScenarioId
            String bestSceneScenarioId = taskList.stream()
                    .filter(e -> e.getSceneId().equals(bestSceneId))
                    .map(ContributionRateCalculatedValueEntity::getSceneScenarioId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("");

            // 筛选该样本下的所有指标，按贡献率降序排序，取前3
            List<ContributionRateCalculatedValueEntity> topIndicators = taskList.stream()
                    .filter(e -> bestSceneId.equals(e.getSceneId()))
                    .sorted(Comparator.comparing((ContributionRateCalculatedValueEntity item) -> safeParseBigDecimal(item.getCalculatedValue())).reversed())
                    .limit(3)
                    .collect(Collectors.toList());


            if (CollUtil.isEmpty(topIndicators)) continue;

            // 组装结论字符串
            String taskName = missionTaskIdNameMap.getOrDefault(missionTaskId, "未知任务");
            String sceneScenarioName = sceneScenarioMap.getOrDefault(bestSceneScenarioId, "未知方案");
            String sceneName = sceneEntitieMap.getOrDefault(bestSceneId, "未知样本");

            conclusion.append(num).append("、在").append(taskName).append("中，").append(sceneScenarioName).append("的").append(sceneName)
                    .append("综合贡献率最大，其中");

            for (int i = 0; i < topIndicators.size(); i++) {
                ContributionRateCalculatedValueEntity indEntity = topIndicators.get(i);
                String indName = indicatorEntitieMap.getOrDefault(indEntity.getIndicatorId(), "未知指标");
                BigDecimal val = safeParseBigDecimal(indEntity.getCalculatedValue());
                // 乘以100转百分比，保留2位小数
                BigDecimal percent = val.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                String direction = val.compareTo(BigDecimal.ZERO) >= 0 ? "提升" : "下降";

                if (i > 0) conclusion.append("、");
                conclusion.append("").append(indName).append("").append(direction).append(percent).append("%");
            }
            conclusion.append("。\n");
            num++;
        }

        if (conclusion.isEmpty()) {
            return R.data("暂无符合条件的贡献率结论");
        }
        return R.data(conclusion.toString().trim());
    }

    /**
     * 安全解析字符串为 BigDecimal，避免空指针或格式异常
     */
    private BigDecimal safeParseBigDecimal(String value) {
        if (StringUtils.isBlank(value)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取最大子方案 和 其样本下均值
     */
    private Map.Entry<String, Double> getCalculatedMaxValue(List<ContributionRateCalculatedValueEntity> calculatedValueEntities) {
        //计算各子方案下样本贡献率均值
        Map<String, Double> avgMap = calculatedValueEntities.stream()
                .filter(e -> e.getSceneScenarioId() != null)
                .collect(Collectors.groupingBy(ContributionRateCalculatedValueEntity::getSceneScenarioId,
                        Collectors.collectingAndThen(
                                Collectors.mapping(
                                        e -> {
                                            String val = e.getCalculatedValue();
                                            if (val == null || val.trim().isEmpty()) return Double.NaN;
                                            try {
                                                return Double.parseDouble(val.trim());
                                            } catch (NumberFormatException ex) {
                                                return Double.NaN; // 跳过非法格式
                                            }
                                        },
                                        Collectors.toList()
                                ),
                                list -> list.stream()
                                        .filter(d -> !Double.isNaN(d))
                                        .mapToDouble(Double::doubleValue)
                                        .average()
                                        .orElse(0.0) // 若某组无有效数字，均值按 0 处理（可按需改为 null 或跳过）
                        )
                ));
        //找到最大均值
        Optional<Map.Entry<String, Double>> max = avgMap.entrySet().stream().max(Map.Entry.comparingByValue());
        if (max.isEmpty()) {
            throw new BusinessException("未获取到最大子方案信息");
        }
        return max.get();
    }

    /**
     * 获取样本名称
     */
    private String getSceneName(String name) {
        if (!name.contains("/") || !name.contains("-")) {
            return name;
        }
        String[] split1 = name.split("/");
        String targetName = split1.length <= 1 ? split1[0] : split1[1];
        targetName = targetName.split("-")[1];
        return "仿真样本-" + targetName;
    }

    /**
     * 获取根指标ID
     */
    private String getRootIndicatorId(String sid, String indicatorTreeId) {
        if (StrUtil.isBlank(indicatorTreeId)) {
            return sid;
        } else {
            //查询根指标ID
            IndicatorEntity indicatorEntity = indicatorService.getOne(new LambdaQueryWrapper<IndicatorEntity>()
                    .eq(IndicatorEntity::getTreeId, indicatorTreeId)
                    .eq(IndicatorEntity::getPid, StringPool.ZERO));
            if (ObjectUtil.isNull(indicatorEntity)) {
                throw new BusinessException("根据指标树ID，未查询到根指标信息");
            }
            return indicatorEntity.getId();
        }
    }


    /**
     * 构建表头
     */
    private List<BaseTableHeaderBody> buildTableHeader(List<ContributionRateCalculatedValueEntity> calculatedValueEntities) {
        List<BaseTableHeaderBody> tableHeader = Lists.newArrayList();
        //构建任务ID，样本ID map
        Map<String, List<ContributionRateCalculatedValueEntity>> groupingByMissionTaskId = calculatedValueEntities.stream().collect(Collectors.groupingBy(ContributionRateCalculatedValueEntity::getMissionTaskId));

        //查询使命任务信息
        List<String> missionTaskIdList = calculatedValueEntities.stream().map(ContributionRateCalculatedValueEntity::getMissionTaskId).collect(Collectors.toList());
        List<MissionTaskEntity> missionTaskEntities = missionTaskService.listByIds(missionTaskIdList);
        //查询样本信息
        List<String> sceneIdList = calculatedValueEntities.stream().map(ContributionRateCalculatedValueEntity::getSceneId).collect(Collectors.toList());
        Map<String, SceneEntity> sceneEntitiesMap = sceneService.listByIds(sceneIdList)
                .stream().collect(Collectors.toMap(SceneEntity::getId, Function.identity()));


        for (MissionTaskEntity missionTaskEntity : missionTaskEntities) {
            BaseTableHeaderBody baseTableHeaderBody = new BaseTableHeaderBody();
            baseTableHeaderBody.setLabel(missionTaskEntity.getName());
            baseTableHeaderBody.setProp(missionTaskEntity.getId());
            baseTableHeaderBody.setIsMerge(IntegerPool.ZERO);

            //构建二级表头
            //根据任务ID获取样本ID
            List<ContributionRateCalculatedValueEntity> calculatedValueEntitiesByTaskId = groupingByMissionTaskId.get(missionTaskEntity.getId());
            if (CollUtil.isEmpty(calculatedValueEntitiesByTaskId)) {
                continue;
            }
            List<String> missionTaskSceneIdList = calculatedValueEntitiesByTaskId.stream().map(ContributionRateCalculatedValueEntity::getSceneId).collect(Collectors.toList());
            for (String sceneId : missionTaskSceneIdList) {
                SceneEntity sceneEntity = sceneEntitiesMap.get(sceneId);
                BaseTableHeaderBody towTableHeaderBody = new BaseTableHeaderBody();
                towTableHeaderBody.setLabel(getSceneName(sceneEntity.getName()));
                towTableHeaderBody.setProp(sceneEntity.getId());
                towTableHeaderBody.setIsMerge(IntegerPool.ONE);
                baseTableHeaderBody.getPropsChildren().add(towTableHeaderBody);
            }
            tableHeader.add(baseTableHeaderBody);
        }
        return tableHeader;
    }


    /**
     * 获取加权综合体系贡献率结果
     */
    private double getCalculatedValue(IndicatorEntity indicatorEntity, Map<String, String> liftDegreeCalculatedValueMap, Map<String, String> satisfactionCalculatedValueMap, ContributionWeightVO contributionWeightVO) {
        String ldValue = liftDegreeCalculatedValueMap.get(indicatorEntity.getId());
        String scValue = satisfactionCalculatedValueMap.get(indicatorEntity.getId());

        if (StrUtil.isBlank(ldValue)) {
//            log.error("指标-{}，基于提升度的体系贡献率结果为null", indicatorEntity.getName());
//            throw new BusinessException("指标-" + indicatorEntity.getName() + "，没有计算基于提升度的体系贡献率结果");
            //存在基础样本是没有提升度贡献率结果的
            ldValue = StringPool.ZERO;
        }
        if (StrUtil.isBlank(scValue)) {
            log.error("指标-{}，基于满足度的体系贡献率结果为null", indicatorEntity.getName());
            throw new BusinessException("指标-" + indicatorEntity.getName() + "，没有基于满足度的体系贡献率结果");
        }
        return roundValue(Double.parseDouble(ldValue), contributionWeightVO.getLiftDegreeWeight()) + roundValue(Double.parseDouble(scValue), contributionWeightVO.getSatisfactionWeight());
    }

    /**
     * 向综合获取上层指标值
     */
    private void upwardSynthesis(List<IndicatorEntity> indicatorEntities, List<ContributionRateCalculatedValueEntity> calculatedValueEntities, Map<String, Double> weightMap, ContributionRateAlgorithmEntity algorithmEntity, String sceneId, Integer type) {
        //根据指标map
        Map<String, IndicatorEntity> idToEntityMap = indicatorEntities.stream().collect(Collectors.toMap(IndicatorEntity::getId, Function.identity()));
        //根据pid分组
        Map<String, List<IndicatorEntity>> groupingByPid = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        //构建节点ID -> 未计算节点数量 计数
        Map<String, Integer> unCalcChildCount = Maps.newHashMap();
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            String id = indicatorEntity.getId();
            unCalcChildCount.put(id, groupingByPid.getOrDefault(id, Lists.newArrayList()).size());
        }
        //初始化指标值映射
        Map<String, Double> valueMap = calculatedValueEntities.stream().filter(f -> sceneId.equals(f.getSceneId())).collect(Collectors
                .toMap(ContributionRateCalculatedValueEntity::getIndicatorId, item -> Double.parseDouble(item.getCalculatedValue()), (a, b) -> a));

        //初始化队列
        Queue<String> queue = new LinkedList<>();
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            if (unCalcChildCount.get(indicatorEntity.getId()) == 0) {
                queue.offer(indicatorEntity.getId());
            }
        }
        while (!queue.isEmpty()) {
            String childId = queue.poll();
            IndicatorEntity indicatorEntity = idToEntityMap.get(childId);
            //获取pid
            String pid = indicatorEntity.getPid();
            //跟节点无父，终止
            if (StrUtil.isBlank(pid) || StringPool.ZERO.equals(pid)) {
                continue;
            }
            int remain = unCalcChildCount.get(pid) - 1;
            unCalcChildCount.put(pid, remain);
            if (remain == 0) {
                List<IndicatorEntity> children = groupingByPid.getOrDefault(pid, Lists.newArrayList());
                double parentValue = 0.0;
                for (IndicatorEntity child : children) {
                    Double value = valueMap.get(child.getId());
                    Double weight = weightMap.getOrDefault(child.getId(), 0.0);
                    parentValue += roundValue(value, weight);
                }
                valueMap.put(pid, parentValue);
                queue.offer(pid);
            }
            for (IndicatorEntity entity : indicatorEntities) {
                String id = entity.getId();
                if (valueMap.containsKey(id) && calculatedValueEntities.stream().noneMatch(e -> e.getIndicatorId().equals(id) && e.getSceneId().equals(sceneId))) {
                    ContributionRateCalculatedValueEntity calculatedValue = new ContributionRateCalculatedValueEntity();
                    calculatedValue.setIndicatorId(id);
                    calculatedValue.setCalculatedValue(NumberUtil.round(valueMap.get(id), 3).toString());
                    calculatedValue.setSceneId(sceneId);
                    calculatedValue.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
                    calculatedValue.setMissionTaskId(algorithmEntity.getMissionTaskId());
                    calculatedValue.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
                    calculatedValue.setType(type);

                    calculatedValueEntities.add(calculatedValue);
                }
            }
        }

    }

    /**
     * 四舍五入保留三为小数
     */
    private double roundValue(Double value, Double weight) {
        return NumberUtil.round(NumberUtil.mul(value, weight), 3).doubleValue();
    }

    /**
     * 获取指标体系中的叶子节点集合
     */
    private List<IndicatorEntity> getLeafNode(List<IndicatorEntity> indicatorEntityList) {
        List<String> pids = indicatorEntityList.stream()
                .map(IndicatorEntity::getPid)
                .collect(Collectors.toList());

        return indicatorEntityList.stream()
                .filter(f -> !pids.contains(f.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 初始化指标计算数据
     */
    private Map<String, IndicatorValueInfo> initializeIndicatorValueInfoMap(List<IndicatorValuesEntity> indicatorValuesEntities, List<IndicatorEntity> indicatorEntities, Map<String, String> indicatorStandardMap, Map<String, String> badStandardMap) {
        //获取指标ID、指标值map
        Map<String, String> indicatorValuesMap = indicatorValuesEntities.stream().collect(Collectors.toMap(IndicatorValuesEntity::getIndicatorId, IndicatorValuesEntity::getNormalizedValue, (a, b) -> a));
        Map<String, IndicatorValueInfo> indicatorValueInfoMap = Maps.newHashMap();
        Map<String, List<IndicatorEntity>> pidIndicatorMap = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));

        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            if (pidIndicatorMap.containsKey(indicatorEntity.getId())) {
                continue;
            }
            Integer indicatorType = indicatorEntity.getIndicatorType();
            if (ObjectUtil.isNull(indicatorType)) {
                log.error("指标-{}未设置指标类型", indicatorEntity.getName());
                throw new BusinessException("指标-" + indicatorEntity.getName() + "未设置指标类型");
            }
            IndicatorValueInfo indicatorValueInfo = new IndicatorValueInfo();
            indicatorValueInfo.setIndicatorType(indicatorType);
            String indicatorValue = indicatorValuesMap.get(indicatorEntity.getId());
            if (StrUtil.isBlank(indicatorValue)) {
                log.error("未查询到指标-{}的计算结果", indicatorEntity.getName());
                throw new BusinessException("未查询到指标-" + indicatorEntity.getName() + "的计算结果");
            }
            double value = Double.parseDouble(indicatorValue);
            if (indicatorType == 1) { // 成本
                indicatorValueInfo.setIdealValue(Double.parseDouble(indicatorStandardMap.get(indicatorEntity.getId())));
                indicatorValueInfo.setMinValue(Double.parseDouble(badStandardMap.get(indicatorEntity.getId())));
            } else if (indicatorType == 2) { // 效益
                indicatorValueInfo.setIdealValue(Double.parseDouble(indicatorStandardMap.get(indicatorEntity.getId())));
                indicatorValueInfo.setMinValue(Double.parseDouble(badStandardMap.get(indicatorEntity.getId())));
            } else {
                indicatorValueInfo.setIdealValue(0.0);
                indicatorValueInfo.setMinValue(0.0);
            }
            indicatorValueInfoMap.put(indicatorEntity.getId(), indicatorValueInfo);
        }
        return indicatorValueInfoMap;
    }

    /**
     * 获取指标id、准则优最差值map
     *
     * @param indicatorEntities 指标集合
     * @return 指标id、准则优最差值map
     */
    private Map<String, String> getIndicatorStandardMap(List<IndicatorEntity> indicatorEntities, String standardLabelsName) {
        List<String> indicatorIdList = indicatorEntities.stream().map(IndicatorEntity::getId).collect(Collectors.toList());

        //获取指标准则信息
        List<IndicatorStandardEntity> indicatorStandardEntitieList = indicatorStandardService.list(new LambdaQueryWrapper<IndicatorStandardEntity>()
                .in(IndicatorStandardEntity::getIndicatorId, indicatorIdList));
        if (CollUtil.isEmpty(indicatorStandardEntitieList)) {
            log.error("未设置指标准则");
            throw new BusinessException("未设置指标准则，请先设置指标准则");
        }
        //获取labelId
        List<String> labelIdList = indicatorStandardEntitieList.stream().map(IndicatorStandardEntity::getLabelId).collect(Collectors.toList());
        List<String> excellentLabelIdList = standardLabelsService.listByIds(labelIdList)
                .stream().filter(f -> standardLabelsName.equals(f.getName())).map(StandardLabelsEntity::getId).collect(Collectors.toList());
        Map<String, List<IndicatorStandardEntity>> groupingByIndicatorId = indicatorStandardEntitieList.stream().filter(f -> excellentLabelIdList.contains(f.getLabelId())).collect(Collectors.groupingBy(IndicatorStandardEntity::getIndicatorId));

        Map<String, String> result = Maps.newHashMap();
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            List<IndicatorStandardEntity> indicatorStandardEntities = groupingByIndicatorId.get(indicatorEntity.getId());
            if (CollUtil.isEmpty(indicatorStandardEntities)) {
                log.error("指标-{}未设置准则，请先设置准则", indicatorEntity.getName());
                throw new BusinessException("指标-" + indicatorEntity.getName() + "未设置准则，请先设置准则");
            }
            //指标类型为空（默认按效益型处理）
            if (ObjectUtil.isNull(indicatorEntity.getIndicatorType())) {
                indicatorEntity.setIndicatorType(ContributionRateType.ENHANCEMENT.getCode());
            }
            //获取准则为优的最差值
            IndicatorStandardEntity indicatorStandardEntity = indicatorStandardEntities.get(0);
            String indicatorStandardValue = ContributionRateType.ENHANCEMENT.getCode().equals(indicatorEntity.getIndicatorType()) ? indicatorStandardEntity.getMin() : indicatorStandardEntity.getMax();
            if (!NumberUtil.isNumber(indicatorStandardValue)) {
                indicatorStandardValue = "0";
            }
            result.put(indicatorEntity.getId(), indicatorStandardValue);
        }
        return result;
    }

    /**
     * 计算叶子节点满足度 及 构建叶子节点父级的条件规则
     *
     * @param indicatorTreeId 指标树主键
     * @return
     */
    // 计算叶子节点满足度 及 构建叶子节点父级的条件规则
    @SchemaOperationTimeAnnotation
    @GetMapping("calc")
    @ApiOperation("计算叶子节点满足度 及 构建叶子节点父级的条件规则")
    public R<List<IndicatorCombineCredibilityVO>> calc(@RequestParam String indicatorTreeId) {

        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }

        // 叶子节点配置
        List<String> indicatorIds = leaves.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        List<IndicatorValueInfoVO> valueInfoVOS = indicatorValueInfoService.listByIndicatorId(indicatorIds);
        Map<String, IndicatorValueInfoVO> valueInfoVOMap = valueInfoVOS.stream()
                .collect(Collectors.toMap(IndicatorValueInfoVO::getIndicatorId, v -> v));
        if (valueInfoVOS.isEmpty()) {
            throw new BusinessException("指标数据未配置");
        }

        // 获取计算结果
        String schemaId = valueInfoVOS.get(0).getSchemaId();
        List<String> sceneIdList = sceneService.findIdsBySid(schemaId);
        List<IndicatorValuesWithNameDTO> schemaIndicatorValuesBoList = indicatorValueService
                .findWithNameBySidIndicatorIdSceneId(schemaId, null, sceneIdList);
        if (ObjectUtils.isEmpty(schemaIndicatorValuesBoList)) {
            throw new BusinessException("指标未计算");
        }

        List<IndicatorCombineCredibility> resultCredibilities = new ArrayList<>();
        // 叶子满足度
        for (IndicatorValuesWithNameDTO bo : schemaIndicatorValuesBoList) {
            IndicatorValueInfoVO vo = valueInfoVOMap.get(bo.getIndicatorId());
            if (ObjectUtils.isEmpty(vo)) {
                continue;
            }
            IndicatorCombineCredibility credibility = new IndicatorCombineCredibility();
            credibility.setIndicatorId(bo.getIndicatorId());
            credibility.setIndicatorName(bo.getName());
            credibility.setSceneId(bo.getSceneId());
            credibility.setSchemaId(bo.getSid());
            credibility.setMatchRate(BigDecimal.valueOf(
                    CalculationUtil.getMatchRate(vo.getIdealValue(),
                            vo.getMinValue(),
                            Double.parseDouble(bo.getNormalizedValue()),
                            vo.getIndicatorValueType()) * vo.getCredibilityRate()).setScale(3, RoundingMode.HALF_UP).doubleValue());
            resultCredibilities.add(credibility);
        }

        // 叶子节点父级条件规则
        Map<String, List<IndicatorEntity>> map = leaves.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        List<String> fatherIds = new ArrayList<>(map.keySet());
        List<IndicatorEntity> fathers = indicatorService.findByBacthId(fatherIds);

        Map<String, List<IndicatorCombineCredibility>> sceneMap = resultCredibilities.stream()
                .collect(Collectors.groupingBy(IndicatorCombineCredibility::getSceneId));
        // 遍历样本
        for (Map.Entry<String, List<IndicatorCombineCredibility>> e : sceneMap.entrySet()) {
            // key indicatorId value 满足度信度
            Map<String, double[]> resultMap = new HashMap<>();
            // 遍历指标
            for (IndicatorCombineCredibility i : e.getValue()) {
                Double matchRate = i.getMatchRate();
                Double credibilityRate = valueInfoVOMap.get(i.getIndicatorId()).getCredibilityRate();
                double[] result = CalculationUtil.getCombineCredibilityRate(matchRate, credibilityRate);
                resultMap.put(i.getIndicatorId(), result);
            }
            // 计算叶子节点父节点的满足度
            for (IndicatorEntity i : fathers) {

                IndicatorCombineCredibility credibility = new IndicatorCombineCredibility();
                List<IndicatorEntity> children = map.get(i.getId());

                // 条件规则
                String prefix = "m(" + i.getName() + "=1|";
                List<List<Integer>> combine = generateCombinations(children.size());
                for (List<Integer> c : combine) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(prefix);
                    double combineMatchRate = 1D;
                    for (int j = 0; j < children.size(); j++) {
                        IndicatorEntity currentIndicator = children.get(j);
                        double[] combineCredibilityRate = resultMap.get(currentIndicator.getId());
                        int index = c.get(j);
                        combineMatchRate *= combineCredibilityRate[index];
                        sb.append(currentIndicator.getName()).append("=").append(index).append("^");
                    }
                    sb.delete(sb.length() - 1, sb.length());
                    // 组装条件规则
                    if (StringUtils.isEmpty(credibility.getCombineCredibilityParam())) {
                        credibility.setCombineCredibilityParam(sb.toString() + ")=" + decimalFormat.format(combineMatchRate));
                    } else {
                        String tmp = credibility.getCombineCredibilityParam() + sb.toString() + ")=" + decimalFormat.format(combineMatchRate);
                        credibility.setCombineCredibilityParam(tmp);
                    }
                    credibility.setCombineCredibilityParam(credibility.getCombineCredibilityParam() + ";");
                }

                credibility.setSceneId(e.getKey());
                credibility.setIndicatorId(i.getId());
                credibility.setIndicatorName(i.getName());
                credibility.setCombineCredibilityNumber(combine.size());
                credibility.setSchemaId(i.getSid());
                resultCredibilities.add(credibility);
            }
        }
        // 保存
        indicatorIds.addAll(fatherIds);
        indicatorCombineCredibilityService.deleteByIndicatorIds(indicatorIds);
        indicatorCombineCredibilityService.saveBatch(resultCredibilities);


        // 组装样本名称
        List<IndicatorCombineCredibilityVO> vos = resultCredibilities.stream().filter(c -> fatherIds.contains(c.getIndicatorId())).map(IndicatorCombineCredibility::toVO).collect(Collectors.toList());
        List<String> sceneIds = new ArrayList<>(sceneMap.keySet());
        List<SceneEntity> sceneEntities = sceneService.findByIdIn(sceneIds);
        Map<String, String> sceneEntityMap = sceneEntities.stream().collect(Collectors.toMap(SceneEntity::getId, SceneEntity::getName));
        vos.forEach(vo -> {
            String fullName = sceneEntityMap.get(vo.getSceneId());
            String[] names = fullName.split("/");
            List<String> newNames = Arrays.stream(names).map(name -> {
                if (name.split("-").length > 1) {
                    return name.split("-")[1];
                } else {
                    return name;
                }
            }).collect(Collectors.toList());
            vo.setSceneName(String.join("/", newNames));
        });
        return R.data(vos);
    }


    /**
     * 校验指标输入相关数值是否合理
     * 1，效益型：
     * 最低值<实际值<理想值
     * 1，成本型：
     * 理想值<实际值<最低值
     *
     * @param vos 校验对象
     * @return
     */
    private String isValid(List<IndicatorValueInfoWithCredibilityRateVO> vos) {
        //指标名称
        String result = null;
        for (IndicatorValueInfoWithCredibilityRateVO vo : vos) {
            if (0 > vo.getCredibilityRate() || 1 < vo.getCredibilityRate()) {
                result = vo.getIndicatorId();
                break;
            }
            double value = Double.parseDouble(vo.getNormalizedValue());
            // 效益型
            if (vo.getIndicatorType() == 1) {
                if (value <= vo.getMinValue() || value >= vo.getIdealValue()) {
                    result = vo.getIndicatorId();
                }
                //成本型
            } else if (vo.getIndicatorType() == 2) {
                if (value >= vo.getMinValue() || value <= vo.getIdealValue()) {
                    result = vo.getIndicatorId();
                }
//                if (result!=null){
//                    throw new BusinessException("指标：" + result + "在成本型中设置不满足(理想值<实际值<最低值)");
//                }
            }
        }
        return result;
    }

    /**
     * 计算贡献率（加权综合）基础想定多样本
     *
     * @param indicatorTreeId 指标树ID
     * @return
     */
    @GetMapping("/calcContributionRateMultiBenchmarkScene")
    @ApiOperation("计算贡献率（加权综合）基础想定多样本")
    public R calcContributionRateMultiBenchmarkScene(@RequestParam String indicatorTreeId) {

        List<ContributionRateCalculatedValueEntity> results = new ArrayList<>();

        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }

        String schemaId = leaves.get(0).getSid();
        List<MissionTaskEntity> taskEntities = missionTaskService.list(
                new LambdaQueryWrapper<MissionTaskEntity>().eq(MissionTaskEntity::getSid, schemaId));

        // 每个任务下都得选出基础想定
        for (MissionTaskEntity m : taskEntities) {

            // 获取基础想定下的所有样本id
            List<String> benchmarkSceneIds = indicatorContributionCalculationService.listBenchmarkSceneIds(schemaId, m.getId());
            if (ObjectUtils.isEmpty(benchmarkSceneIds)) {
                throw new BusinessException("无样本，计算失败");
            }
            // 笛卡尔积定出比较组合
            List<ContributionRateCalculatedValueEntity> valueEntities = indicatorContributionCalculationService
                    .calcMultiBenchmarkSceneContribution(benchmarkSceneIds, indicatorTreeId);
            results.addAll(valueEntities);
        }

        if (contributionRateCalculatedValueService.saveValues(results, ContributionRateType.ENHANCEMENT.getCode())) {
            return R.success();
        } else {
            return R.fail("计算失败");
        }
    }

    /**
     * 计算贡献率（证据推理）基础想定多样本
     *
     * @param indicatorTreeId 指标树id
     * @return
     */
    @GetMapping("/calcContributionRateMultiBenchmarkScene2")
    @ApiOperation("计算贡献率（证据推理）基础想定多样本")
    public R calcContributionRateMultiBenchmarkScene2(@RequestParam String indicatorTreeId) {

        List<ContributionRateCalculatedValueEntity> results = new ArrayList<>();

        // 获取叶子节点
        List<IndicatorEntity> leaves = indicatorService.getLeafIndicator(indicatorTreeId);
        if (ObjectUtils.isEmpty(leaves)) {
            throw new BusinessException("指标树不存在");
        }

        String schemaId = leaves.get(0).getSid();
        List<MissionTaskEntity> taskEntities = missionTaskService.list(
                new LambdaQueryWrapper<MissionTaskEntity>().eq(MissionTaskEntity::getSid, schemaId));

        // 每个任务下都得选出基础想定
        for (MissionTaskEntity m : taskEntities) {

            // 获取基础想定下的所有样本id
            List<String> benchmarkSceneIds = indicatorContributionCalculationService.listBenchmarkSceneIds(schemaId, m.getId());
            if (ObjectUtils.isEmpty(benchmarkSceneIds)) {
                throw new BusinessException("无样本，计算失败");
            }
            // 笛卡尔积定出比较组合
            List<ContributionRateCalculatedValueEntity> valueEntities = indicatorContributionCalculationService
                    .calc(benchmarkSceneIds, indicatorTreeId);
            results.addAll(valueEntities);
        }
        //判断方案是否在存在
        Optional<SchemaEntity> schemaEntityOptional = schemaService.findById(schemaId);
        if (schemaEntityOptional.isPresent()) {
            updateSchema(indicatorTreeId, schemaEntityOptional, "EVIDENCE_REASONING");
        }

        if (contributionRateCalculatedValueService.saveValues(results, ContributionRateType.ENHANCEMENT.getCode())) {
            return R.success();
        } else {
            return R.fail("计算失败");
        }
    }

    /**
     * 修改方案信息
     *
     * @param indicatorTreeId      指标树
     * @param schemaEntityOptional 方案
     * @param algName              算法名称
     */
    public void updateSchema(String indicatorTreeId, Optional<SchemaEntity> schemaEntityOptional, String algName) {
        Map<String, String> evaluationMethod = Maps.newHashMap();
        SchemaEntity schemaEntity = schemaEntityOptional.get();
        IndicatorTreeEntity byId = indicatorTreeService.getById(indicatorTreeId);
        Integer indicatorTreeType = byId.getIndicatorTreeType();
        String f1 = schemaEntity.getF1();
        if (IndicatorTypeEnum.ABILITY_INDICATOR.getId() == indicatorTreeType) {
            evaluationMethod.put("abilityEvaluationMethod", algName);
            if (StringUtils.isNotEmpty(f1)) {
                Map<String, String> maps = JSON.parseObject(schemaEntity.getF1(), new TypeReference<Map<String, String>>() {
                });
                evaluationMethod.put("efficiencyEvaluationMethod", maps.get("efficiencyEvaluationMethod"));
            }
        } else {
            evaluationMethod.put("efficiencyEvaluationMethod", algName);
            if (StringUtils.isNotEmpty(f1)) {
                Map<String, String> maps = JSON.parseObject(schemaEntity.getF1(), new TypeReference<Map<String, String>>() {
                });
                evaluationMethod.put("abilityEvaluationMethod", maps.get("abilityEvaluationMethod"));
            }
        }
        schemaEntity.setF1(JSON.toJSONString(evaluationMethod));
        schemaService.edit(schemaEntity);
    }

    /**
     * 贡献率计算（逼近理想排序法）
     *
     * @param indicatorTreeId 指标树id
     * @param sid             方案id
     */
    @GetMapping("calculationTopsis")
    @ApiOperation("贡献率计算（逼近理想排序法）")
    public R calculationTopsis(@RequestParam(value = "indicatorTreeId") String indicatorTreeId,
                               @RequestParam(value = "sid") String sid) {
        //计算贡献率
        if (indicatorCombineCredibilityService.calculationTopsis(indicatorTreeId, sid)) {
            return R.success();
        } else {
            return R.fail("计算失败");
        }
    }

    /**
     * 样本贡献率查询（逼近理想排序法）
     *
     * @param sampleIds 样本id
     */
    @GetMapping("calculationQuery")
    @ApiOperation("贡献率查询（逼近理想排序法）")
    public R<Map<String, Object>> calculationQuery(@RequestParam(value = "sampleId") List<String> sampleIds, @RequestParam(value = "type", required = false) Integer type) {
        //返回样本贡献率信息
        return R.data(indicatorCombineCredibilityService.calculationQuery(sampleIds, type));
    }
}
