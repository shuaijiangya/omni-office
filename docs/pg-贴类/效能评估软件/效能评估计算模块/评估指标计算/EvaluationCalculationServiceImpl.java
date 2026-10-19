package com.yw.module.calculation.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.convert.NumberChineseFormatter;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.yw.common.defines.base.FixedColumnVo;
import com.yw.common.defines.base.MergeTablesVo;
import com.yw.common.defines.enums.*;
import com.yw.common.utils.ArrayUtils;
import com.yw.common.utils.TreeToolUtils;
import com.yw.common.utils.UUIDGenerator;
import com.yw.framework.backMgr.dbTableGenerator.domain.dto.ExerciseInfoDTO;
import com.yw.framework.common.base.api.R;
import com.yw.framework.common.defines.constant.IntegerPool;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.gather.dbinfomgr.DataSrcManagerService;
import com.yw.gather.dbinfomgr.DataTableChNameService;
import com.yw.gather.dbinfomgr.IDatasrcService;
import com.yw.gather.dbinfomgr.entity.DatasrcEntity;
import com.yw.gather.dbinfomgr.entity.TableChNameEntity;
import com.yw.gather.gatherscheme.schemeMgr.entity.GatherRoundRecord;
import com.yw.gather.gatherscheme.schemeMgr.service.IGatherRoundRecordService;
import com.yw.gather.gathertask.taskmgr.domain.vo.TableInfoVO;
import com.yw.module.calculation.contributionRate.domain.vo.EfficiencyIndicatorValueVo;
import com.yw.module.calculation.contributionRate.domain.vo.EvaluateOverviewVO;
import com.yw.module.calculation.contributionRate.domain.vo.ScriptMethodVO;
import com.yw.module.calculation.contributionRate.service.ContributionRateResultService;
import com.yw.module.calculation.domain.dto.*;
import com.yw.module.calculation.domain.entity.*;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.domain.enums.WeightModelEnums;
import com.yw.module.calculation.domain.vo.*;
import com.yw.module.calculation.indicatorCalculation.helper.ParallelCalculateHelper;
import com.yw.module.calculation.indicatorCalculation.mapper.ICalcorTreeViewMapper;
import com.yw.module.calculation.indicatorCalculation.service.*;
import com.yw.module.calculation.indicatorCalculation.strategy.calcorAlg.invulerability.SSLCalc;
import com.yw.module.calculation.mapper.AlgorithmResultMapper;
import com.yw.module.calculation.mapper.IPartsStateMapper;
import com.yw.module.calculation.service.IEquInvulnerabilityService;
import com.yw.module.calculation.service.IEvaluateAlgService;
import com.yw.module.calculation.service.IEvaluationCalculationService;
import com.yw.module.calculation.service.ScriptMethodService;
import com.yw.module.datasource.domain.entity.CfgTaskConditionEntity;
import com.yw.module.datasource.service.IDataPrepareService;
import com.yw.module.equipmentImportance.domain.entity.EquipmentImportanceResult;
import com.yw.module.equipmentImportance.service.EquipmentImportanceResultService;
import com.yw.module.equnode.domain.dto.DamageCalcResultDTO;
import com.yw.module.equnode.domain.entity.AtlasEntity;
import com.yw.module.equnode.service.AtlasEntityService;
import com.yw.module.indicator.domain.dto.IndicatorValuesWithNameDTO;
import com.yw.module.indicator.domain.dto.IndicatorValuesWithNameResultDTO;
import com.yw.module.indicator.domain.dto.QuotaTaskConditionLineData;
import com.yw.module.indicator.domain.entity.*;
import com.yw.module.indicator.domain.vo.IndicatorStandardVO;
import com.yw.module.indicator.service.*;
import com.yw.module.missionScene.domain.entity.CompeteSceneEntity;
import com.yw.module.missionScene.domain.entity.ContributionRateAlgorithmEntity;
import com.yw.module.missionScene.domain.entity.ContributionRateResultEntity;
import com.yw.module.missionScene.domain.entity.SceneScenarioEntity;
import com.yw.module.missionScene.domain.vo.IndicatorResultVO;
import com.yw.module.missionScene.service.CompeteSceneService;
import com.yw.module.missionScene.service.ContributionRateAlgorithmService;
import com.yw.module.missionScene.service.SceneScenarioService;
import com.yw.module.plan.domain.dto.TaskConditionDTO;
import com.yw.module.plan.domain.dto.TaskConditionInfoDTO;
import com.yw.module.plan.domain.entity.*;
import com.yw.module.plan.domain.vo.SchemaResultVO;
import com.yw.module.plan.mapper.SchemaAliasEntityMapper;
import com.yw.module.plan.service.*;
import com.yw.module.system.domain.entity.EquipmentEntity;
import com.yw.module.system.domain.entity.EquipmentSystemEntity;
import com.yw.module.system.service.IEquipmentService;
import com.yw.module.system.service.IEquipmentSystemService;
import com.yw.source.common.enums.SceneTypeEnum;
import com.yw.source.dataManagement.handler.method.SimpleFrameworkController;
import com.yw.source.preprocessing.service.CustomDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * pg计算实现
 *
 * @Author admin
 * @Description: pg计算实现
 * @Date: 2020/12/10 11:53
 */
@Service
@Slf4j
public class EvaluationCalculationServiceImpl implements IEvaluationCalculationService {

    /**
     * 数据获取服务
     */
    private final ICalculateDataService calculateDataService;
    /**
     * 数据表别名服务
     */
    @Autowired
    private SchemaAliasEntityMapper schemaAliasEntityMapper;
    /**
     * 算法模型服务
     */
    @Autowired
    private ScriptMethodService scriptMethodService;
    /**
     * 权重数据服务
     */
    @Autowired
    private IWeightDataService weightDataService;
    /**
     * 算子服务
     */
    @Autowired
    private ICalcorService calcorService;
    /**
     * 数据中文名称服务
     */
    @Autowired
    private DataTableChNameService dataTableChNameService;
    /**
     * 准则列表服务
     */
    @Autowired
    private IStandardLabelsService standardLabelsService;
    /**
     * 任务条件配置服务
     */
    @Autowired
    private CfgTaskConditionService cfgTaskConditionService;
    /**
     * 并行计算服务
     */
    @Autowired
    private ParallelCalculateHelper parallelCalculateHelper;
    /**
     * 装备服务
     */
    @Resource
    private IEquipmentService equipmentService;
    /**
     * 系统装备服务
     */
    @Resource
    private IEquipmentSystemService equipmentSystemService;
    /**
     * 自定义数据服务
     */
    @Autowired
    private CustomDataService customDataService;
    /**
     * 算法配置服务
     */

    @Autowired
    private AlgorithmResultMapper algorithmResultMapper;
    /**
     * 状态服务
     */
    @Autowired
    private IPartsStateMapper partsStateMapper;
    /**
     * 数据管理服务
     */
    @Autowired
    private DataSrcManagerService dataSrcManagerService;
    /**
     * 样本服务
     */
    @Autowired
    private ISceneService sceneService;
    /**
     * 数据获取框架
     */
    @Autowired
    private SimpleFrameworkController simpleFrameworkController;
    /**
     * 算法模型服务
     */
    @Autowired
    private ICalcModuleService calcModuleService;
    /**
     * 指标结果服务
     */
    @Autowired
    private ITIndicatorValueService indicatorValueService;
    /**
     * 算子结果服务
     */
    @Autowired
    private ICalcorValuesService calcorValuesService;
    /**
     * 方案服务
     */
    @Autowired
    private ISchemaService schemaService;
    /**
     * 任务服务
     */
    private final ITaskService taskService;
    /**
     * 指标服务
     */
    @Autowired
    private IIndicatorService indicatorService;
    /**
     * 算子试图服务
     */
    private final ICalcorTreeViewMapper calcorTreeViewMapper;
    /**
     * 评估算法服务
     */
    private final IEvaluateAlgService evaluateAlgService;
    /**
     * 指标准则值服务
     */
    @Autowired
    private ITIndicatorStandardValuesService indicatorStandardValuesService;
    /**
     * 数据解析服务
     */
    private final IDataPrepareService dataPrepareService;
    /**
     * 样本数据结构服务
     */
    @Autowired
    private ISceneTableService sceneTableService;
    /**
     * 数据源服务
     */
    private final IDatasrcService datasrcService;

    /**
     * 指标权重服务
     */
    @Autowired
    private ITIndicatorStandardService indicatorStandardService;
    /**
     * 准则列表服务
     */
    private final IStandardLabelsService labelsService;
    /**
     * 指标树服务
     */
    @Autowired
    private IIndicatorTreeService indicatorTreeService;
    /**
     * 想定样本关联服务
     */
    private final ContributionRateAlgorithmService contributionRateAlgorithmService;

    /**
     * 场景服务
     */
    @Autowired
    private CompeteSceneService competeSceneService;
    /**
     * 体系贡献率结果服务
     */
    @Autowired
    private ContributionRateResultService contributionRateResultService;
    /**
     * 想定服务
     */
    @Autowired
    private SceneScenarioService sceneScenarioService;
    /**
     * 指标准则配置服务
     */
    @Autowired
    private IIndicatorCriteriaService iIndicatorCriteriaService;

    /**
     * 注入：装备抗毁性service
     */
    @Autowired
    private IEquInvulnerabilityService equInvulnerabilityService;

    /**
     * 注入：ssl计算
     */
    @Autowired
    private SSLCalc sslCalc;


    @Autowired
    private EquipmentImportanceResultService equipmentImportanceResultService;

    /**
     * 想定id 装备id 集合 上下文。方便计算时，获取
     */
    public static Map<String, String> scenarioIdEquIdMap = Maps.newHashMap();

    /**
     * 想定id 装备id集合（被摧毁）
     */
    public static Map<String, List<String>> scenarioIdRemoveEquIdMap = Maps.newHashMap();

    /**
     * 指标实体service
     */
    @Autowired
    private AtlasEntityService atlasEntityService;

    /**
     * 采集轮次表
     */
    @Autowired
    private IGatherRoundRecordService iGatherRoundRecordService;

    private static final List<String> LABEL_LIST = List.of("差", "中", "良", "优");


    public EvaluationCalculationServiceImpl(ICalculateDataService calculateDataService, ITIndicatorValueService indicatorValueService, ICalcorValuesService calcorValuesService, ISchemaService schemaService, ITaskService taskService, IIndicatorService indicatorService, ICalcorTreeViewMapper calcorTreeViewMapper, IEvaluateAlgService evaluateAlgService, ITIndicatorStandardValuesService indicatorStandardValuesService, IDataPrepareService dataPrepareService, ISceneTableService sceneTableService, IDatasrcService datasrcService, ITIndicatorStandardService indicatorStandardService, IStandardLabelsService labelsService, ContributionRateAlgorithmService contributionRateAlgorithmService) {
        this.calculateDataService = calculateDataService;
        this.indicatorValueService = indicatorValueService;
        this.calcorValuesService = calcorValuesService;
        this.schemaService = schemaService;
        this.taskService = taskService;
        this.indicatorService = indicatorService;
        this.calcorTreeViewMapper = calcorTreeViewMapper;
        this.evaluateAlgService = evaluateAlgService;
        this.indicatorStandardValuesService = indicatorStandardValuesService;
        this.dataPrepareService = dataPrepareService;
        this.sceneTableService = sceneTableService;
        this.datasrcService = datasrcService;
        this.indicatorStandardService = indicatorStandardService;
        this.labelsService = labelsService;
        this.contributionRateAlgorithmService = contributionRateAlgorithmService;
    }

    /**
     * 评估计算V2版本
     * @Description:
     * @formatter:off part1：只输入sid → 遍历计算保存，方案下的所有场景所有指标
     * part2：输入sid，sceneId → 计算该方案下，场景下所有指标
     * part3：输入sid，sceneId，indicatorId → 计算该方案下，场景下，该指标节点
     * @formatter:off
     * @Date 2021/3/8 14:12
     * @param sid  方案
     * @param indicatorId 指标
     * @param sceneId  样本
     */
    @Override
    @Transactional
    public List<IndicatorValuesEntity> evaluationCalculateV2(String sid, String sceneId, String indicatorId, String calcModuleId, String trainedModelId) {
        List<String> sidList = ArrayUtils.stringToList(sid);
        final AllDataPrepareDTO allDataPrepareBo = dataPrepareService.prepareV2(sid, sceneId, indicatorId, calcModuleId, 0);
        String id = allDataPrepareBo.getCurrentIndicator().getId();
        //当前节点指标的指标计算模型
        CalcModuleEntity calcModuleEntity = allDataPrepareBo.getCalModelsMapByIndicatorId().get(id);
        if (calcModuleEntity == null) {
            throw new BusinessException("请先设置默认指标算法");
        }
        if (calcModuleEntity.getCalcType() == null) {
            throw new BusinessException("请先设置默认指标算法");
        }
        if (calcModuleEntity.getCalcType() == ModuleType.METHOD_MODEL.id
                && EvaluationType.getTypeByName(calcModuleEntity.getMethodId()) == EvaluationType.AHP_MEANS
                && allDataPrepareBo.getWeightModelMapByIndicatorId().containsKey(id)
                && !WeightModelEnums.ANALYTIC_HIERARCHY.getCode().equals(allDataPrepareBo.getWeightModelMapByIndicatorId().get(id).getCode())) {
            throw new BusinessException("AHP法必须使用层次分析构建权重模型");
        }

        if (calcModuleEntity.getCalcType() == ModuleType.METHOD_MODEL.id
                && EvaluationType.getTypeByName(calcModuleEntity.getMethodId()) == EvaluationType.ENTROPY_WEIGHT_MEANS
                && allDataPrepareBo.getWeightModelMapByIndicatorId().containsKey(id)
                && !WeightModelEnums.ENTROWEIGHT.getCode().equals(allDataPrepareBo.getWeightModelMapByIndicatorId().get(id).getCode())) {
            throw new BusinessException("熵权法必须使用熵权构建权重模型");
        }
        List<IndicatorValuesEntity> currentIndicatorValue = Lists.newArrayList();
        for (String f : sidList) {
            //当前节点指标的指标计算模型
            EvaluationType type = null;
            if (ModuleType.valueOf(calcModuleEntity.getCalcType()) == ModuleType.METHOD_MODEL) {
                String methodId = calcModuleEntity.getMethodId();
                type = EvaluationType.getTypeByName(methodId);
                // todo 存的id后面PyScriptALG有用的name 有问题
                allDataPrepareBo.setEvaluateALG(methodId);
            }
            boolean typeFlag = type == EvaluationType.PRINCIPAL || type == EvaluationType.CLUSTER || type == EvaluationType.IDEAL_POINT || type == EvaluationType.GEA_METHOD;
            List<String> sceneList = Lists.newArrayList();
            if (StringUtils.isEmpty(sceneId)) {
                sceneList.addAll(sceneService.findIdsBySid(f));
            } else {
                sceneList.addAll(typeFlag ? sceneService.findIdsBySid(f) : ArrayUtils.stringToList(sceneId));
            }
            //样本不再区分指标树
//            Collection<SceneEntity> sceneEntities = allDataPrepareBo.getSceneEntityMapById().values();
//            String treeId = allDataPrepareBo.getCurrentIndicator().getTreeId();
//            Set<String> unCalculationSceneIds = Sets.newHashSet();
//            for (SceneEntity sceneEntity : sceneEntities) {
//                if (sceneEntity.getTreeId() != null && !treeId.contains(sceneEntity.getTreeId())) {
//                    unCalculationSceneIds.add(sceneEntity.getId());
//                }
//            }
//            sceneList.removeAll(unCalculationSceneIds);

            if (typeFlag && sceneList.size() <= 1) {
                throw new BusinessException("方案样本数量不满足脚本算法需求");
            }
            if (!sceneList.isEmpty()) {
                String lastScene = sceneList.get(sceneList.size() - 1);
                for (String s : sceneList) {
                    EvaluationCalculationDTO evaluationCalculationBO = allDataPrepareBo.prepareEvaluationCalculationBoV2(s);
                    //准则调整
                    adjustStandard(evaluationCalculationBO, allDataPrepareBo);
                    // 标记 是否为最后一个指标
                    boolean lastFlag = lastScene.equals(s);
                    execulateSingleCalculateV2(f, s, indicatorId, evaluationCalculationBO, lastFlag, sceneList);
//                    List<IndicatorValuesEntity> indicatorValuesEntityListNeedUpdate = evaluationCalculationBO.getIndicatorValuesEntityListNeedUpdate();
//                    if (!CollUtil.isEmpty(indicatorValuesEntityListNeedUpdate)) {
//                        currentIndicatorValue.addAll(indicatorValuesEntityListNeedUpdate);
//                    }
                }
            }
        }
        return currentIndicatorValue;

    }

    /**
     * V0.17版本调整：优先取评估矩阵中指标准则，其次取评估指标处设置的准则
     */
    private void adjustStandard(EvaluationCalculationDTO evaluationCalculationBO, AllDataPrepareDTO allDataPrepareBo) {
        if(CollUtil.isEmpty(allDataPrepareBo.getIndicatorCriteriaMap())){
            return;
        }
        //获取当前样本对象
        SceneEntity sceneEntity = evaluationCalculationBO.getSceneEntity();
        //获取当前样本所在任务
        List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.list(new LambdaQueryWrapper<ContributionRateAlgorithmEntity>().eq(ContributionRateAlgorithmEntity::getSceneId, sceneEntity.getId()));
        if(CollUtil.isEmpty(contributionRateAlgorithmEntities)){
            return;
        }
        //获取任务ID
        String missionTaskId = contributionRateAlgorithmEntities.get(0).getMissionTaskId();
        if(StrUtil.isBlank(missionTaskId)){
            log.warn("样本所属的作战任务ID为空，未能替换准则，请排查");
            return;
        }

        //获取任务下的准则
        Map<String,List<IndicatorCriteria>> indicatorCriteria = allDataPrepareBo.getIndicatorCriteriaMap().get(missionTaskId);
        if(CollUtil.isEmpty(indicatorCriteria)){
            log.info("当前任务下未查询到准则");
            return;
        }

        //获取指标准则
        Map<String,List<IndicatorStandardEntity>> indicatorStandardMap = evaluationCalculationBO.getIndicatorStandardMapByIndicatorId();
        if(CollUtil.isEmpty(indicatorStandardMap)){
            //考虑未设置指标准则情况
            indicatorStandardMap = Maps.newHashMap();
            evaluationCalculationBO.setIndicatorStandardMapByIndicatorId(indicatorStandardMap);
        }
        //获取等级集合
        List<StandardLabelsEntity> standardLabelsEntityList = evaluationCalculationBO.getStandardLabelsEntityList();
        if(CollUtil.isEmpty(standardLabelsEntityList)){
            //考虑未设置指标准则情况
            standardLabelsEntityList = Lists.newArrayList();
        }

        //替换指标ID 准则map
        for (Map.Entry<String,List<IndicatorCriteria>> entry : indicatorCriteria.entrySet()) {
            List<IndicatorCriteria> indicatorCriteriaList = entry.getValue();
            if(CollUtil.isEmpty(indicatorCriteriaList)){
                log.warn("评估矩阵下准则信息为空，请排查");
                continue;
            }
            List<IndicatorStandardEntity> standardEntityList = indicatorStandardMap.get(entry.getKey());
            List<IndicatorStandardEntity> standardEntities = convertStandardEntities(indicatorCriteriaList, standardLabelsEntityList, standardEntityList);
            if(CollUtil.isEmpty(standardEntities)){
                continue;
            }
            //替换指标准则
            indicatorStandardMap.put(entry.getKey(), standardEntities);
            if (CollectionUtil.isEmpty(evaluationCalculationBO.getStandardLabelsEntityList())){
                List<StandardLabelsEntity> list = standardLabelsService.list(new LambdaQueryWrapper<StandardLabelsEntity>().eq(StandardLabelsEntity::getFlag, 0));
                evaluationCalculationBO.setStandardLabelsEntityList(list);
            }
            //清理旧等级信息
            if(CollUtil.isNotEmpty(standardEntityList)){
                List<String> labelIdList = standardEntityList.stream().map(IndicatorStandardEntity::getLabelId).collect(Collectors.toList());
                List<String> newLabelIdList = standardEntities.stream().map(IndicatorStandardEntity::getLabelId).collect(Collectors.toList());
                standardLabelsEntityList.removeIf(f -> labelIdList.contains(f.getId()) && !newLabelIdList.contains(f.getId()));
            }
        }
    }

    /**
     * 评估矩阵转为指标矩阵
     */
    private List<IndicatorStandardEntity> convertStandardEntities(List<IndicatorCriteria> indicatorCriteriaList, List<StandardLabelsEntity> standardLabelsEntityList, List<IndicatorStandardEntity> standardEntityList) {
        List<IndicatorStandardEntity> indicatorStandardEntityList = Lists.newArrayList();
        for (IndicatorCriteria indicatorCriteria : indicatorCriteriaList) {
            String maxValue = indicatorCriteria.getMaxValue();
            String minValue = indicatorCriteria.getMinValue();
            if(ObjectUtil.isNull(maxValue) && ObjectUtil.isNull(minValue)){
                continue;
            }

            Optional<StandardLabelsEntity> first = standardLabelsEntityList.stream().filter(item -> item.getName().equals(indicatorCriteria.getTypeCode())).findFirst();
            String labelId = first.isEmpty() ? IdUtil.simpleUUID() : first.get().getId();
            IndicatorStandardEntity indicatorStandardEntity = new IndicatorStandardEntity();
            indicatorStandardEntity.setId(indicatorCriteria.getId());
            indicatorStandardEntity.setIndicatorId(indicatorCriteria.getIndicatorId());
            indicatorStandardEntity.setLabelId(labelId);
            indicatorStandardEntity.setFunc(indicatorCriteria.getFunc());
            indicatorStandardEntity.setMax(String.valueOf(maxValue));
            indicatorStandardEntity.setMin(String.valueOf(minValue));
            Integer openFlag = IntegerPool.ZERO;
            if(CollUtil.isNotEmpty(standardEntityList) && ObjectUtil.isNotNull(standardEntityList.get(0).getOpenFlag())){
                openFlag = standardEntityList.get(0).getOpenFlag();
            }
            indicatorStandardEntity.setOpenFlag(openFlag);

            //添加指标准则
            indicatorStandardEntityList.add(indicatorStandardEntity);

            //添加准则等级
            if(first.isEmpty()){
                StandardLabelsEntity standardLabelsEntity = new StandardLabelsEntity();
                standardLabelsEntity.setId(labelId);
                standardLabelsEntity.setFlag(IntegerPool.ZERO);//默认常规准则
                standardLabelsEntity.setName(indicatorCriteria.getTypeCode());
                standardLabelsEntity.setSortno(LABEL_LIST.indexOf(standardLabelsEntity.getName()) + 1);
                standardLabelsEntity.setValue(indicatorCriteria.getFunc());
                standardLabelsEntityList.add(standardLabelsEntity);
            }
        }
        return indicatorStandardEntityList;
    }

    /**
     * 提前预热计算数据减少计算时的网络IO
     *
     * @param allDataPrepareDTO 待计算的数据
     * @return 热点key
     */
    @NotNull
    private List<String> calculateDataPreheating(AllDataPrepareDTO allDataPrepareDTO) {
        Collection<SceneTableEntity> sceneTableEntities = allDataPrepareDTO.getSceneTableEntityMap().values();
        List<String> tobeQueried = Lists.newArrayList();
        Map<String, GatherRoundRecord> gatherRoundRecordMap = new HashMap<>();
        List<String> dataCacheKey = Lists.newArrayList();
        for (SceneEntity sceneEntity : allDataPrepareDTO.getSceneEntityMapById().values()) {
            Map<String, String> sampleTaskMap = sceneEntity.getSampleTaskMap();
            for (Map.Entry<String, String> dataSrcKeyAndRoundValueMap : sampleTaskMap.entrySet()) {
                String dataSrcKey = dataSrcKeyAndRoundValueMap.getKey();
                String roundValue = dataSrcKeyAndRoundValueMap.getValue();
                if (!gatherRoundRecordMap.containsKey(dataSrcKey)) {
                    List<GatherRoundRecord> gatherRoundRecords = iGatherRoundRecordService.queryList(dataSrcKey);
                    gatherRoundRecordMap.put(dataSrcKey, gatherRoundRecords.get(0));
                }
                GatherRoundRecord gatherRoundRecord = gatherRoundRecordMap.get(dataSrcKey);
                String roundField = gatherRoundRecord.getRoundField();
                String[] key = roundField.split("/");
                String[] value = roundValue.split("/");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < key.length; i++) {
                    sb.append(key[i]).append("-").append(value[i]).append(";");
                }
                tobeQueried.add(sb.toString());
            }
        }
        for (SceneTableEntity sceneTableEntity : sceneTableEntities) {
            String dsid = sceneTableEntity.getDsid();
            Optional<DatasrcEntity> datasrcEntityOptional = datasrcService.findById(dsid);
            if (!datasrcEntityOptional.isPresent()) {
                continue;
            }
            dataCacheKey.add(sceneTableEntity.getTableName());
            Set<String> columnNames = sceneTableEntity.getColumnNames();
            try {
                simpleFrameworkController.initObjectPool(tobeQueried, datasrcEntityOptional.get(), Lists.newArrayList(columnNames), sceneTableEntity.getTableName());
            } catch (Exception e) {
                log.error("提前预热数据失败: {} ", e.getMessage());
            }
        }
        return dataCacheKey;
    }

    /**
     * 将计算结果存储到数据库中去
     *
     * @param sid                    方案id
     * @param lastFlag               是否是最后于一个样本
     * @param saveIndicatorValueData 指标结果集
     * @param sceneList              样本集合
     * @param saveCalCorData         算子结果集
     * @param saveStandardValueData  准则结果集
     */
    private void calculationResultSaveToDb(String sid, boolean lastFlag, List<IndicatorValuesEntity> saveIndicatorValueData, List<String> sceneList, List<CalCorValuesEntity> saveCalCorData, List<IndicatorStandardValuesEntity> saveStandardValueData) {
        if (lastFlag) {
            Set<String> indicatorIds = Sets.newHashSet();
            Set<String> sceneLists = Sets.newHashSet();
            for (IndicatorValuesEntity saveIndicatorValueDatum : saveIndicatorValueData) {
                indicatorIds.add(saveIndicatorValueDatum.getIndicatorId());
                sceneLists.add(saveIndicatorValueDatum.getSceneId());
            }
            //更新数据库关联信息
            if (!CollectionUtils.isEmpty(saveIndicatorValueData)) {
                //更新数据库指标值
                indicatorValueService.deleteBySceneIdAndSid(sid, Lists.newArrayList(sceneLists), indicatorIds);
                indicatorValueService.saveAll(saveIndicatorValueData);
            }
            if (!CollectionUtils.isEmpty(saveCalCorData)) {
                //根据样本id和指标id更新关联信息
                calcorValuesService.deleteBySceneIds(Lists.newArrayList(sceneLists), indicatorIds);
                calcorValuesService.saveOrUpdateBatch(saveCalCorData);
            }
            if (!CollectionUtils.isEmpty(saveStandardValueData)) {
                indicatorStandardValuesService.deleteBySceneIds(Lists.newArrayList(sceneLists), indicatorIds);
                indicatorStandardValuesService.insertAll(saveStandardValueData);
            }
        }
    }

    /**
     * 同步计算
     */
    @Override
    public void parallelCalculate(String sid, Integer treeType) {
        List<SchemaEntity> schemaEntityList = new ArrayList<>();
        // 没有传方案，计算所有的
        if (StringUtils.isBlank(sid)) {
//            schemaEntityList = schemaRepository.findAll();
            schemaEntityList = schemaService.list();
        } else {
//            schemaEntityList = schemaService.find(SchemaEntity.builder().id(sid).build());
            schemaEntityList = schemaService.list(new QueryWrapper<SchemaEntity>().eq("id", sid));
        }

        parallelCalculateHelper.parallelCalculate(schemaEntityList, treeType);
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!StringUtils.isEmpty(sid)) {
            while (parallelCalculateHelper.isRunning()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    Thread.yield();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 设置pg结论
     * @param sceneId  样本
     * @param sid  方案ID
     * @param summary  评估结论
     */
    @Override
    public void setSummary(String sid, String sceneId, String summary) {
        Optional<SchemaEntity> schemaEntity = schemaService.findById(sid);
        if (!schemaEntity.isPresent()) {
            throw new BusinessException("未找到对应任务");
        }
        SchemaEntity entity = schemaEntity.get();
        entity.setSummary(summary);
        schemaService.edit(entity);
    }

    /**
     * 执行单场景计算
     * @param sid         方案id
     * @param sceneId     场景id->样本ID
     * @param indicatorId 指标id
     * @throws BusinessException example：位置：【场景A】->【指标名】->【时间转换】
     *                           信息：时间转算算子无输入
     * @Author 刘乐法
     * @Description 执行单场景计算
     * @Return void
     * @Date 2020/12/25 14:36
     * @Update superman 2021/10/26 14:20
     */
    @Transactional(rollbackFor = Exception.class)
    public void execulateSingleCalculateV2(String sid, String sceneId, String indicatorId, EvaluationCalculationDTO bo, boolean lastFlag, List<String> sceneList) {
        try {
            log.info("**************开始计算{}场景******************", bo.getSceneEntity().getName());
            bo = doEvaluationCalculateV2(bo.getCurrentIndicator().getId(), bo, lastFlag, sceneList, null);
            //更新数据库关联信息
            if (!CollectionUtils.isEmpty(bo.getIndicatorValuesEntityListNeedUpdate())) {
                //更新数据库指标值
//                for (IndicatorValuesEntity indicatorValuesEntity : bo.getIndicatorValuesEntityListNeedUpdate()) {
//                    indicatorValueService.deleteBySceneIdIndicatorIdSid(sid, indicatorValuesEntity.getSceneId(), indicatorValuesEntity.getIndicatorId());
//                }
                //删除旧指标的值
                List<String> removeIndicator = bo.getGlistIndicatorMapByPid().values().stream().flatMap(Collection::stream).map(IndicatorEntity::getId).collect(Collectors.toList());
                indicatorValueService.deleteBySceneIdSid(sid, bo.getSceneEntity().getId(), removeIndicator);
                indicatorValueService.saveAll(bo.getIndicatorValuesEntityListNeedUpdate());
            }
            if (!CollectionUtils.isEmpty(bo.getCalcorValuesNeedUpdate())) {
                //根据样本id和指标id更新关联信息
                for (CalCorValuesEntity calcorValuesEntity : bo.getCalcorValuesNeedUpdate()) {
                    calcorValuesService.deleteBySceneIdIndicatorId(sid, calcorValuesEntity.getIndicatorId());
                }
                calcorValuesService.insertAll(bo.getCalcorValuesNeedUpdate());
            }
            if (!CollectionUtils.isEmpty(bo.getStandardLabelsListNeedUpdate())) {
                //通过方案id、场景id、指标id更新关联信息
                indicatorStandardValuesService.deleteBySidIndicatorIdSceneId(sid, indicatorId, sceneId);
                indicatorStandardValuesService.insertAll(bo.getStandardLabelsListNeedUpdate());
            }
            // 是体系贡献率时，获取指标的归一值，入result表
//            List<IndicatorValuesEntity> indicatorList = bo.getIndicatorValuesEntityListNeedUpdate();
            // 根据样本id查询场景、任务、想定
//            ContributionRateAlgorithmEntity algorithmEntity = contributionRateAlgorithmService.findBySceneId(sceneId);
            // 如果over flag == 1 标识为当前参与体系贡献计算样本
            // todo 记录一下 后续支持设置参与体系贡献率计算的样本时将IMITATE_TYPE修改为OVER_TYPE
//            if (null != algorithmEntity && algorithmEntity.getOverFlag() == ContributionRateAlgorithmOverFlagType.IMITATE_TYPE.id) {
//                List<ContributionRateResultEntity> insertList = new ArrayList<>();
//                ContributionRateResultEntity resultEntity = null;
//                for (IndicatorValuesEntity entity : indicatorList) {
//                    String labelName = "未设置任务映射信息";
//                    Map<String, List<IndicatorCriteria>> criteriaServiceClassifyData = iIndicatorCriteriaService.getClassifyDataBySceneId(algorithmEntity.getCompeteSceneId(), algorithmEntity.getMissionTaskId());
//                    List<IndicatorCriteria> indicatorCriteria = criteriaServiceClassifyData.get(algorithmEntity.getMissionTaskId() + "_" + entity.getIndicatorId());
//                    if (CollUtil.isNotEmpty(indicatorCriteria)) {
//                        boolean flag = false;
//                        double value = Double.parseDouble(entity.getNormalizedValue());
//                        for (IndicatorCriteria criteria : indicatorCriteria) {
//                            if (null == criteria.getMinValue() && null == criteria.getMaxValue()) {
//                                labelName = "未设置任务映射信息";
//                                continue;
//                            } else if (StringUtils.equals(criteria.getTaskId(), algorithmEntity.getMissionTaskId()) &&
//                                    ((null == criteria.getMaxValue() && null != criteria.getMinValue() && value >= criteria.getMinValue()) ||
//                                            (null == criteria.getMinValue() && null != criteria.getMaxValue() && value <= criteria.getMaxValue()) ||
//                                            (null != criteria.getMinValue() && null != criteria.getMaxValue() && value >= criteria.getMinValue() && value <= criteria.getMaxValue()))) {
//                                labelName = criteria.getTypeCode();
//                                break;
//                            }
//                            flag = true;
//                        }
//                        if (flag && StringUtils.equals("未设置任务映射信息", labelName)) {
//                            labelName = "超出任务映射范围";
//                        }
//                    }
//
//                    resultEntity = new ContributionRateResultEntity();
//                    resultEntity.setId(UUIDGenerator.getUUID());
//                    resultEntity.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
//                    resultEntity.setMissionTaskId(algorithmEntity.getMissionTaskId());
//                    resultEntity.setIndicatorId(entity.getIndicatorId());
//                    resultEntity.setIndicatorResult(entity.getNormalizedValue());
//                    resultEntity.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
//                    resultEntity.setStandardLabelName(labelName);
//                    resultEntity.setCreateDate(LocalDateTime.now());
//                    insertList.add(resultEntity);
//                }
//
//                List<String> indicatorIdList = indicatorList.stream().map(IndicatorValuesEntity::getIndicatorId).collect(Collectors.toList());
//                if (CollUtil.isNotEmpty(indicatorIdList)){
//                    contributionRateResultService.deleteContributionRateResult(algorithmEntity.getCompeteSceneId(),
//                            algorithmEntity.getMissionTaskId(), algorithmEntity.getSceneScenarioId(), indicatorIdList);
//                }
//                if (CollUtil.isNotEmpty(insertList)){
//                    contributionRateResultService.saveContributionRateResult(insertList);
//                }
//            }

            bo = null;
        } catch (BusinessException e) {
            log.warn(e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Exception ee) {
            ee.printStackTrace();
            log.warn(ee.getMessage());
            throw new BusinessException(ee.getMessage());
        }
    }

    /**
     * 体系贡献率的准备阶段，使用准则结果向上综合计算
     *
     * @param criteriaServiceClassifyData 准则信息
     * @param insertList                  贡献率的存储数据
     * @param indicatorWeightEntityMap    指标权重结果
     * @param currentIndicator            根指标
     * @param values                      指标准则信息
     * @param glistIndicatorMapByPid      指标的所属父级信息
     * @param indicatorList               指标value结果
     * @param algorithmEntity             所属场景 想定 任务信息
     */
    private void upwardComprehensiveContributionRate(Map<String, List<IndicatorCriteria>> criteriaServiceClassifyData, List<ContributionRateResultEntity> insertList, Map<String, IndicatorWeightEntity> indicatorWeightEntityMap, IndicatorEntity currentIndicator, Collection<List<IndicatorStandardEntity>> values, Map<String, List<IndicatorEntity>> glistIndicatorMapByPid, List<IndicatorValuesEntity> indicatorList, ContributionRateAlgorithmEntity algorithmEntity) {


        String rootIndicatorId = currentIndicator.getId();
        List<IndicatorEntity> indicatorEntities = glistIndicatorMapByPid.get(rootIndicatorId);

        if (CollUtil.isNotEmpty(indicatorEntities)) {
            double sum = 0.0;
            for (IndicatorEntity indicatorEntity : indicatorEntities) {
                String indicatorId = indicatorEntity.getId();
                upwardComprehensiveContributionRate(criteriaServiceClassifyData, insertList, indicatorWeightEntityMap, indicatorEntity, values, glistIndicatorMapByPid, indicatorList, algorithmEntity);
                for (ContributionRateResultEntity rateResultEntity : insertList) {
                    if (rateResultEntity.getIndicatorId().equals(indicatorId)) {
                        sum = sum + Double.parseDouble(rateResultEntity.getStandardLabelValue()) * indicatorWeightEntityMap.get(indicatorId).getWeight();
                    }
                }
            }
            buildContributionRateResult(sum + "", currentIndicator.getId(), criteriaServiceClassifyData, insertList, values, algorithmEntity);

        } else {
            for (IndicatorValuesEntity entity : indicatorList) {
                if (entity.getIndicatorId().equals(currentIndicator.getId())) {
                    buildContributionRateResult(entity.getNormalizedValue(), currentIndicator.getId(), criteriaServiceClassifyData, insertList, values, algorithmEntity);
                }
            }
        }

    }

    /**
     * 构建贡献率结果
     *
     * @param normalizedValue             归一化值
     * @param indicatorId                 当前指标
     * @param criteriaServiceClassifyData 指标准则
     * @param insertList                  待写入的数据
     * @param values                      指标准则信息
     * @param algorithmEntity             场景、使命任务、想定信息
     */
    private void buildContributionRateResult(String normalizedValue, String indicatorId, Map<String, List<IndicatorCriteria>> criteriaServiceClassifyData, List<ContributionRateResultEntity> insertList, Collection<List<IndicatorStandardEntity>> values, ContributionRateAlgorithmEntity algorithmEntity) {
        ContributionRateResultEntity resultEntity = null;
        String labelName = "未设置任务映射信息";
        String sourceStandardLabelId = "";
        List<IndicatorCriteria> indicatorCriteria = criteriaServiceClassifyData.get(algorithmEntity.getMissionTaskId() + "_" + indicatorId);
        if (CollUtil.isNotEmpty(indicatorCriteria)) {
            boolean flag = false;
            double value = Double.parseDouble(normalizedValue);
            for (IndicatorCriteria criteria : indicatorCriteria) {
                if (null == criteria.getMinValue() && null == criteria.getMaxValue()) {
                    labelName = "未设置任务映射信息";
                    continue;
                } else if (StringUtils.equals(criteria.getTaskId(), algorithmEntity.getMissionTaskId()) &&
                        (
                                (StringUtils.isNotEmpty(criteria.getMaxValue()) && StringUtils.isNotEmpty(criteria.getMinValue()) && ("-".equals(criteria.getMinValue())||value >= Double.parseDouble(criteria.getMinValue()))) ||
                                (StringUtils.isNotEmpty(criteria.getMaxValue()) && StringUtils.isNotEmpty(criteria.getMinValue()) && ("+".equals(criteria.getMaxValue()) || value <= Double.parseDouble(criteria.getMaxValue()))) ||
                                (StringUtils.isNotEmpty(criteria.getMaxValue()) && StringUtils.isNotEmpty(criteria.getMinValue()) && ("-".equals(criteria.getMinValue())||value >= Double.parseDouble(criteria.getMinValue())) && ("+".equals(criteria.getMaxValue()) || value <= Double.parseDouble(criteria.getMaxValue())))
                        )) {
                    labelName = criteria.getTypeCode();
                    sourceStandardLabelId = criteria.getSourceIndicatorStandard();
                    break;
                }
                flag = true;
            }
            if (flag && StringUtils.equals("未设置任务映射信息", labelName)) {
                labelName = "超出任务映射范围";
            }
        }

        resultEntity = new ContributionRateResultEntity();
        resultEntity.setId(UUIDGenerator.getUUID());
        resultEntity.setCompeteSceneId(algorithmEntity.getCompeteSceneId());
        resultEntity.setMissionTaskId(algorithmEntity.getMissionTaskId());
        resultEntity.setIndicatorId(indicatorId);
        resultEntity.setIndicatorResult(normalizedValue);
        resultEntity.setSceneScenarioId(algorithmEntity.getSceneScenarioId());
        resultEntity.setStandardLabelName(labelName);
        resultEntity.setCreateDate(LocalDateTime.now());
        resultEntity.setStandardLabelValue(0.0 + "");
        if (!StringUtils.isEmpty(sourceStandardLabelId)) {
            if (CollUtil.isNotEmpty(values)) {
                retry:
                for (List<IndicatorStandardEntity> value : values) {
                    for (IndicatorStandardEntity indicatorStandardEntity : value) {
                        if (indicatorStandardEntity.getId().equals(sourceStandardLabelId)) {
                            resultEntity.setStandardLabelValue(indicatorStandardEntity.getFunc());
                            break retry;
                        }
                    }
                }
            }
        }
        insertList.add(resultEntity);
        log.info("当前指标的体系贡献率结果信息：{}", JSON.toJSONString(resultEntity));
    }


    /**
     * 捕获到的异常信息格式转化
     * @param sceneName     场景名称
     * @param indicatorName 指标名称
     * @param errorMsg      错误信息 example：加法算子加法算子无输入
     * @return String
     * @Description: 捕获到的异常信息格式转化
     */
    private String errorMsgParse(String sceneName, String indicatorName, String errorMsg) {
        if (StringUtils.isBlank(errorMsg)) {
            return "位置：【" + sceneName + "】→【" + indicatorName + "】\n" + "信息：空";
        }

//        String substring = errorMsg.substring(errorMsg.indexOf("") + 6);

        if (!errorMsg.contains("") && !errorMsg.contains("")) {
            return "位置：【" + sceneName + "】→【" + indicatorName + "】\n" + "信息：" + errorMsg;
        } else if (!errorMsg.contains("") || errorMsg.contains("")) {
            return "位置：【" + sceneName + "】→【" + indicatorName + "】\n" + "信息：" + errorMsg;
        }

        String site = errorMsg.substring(errorMsg.indexOf("") + 5, errorMsg.lastIndexOf(""));
        String cause = errorMsg;

        List<String> errorList = new ArrayList<>();
        errorList.add(sceneName);//添加分类名称
        errorList.add(indicatorName);
        errorList.addAll(splitToList(site, ""));//添加捕获的异常信息
        StringBuilder sb = new StringBuilder();
        errorList.forEach(f -> {
            sb.append("→【" + f + "】");
        });
        String sbString = sb.toString();
        sbString = sbString.replaceFirst("→", "");
        String str = "位置：" + sbString + " \n" + "信息：" + cause;
        return str;
    }

    // 字符串转集合
    private List<String> splitToList(String string, String symbol) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isNotBlank(string) && string.contains(symbol)) {
            list = Arrays.asList(string.split(symbol));
        } else if (StringUtils.isNotBlank(string)) {
            list.add(string);
        }
        return list;
    }

    /**
     * 计算模型 > 权重算法；计算模型可以在任意节点，权重算法在非叶子节点
     * @param bo 分类下信息封装
     * @param lastFlag 是否最后一个
     * @param rootId 计算指标
     * @param sceneList 样本集合
     * @param paramEntityMap 算法配置
     * @Author 刘乐法
     * @Description 计算分类下整个指标树/选中某个指标节点的数据
     * @Description: 计算模型 > 权重算法；计算模型可以在任意节点，权重算法在非叶子节点
     * @Return com.yw.evaluationCalculation.domain.bo.EvaluationCalculationBO
     * @Date 2020/12/11 14:20
     * @Update superman 2021/10/26 14:20
     */
    private EvaluationCalculationDTO doEvaluationCalculateV2(String rootId, EvaluationCalculationDTO bo, boolean lastFlag, List<String> sceneList, Map<String, AlgorithmParamEntity> paramEntityMap) {
        //当前计算节点指标
        IndicatorEntity thisIndicator = bo.getCurrentIndicator();
        //当前节点指标的指标计算模型
        CalcModuleEntity thisCalModel = bo.getCalModelsMapByIndicatorId().get(thisIndicator.getId());
        //当前节点子指标
        List<IndicatorEntity> childList = bo.getGlistIndicatorMapByPid().get(thisIndicator.getId());
        if (thisCalModel == null) {
            if (CollUtil.isEmpty(childList) && bo.getSchemaEntity().getSchemaEvaluationType() != SchemaEvaluateType.SYSTEM_CONTRIBUTION_RATE_EVALUATE.id) {
                throw new BusinessException("当前指标未设置默认算法,指标名称：" + thisIndicator.getName());
            }
            if (CollUtil.isEmpty(childList) && bo.getSchemaEntity().getSchemaEvaluationType() == SchemaEvaluateType.SYSTEM_CONTRIBUTION_RATE_EVALUATE.id) {
                return calculateDataService.setDefaultValue(bo);
            }
            thisCalModel = CalcModuleEntity.builder().methodId(bo.getEvaluateALG()).calcType(ModuleType.METHOD_MODEL.id).build();
        }
        //当前计算节点所有子指标集合
        if (childList == null) {
            childList = Lists.newArrayList();
        }
        // 计算叶子指标值
        if ((thisCalModel != null && thisCalModel.getCalcType() != ModuleType.METHOD_MODEL.id) && childList.isEmpty()) {
            return calculateDataService.calculateIndicatorCalculationModel(bo);
        } else {
            List<String> alreadySetList = Lists.newArrayList();
            assert thisCalModel != null;
            String methodId = thisCalModel.getMethodId();
            if (thisCalModel.getCalcType() == ModuleType.ALGORITHM_MODEL.id) {
                methodId = bo.getEvaluateALG();
            }
            //当前pg算法 根据不同的pg算法走不同的逻辑
//            EvaluationType type = EvaluationType.getTypeByName(methodId);
            ScriptMethodEntity scriptMethodEntity = scriptMethodService.getById(methodId);
            EvaluationType type;
            if (org.apache.commons.lang3.ObjectUtils.isEmpty(scriptMethodEntity)) {
                type = EvaluationType.getTypeByName(methodId);
            } else {
                type = EvaluationType.UPLOAD;
            }
            if (CollUtil.isEmpty(paramEntityMap)) {
                paramEntityMap = bo.getAlgorithmParamMaps().get(thisCalModel.getId());
            }
            boolean typeFlag = type != EvaluationType.PRINCIPAL && type != EvaluationType.CLUSTER && type != EvaluationType.IDEAL_POINT && type != EvaluationType.GEA_METHOD;
            switch (type) {
                case NONE:
                    WeightModelEntity weightModelEntity = bo.getWeightModelMapByIndicatorId().get(thisIndicator.getId());
                    if (weightModelEntity != null && (!CollectionUtils.isEmpty(childList))) {
                        for (IndicatorEntity indicatorEntity : childList) {
                            bo.setCurrentIndicator(indicatorEntity);
                            bo = doEvaluationCalculateV2(rootId, bo, lastFlag, sceneList, paramEntityMap);
                        }
                    }
                    bo.setCurrentIndicator(thisIndicator);
                    calculateDataService.calculateIndicatorCalculationModel(bo);
                    return bo;
                case EXPERT_SCORE:
                    WeightModelEntity ScoreWeight = bo.getWeightModelMapByIndicatorId().get(thisIndicator.getId());

                    if (CollectionUtils.isEmpty(childList) && ScoreWeight != null) {
                        throw new BusinessException("权重算法只能配置在非叶子节点");
                    }
                    if (!CollectionUtils.isEmpty(childList)) {
                        for (IndicatorEntity indicatorEntity : childList) {
                            bo.setCurrentIndicator(indicatorEntity);

                            bo = doEvaluationCalculateV2(rootId, bo, lastFlag, sceneList, paramEntityMap);
                        }
                    }
                    bo.setCurrentIndicator(thisIndicator);

                    return weightDataService.calculateIndicatorWeightV2(bo, paramEntityMap, type.getName(), thisCalModel.getTreeParamList(), alreadySetList);
                case AHP_MEANS:
                case WEIGHT_SUM:
                case WEIGHT_NORM:
                case ENTROPY_WEIGHT_MEANS:
                case DICTIONARY_ORDER_METHOD:
                    WeightModelEntity thisWeight = bo.getWeightModelMapByIndicatorId().get(thisIndicator.getId());
                    if (CollectionUtils.isEmpty(childList) && thisWeight != null) {
                        throw new BusinessException("权重算法只能配置在非叶子节点");
                    }
                    for (IndicatorEntity indicatorEntity : childList) {
                        bo.setCurrentIndicator(indicatorEntity);
                        bo = doEvaluationCalculateV2(rootId, bo, lastFlag, sceneList, paramEntityMap);
                    }
                    bo.setCurrentIndicator(thisIndicator);
                    bo.setLastFlag(lastFlag);
                    return weightDataService.calculateIndicatorWeightV2(bo, paramEntityMap, type.getName(), thisCalModel.getTreeParamList(), alreadySetList);
                default:
                    for (IndicatorEntity indicatorEntity : childList) {
                        bo.setCurrentIndicator(indicatorEntity);
                        bo = doEvaluationCalculateV2(rootId, bo, lastFlag, sceneList, paramEntityMap);
                    }
                    bo.setCurrentIndicator(thisIndicator);
                    bo.setScriptMethodId(thisCalModel.getMethodId());
                    if (bo.getCurrentIndicator().getId().equals(rootId)) {
                        // ADC/SEA/VTF  每个样本都进行脚本算法执行计算
                        // 主成分分析/聚类分析/逼近理想点  其他样本计算完之后, 最后一个脚本统一封装数据进行脚本掉用
                        if (typeFlag || lastFlag) {
                            return weightDataService.calculateIndicatorWeightV2(bo, paramEntityMap, type.getName(), thisCalModel.getTreeParamList(), alreadySetList);
                        }
                    }
                    // 中间节点设置算法模型
//                    else if (StringUtils.isNotEmpty(thisCalModel.getId())) {
//                        List<IndicatorValuesEntity> result = evaluationCalculateV2(thisIndicator.getSid(), null, thisCalModel.getIndicatorId(), "");
//                        if (!CollUtil.isEmpty(result)) {
//                            Map<IndicatorValuesEntity, IndicatorValuesEntity> indicatorValuesEntityMap = bo.getIndicatorValuesEntityMap();
//                            bo.getIndicatorValuesEntityListNeedUpdate().addAll(result);
//                            for (IndicatorValuesEntity entity : result) {
//                                if (entity.getIndicatorId().equals(thisCalModel.getIndicatorId())) {
//                                    alreadySetList.add(entity.getIndicatorId());
//                                    indicatorValuesEntityMap.put(entity, entity);
//                                    bo.getIndicatorValuesEntityListNeedUpdate().add(entity);
//                                }
//                            }
//                        }
//                    }
                    break;
            }
            return bo;
        }
    }

    /**
     * 计算模型 > 权重算法；计算模型可以在任意节点，权重算法在非叶子节点
     * @param bo 分类下信息封装
     * @param rootId  计算指标
     * @param lastFlag  是否最后一个
     * @Author 刘乐法
     * @Description 计算分类下整个指标树/选中某个指标节点的数据
     * @Description: 计算模型 > 权重算法；计算模型可以在任意节点，权重算法在非叶子节点
     * @Return com.yw.evaluationCalculation.domain.bo.EvaluationCalculationBO
     * @Date 2020/12/11 14:20
     * @Update superman 2021/10/26 14:20
     */
    private EvaluationCalculationDTO doEvaluationCalculate(String rootId, EvaluationCalculationDTO bo, boolean lastFlag) {
        //当前计算节点指标
        IndicatorEntity thisIndicator = bo.getCurrentIndicator();
        //当前节点指标的指标计算模型
        CalcModuleEntity thisCalModel = bo.getCalModelsMapByIndicatorId().get(thisIndicator.getId());
        //当前计算节点所有子指标集合
        List<IndicatorEntity> childList = bo.getGlistIndicatorMapByPid().get(thisIndicator.getId());
        if (childList == null) {
            childList = Lists.newArrayList();
        }
        // 计算叶子指标值
        if (thisCalModel != null && childList.isEmpty()) {
            return calculateDataService.calculateIndicatorCalculationModel(bo);
        } else {
            //当前pg算法 根据不同的pg算法走不同的逻辑
            EvaluationType type = EvaluationType.getTypeByName(bo.getEvaluateALG());
            boolean typeFlag = type != EvaluationType.PRINCIPAL && type != EvaluationType.CLUSTER;
            switch (type) {
                case EXPERT_SCORE:
                    WeightModelEntity ScoreWeight = bo.getWeightModelMapByIndicatorId().get(thisIndicator.getId());
                    if (CollectionUtils.isEmpty(childList) && ScoreWeight != null) {
                        throw new BusinessException("权重算法只能配置在非叶子节点");
                    }
                    if (!CollectionUtils.isEmpty(childList)) {
                        for (IndicatorEntity indicatorEntity : childList) {
                            bo.setCurrentIndicator(indicatorEntity);
                            bo = doEvaluationCalculate(rootId, bo, lastFlag);
                        }
                    }
                    bo.setCurrentIndicator(thisIndicator);
                    return weightDataService.calculateIndicatorWeight(bo);
                case AHP_MEANS:
                case WEIGHT_SUM:
                case WEIGHT_NORM:
                case DICTIONARY_ORDER_METHOD:
                    WeightModelEntity thisWeight = bo.getWeightModelMapByIndicatorId().get(thisIndicator.getId());
                    if (thisCalModel == null && thisWeight == null) {
                        throw new BusinessException("没有配置指标算法/权重算法");
                    }
                    if (CollectionUtils.isEmpty(childList) && thisWeight != null) {
                        throw new BusinessException("权重算法只能配置在非叶子节点");
                    }
                    if (thisWeight != null && (!CollectionUtils.isEmpty(childList))) {
                        for (IndicatorEntity indicatorEntity : childList) {
                            bo.setCurrentIndicator(indicatorEntity);
                            bo = doEvaluationCalculate(rootId, bo, lastFlag);
                        }
                    }
                    bo.setCurrentIndicator(thisIndicator);
                    return weightDataService.calculateIndicatorWeight(bo);
                default:
                    for (IndicatorEntity indicatorEntity : childList) {
                        bo.setCurrentIndicator(indicatorEntity);
                        bo = doEvaluationCalculate(rootId, bo, lastFlag);
                    }
                    bo.setCurrentIndicator(thisIndicator);
                    if (bo.getCurrentIndicator().getId().equals(rootId)) {
                        // ADC/SEA/VTF  每个样本都进行脚本算法执行计算
                        // 主成分分析/聚类分析  其他样本计算完之后, 最后一个脚本统一封装数据进行脚本掉用
                        if (typeFlag || (!typeFlag && lastFlag)) {
                            return weightDataService.calculateIndicatorWeight(bo);
                        }
                    }
                    break;
            }
            return bo;
        }
    }

    /**
     * 查询指标计算结果
     * @param sid 方案id
     * @param sceneId 样本id
     * @param indicatorId 指标id
     * @return
     */
    @Override
    public IndicatorValuesWithNameResultDTO findIndicatorResultV2(String sid, String sceneId, String indicatorId) {
        IndicatorValuesWithNameResultDTO dto = new IndicatorValuesWithNameResultDTO();
        String newIndicatorId = indicatorId;
        if (StringUtils.isEmpty(newIndicatorId) || indicatorId.contains(",")) {
            List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).pid("0").build());
            if (CollUtil.isEmpty(indicatorEntities)) {
                return new IndicatorValuesWithNameResultDTO();
            }
            newIndicatorId = indicatorEntities.get(0).getId();
        }
        Optional<IndicatorEntity> optionalIndicatorEntity = indicatorService.findById(newIndicatorId);
        // 体系贡献率 root indicator id 不需要做处理
        indicatorId = optionalIndicatorEntity.get().getPid().equals("0") ? null : indicatorId;
//        List<CalcModuleEntity> all = calcModuleRepository.findAll(Example.of(CalcModuleEntity.builder()
//                .indicatorId(newIndicatorId).calcType(ModuleType.METHOD_MODEL.id).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build()));
        List<CalcModuleEntity> all = calcModuleService.list(new QueryWrapper<CalcModuleEntity>()
                .eq("indicator_id", newIndicatorId)
                .eq("CALC_TYPE", ModuleType.METHOD_MODEL.id)
                .eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id));
        if (CollUtil.isEmpty(all)) {
            return dto;
        }
        CalcModuleEntity calcModuleEntity = all.get(0);
        //获得当前pg任务的pg算法
        EvaluationType typeByName = EvaluationType.getTypeByName(calcModuleEntity.getMethodId());
        Map<String, List<IndicatorStandardVO>> indicatorStandardLabelsMap = new HashMap<>();
        List<CalcorEntity> calcorEntities = new ArrayList<>();
        Map<String, String> enNameMap = Maps.newHashMap();
        Map<String, String> chNameMap = Maps.newHashMap();
        Set<String> defaultModuleIds = new HashSet<>();
        loadDataForResult(sid, indicatorStandardLabelsMap, calcorEntities, enNameMap, chNameMap, defaultModuleIds);
        return getIndicatorValuesWithNameDTO(sid, sceneId, indicatorId, dto, typeByName, indicatorStandardLabelsMap, calcorEntities, enNameMap, chNameMap, defaultModuleIds);
    }

    /**
     * 获取指标结果
     * @param sid 方案
     * @param indicatorTreeId 指标树
     * @return
     */

    @Override
    public List<Map<String, Object>> findIndicatorResultV3(String sid, String indicatorTreeId) {
        List<Map<String, Object>> result = new ArrayList<>();

        Optional<IndicatorTreeEntity> indicatorTreeEntity = indicatorTreeService.findById(indicatorTreeId);
        if (!indicatorTreeEntity.isPresent()) {
            return Lists.newArrayList();
        }
        // todo 是否有多个同类型指标树
        IndicatorTreeEntity treeEntity = indicatorTreeEntity.get();
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().treeId(treeEntity.getId()).build());
        String rootIndicatorId = indicatorEntities.stream().filter(indicatorEntity -> indicatorEntity.getPid().equals("0")).findAny().get().getId();

//        List<CalcModuleEntity> all = calcModuleRepository.findAll(Example.of(CalcModuleEntity.builder().indicatorId(rootIndicatorId)
//                        .calcType(ModuleType.METHOD_MODEL.id).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build())
//                , Sort.by("createTime").descending());
        List<CalcModuleEntity> all = calcModuleService.list(new QueryWrapper<CalcModuleEntity>()
                .eq("indicator_id", rootIndicatorId).eq("CALC_TYPE", ModuleType.METHOD_MODEL.id)
                .eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id).orderByDesc("CREATE_TIME"));
        if (CollUtil.isEmpty(all)) {
            return result;
        }
        String rootMethodId = all.get(0).getMethodId();

        //获得当前pg任务的pg算法
        EvaluationType typeByName = EvaluationType.getTypeByName(rootMethodId);
        List<SceneEntity> sceneEntities = new ArrayList<>();
        //增加判断，确定是否需要IndicatorTree类型，根据indicatorTreeId匹配
//        if (StringUtils.isNotBlank(indicatorTreeId)) {
//            sceneEntities = sceneService.findBySIdAndInicatorTreeId(sid, indicatorTreeId);
//        } else {
//            sceneEntities = sceneService.findBySchemeId(sid);
//        }
        sceneEntities = sceneService.findBySchemeId(sid);
        List<IndicatorValuesWithNameDTO> indicatorValuesWithNameDTOList = indicatorValueService.findWithNameBySidIndicatorTreeId(sid, indicatorTreeId);
        Map<String,List<IndicatorValuesWithNameDTO>> indicatorValuesWithNameMap = indicatorValuesWithNameDTOList.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getSceneId));
        for (SceneEntity sceneEntity : sceneEntities) {
            Map<String, Object> resultUnit = new HashMap<>();
            List<IndicatorValuesWithNameDTO> indicatorValuesWithNameDTOS = indicatorValuesWithNameMap.get(sceneEntity.getId());

            resultUnit.put("sceneId", sceneEntity.getId());
            resultUnit.put("sceneName", sceneEntity.getName());
            resultUnit.put("indicatorResult", indicatorValuesWithNameDTOS.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getLevel)));
            result.add(resultUnit);
        }
        return result;
    }

    private IndicatorValuesWithNameResultDTO getIndicatorValuesWithNameDTO(String sid, String sceneId, String indicatorId,
                                                                           IndicatorValuesWithNameResultDTO dto, EvaluationType typeByName,
                                                                           Map<String, List<IndicatorStandardVO>> indicatorStandardLabelsMap, List<CalcorEntity> calcorEntities,
                                                                           Map<String, String> enNameMap, Map<String, String> chNameMap, Set<String> defaultModuleIds) {
        Optional<SchemaEntity> schemaEntityOptional = Optional.ofNullable(schemaService.getById(sid));
        if (typeByName == EvaluationType.NONE && schemaEntityOptional.get().getSchemaEvaluationType() == SchemaEvaluateType.ABILITY_EVALUATE.id) {
            return dto;
        }
        List<String> indicatorIds = ArrayUtils.stringToList(indicatorId);
        String targetIds = sceneId;
        if (typeByName == EvaluationType.PRINCIPAL || typeByName == EvaluationType.CLUSTER) {
            targetIds = "";
        }

        //targetIds不为空则样本数量大于一
        // todo 此处获取的指标未过滤
        List<String> sceneIdList = StringUtils.isBlank(targetIds) ? sceneService.findIdsBySid(sid) : ArrayUtils.stringToList(targetIds);
        List<IndicatorValuesWithNameDTO> schemaIndicatorValuesBoList = indicatorValueService.findWithNameBySidIndicatorIdSceneId(sid, indicatorIds, sceneIdList);
        List<IndicatorValuesWithNameDTO> indicatorValuesBoList = schemaIndicatorValuesBoList.stream().filter(v -> v.getSceneId().equals(sceneId)).collect(Collectors.toList());
        List<IndicatorValuesWithNameDTO> indicatorValuesPidBoList = schemaIndicatorValuesBoList.stream().filter(v -> v.getPid().equals("0")).sorted((o1, o2) -> {
            double value1 = Double.parseDouble(o1.getNormalizedValue());
            double value2 = Double.parseDouble(o2.getNormalizedValue());
            if (value1 > value2) {
                return 1;
            } else if (value1 < value2) {
                return -1;
            }
            return 0;
        }).collect(Collectors.toList());
        Map<String, List<IndicatorValuesWithNameDTO>> glist = indicatorValuesBoList.stream().collect(Collectors
                .groupingBy(IndicatorValuesWithNameDTO::getPid));
        List<IndicatorValuesWithNameDTO> expectResult = Lists.newArrayListWithExpectedSize(indicatorValuesBoList.size());
        // 避免重复列明,加上表别名前缀
//        List<SceneTableEntity> sceneTableEntities = sceneTableService.find(SceneTableEntity.builder().sid(sid).build());

        Map<String, List<CalcorEntity>> calcorIndicatorMap = Maps.newHashMap();
        if (!CollUtil.isEmpty(calcorEntities)) {
            calcorIndicatorMap = calcorEntities.stream().collect(Collectors.groupingBy(CalcorEntity::getIndicatorId));
        }
        List<CalCorValuesEntity> calcorValuesEntities = calcorValuesService.list(new QueryWrapper<CalCorValuesEntity>().eq("SCENE_ID", sceneId));
        Map<String, List<CalCorValuesEntity>> calcorValuesMaps = Maps.newHashMap();
        if (!CollUtil.isEmpty(calcorValuesEntities)) {
            calcorValuesMaps = calcorValuesEntities.stream().collect(Collectors.groupingBy(CalCorValuesEntity::getIndicatorId));
        }
        // 拼接中文名字
        for (IndicatorValuesWithNameDTO indicatorValuesWithNameDTO : indicatorValuesBoList) {
            String childIndicatorId = indicatorValuesWithNameDTO.getIndicatorId();
            List<IndicatorStandardVO> data = indicatorStandardLabelsMap.get(childIndicatorId);
            if (CollUtil.isNotEmpty(data)) {
                for (IndicatorStandardVO vo : data) {
                    if (!StringUtils.isEmpty(indicatorValuesWithNameDTO.getStandardLabelName()) && indicatorValuesWithNameDTO.getStandardLabelName().equals(vo.getLableName()) && vo.getFlag() != 2) {
                        indicatorValuesWithNameDTO.setRange(String.format("%s - %s", vo.getMin(), vo.getMax()));
                    }
                }
            }
//            List<IndicatorValuesWithNameBO> parent = new ArrayList<>();
            Map<String, Object> protoData = Maps.newHashMap();
            // 查找指标所有DB算子名称以及值
            List<CalcorEntity> calEntities = calcorIndicatorMap.get(indicatorValuesWithNameDTO.getIndicatorId());
            indicatorValuesWithNameDTO.setProtoData(protoData);
//            parent.add(f);
            expectResult.add(indicatorValuesWithNameDTO);
            if (CollUtil.isEmpty(calEntities)) {
                continue;
            }
            calEntities = calEntities.stream().filter(c -> c.getColName() != null && StringUtils.isNotEmpty(c.getColName())
                    && !c.getColName().equals("NULL") && !c.getPid().equals("0")).collect(Collectors.toList());
            List<CalCorValuesEntity> calValuesEntities = calcorValuesMaps.get(indicatorValuesWithNameDTO.getIndicatorId());
            Map<String, Object> valueMap = Maps.newHashMap();

            if (!CollUtil.isEmpty(calValuesEntities)) {
                calValuesEntities.forEach(c -> valueMap.put(c.getCalcorId(), c.getValue()));
            }
            calEntities.forEach(c -> {
                if (!defaultModuleIds.contains(c.getCalcModuleId())) {
                    return;
                }
                Object value = valueMap.getOrDefault(c.getId(), 0);
                String colChName = null;
                String colName = c.getColName();
                if (c.getTaskType() != null && c.getTaskType() == DataAnalysisType.CUSTOM_DATA.getId()) {
                    colChName = enNameMap.get(c.getAliasId()) + "---" + colName;
                } else {
                    String alias = c.getAliasId() + c.getColName();
                    colChName = chNameMap.get(alias);
                    if (colChName == null) {
                        colChName = enNameMap.getOrDefault(c.getAliasId(), c.getAliasId()) + "---" + colName;
                    }
                }
                protoData.put(colChName, value);
            });


        }

        dto.setDataList(expectResult);

        Map<String, String> sortMap = Maps.newLinkedHashMap();
        indicatorValuesPidBoList.forEach(b -> sortMap.put(b.getSceneName(), b.getNormalizedValue()));
        dto.setSceneSortMap(sortMap);
        return dto;
    }


    private void loadDataForResult(String sid, Map<String, List<IndicatorStandardVO>> indicatorStandardLabelsMap, List<CalcorEntity> calcorEntities,
                                   Map<String, String> enNameMap, Map<String, String> chNameMap, Set<String> defaultModuleIds){
        indicatorStandardLabelsMap = standardLabelsService.findBySid(sid);
        calcorEntities = calcorService.list(Wrappers.lambdaQuery(CalcorEntity.class).eq(CalcorEntity::getSid,sid));

        //获取当前方案的所有默认计算模型id
        List<CalcModuleEntity> bySidAndDefaultCalModule = calcModuleService.list(new QueryWrapper<CalcModuleEntity>().eq("SID", sid)
                .eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id));
        if (CollUtil.isNotEmpty(bySidAndDefaultCalModule)) {
            defaultModuleIds.addAll(bySidAndDefaultCalModule.stream().map(CalcModuleEntity::getId).collect(Collectors.toSet()));
        }

        List<SceneTableEntity> sceneTableEntities = sceneTableService.list(new QueryWrapper<SceneTableEntity>().eq("SID", sid));
        Set<String> tableNames = Sets.newHashSet();
        for (SceneTableEntity tableEntity : sceneTableEntities) {
            tableNames.add(tableEntity.getTableName());
        }
        Map<String, List<TableChNameEntity>> nameEntitiesMap = dataTableChNameService.selectBySceneTable(tableNames);
        sceneTableEntities.forEach(s -> {
            // 英文名
            enNameMap.put(s.getAliasId(), s.getTableName());
            // 中文名
            List<TableChNameEntity> nameEntities = nameEntitiesMap.get(s.getTableName());
            if (CollUtil.isEmpty(nameEntities)) {
                return;
            }
            Map<String, String> tabCnName = Maps.newHashMap();
            nameEntities.forEach(n -> {
                if (n.getFlag() == 1 && StringUtils.isNotEmpty(n.getChName())) {
                    tabCnName.put(n.getTableName(), n.getChName());
                }
            });
            nameEntities.forEach(n -> {
                int flag = n.getFlag();
                String aliasKey;
                String cnName = n.getChName();
                if (cnName == null) {
                    cnName = "";
                }
                if (flag == 1) {
                    aliasKey = s.getAliasId();
                    cnName = cnName.equals("") ? n.getTableName() : cnName;
                } else {
                    aliasKey = s.getAliasId() + n.getFieldName();
                    cnName = cnName.equals("") ? n.getFieldName() : cnName;
                    cnName = tabCnName.getOrDefault(n.getTableName(), n.getTableName()) + "---" + cnName;
                }
                chNameMap.put(aliasKey, cnName);
            });
        });
    }

    /**
     * 查询指标计算结果
     *
     * @param sid         查询参数
     * @param sceneId     查询参数
     * @param indicatorTreeId 查询参数
     */
    @Override
    public Map<Integer,List<IndicatorValuesWithNameDTO>> findIndicatorResult(String sid, String sceneId, String indicatorTreeId) {
        IndicatorValuesWithNameResultDTO dto = new IndicatorValuesWithNameResultDTO();
        Optional<SchemaEntity> optional = Optional.ofNullable(schemaService.getById(sid));
        if (!optional.isPresent()) {
            return new HashMap<>();
        }
        SchemaEntity schemaEntity = optional.get();
        //获得当前pg任务的pg算法
        EvaluationType typeByName = EvaluationType.getTypeByName(schemaEntity.getEvaluationMethod());

        List<IndicatorValuesWithNameDTO> indicatorValuesWithNameDTOList = indicatorValueService.findWithNameBySidIndicatorTreeIdSceneId(sid,indicatorTreeId, sceneId);
        return indicatorValuesWithNameDTOList.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getLevel));
    }

    /**
     * 查询指标计算结果(ByTreeId)
     *
     * @param treeId 指标树ID
     */
    @Override
    public IndicatorTreeResultDTO findIndicatorResultByTreeId(String treeId) {
//        Optional<IndicatorTreeEntity> indicatorTreeEntityOptional = indicatorTreeRepository.findById(treeId);
        Optional<IndicatorTreeEntity> indicatorTreeEntityOptional = Optional.ofNullable(indicatorTreeService.getById(treeId));
        if (!indicatorTreeEntityOptional.isPresent()) {
            return new IndicatorTreeResultDTO();
        }
        IndicatorTreeEntity entity = indicatorTreeEntityOptional.get();
        String sid = entity.getSid();
        String treeName = entity.getName();
        List<SampleResultDTO> sampleList = Lists.newArrayList();
        IndicatorTreeResultDTO treeResultDTO = IndicatorTreeResultDTO.valueOf(treeId, treeName, sampleList);
        // indicator value group
        List<IndicatorValuesEntity> indicatorValuesEntities = indicatorValueService.findBySid(sid);
        Map<String, List<IndicatorValuesEntity>> valueGroupBySceneMap = indicatorValuesEntities.stream().collect(
                Collectors.groupingBy(IndicatorValuesEntity::getSceneId));
        // indicator standard group
//        List<IndicatorStandardEntity> indicatorStandardEntities = indicatorStandardService.find(IndicatorStandardEntity.builder().sid(sid).build());
        List<IndicatorStandardEntity> indicatorStandardEntities = indicatorStandardService.list(new QueryWrapper<IndicatorStandardEntity>().eq("SID", sid));
        Map<String, List<IndicatorStandardEntity>> standardGroupByIndicatorMap = indicatorStandardEntities.stream().collect(
                Collectors.groupingBy(IndicatorStandardEntity::getIndicatorId));
        List<StandardLabelsEntity> standardLabelsEntityList = standardLabelsService.evaluationCriteriaLevel(sid);
        Map<String, StandardLabelsEntity> standardLabelGroupByIndicatorMap = Maps.newHashMap();
        standardLabelsEntityList.forEach(s -> standardLabelGroupByIndicatorMap.put(s.getId(), s));
        // all indicator
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
        Map<String, List<ProtoDataResultDTO>> indicatorProtoDataMap = getProtoDataByIndicators(sid, indicatorEntities);
        Map<String, List<IndicatorEntity>> indicatorGroupBySceneMap = indicatorEntities.stream().collect(
                Collectors.groupingBy(IndicatorEntity::getPid));
        List<IndicatorEntity> rootList = indicatorGroupBySceneMap.get("0");
        if (CollectionUtils.isEmpty(rootList)) {
            return treeResultDTO;
        }
        IndicatorEntity rootEntity = rootList.get(0);
        List<IndicatorResultDTO> indicator = Lists.newArrayList(transForIndicatorResultDTO(rootEntity, indicatorGroupBySceneMap,
                standardGroupByIndicatorMap, standardLabelGroupByIndicatorMap, indicatorProtoDataMap));
        // all sceneEntity
//        List<SceneEntity> sceneEntityList = sceneService.find(SceneEntity.builder().sid(sid).build());
        List<SceneEntity> sceneEntityList = sceneService.list(new QueryWrapper<SceneEntity>().eq("SID", sid));
        for (SceneEntity sceneEntity : sceneEntityList) {
            String sceneId = sceneEntity.getId();
            String sceneName = sceneEntity.getName();
            List<TaskConditionResultDTO> taskCondition = Lists.newArrayList();
            List<IndicatorResultDTO> sceneIndicatorList = Lists.newArrayList(indicator);
            SampleResultDTO sampleResultDTO = SampleResultDTO.valueOf(sceneId, sceneName, taskCondition, sceneIndicatorList);
            sampleList.add(sampleResultDTO);
            // process task condition
            // todo 此处得改
            Map<String, String> sampleTaskMap = sceneEntity.getSampleTaskMap();
            for (Map.Entry<String, String> entry : sampleTaskMap.entrySet()) {
                String dSid = entry.getKey();
                String taskId = entry.getValue();
                Optional<DatasrcEntity> dataSrcEntityOptional = datasrcService.findById(dSid);
                if (!dataSrcEntityOptional.isPresent()) {
                    continue;
                }
                Integer type = dataSrcEntityOptional.get().getTaskType();
//                CfgTaskConditionEntity cfgTaskConditionEntity = cfgTaskConditionRepository.findByTaskTypeAndDataSrcId(dSid, taskId);
                CfgTaskConditionEntity cfgTaskConditionEntity = cfgTaskConditionService.getOne(new QueryWrapper<CfgTaskConditionEntity>().eq("DATA_SRC_ID", dSid).eq("TASK_ID", taskId));
                List<TaskConditionInfoDTO> conditionInfoDTOList = new ArrayList<>();
                if (cfgTaskConditionEntity != null) {
                    conditionInfoDTOList = cfgTaskConditionEntity.getTaskConditionList();
                }

                if (CollectionUtils.isEmpty(conditionInfoDTOList)) {
                    continue;
                }
                taskCondition.addAll(conditionInfoDTOList.stream().filter(c -> !CollectionUtils.isEmpty(c.getDto())).map(c -> {
                    Object value = c.getDto().get(0).getRationClassify();
                    return TaskConditionResultDTO.valueOf(c.getName(), value);
                }).collect(Collectors.toList()));
                break;
            }
            // indicator data replace
            List<IndicatorValuesEntity> indicatorValuesEntityList = valueGroupBySceneMap.get(sceneId);
            if (!CollectionUtils.isEmpty(indicatorValuesEntityList)) {
                Map<String, List<IndicatorValuesEntity>> indicatorValuesEntityMap = indicatorValuesEntityList.stream().collect(Collectors
                        .groupingBy(IndicatorValuesEntity::getIndicatorId));
                for (IndicatorResultDTO indicatorResultDTO : sceneIndicatorList) {
                    replaceIndicatorData(sceneId, indicatorResultDTO, indicatorValuesEntityMap);
                }
            }
        }
        return treeResultDTO;
    }

    /**
     * 多个指标树id查询指标计算结果(ByTreeId)
     *
     * @param treeIds 指标树ID
     */
    @Override
    public List<IndicatorTreeResultDTO> findIndicatorResultByTreeIds(String[] treeIds) {
        if (null == treeIds) {
            throw new BusinessException("指标树id不能为空");
        }
        List<IndicatorTreeResultDTO> indicatorTreeResultDTOS = new ArrayList<>();
        for (String treeId : treeIds) {
            IndicatorTreeResultDTO indicatorResultByTreeId = findIndicatorResultByTreeId(treeId);
            indicatorTreeResultDTOS.add(indicatorResultByTreeId);
        }
        return indicatorTreeResultDTOS;
    }

    private IndicatorResultDTO transForIndicatorResultDTO(IndicatorEntity currIndicator, Map<String, List<IndicatorEntity>> indicatorPidMap,
                                                          Map<String, List<IndicatorStandardEntity>> standardGroupByIndicatorMap,
                                                          Map<String, StandardLabelsEntity> standardLabelGroupByIndicatorMap,
                                                          Map<String, List<ProtoDataResultDTO>> indicatorProtoDataMap) {
        IndicatorResultDTO indicatorResult = new IndicatorResultDTO();
        List<IndicatorStandardEntity> indicatorStandardEntities = standardGroupByIndicatorMap.get(currIndicator.getId());
        List<String> standardNameList = Collections.emptyList();
        List<String> minList = Collections.emptyList();
        List<String> maxList = Collections.emptyList();
        if (!CollectionUtils.isEmpty(indicatorStandardEntities)) {
            standardNameList = indicatorStandardEntities.stream().map(e -> {
                StandardLabelsEntity standardLabelsEntity = standardLabelGroupByIndicatorMap.get(e.getLabelId());
                if (standardLabelsEntity == null) {
                    return "";
                }
                return standardLabelsEntity.getName();
            }).collect(Collectors.toList());
            minList = indicatorStandardEntities.stream().map(IndicatorStandardEntity::getMin).collect(Collectors.toList());
            maxList = indicatorStandardEntities.stream().map(IndicatorStandardEntity::getMax).collect(Collectors.toList());
        }
        StandardResultDTO nameStandard = StandardResultDTO.valueOf("标准名称", standardNameList);
        StandardResultDTO minStandard = StandardResultDTO.valueOf("最小值", minList);
        StandardResultDTO maxStandard = StandardResultDTO.valueOf("最大值", maxList);
        List<StandardResultDTO> bzCore = Lists.newArrayList(nameStandard, minStandard, maxStandard);
        List<IndicatorResultDTO> children = Lists.newArrayList();
        List<ProtoDataResultDTO> protoDataList = indicatorProtoDataMap.getOrDefault(currIndicator.getId(), Collections.emptyList());
        indicatorResult.init(currIndicator, bzCore, children, protoDataList);
        List<IndicatorEntity> childrenIndicators = indicatorPidMap.get(currIndicator.getId());
        if (CollectionUtils.isEmpty(childrenIndicators)) {
            return indicatorResult;
        }
        for (IndicatorEntity childrenEntity : childrenIndicators) {
            IndicatorResultDTO childrenResultDTO = transForIndicatorResultDTO(childrenEntity, indicatorPidMap,
                    standardGroupByIndicatorMap, standardLabelGroupByIndicatorMap, indicatorProtoDataMap);
            children.add(childrenResultDTO);
        }
        return indicatorResult;
    }

    private Map<String, List<ProtoDataResultDTO>> getProtoDataByIndicators(String sid, List<IndicatorEntity> indicatorEntities) {
        Map<String, List<ProtoDataResultDTO>> protoDataMap = Maps.newHashMap();
        Map<String, String> tableCnNameCache = Maps.newHashMap();
        Map<String, String> fieldCnNameCache = Maps.newHashMap();
        /** {key:aliasId, value:SceneTableEntity} **/
        Map<String, SceneTableEntity> sceneTableAliasMap = Maps.newHashMap();
        List<SceneTableEntity> sceneTableEntities = sceneTableService.find(SceneTableEntity.builder().sid(sid).build());
        sceneTableEntities.forEach(s -> sceneTableAliasMap.put(s.getAliasId(), s));
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            List<CalcorEntity> calEntities = calcorService.find(CalcorEntity.builder().indicatorId(indicatorEntity.getId()).build());
            calEntities = calEntities.stream().filter(c -> c.getColName() != null && StringUtils.isNotEmpty(c.getColName())
                    && !c.getColName().equals("NULL")).collect(Collectors.toList());
            List<ProtoDataResultDTO> protoDataList = calEntities.stream().filter(cal -> sceneTableAliasMap.containsKey(cal.getAliasId())).map(cal -> {
                String aliasId = cal.getAliasId();
                SceneTableEntity sceneTableEntity = sceneTableAliasMap.get(aliasId);
                String tableEnName = sceneTableEntity.getTableName();
                String fieldEnName = cal.getColName();
                if (!tableCnNameCache.containsKey(tableEnName)) {
                    List<TableChNameEntity> tableChNameEntities = dataTableChNameService.viewByName(tableEnName);
                    tableChNameEntities.forEach(t -> {
                        String cnName = t.getChName();
                        if (t.getFlag() == 1) {
                            if (StringUtils.isEmpty(cnName)) {
                                cnName = tableEnName;
                            }
                            tableCnNameCache.put(tableEnName, cnName);
                        } else {
                            if (StringUtils.isEmpty(cnName)) {
                                cnName = t.getFieldName();
                            }
                            fieldCnNameCache.put(aliasId + t.getFieldName(), cnName);
                        }
                    });
                }
                String tableCnName = tableCnNameCache.getOrDefault(tableEnName, tableEnName);
                String fieldCnName = fieldCnNameCache.getOrDefault(aliasId + fieldEnName, fieldEnName);
                return ProtoDataResultDTO.valueOf(aliasId, tableEnName, tableCnName, fieldEnName, fieldCnName);
            }).collect(Collectors.toList());
            protoDataMap.put(indicatorEntity.getId(), protoDataList);
        }
        return protoDataMap;
    }

    private void replaceIndicatorData(String sceneId, IndicatorResultDTO indicatorResultDTO, Map<String, List<IndicatorValuesEntity>> indicatorValuesEntityMap) {
        List<IndicatorValuesEntity> indicatorValuesEntities = indicatorValuesEntityMap.get(indicatorResultDTO.getId());
        if (CollectionUtils.isEmpty(indicatorValuesEntities)) {
            return;
        }
        IndicatorValuesEntity indicatorValuesEntity = indicatorValuesEntities.get(0);
        indicatorResultDTO.setLevel(indicatorValuesEntity.getStandardLabelName());
        indicatorResultDTO.setFraction(indicatorValuesEntity.getNormalizedValue());
//        List<CalcorEntity> calEntities = calcorService.find(CalcorEntity.builder().indicatorId(indicatorResultDTO.getId()).build());
        List<CalcorEntity> calEntities = calcorService.list(new QueryWrapper<CalcorEntity>().eq("indicator_id", indicatorResultDTO.getId()));
        if (!CollectionUtils.isEmpty(calEntities)) {
            Map<String, Object> sceneCalEntityValueMap = Maps.newHashMap();
            calEntities = calEntities.stream().filter(c -> c.getColName() != null && StringUtils.isNotEmpty(c.getColName())
                    && !c.getColName().equals("NULL")).collect(Collectors.toList());
//            List<CalCorValuesEntity> calValuesEntities = calcorValuesService.find(CalCorValuesEntity.builder().indicatorId(indicatorResultDTO.getId())
//                    .sceneId(sceneId).build());
            List<CalCorValuesEntity> calValuesEntities = calcorValuesService.list(new QueryWrapper<CalCorValuesEntity>()
                    .eq("indicator_id", indicatorResultDTO.getId()).eq("scene_id", sceneId));
            Map<String, Object> calEntityValueMap = Maps.newHashMap();
            calValuesEntities.forEach(c -> calEntityValueMap.put(c.getCalcorId(), c.getValue()));
            calEntities.forEach(c -> {
                String aliasKey = c.getAliasId() + c.getColName();
                Object value = calEntityValueMap.getOrDefault(c.getId(), 0);
                sceneCalEntityValueMap.put(aliasKey, value);
            });
            for (ProtoDataResultDTO protoData : indicatorResultDTO.getProtoData()) {
                String aliasKey = protoData.getTabAliasId() + protoData.getFieldEnName();
                protoData.setData(sceneCalEntityValueMap.getOrDefault(aliasKey, 0));
            }
        }
        for (IndicatorResultDTO children : indicatorResultDTO.getChildren()) {
            replaceIndicatorData(sceneId, children, indicatorValuesEntityMap);
        }
    }

    /**
     * 查询指标计算结果(ById)
     *
     * @param sceneId     样本ID
     * @param indicatorId 指标ID
     */
    @Override
    public List<ProtoDataResultDTO> findIndicatorProtoDataById(String sceneId, String indicatorId) {
        //查询出对应的一层指标信息
        Optional<IndicatorEntity> indicatorEntityOptional = indicatorService.findById(indicatorId);
        if (!indicatorEntityOptional.isPresent()) {
            return Collections.emptyList();
        }
        //通过查询出来的指标信息对应的SID(方案id)查询具体的指标树
        IndicatorEntity indicatorEntity = indicatorEntityOptional.get();
        IndicatorTreeEntity entity = IndicatorTreeEntity.builder().sid(indicatorEntity.getSid()).build();
//        Optional<IndicatorTreeEntity> indicatorTreeEntityOptional = indicatorTreeRepository.findOne(Example.of(entity));
        Optional<IndicatorTreeEntity> indicatorTreeEntityOptional = Optional.ofNullable(indicatorTreeService.getById(indicatorEntity.getTreeId()));
        if (!indicatorTreeEntityOptional.isPresent()) {
            return Collections.emptyList();
        }
        IndicatorTreeResultDTO indicatorTreeResultDTO = findIndicatorResultByTreeId(indicatorTreeEntityOptional.get().getId());
        for (SampleResultDTO sampleResultDTO : indicatorTreeResultDTO.getSample()) {
            if (!sampleResultDTO.getSampleId().equals(sceneId)) {
                continue;
            }
            Map<String, IndicatorResultDTO> indicatorResultDTOMap = Maps.newHashMap();
            for (IndicatorResultDTO resultDTO : sampleResultDTO.getIndicator()) {
                indicatorResultGroupById(resultDTO, indicatorResultDTOMap);
            }
            IndicatorResultDTO targetIndicatorResultDTO = indicatorResultDTOMap.get(indicatorId);
            if (targetIndicatorResultDTO == null) {
                return Collections.emptyList();
            }
            return targetIndicatorResultDTO.getProtoData();
        }
        return Collections.emptyList();
    }

    private void indicatorResultGroupById(IndicatorResultDTO resultDTO, Map<String, IndicatorResultDTO> indicatorResultDTOMap) {
        indicatorResultDTOMap.put(resultDTO.getId(), resultDTO);
        for (IndicatorResultDTO children : resultDTO.getChildren()) {
            indicatorResultGroupById(children, indicatorResultDTOMap);
        }
    }

    /**
     * @param parent       父指标值节点集合
     * @param expectResult 收集期望结果集合
     * @param gList        通过pid分组的指标值map
     * @Author 刘乐法
     * @Description 递归获取parent节点的下一层子节点，并添加到期望集合
     * @Return void
     * @Date 2021/2/7 10:50
     */
    private void findNextIndicatorResult(List<IndicatorValuesWithNameDTO> parent, List<IndicatorValuesWithNameDTO> expectResult,
                                         Map<String, List<IndicatorValuesWithNameDTO>> gList) {
        List<IndicatorValuesWithNameDTO> nextParent = new ArrayList<>();

        List<IndicatorValuesWithNameDTO> child;
        for (IndicatorValuesWithNameDTO indicatorValuesBo : parent) {
            child = gList.get(indicatorValuesBo.getIndicatorId());
            if (CollectionUtils.isEmpty(child)) {
                return;
            } else {
                child.stream()
                        .filter(f -> indicatorValuesBo.getSceneId().equals(f.getSceneId()))
                        .forEach(f -> {
                            expectResult.add(f);
                            nextParent.add(f);
                        });
                findNextIndicatorResult(nextParent, expectResult, gList);
            }
        }
    }

    /******************************************************************************/

    /**
     * 获取算法模型中每个算子计算结果
     * @param moduleId 计算模型id
     * @param sceneIds 样本集合
     * @return
     */
    @Override
    public List<CalcorValuesVO> findCalcorResult(String moduleId, String sceneIds) {
//        Optional<CalcModuleEntity> calcModuleEntity = calcModuleRepository.findById(moduleId);
        Optional<CalcModuleEntity> calcModuleEntity = Optional.ofNullable(calcModuleService.getById(moduleId));
        if (!calcModuleEntity.isPresent()) {
            return Lists.newArrayList();
        }
//        Optional<SceneEntity> sceneEntityOptional = sceneService.findById(sceneIds);
        Optional<SceneEntity> sceneEntityOptional = Optional.ofNullable(sceneService.getById(sceneIds));
        SceneEntity sceneEntity = sceneEntityOptional.get();
        CalcModuleEntity moduleEntity = calcModuleEntity.get();
        if (moduleEntity.getCalcType() == ModuleType.VISUALIZATION_ALGORITHM_MODEL.id) {
            List<IndicatorValuesEntity> indicatorValuesEntities = indicatorValueService.findBySidSceneId(moduleEntity.getSid(), sceneIds);
            if (CollUtil.isEmpty(indicatorValuesEntities)) {
                return Lists.newArrayList();
            }
            for (IndicatorValuesEntity valuesEntity : indicatorValuesEntities) {
                if (valuesEntity.getIndicatorId().equals(moduleEntity.getIndicatorId())) {
                    CalcorValuesVO calcorValuesVO = new CalcorValuesVO();
                    calcorValuesVO.setSceneId(sceneIds);
                    calcorValuesVO.setValue(valuesEntity.getNormalizedValue());
                    calcorValuesVO.setIndicatorId(valuesEntity.getIndicatorId());
                    calcorValuesVO.setSceneName(sceneEntity.getName());
                    return Lists.newArrayList(calcorValuesVO);
                }
            }
        }

        List<CalcorValuesVO> calcorValuesVoList = calcorValuesService.findByModuleIdAndSceneId(moduleId, ArrayUtils.stringToList(sceneIds));

        Map<String, List<CalcorValuesVO>> gList = calcorValuesVoList.stream().collect(Collectors.groupingBy(CalcorValuesVO::getPid));

        List<CalcorValuesVO> expectResult = Lists.newArrayListWithExpectedSize(calcorValuesVoList.size());

        //根据pid id 关系排序，从pid开始
        calcorValuesVoList.stream()
                .filter(f -> "0".equals(f.getPid()))
                .forEach(f -> {
                    List<CalcorValuesVO> parent = new ArrayList<>();
                    parent.add(f);
                    expectResult.add(f);
                    findChildNextCalcor(parent, expectResult, gList);
                });

        return expectResult;
    }

    /**
     * 递归获取parent节点的下一层子节点，并添加到期望集合
     * @param parent       父算子节点集合
     * @param expectResult 收集期望结果集合
     * @param gList        通过pid分组的算子map
     * @Author 刘乐法
     * @Description 递归获取parent节点的下一层子节点，并添加到期望集合
     * @Return void
     * @Date 2021/1/27 13:53
     */
    private void findChildNextCalcor(List<CalcorValuesVO> parent, List<CalcorValuesVO> expectResult, Map<String, List<CalcorValuesVO>> gList) {
        List<CalcorValuesVO> nextParent = new ArrayList<>();

        List<CalcorValuesVO> child;
        for (CalcorValuesVO calcorValuesVo : parent) {
            child = gList.get(calcorValuesVo.getCalcorId());
            if (CollectionUtils.isEmpty(child)) {
                return;
            } else {
                child.stream()
                        .filter(f -> calcorValuesVo.getSceneId().equals(f.getSceneId()))
                        .forEach(f -> {
                            expectResult.add(f);
                            nextParent.add(f);
                        });
                findChildNextCalcor(nextParent, expectResult, gList);
            }
        }

    }

    /**
     * 查询计算页面树
     */
    @Override
    public List<CalculationTreeVO> findTree() {
        List<CalculationTreeVO> rootList = getTaskData();

        List<CalculationTreeVO> bodyListView = calcorTreeViewMapper.findAll();
        List<CalculationTreeVO> bodyListNode = getNodeVo();
        List<CalculationTreeVO> bodyList = Lists.newArrayListWithExpectedSize(bodyListView.size() + bodyListNode.size());
        bodyList.addAll(bodyListView);
        bodyList.addAll(bodyListNode);

        //生成树形结构
        TreeToolUtils treeToolUtils = new TreeToolUtils(rootList, bodyList);
        List<CalculationTreeVO> list = treeToolUtils.getTree();
        return list;
    }



    /**
     * 查询pg概况
     *
     * @param sceneId 样本ID
     * @param sid     方案ID
     */
    @Override
    public EvaluateOverviewVO findEvaluateOverview(String sceneId, String sid) {

//        List<IndicatorEntity> indicatorEntityList = indicatorService.find(IndicatorEntity.builder().pid("0").sid(sid).build());
        List<IndicatorEntity> indicatorEntityList = indicatorService.list(new QueryWrapper<IndicatorEntity>().eq("SID", sid).eq("PID", "0"));
        if (CollectionUtils.isEmpty(indicatorEntityList)) {
            return new EvaluateOverviewVO();
        }

        IndicatorEntity indicatorRoot = indicatorEntityList.get(0);

//        List<EvaluateAlgEntity> evaluateAlgEntityList = evaluateAlgService.find(EvaluateAlgEntity.builder()
//                .sid(sid).build());
        List<EvaluateAlgEntity> evaluateAlgEntityList = evaluateAlgService.list(new QueryWrapper<EvaluateAlgEntity>().eq("SID", sid));


        String evaAlgName = CollectionUtils.isEmpty(evaluateAlgEntityList) ? "" : evaluateAlgEntityList.get(0).getName();

//        List<IndicatorValuesEntity> indicatorValuesEntityList = indicatorValueService.find(IndicatorValuesEntity.builder()
//                .indicatorId(indicatorRoot.getId()).sceneId(sceneId).build());
        List<IndicatorValuesEntity> indicatorValuesEntityList = indicatorValueService.list(new QueryWrapper<IndicatorValuesEntity>()
                .eq("indicator_id", indicatorRoot.getId()).eq("SCENE_ID", sceneId));


        String value = CollectionUtils.isEmpty(indicatorValuesEntityList) ? "" : indicatorValuesEntityList.get(0).getNormalizedValue();

//        List<IndicatorStandardEntity> indicatorStandardEntityList = indicatorStandardService
//                .find(IndicatorStandardEntity.builder().indicatorId(indicatorRoot.getId()).build());
        List<IndicatorStandardEntity> indicatorStandardEntityList = indicatorStandardService.list(new QueryWrapper<IndicatorStandardEntity>()
                .eq("indicator_id", indicatorRoot.getId()));


        String levelId = null;
        String standName = null;
        if (!CollectionUtils.isEmpty(indicatorStandardEntityList) && StringUtils.isNotBlank(value)) {
            double vD = Double.parseDouble(value);
            for (IndicatorStandardEntity indicatorStandardEntity : indicatorStandardEntityList) {
                double minD = Double.parseDouble(indicatorStandardEntity.getMin());
                double maxD = Double.parseDouble(indicatorStandardEntity.getMax());
                if (minD <= vD && maxD > vD) {
                    levelId = indicatorStandardEntity.getLabelId();
                    break;
                }
            }
        }
        if (StringUtils.isNotBlank(levelId)) {
            StandardLabelsEntity standardLabelsEntity = labelsService.findById(levelId).orElse(new StandardLabelsEntity());
            standName = standardLabelsEntity.getName();
        }

        EvaluateOverviewVO evaluateOverviewVo = new EvaluateOverviewVO();
        evaluateOverviewVo.setEvaAlgName(evaAlgName);
        evaluateOverviewVo.setIndicatorIdRoot(indicatorRoot.getId());
        evaluateOverviewVo.setIndicatorName(indicatorRoot.getName());
        evaluateOverviewVo.setIndicatorValue(value);
        evaluateOverviewVo.setSceneId(sceneId);
        evaluateOverviewVo.setStandName(standName);
        return evaluateOverviewVo;
    }

    /**
     * 查询单个算子计算结果
     *
     * @param sceneId     样本ID
     * @param calcorId    算子ID
     * @param indicatorId 指标ID
     */
    @Override
    public String findCalcorValue(String sceneId, String calcorId, String indicatorId) {

//        List<CalCorValuesEntity> calCorValuesEntityList = calcorValuesService.find(CalCorValuesEntity.builder()
//                .calcorId(calcorId)
//                .indicatorId(indicatorId)
//                .sceneId(sceneId).build());
        //默认查第一个样本的结果
//        if(StrUtil.isBlank(sceneId)){
//            sceneId = getOneSceneId(indicatorId);
//        }
        List<IndicatorValuesEntity> indicatorValuesEntities = indicatorValueService.list(new LambdaQueryWrapper<IndicatorValuesEntity>()
                .eq(IndicatorValuesEntity::getIndicatorId, indicatorId)
                .eq(StrUtil.isNotBlank(sceneId), IndicatorValuesEntity::getSceneId, sceneId));

        if (CollUtil.isEmpty(indicatorValuesEntities)) {
            throw new BusinessException("算子结果为空");
        }
        Optional<Double> value = indicatorValuesEntities.stream()
                .filter(f -> StrUtil.isNotBlank(f.getNormalizedValue()) && NumberUtil.isNumber(f.getNormalizedValue()))
                .map(item -> Double.parseDouble(item.getNormalizedValue())).max(Comparator.naturalOrder());
        return value.map(String::valueOf).orElse(null);
    }

    /**
     * 计算状态
     *
     * @param sid          方案ID
     * @param sceneId      样本ID
     * @param calcModuleId
     */
    @Override
    public List<CalculateTypeVO> calculateType(String sid, String sceneId, String calcModuleId) {
        List<CalculateTypeVO> results = Lists.newArrayList();
        // 根据方案id获取指标树信息
//        Optional<CalcModuleEntity> calcModuleEntityOptional = calcModuleRepository.findById(calcModuleId);
        Optional<CalcModuleEntity> calcModuleEntityOptional = Optional.ofNullable(calcModuleService.getById(calcModuleId));
        if (!calcModuleEntityOptional.isPresent()) {
            log.error("There is no indicator information for the current task");
            return Lists.newArrayList();
        }
        String indicatorId = calcModuleEntityOptional.get().getIndicatorId();
//        Optional<IndicatorEntity> indicatorEntityOptional = indicatorService.findById(indicatorId);
        Optional<IndicatorEntity> indicatorEntityOptional = Optional.ofNullable(indicatorService.getById(indicatorId));
        if (!indicatorEntityOptional.isPresent()) {
            return Lists.newArrayList();
        }

        String treeId = indicatorEntityOptional.get().getTreeId();
        Map<String, String> indicatorMap = Maps.newHashMap();
        //根据指标树id获取所有指标项集合
//        List<IndicatorEntity> entityList = indicatorService.find(IndicatorEntity.builder().treeId(treeId).build());
        List<IndicatorEntity> entityList = indicatorService.list(new QueryWrapper<IndicatorEntity>().eq("TREE_ID", treeId));
        for (IndicatorEntity s : entityList) {
            indicatorMap.put(s.getId(), s.getName());
        }
        Map<String, List<IndicatorEntity>> collect = entityList.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));

        Set<String> indicatorIds = Sets.newLinkedHashSet();
        indicatorIds.add(indicatorId);
        getIndicatorIds(indicatorIds, indicatorId, collect);
//        List<IndicatorValuesEntity> valuesEntities = indicatorValueService.find(IndicatorValuesEntity.builder().sceneId(sceneId).build());
        List<IndicatorValuesEntity> valuesEntities = indicatorValueService.list(new QueryWrapper<IndicatorValuesEntity>().eq("SCENE_ID", sceneId));
        if (CollUtil.isEmpty(valuesEntities)) {
            //设置为准备状态
            for (String indicator : indicatorIds) {
                results.add(CalculateTypeVO.valueOf(indicator, indicatorMap.get(indicatorId)));
            }
        } else {
            for (IndicatorValuesEntity s1 : valuesEntities) {
                if (indicatorIds.contains(s1.getIndicatorId())) {
                    results.add(CalculateTypeVO.valueOf(indicatorMap.get(s1.getIndicatorId()), s1.getIndicatorId(), s1.getNormalizedValue()));
                }
            }
        }
        return results;
    }

    private void getIndicatorIds(Set<String> indicatorIds, String indicatorId, Map<String, List<IndicatorEntity>> collect) {
        List<IndicatorEntity> indicatorEntities = collect.get(indicatorId);
        if (CollUtil.isNotEmpty(indicatorEntities)) {
            for (IndicatorEntity entity : indicatorEntities) {
                indicatorIds.add(entity.getId());
                getIndicatorIds(indicatorIds, entity.getId(), collect);
            }
        }

    }


    /**
     * 查询某一列数据
     *
     * @param aliasId 别名ID
     * @param colName 字段名
     */
    @Override
    public List<Object> findColData(String aliasId, String colName, String sceneId) {
//        List<SceneTableEntity> sceneTableEntityList = sceneTableService.find(SceneTableEntity.builder().aliasId(aliasId).build());
        List<SceneTableEntity> sceneTableEntityList = sceneTableService.list(new QueryWrapper<SceneTableEntity>().eq("alias_id", aliasId));
        if (CollectionUtils.isEmpty(sceneTableEntityList)) {
            throw new BusinessException("关联性检查失败");
        }
        SceneTableEntity sceneTableEntity = sceneTableEntityList.get(0);
        SceneEntity sceneEntity = null;
        if (StringUtils.isEmpty(sceneId)) {
//            List<SceneEntity> sceneEntities = sceneService.find(SceneEntity.builder().sid(sceneTableEntity.getSid()).build());
            List<SceneEntity> sceneEntities = sceneService.list(new QueryWrapper<SceneEntity>().eq("SID", sceneTableEntity.getSid()));
            if (CollUtil.isEmpty(sceneEntities)) {
                throw new BusinessException("关联性检查失败");
            }
            sceneEntity = sceneEntities.get(0);
        } else {
            Optional<SceneEntity> optionalSceneEntity = sceneService.findById(sceneId);

            if (!optionalSceneEntity.isPresent()) {
                throw new BusinessException("关联性检查失败");
            }
            sceneEntity = optionalSceneEntity.get();
        }
        // todo 调整位置
        Map<String, String> taskMap = sceneEntity.getSampleTaskMap();
        SceneTypeEnum sceneTypeEnum = sceneEntity.getSceneType() == 0 ? SceneTypeEnum.DEFAULT : SceneTypeEnum.SNAPSHOT;
        DatasrcEntity datasrcEntity = datasrcService.findById(sceneTableEntity.getDsid()).orElse(null);

        if (datasrcEntity == null) {
            throw new BusinessException("关联性检查失败");
        }
        List<Object> resultDataList = Lists.newArrayList();
        try {
            Object result = simpleFrameworkController.obtainData(colName, sceneTableEntity.getTableName(), sceneTableEntity.getDbName(), taskMap.get(sceneTableEntity.getDsid()), datasrcEntity, sceneTypeEnum);
            if (result instanceof List) {
                resultDataList.addAll((Collection<?>) result);
            } else {
                resultDataList.add(result);
            }
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
        return resultDataList;
    }

    List<CalculationTreeVO> getNodeVo() {
        List<SchemaEntity> schemaEntityList = schemaService.find(SchemaEntity.builder().build());
        List<CalculationTreeVO> bodeNode = new ArrayList<>();
        schemaEntityList.forEach(f -> {
            CalculationTreeVO modelNode = new CalculationTreeVO();
            modelNode.setType(TreeTypeEnum.NODE_MODEL.toString());
            modelNode.setName(TreeTypeEnum.NODE_MODEL.getName());
            modelNode.setPid(f.getId());
            modelNode.setNodeId(TreeTypeEnum.NODE_MODEL.toString() + f.getId());
            modelNode.setSort(1);
            CalculationTreeVO resultNode = new CalculationTreeVO();
            resultNode.setType(TreeTypeEnum.NODE_RESULT.toString());
            resultNode.setName(TreeTypeEnum.NODE_RESULT.getName());
            resultNode.setPid(f.getId());
            resultNode.setNodeId(TreeTypeEnum.NODE_RESULT.toString() + f.getId());
            resultNode.setSort(2);
            bodeNode.add(modelNode);
            bodeNode.add(resultNode);
        });
        return bodeNode;
    }

    /**
     * @Author 刘乐法
     * @Description 获取所有任务vo
     * @Return java.util.List<com.yw.module.calculation.domain.vo.CalculationTreeVO>
     * @Date 2021/1/25 13:49
     */
    List<CalculationTreeVO> getTaskData() {
        List<TaskEntity> taskEntityList = taskService.find(TaskEntity.builder().build());
        return taskEntityList.stream().map(m -> {
            CalculationTreeVO vo = new CalculationTreeVO();
            vo.setName(m.getTaskName());
            vo.setPid("0");
            vo.setNodeId(m.getId());
            vo.setType(CalculationTreeEnum.TASK.toString());
            vo.setId(m.getId());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 获取子指标中指标算法选择字段的表名称
     *
     * @param indicatorId 指标ID
     */
    @Override
    public Map<String, String> showTableByIndicator(String indicatorId) {
        Map<String, String> result = Maps.newHashMap();
        Set<String> tables = Sets.newHashSet();
        if (StringUtils.isEmpty(indicatorId)) {
            throw new BusinessException("指标信息不存在");
        }
        //根据指标id获取指标算法信息
//        List<CalcorEntity> calcorInfo = calcorService.find(CalcorEntity.builder().indicatorId(indicatorId).build());
        List<CalcorEntity> calcorInfo = calcorService.list(new QueryWrapper<CalcorEntity>().eq("indicator_id", indicatorId));
        if (CollUtil.isEmpty(calcorInfo)) {
            return result;
        }
        //过滤后的主信息
        calcorInfo = calcorInfo.stream().filter(s ->
                s.getAliasId() != null
        ).collect(Collectors.toList());
        for (CalcorEntity calcorEntity : calcorInfo) {
            String aliasId = calcorEntity.getAliasId(); //获取表名称
//            Optional<SchemaAliasEntity> schemaAliasInfo = iSchemaAliasRepository.findById(aliasId);
            Optional<SchemaAliasEntity> schemaAliasInfo = Optional.ofNullable(schemaAliasEntityMapper.selectById(aliasId));
            if (!schemaAliasInfo.isPresent()) {
                continue;
            }
            SchemaAliasEntity entity = schemaAliasInfo.get();
            tables.add(entity.getAlias());
        }
        // 获取所有表中文信息
        List<TableChNameEntity> chNameEntityList = dataTableChNameService.showTableChName(1);
        Map<String, String> tableChName = Maps.newHashMap();
        for (TableChNameEntity chName : chNameEntityList) {
            tableChName.put(chName.getTableName(), chName.getChName());
        }
        for (String table : tables) {
            result.put(table, tableChName.get(table));
        }
        return result;
    }

    /**
     * 获取子指标中指标算法选择字段的表信息
     *
     * @param tableName 表名
     * @param offSet    开始页
     * @param limit     页大小
     * @param sceneId   样本ID
     */
    @Override
    public String showTableInfoByIndicator(String tableName, int offSet, int limit, String sceneId) {
        if (StringUtils.isEmpty(sceneId)) {
            return "";
        }
        if (StringUtils.isEmpty(tableName)) {
            throw new BusinessException("表名称不能为null");
        }
        Optional<SceneEntity> sceneEntityOptional = sceneService.findById(sceneId);
        if (!sceneEntityOptional.isPresent()) {
            throw new BusinessException("数据源信息获取失败");
        }
        SceneEntity sceneEntity = sceneEntityOptional.get();
        TableInfoVO tableInfoVO = null;

//        List<SceneTableEntity> tableEntityList = sceneTableService.find(SceneTableEntity.builder().sid(sceneEntity.getSid()).tableName(tableName).build());
        List<SceneTableEntity> tableEntityList = sceneTableService.list(new QueryWrapper<SceneTableEntity>()
                .eq("SID", sceneEntity.getSid()).eq("table_name", tableName));
        if (CollUtil.isEmpty(tableEntityList)) {
            return "";
        }
        SceneTableEntity sceneTableEntity = tableEntityList.get(0);
        String key = sceneTableEntity.getDsid();
        com.yw.source.common.enums.DataAnalysisType analysisType = datasrcService.selectById(key);
        if (analysisType == com.yw.source.common.enums.DataAnalysisType.NONE) {
            return "";
        }
        if (analysisType == com.yw.source.common.enums.DataAnalysisType.CUSTOM_DATA) {
            // 尝试从自定义数据结果获取
            tableInfoVO = customDataService.getExecuteResult(tableName, offSet, limit);
            return JSON.toJSONString(tableInfoVO);
        } else {
            ExerciseInfoDTO dto = ExerciseInfoDTO.valueOf(tableName, key, offSet, limit);
            try {
                tableInfoVO = dataSrcManagerService.tableNameDefineField(dto);
                return JSON.toJSONString(tableInfoVO);
            } catch (Exception e) {
                log.error("error message: {}", e.getMessage());
            }
        }
        return "";

    }

    @Override
    public ADCMatrixVO adcMatrixInfoV2(String sid, String sceneId, String indicatorId) {
        ADCMatrixVO vo = ADCMatrixVO.valueOf();
        if (StringUtils.isEmpty(sceneId) || StringUtils.isEmpty(indicatorId)) {

            return vo;
        }
//        AlgorithmResultEntity resultEntity = iAlgorithmResultRepository.findBySceneId(sceneId);
        AlgorithmResultEntity resultEntity = algorithmResultMapper.selectOne(new QueryWrapper<AlgorithmResultEntity>().eq("SCENE_ID", sceneId));
        if (resultEntity != null) {
            String aMatrix = resultEntity.getAMatrix();
            String cMatrix = resultEntity.getCMatrix();
            String dMatrix = resultEntity.getDMatrix();
            Map<String, String> tableFieldMap = Maps.newHashMap();
//            Optional<CalcModuleEntity> moduleRepositoryOne = calcModuleRepository.findOne(Example.of(CalcModuleEntity.builder()
//                    .indicatorId(indicatorId).defaultCalModule(CalModuleType.DEFAULT_MODEL.id)
//                    .calcType(ModuleType.METHOD_MODEL.id).build()));
            Optional<CalcModuleEntity> moduleRepositoryOne = Optional.ofNullable(calcModuleService.getOne(new QueryWrapper<CalcModuleEntity>().eq("indicator_id", indicatorId).eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id)
                    .eq("CALC_TYPE", ModuleType.METHOD_MODEL.id)));
            if (!moduleRepositoryOne.isPresent()) {
                return vo;
            }
            //设置表头
//            List<PartsStateEntity> stateEntities = iPartsStateRepository.findBySidOrderByCreateTimeDesc(moduleRepositoryOne.get().getId());
            List<PartsStateEntity> stateEntities = partsStateMapper.selectList(new QueryWrapper<PartsStateEntity>().eq("SID", moduleRepositoryOne.get().getId()).orderByDesc("CREATE_TIME"));
            extracted(vo, aMatrix, cMatrix, dMatrix, tableFieldMap, stateEntities);
        }
        return vo;
    }

    @Override
    public List<ComparativeAnalysisVO> comparativeAnalysisResult(List<String> schemaIds) {
        List<ComparativeAnalysisVO> result = Lists.newArrayList();
        //获取方案名称
        List<SchemaEntity> schemaEntityOptional = schemaService.findListByIds(schemaIds);

        if (CollUtil.isEmpty(schemaEntityOptional)) {
            return result;
        }

        for (SchemaEntity schemaEntity : schemaEntityOptional) {
            String schemaId = schemaEntity.getId();
            String schemaName = schemaEntity.getName();
            String assessObject = schemaEntity.getAssessObject();
            List<String> equipmentNames = new ArrayList<>();
//            List<SceneEntity> sceneEntities = sceneService.find(SceneEntity.builder().sid(schemaId).build());
            List<SceneEntity> sceneEntities = sceneService.list(new QueryWrapper<SceneEntity>().eq("SID", schemaId).orderByAsc("sortno"));
            if (CollUtil.isEmpty(sceneEntities)) {
                return result;
            }
            //获取pg对象名称
            if (StringUtils.isNotEmpty(assessObject)) {
                if (assessObject.contains("equ")) {
                    String substring = assessObject.substring(3, assessObject.length());
//                    Optional<EquipmentEntity> equ = equipmentService.findById(Integer.valueOf(substring));
                    Optional<EquipmentEntity> equ = Optional.ofNullable(equipmentService.getById(Integer.valueOf(substring)));
                    if (equ.isPresent()) {
                        equipmentNames.add(equ.get().getEquName());
                    }
                } else {
//                    Optional<EquipmentSystemEntity> eSys = equipmentSystemService.
//                            findById(Integer.valueOf(assessObject.substring(4)));
                    Optional<EquipmentSystemEntity> eSys = Optional.ofNullable(equipmentSystemService.getById(Integer.valueOf(assessObject.substring(4))));
                    if (eSys.isPresent()) {
                        List<Integer> equipmentIds = JSONArray.parseArray(eSys.get().getComposition(), Integer.class);
                        //查询体系中所有装备名称
                        equipmentNames = equipmentService.listByIds(equipmentIds)
                                .stream().map(EquipmentEntity::getEquName).collect(Collectors.toList());
                    }
                }
            }
            for (int i = 0; i < sceneEntities.size(); i++) {
                SceneEntity sceneEntity = sceneEntities.get(i);
                SchemaResultVO schemaResultVO = schemaService.showTaskResult(schemaId, sceneEntity.getId());
                String resultName = schemaName + "-" + sceneEntity.getName();
                if (i == 0) {
                    result.add(ComparativeAnalysisVO.valueOf(schemaId, sceneEntity.getId(), resultName, schemaResultVO, sceneEntity.getName(), equipmentNames));
                    continue;
                }
                result.add(ComparativeAnalysisVO.valueOf(schemaId, sceneEntity.getId(), resultName, schemaResultVO, sceneEntity.getName()));
            }

        }
        return result;
    }

    private void extracted(ADCMatrixVO vo, String aMatrix, String cMatrix, String dMatrix, Map<String, String> tableFieldMap, List<PartsStateEntity> stateEntities) {
        for (PartsStateEntity stateEntity : stateEntities) {
            tableFieldMap.put(stateEntity.getId(), stateEntity.getStateName());
        }
        vo.setTableFieldMap(tableFieldMap);
        if (StringUtils.isNotEmpty(aMatrix)) {
            //操作a矩阵信息
            Map<String, BigDecimal> aMatrixMap = JSONObject.parseObject(aMatrix, Map.class);
            vo.setaMatrixMap(aMatrixMap);
        }
        if (StringUtils.isNotEmpty(cMatrix)) {
            //操作a矩阵信息
            Map<String, Map<String, BigDecimal>> cMatrixMap = JSONObject.parseObject(cMatrix, Map.class);
            vo.setcMatrixMap(cMatrixMap);

        }
        if (StringUtils.isNotEmpty(dMatrix)) {
            //操作a矩阵信息
            Map<String, Map<String, BigDecimal>> dMatrixMap = JSONObject.parseObject(dMatrix, Map.class);
            vo.setdMatrixMap(dMatrixMap);
        }
    }

    /**
     * ADC矩阵信息查询
     *
     * @param sid     方案ID
     * @param sceneId 样本ID
     */
    @Override
    public ADCMatrixVO adcMatrixInfo(String sid, String sceneId) {
        ADCMatrixVO vo = ADCMatrixVO.valueOf();
        if (StringUtils.isEmpty(sceneId) || StringUtils.isEmpty(sid)) {
            return vo;
        }
//        AlgorithmResultEntity resultEntity = iAlgorithmResultRepository.findBySceneId(sceneId);
        AlgorithmResultEntity resultEntity = algorithmResultMapper.selectOne(new QueryWrapper<AlgorithmResultEntity>().eq("SCENE_ID", sceneId));
        if (resultEntity != null) {
            String aMatrix = resultEntity.getAMatrix();
            String cMatrix = resultEntity.getCMatrix();
            String dMatrix = resultEntity.getDMatrix();
            Map<String, String> tableFieldMap = Maps.newHashMap();
            //设置表头
//            List<PartsStateEntity> stateEntities = iPartsStateRepository.findBySidOrderByCreateTimeDesc(sid);
            List<PartsStateEntity> stateEntities = partsStateMapper.selectList(new QueryWrapper<PartsStateEntity>().eq("SID", sid).orderByDesc("CREATE_TIME"));
            extracted(vo, aMatrix, cMatrix, dMatrix, tableFieldMap, stateEntities);
        }
        return vo;
    }

    private List<String> toEvaluationObjList(Map<String, Object> map, int evaluationObjType) {
        List<String> ret = new ArrayList<>();
        List<Integer> trunkList = (List<Integer>) map.get("trunkList");
        trunkList.stream().forEach(e -> {
            if (evaluationObjType > 2) {
                ret.add("equ" + e);
            } else {
                ret.add("eSys" + e);
            }
        });
        return ret;
    }


    private Map<String, String> getSameQuota(List<String> sameQuotaFieldKey) {
        //获取fieldKey的中文名称
        Map<String, String> ret = Maps.newLinkedHashMap();
        sameQuotaFieldKey.stream().forEach(s -> {
            if ("ROOT".equals(s) || "MIDDLE".equals(s) || "LEAF".equals(s)) {
                return;
            }
            Optional<IndicatorEntity> byId = indicatorService.findById(s);
            ret.put(s, byId.get().getName());
        });
        return ret;
    }

    private Integer toAnalysisTargetId(String evaluationObject) {
        Integer ret = -1;
        try {
            if (evaluationObject.startsWith("equ")) {
                ret = Integer.parseInt(evaluationObject.substring(3));
            } else {
                ret = Integer.parseInt(evaluationObject.substring(4));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    /**
     * 获得样本关联的仿真或演戏任务的id及中文名,把结果写到ret里面的对应字段
     *
     * @param lineData
     * @param sampleTaskMap
     */
    private void getFangzhenOrYanxiTaskChName(QuotaTaskConditionLineData lineData, Map<String, String> sampleTaskMap, List<String> sameTaskConditionIds) {
        //获取两个本地数据源类型
        List<DatasrcEntity> datasrcEntities = datasrcService.find(DatasrcEntity.builder().build());
        Map<String, Integer> analisysTaskType = Maps.newLinkedHashMap();
        //任务类型
        datasrcEntities.forEach(s -> {
            analisysTaskType.put(s.getId(), s.getTaskType());
        });
        Map<String, Object> ret = Maps.newLinkedHashMap();

        //获取所有任务
        sampleTaskMap.entrySet().forEach(s -> {
            String key = s.getKey(); // 数据源id
            String taskId = s.getValue(); //任务id
//            CfgTaskConditionEntity entity = cfgTaskConditionRepository.findByTaskTypeAndDataSrcId(key, taskId);
            CfgTaskConditionEntity entity = cfgTaskConditionService.getOne(new QueryWrapper<CfgTaskConditionEntity>().eq("TASK_ID", taskId)
                    .eq("DATA_SRC_ID", key));
            if (entity != null) {
                if (CollUtil.isEmpty(lineData.getTaskIdAndTaskNameMaps())) {
                    lineData.taskIdAndTaskNameMaps = Maps.newLinkedHashMap();
                }
                String taskName = entity.getTaskName();
                lineData.taskIdAndTaskNameMaps.put(taskId, taskName);
                entity.getTaskConditionList();
                //获取所有任务条件进行设置
                List<TaskConditionInfoDTO> taskConditionList = entity.getTaskConditionList();
                Map<String, String> taskConditionValue = Maps.newLinkedHashMap();
                taskConditionList.forEach(s2 -> {
                    List<TaskConditionDTO.BaseTypeDTO> dto = s2.getDto();
                    String value = String.valueOf(0);
                    if (CollUtil.isNotEmpty(dto)) {
                        value = dto.get(0).getCodeValue().toString();
                    }
                    taskConditionValue.put(s2.getId(), value);
                });
                if (sameTaskConditionIds != null && !sameTaskConditionIds.isEmpty()) {
                    for (String taskConId : sameTaskConditionIds) {
                        ret.put(taskConId, taskConditionValue.get(taskConId));
                    }
                }
            }
        });
        lineData.fuseData.putAll(ret);
    }

    /**
     * 体系贡献率计算
     *
     * @param competeSceneId  场景ID
     * @param missionTaskId   任务ID
     * @param sceneScenarioId 想定ID
     * @param sid             方案ID
     * @param indicatorId     指标ID
     * @param calcModuleId    算法ID
     */
    @Override
    public void contributionRateCalculated(String competeSceneId, String missionTaskId, String sceneScenarioId, String sid, String indicatorId, String calcModuleId) {

        List<String> sidList = Lists.newArrayList();
        // 准备好计算数据
        if (!StringUtils.isEmpty(missionTaskId)) {
            sidList.add(missionTaskId);
        } else {
            CompeteSceneEntity competeSceneEntity = competeSceneService.selectById(competeSceneId);
            if (Objects.isNull(competeSceneEntity) || CollUtil.isEmpty(competeSceneEntity.getTaskIdLists())) {
                return;
            }
            sidList.addAll(competeSceneEntity.getTaskIdLists());
        }
        List<String> sceneScenarioIds = Lists.newArrayList();
        if (StringUtils.isNotEmpty(sceneScenarioId)) {
            sceneScenarioIds.add(sceneScenarioId);
        } else {
            PageInfo<SceneScenarioEntity> scenarioEntityPageInfo = sceneScenarioService.showSceneScenario(competeSceneId, null, 0, 0);
            List<SceneScenarioEntity> entityPageInfoList = scenarioEntityPageInfo.getList();
            if (CollUtil.isEmpty(entityPageInfoList)) {
                throw new BusinessException("请先创建想定");
            }
            for (SceneScenarioEntity entity : entityPageInfoList) {
                sceneScenarioIds.add(entity.getId());
            }
        }
        Map<String, List<IndicatorCriteria>> criteriaServiceClassifyData = iIndicatorCriteriaService.getClassifyDataBySceneId(competeSceneId, missionTaskId);
        //准备计算数据
        final List<AllDataPrepareDTO> allDataPrepareDTOMap = dataPrepareService.prepareContributionRateCalculated(competeSceneId, sid, sidList, sceneScenarioIds, indicatorId, calcModuleId);
        allDataPrepareDTOMap.forEach(bo -> {
            //获取pg任务下算法类别
            String alg = bo.getEvaluateALG();
            EvaluationType type = EvaluationType.getTypeByName(alg);
            SchemaEntity schemaEntity = bo.getSchemaEntity();
            if (type == EvaluationType.NONE && schemaEntity.getSchemaEvaluationType() != SchemaEvaluateType.EFFICACY_EVALUATE.id) {
                throw new BusinessException("没有设置综合pg方法");
            }
            Collection<SceneEntity> values = bo.getSceneEntityMapById().values();
            //主成分分析法 聚类分析 的样本数量必须大于一
            boolean typeFlag = type == EvaluationType.PRINCIPAL || type == EvaluationType.CLUSTER;
            if (typeFlag && values.size() <= 1) {
                throw new BusinessException("方案样本数量不满足脚本算法需求");
            }
            List<String> sceneIds = Lists.newArrayList();
            for (SceneEntity sceneEntity : values) {
                sceneIds.add(sceneEntity.getId());
            }
            if (!values.isEmpty()) {
                values.forEach(s -> {
                    // 标记 是否为最后一个指标
                    //开始计算
                    doContributionRateCalculate(competeSceneId, bo.getMissionTaskId(), bo.getSceneScenarioId(), sid,
                            s.getId(), indicatorId, bo.prepareEvaluationCalculationBoV3(s.getId()), sceneIds,
                            criteriaServiceClassifyData);
                });
            }
        });
    }

    private void doContributionRateCalculate(String competeSceneId, String missionTaskId, String sceneScenarioId, String sid,
                                             String sceneId, String indicatorId, EvaluationCalculationDTO bo, List<String> sceneIds,
                                             Map<String, List<IndicatorCriteria>> criteriaServiceClassifyData) {
        try {
            log.info("**************开始计算{}场景******************", bo.getSceneEntity().getName());
            bo = doEvaluationCalculateV2(bo.getCurrentIndicator().getId(), bo, true, sceneIds, null);
            //更新数据库关联信息
            if (!CollectionUtils.isEmpty(bo.getIndicatorValuesEntityListNeedUpdate())) {
                //更新数据库指标值
                List<IndicatorValuesEntity> indicatorValuesEntities = bo.getIndicatorValuesEntityListNeedUpdate();
                List<ContributionRateResultEntity> contributionRateResultEntityList = Lists.newArrayList();
                List<String> indicatorIds = Lists.newArrayList();
                for (IndicatorValuesEntity entity : indicatorValuesEntities) {
                    String normalizedValue = entity.getNormalizedValue();
                    String labelName = "未设置任务映射信息";
                    List<IndicatorCriteria> indicatorCriteria = criteriaServiceClassifyData.get(missionTaskId + "_" + entity.getIndicatorId());
                    if (CollUtil.isNotEmpty(indicatorCriteria)) {
                        boolean flag = false;
                        double value = Double.parseDouble(normalizedValue);
                        for (IndicatorCriteria criteria : indicatorCriteria) {
                            if (null == criteria.getMinValue() && null == criteria.getMaxValue()) {
                                continue;
                            } else if (StringUtils.equals(criteria.getTaskId(), missionTaskId) &&
                                    //防止criteria.getSceneId().equals报空指针
                                    competeSceneId.equals(criteria.getSceneId()) &&
                                    (
                                            (StringUtils.isNotEmpty(criteria.getMaxValue()) && StringUtils.isNotEmpty(criteria.getMinValue()) && value >= Double.parseDouble(criteria.getMinValue())) ||
                                            (StringUtils.isNotEmpty(criteria.getMaxValue()) && StringUtils.isNotEmpty(criteria.getMinValue()) && value <= Double.parseDouble(criteria.getMaxValue())) ||
                                            (StringUtils.isNotEmpty(criteria.getMaxValue()) && StringUtils.isNotEmpty(criteria.getMinValue()) && value >= Double.parseDouble(criteria.getMinValue()) && value <= Double.parseDouble(criteria.getMaxValue()))
                                    )) {
                                labelName = criteria.getTypeCode();
                                break;
                            }
                            flag = true;
                        }
                        if (flag && StringUtils.equals("未设置任务映射信息", labelName)) {
                            labelName = "超出任务映射范围";
                        }
                    }
                    ContributionRateResultEntity rateResultEntity = getContributionRateResultEntity(competeSceneId, missionTaskId, sceneScenarioId, entity, normalizedValue, labelName);
                    indicatorIds.add(entity.getIndicatorId());
                    // 设置standard label
                    contributionRateResultEntityList.add(rateResultEntity);
                }
                contributionRateResultService.deleteContributionRateResult(competeSceneId, missionTaskId, sceneScenarioId, indicatorIds);
                contributionRateResultService.saveContributionRateResult(contributionRateResultEntityList);
            }
//            if (!CollectionUtils.isEmpty(bo.getCalcorValuesNeedUpdate())) {
//                //根据样本id和指标id更新关联信息
//                calcorValuesService.deleteBySceneIdIndicatorId(sceneId, indicatorId);
//                calcorValuesService.insertAll(bo.getCalcorValuesNeedUpdate());
//            }
            bo = null;
        } catch (BusinessException e) {
            log.warn(e.getMessage());
            String errMsg = e.getMessage();
//            String str = errorMsgParse(bo.getSceneEntity().getName(), bo.getCurrentIndicator().getName(), errMsg);
            throw e;
        } catch (Exception ee) {
            ee.printStackTrace();
            log.warn(ee.getMessage());
            String errMsg = ee.getMessage();
//            String str = errorMsgParse(bo.getSceneEntity().getName(), bo.getCurrentIndicator().getName(), errMsg);
            throw new BusinessException(errMsg);
        }
    }


    private ContributionRateResultEntity getContributionRateResultEntity(String competeSceneId, String missionTaskId, String sceneScenarioId, IndicatorValuesEntity entity, String normalizedValue, String labelName) {
        ContributionRateResultEntity rateResultEntity = ContributionRateResultEntity.valueOf(competeSceneId, missionTaskId, sceneScenarioId);
        rateResultEntity.setIndicatorId(entity.getIndicatorId());
        rateResultEntity.setIndicatorResult(normalizedValue);
        rateResultEntity.setIndicatorId(entity.getIndicatorId());
        rateResultEntity.setMissionTaskId(missionTaskId);
        rateResultEntity.setSceneScenarioId(sceneScenarioId);
        rateResultEntity.setId(UUIDGenerator.getUUID());
        rateResultEntity.setStandardLabelName(labelName);
        return rateResultEntity;
    }

    /**
     * @param schemaIds       被对比的方案   其他全部方案的ID
     * @param currentSchemaId 当前方案
     * @param indicator       选择的指标    指标
     * @param sceneId         当前方案的样本  样本
     * @return
     */
    @Override
    public Map<String, List<EfficiencyIndicatorValueVo>> efficiencyCurrentValue(List<String> schemaIds, String currentSchemaId, List<String> indicator, String sceneId) {
        // 所有指标的结果值
        List<IndicatorValuesEntity> indicatorValuesEntityList = indicatorValueService.findByIndicatorIdIn(indicator);

        //分组 按样本分组
        Map<String, List<IndicatorValuesEntity>> sceneIndicatorValue = indicatorValuesEntityList.stream().collect(Collectors.groupingBy(IndicatorValuesEntity::getSceneId));

        //通过方案ID获取他下面的样本：默认取第一个
        // key schemaId value sceneId
        Map<String, String> schemaSceneMap = sceneService.findBySidIn(schemaIds);
        // 分好组的数据 取获取每个方案下对应样本的选择的指标结果
        // 指标值可能是数组 求个平均值丢出去
        Map<String, List<EfficiencyIndicatorValueVo>> result = Maps.newHashMap();
        schemaSceneMap.forEach((k, v) -> {
            populateValue(sceneIndicatorValue, result, k, v);
        });
        // 当前方案的样本
        populateValue(sceneIndicatorValue, result, currentSchemaId, sceneId);
        return result;
    }

    private void populateValue(Map<String, List<IndicatorValuesEntity>> sceneIndicatorValue, Map<String, List<EfficiencyIndicatorValueVo>> result, String k, String v) {
        List<EfficiencyIndicatorValueVo> vo = Lists.newArrayList();
        List<IndicatorValuesEntity> indicatorValuesEntities = sceneIndicatorValue.get(v);
        if (CollUtil.isEmpty(indicatorValuesEntities)) {
            return;
        }

        for (IndicatorValuesEntity valuesEntity : indicatorValuesEntities) {
            String normalizedValue = valuesEntity.getNormalizedValue();
            try {
                /**
                 *
                 * if (normalizedValue.contains(",")) {
                 *     List<String> list = Arrays.asList(normalizedValue.split(","));
                 *     for (int i = 0; i < list.size(); i++) {
                 *         value.put(i + 1 + "", list.get(i));
                 *     }
                 * }
                 * */
                // 求均值
                if (normalizedValue.contains(",")) {
                    List<String> list = Arrays.asList(normalizedValue.split(","));
                    normalizedValue = list.stream().mapToDouble(Double::parseDouble).average().getAsDouble() + "";
                }
            } catch (Exception e) {
                // 不能不需要求均值
            }
            vo.add(new EfficiencyIndicatorValueVo(valuesEntity.getIndicatorId(), null, normalizedValue));
        }
        result.put(k, vo);
    }

    @Override
    public Map<String, List<EfficiencyIndicatorValueVo>> efficiencyCurrentIndicator(List<String> schemaIds, String currentSchemaId) {
        // 求每个方案的叶子指标返回出去就可以了
        // 获取所有的指标 判断当前指标有没有孩子指标 如果没有则是叶子指标
        // 根据pid分组 for 被查询方案的指标 判断是否包含key 不包含key就是叶子指标
        Map<String, List<EfficiencyIndicatorValueVo>> result = Maps.newHashMap();

        schemaIds.add(currentSchemaId);
        List<IndicatorEntity> indicatorEntities = indicatorService.findBySchemaIds(schemaIds);
        Map<String, List<IndicatorEntity>> collect = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getSid));
        for (String schemaId : schemaIds) {
            List<EfficiencyIndicatorValueVo> vo = Lists.newArrayList();
            List<IndicatorEntity> indicators = collect.get(schemaId);
            Map<String, List<IndicatorEntity>> map = indicators.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
            // pid key
            Set<String> strings = map.keySet();
            for (IndicatorEntity entity : indicators) {
                String id = entity.getId();
                if (strings.contains(id)) {
                    continue;
                }
                vo.add(new EfficiencyIndicatorValueVo(id, entity.getName(), null));
            }
        }
        return result;
    }

    /**
     * @param competeSceneId -- 场景ID    sceneScenarioId -- 情况ID     parentIndicatorId -- 父指标ID
     * @return com.yw.result.ApiResult
     * @author wangjianjun
     * @Description 根据场景ID、情况ID、父指标ID获取所有子指标的值
     * @date 2023/7/3 11:35
     */
    @Override
    public R querySonIndicatorsResult(String competeSceneId, String sceneScenarioId, String parentIndicatorId) {
        // 通过父指标ID获取所有子指标ID
        List<IndicatorEntity> indicatorEntityList = indicatorService.findByPid(parentIndicatorId);
        log.info("获取到子指标数量：{}", indicatorEntityList.size());
        if (CollectionUtils.isEmpty(indicatorEntityList)) {
            return R.fail("未获取到子指标");
        }
        List<String> indicatorIdList = indicatorEntityList.stream().map(IndicatorEntity::getId).collect(Collectors.toList());

        // 通过场景ID、情况ID、指标ID获取到计算结果值
        List<ContributionRateResultEntity> resultEntityList = contributionRateResultService.querySonIndicatorsResult(competeSceneId, sceneScenarioId, indicatorIdList);
        Map<String, String> indicatorMap = indicatorEntityList.stream().collect(Collectors.toMap(IndicatorEntity::getId, IndicatorEntity::getName));
        Map<String, List<String>> resultIdMap = resultEntityList.stream().collect(Collectors.groupingBy(ContributionRateResultEntity::getIndicatorId,
                Collectors.mapping(ContributionRateResultEntity::getIndicatorResult, Collectors.toList())));

        List<IndicatorResultVO> voList = new ArrayList<>();
        IndicatorResultVO indicatorResultVO = null;
        for (String key : resultIdMap.keySet()) {
            indicatorResultVO = new IndicatorResultVO();
            indicatorResultVO.setIndicatorName(indicatorMap.get(key));
            indicatorResultVO.setIndicatorResultList(resultIdMap.get(key));
            voList.add(indicatorResultVO);
        }

        return R.data(voList);
    }

    @Override
    public List<IndicatorValuesWithNameDTO> findIndicatorResultV4(String sid, String sceneId) {

        List<IndicatorValuesWithNameDTO> result = new ArrayList<>();

//        indicatorTreeService.find() 这边查出来是所有的指标树
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).pid("0").build());
        if (CollUtil.isEmpty(indicatorEntities)) {
            return result;
        }
        String rootIndicatorId = indicatorEntities.get(0).getId();

//        List<CalcModuleEntity> all = calcModuleRepository.findAll(Example.of(CalcModuleEntity.builder()
//                .indicatorId(rootIndicatorId).calcType(ModuleType.METHOD_MODEL.id)
//                .defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build()));
        List<CalcModuleEntity> all = calcModuleService.list(new QueryWrapper<CalcModuleEntity>().eq("indicator_id", rootIndicatorId)
                .eq("CALC_TYPE", ModuleType.METHOD_MODEL.id).eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id));
        if (CollUtil.isEmpty(all)) {
            return result;
        }
        String rootMethodId = all.get(0).getMethodId();

        //获得当前pg任务的pg算法
        EvaluationType typeByName = EvaluationType.getTypeByName(rootMethodId);
        Map<String, List<IndicatorStandardVO>> indicatorStandardLabelsMap = new HashMap<>();
        List<CalcorEntity> calcorEntities = new ArrayList<>();
        Map<String, String> enNameMap = Maps.newHashMap();
        Map<String, String> chNameMap = Maps.newHashMap();
        Set<String> defaultModuleIds = new HashSet<>();
        loadDataForResult(sid, indicatorStandardLabelsMap, calcorEntities, enNameMap, chNameMap, defaultModuleIds);

        IndicatorValuesWithNameResultDTO indicatorValuesWithNameResultDTO =
                getIndicatorValuesWithNameDTO(sid, sceneId, StringUtils.EMPTY, new IndicatorValuesWithNameResultDTO(), typeByName,indicatorStandardLabelsMap,calcorEntities,enNameMap,chNameMap, defaultModuleIds);

        return indicatorValuesWithNameResultDTO.getDataList();
    }

    @Override
    public List<IndicatorValuesWithNameDTO> findIndicatorResultV5(String sid, String sceneId, int indicatorTreeType) {

        List<IndicatorValuesWithNameDTO> result = new ArrayList<>();

        // todo 是否有多个同类型指标树
        List<IndicatorTreeEntity> treeEntities = indicatorTreeService.find(
                IndicatorTreeEntity.builder().sid(sid).indicatorTreeType(indicatorTreeType).build());
        if (CollUtil.isEmpty(treeEntities)) {
            return result;
        }
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().treeId(treeEntities.get(0).getId()).build());
        String rootIndicatorId = indicatorEntities.stream().filter(indicatorEntity -> indicatorEntity.getPid().equals("0")).findAny().get().getId();
        String[] indicators = indicatorEntities.stream().map(IndicatorEntity::getId).toArray(String[]::new);

//        List<CalcModuleEntity> all = calcModuleRepository.findAll(Example.of(CalcModuleEntity.builder().indicatorId(rootIndicatorId)
//                .calcType(ModuleType.METHOD_MODEL.id).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build()));
        List<CalcModuleEntity> all = calcModuleService.list(new QueryWrapper<CalcModuleEntity>().eq("indicator_id", rootIndicatorId)
                .eq("CALC_TYPE", ModuleType.METHOD_MODEL.id).eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id));
        if (CollUtil.isEmpty(all)) {
            return result;
        }
        String rootMethodId = all.get(0).getMethodId();

        //获得当前pg任务的pg算法
        EvaluationType typeByName = EvaluationType.getTypeByName(rootMethodId);
        Map<String, List<IndicatorStandardVO>> indicatorStandardLabelsMap = new HashMap<>();
        List<CalcorEntity> calcorEntities = new ArrayList<>();
        Map<String, String> enNameMap = Maps.newHashMap();
        Map<String, String> chNameMap = Maps.newHashMap();
        Set<String> defaultModuleIds = new HashSet<>();
        loadDataForResult(sid, indicatorStandardLabelsMap, calcorEntities, enNameMap, chNameMap, defaultModuleIds);

        IndicatorValuesWithNameResultDTO indicatorValuesWithNameResultDTO = getIndicatorValuesWithNameDTO(
                sid,
                sceneId,
                StringUtils.join(indicators, ","),
                new IndicatorValuesWithNameResultDTO(),
                typeByName,indicatorStandardLabelsMap,calcorEntities,enNameMap,chNameMap, defaultModuleIds);
        List<IndicatorValuesWithNameDTO> dataList = indicatorValuesWithNameResultDTO.getDataList();
        // 填充不存在的指标
        if (CollUtil.isNotEmpty(dataList)) {
            retry:
            for (IndicatorEntity indicatorEntity : indicatorEntities) {
                for (IndicatorValuesWithNameDTO indicatorValuesWithNameDTO : dataList) {
                    if (indicatorEntity.getId().equals(indicatorValuesWithNameDTO.getIndicatorId())) {
                        continue retry;
                    }
                }
                dataList.add(new IndicatorValuesWithNameDTO(sid, dataList.get(0).getSceneName(), indicatorEntity.getId(), indicatorEntity.getName(), sceneId, indicatorEntity.getPid(), indicatorEntity.getLevel()));
            }
        }
        return indicatorValuesWithNameResultDTO.getDataList();
    }

    private final String column = "column%s";

    @Override
    public MergeTablesVo getTable(String sid, String sceneId, int indicatorTreeType) throws Exception {
        IndicatorTreeEntity indicatorTreeEntity = indicatorTreeService.getOne(new LambdaQueryWrapper<IndicatorTreeEntity>()
                .eq(IndicatorTreeEntity::getSid,sid)
                .eq(IndicatorTreeEntity::getIndicatorTreeType,indicatorTreeType));
        IndicatorEntity indicatorEntity = indicatorService.getOne(new LambdaQueryWrapper<IndicatorEntity>()
                .eq(IndicatorEntity::getTreeId, indicatorTreeEntity.getId())
                .eq(IndicatorEntity::getPid,"0"));
        CalcModuleEntity calcModuleEntity = calcModuleService.getOne(new LambdaQueryWrapper<CalcModuleEntity>()
                .eq(CalcModuleEntity::getIndicatorId, indicatorEntity.getId())
                .eq(CalcModuleEntity::getDefaultCalModule, IntegerPool.ONE));

        // 构造数据
        MergeTablesVo vo = new MergeTablesVo();
        // key indicatorid value 指标数据
        List<IndicatorValuesWithNameDTO> indicatorValuesWithNameDTOS = findIndicatorResultV5(sid, sceneId, indicatorTreeType);
        if (ObjectUtils.isEmpty(indicatorValuesWithNameDTOS)) {
            throw new Exception("指标未计算,无法获取结果");
        }
        Map<String, IndicatorValuesWithNameDTO> indicatorValueMap = new HashMap<>();
        for (IndicatorValuesWithNameDTO indicatorValuesWithName : indicatorValuesWithNameDTOS) {
            if (indicatorValueMap.containsKey(indicatorValuesWithName.getIndicatorId())) {
                continue;
            }
            indicatorValueMap.put(indicatorValuesWithName.getIndicatorId(), indicatorValuesWithName);
        }
        Map<String, List<IndicatorValuesWithNameDTO>> pidIndicatorMap = indicatorValuesWithNameDTOS.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getPid));
        int i = 1;
        List<String> tableFixeds = Lists.newArrayList();
        Map<String /*columnKey*/, List<String>/*indicatorId*/> columnMap = Maps.newHashMap();
        if (!pidIndicatorMap.containsKey("0")) {
            throw new Exception("指标未计算,无法获取结果");
        }
        // 需要保留中间指标数据
        List<IndicatorValuesWithNameDTO> indicatorValuesWithNameDTOList = pidIndicatorMap.get("0");
        IndicatorValuesWithNameDTO indicatorValuesWithNameDTO = indicatorValuesWithNameDTOList.get(0);
        String columnKey = String.format(column, i);
        tableFixeds.add(columnKey);
        columnMap.put(columnKey, Lists.newArrayList(indicatorValuesWithNameDTO.getIndicatorId()));
        List<String> leafIndicators = Lists.newArrayList();
        columnValue(indicatorValuesWithNameDTO.getIndicatorId(), columnMap, ++i, tableFixeds, pidIndicatorMap, leafIndicators);
        List<FixedColumnVo> fixedColumn = vo.getFixedColumn();
        List<Map<String, Object>> listData = Lists.newArrayList();

        int index = 1;
        for (String tableFixed : tableFixeds) {
            if (CollUtil.isEmpty(fixedColumn)) {
                fixedColumn = Lists.newArrayList();
                vo.setFixedColumn(fixedColumn);
            }
            String name = "级指标";
            int flag = 0;
            if (index == tableFixeds.size()) {
                flag = 1;
            }

            FixedColumnVo fixedColumnVo = new FixedColumnVo(NumberChineseFormatter.format(index, false) + name, tableFixed);
            fixedColumnVo.setIsMerge(flag);
            fixedColumn.add(fixedColumnVo);
            index++;
        }

        if (EvaluationType.WEIGHT_NORM.getChName().equals(calcModuleEntity.getName())){
            fixedColumn.add(FixedColumnVo.valueOf("向量值", "vectorValue", 1));
        }
        fixedColumn.add(FixedColumnVo.valueOf("指标值", "value", 1));
        fixedColumn.add(FixedColumnVo.valueOf("指标评价", "standardName", 1));

        for (String leafIndicator : leafIndicators) {
            IndicatorValuesWithNameDTO entity = indicatorValueMap.get(leafIndicator);
            Map<String, Object> rowMap = new LinkedHashMap<>();
            resolverMap(rowMap, entity.getPid(), indicatorValueMap, columnMap, entity.getLevel(),calcModuleEntity.getName());
            rowMap.put(String.format(column, entity.getLevel() + 1), entity.getName());
            if (EvaluationType.WEIGHT_NORM.getChName().equals(calcModuleEntity.getName())){
                rowMap.put("vectorValue", entity.getUnNormalizedValue());
            }
            rowMap.put("value", entity.getNormalizedValue());
            rowMap.put("standardName", entity.getStandardLabelName());
            listData.add(rowMap);
        }
        vo.setListData(listData);
        return vo;
    }


    protected void columnValue(String indicatorId, Map<String /*columnKey*/, List<String>> columnMap, int key,
                               List<String> tableFixed, Map<String, List<IndicatorValuesWithNameDTO>> pidIndicatorMap, List<String> leafIndicators) {

        List<IndicatorValuesWithNameDTO> indicatorEntities = pidIndicatorMap.get(indicatorId);
        if (!CollUtil.isEmpty(indicatorEntities)) {
            String columnKey = String.format(column, key++);
            if (!tableFixed.contains(columnKey)) {
                tableFixed.add(columnKey);
            }
            for (IndicatorValuesWithNameDTO entity : indicatorEntities) {
                List<String> entityList = columnMap.getOrDefault(columnKey, new ArrayList<>());
                ;
                entityList.add(entity.getIndicatorId());
                columnMap.put(columnKey, entityList);
                columnValue(entity.getIndicatorId(), columnMap, key, tableFixed, pidIndicatorMap, leafIndicators);
            }
        } else {
            leafIndicators.add(indicatorId);
        }

    }

    private void resolverMap(Map<String, Object> rowMap, String pid, Map<String, IndicatorValuesWithNameDTO> indicatorValueMap, Map<String, List<String>> columnMap, int key, String modelName) {
        final String columnKey = String.format(column, key);
        List<String> indicatorKey = columnMap.get(columnKey);
        if (CollUtil.isNotEmpty(indicatorKey) && indicatorKey.contains(pid)) {
            IndicatorValuesWithNameDTO indicatorEntity = indicatorValueMap.get(pid);
            resolverMap(rowMap, indicatorEntity.getPid(), indicatorValueMap, columnMap, indicatorEntity.getLevel(), modelName);
        }
        if (pid.equals("0")) {
            return;
        }
        IndicatorValuesWithNameDTO entity = indicatorValueMap.get(pid);
        if (EvaluationType.WEIGHT_NORM.getChName().equals(modelName)){
            rowMap.put(String.format(column, indicatorValueMap.get(pid).getLevel() + 1), entity.getName() + " (指标值:" + entity.getNormalizedValue() + ",向量值" + entity.getUnNormalizedValue() + "," + (StringUtils.isNotEmpty(entity.getStandardLabelName()) ? entity.getStandardLabelName() : "暂未设置") + ") ");
        }else {
            rowMap.put(String.format(column, indicatorValueMap.get(pid).getLevel() + 1), entity.getName() + " (" + entity.getNormalizedValue() + "," + (StringUtils.isNotEmpty(entity.getStandardLabelName()) ? entity.getStandardLabelName() : "暂未设置") + ") ");
        }
    }

    private IndicatorValuesWithNameResultDTO findIndicatorMaxResult(String sid, String indicatorTreeId, List<String> sceneIds) {
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();


        int indicatorTreeType = indicatorTreeService.findById(indicatorTreeId).get().getIndicatorTreeType();

        List<IndicatorTreeEntity> treeEntities = indicatorTreeService.find(
                IndicatorTreeEntity.builder().sid(sid).indicatorTreeType(indicatorTreeType).build());
        if (CollUtil.isEmpty(treeEntities)) {
            return null;
        }
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().treeId(treeEntities.get(0).getId()).build());
        String rootIndicatorId = indicatorEntities.stream().filter(indicatorEntity -> indicatorEntity.getPid().equals("0")).findAny().get().getId();
        String[] indicators = indicatorEntities.stream().map(IndicatorEntity::getId).toArray(String[]::new);
        List<CalcModuleEntity> all = calcModuleService.list(new QueryWrapper<CalcModuleEntity>()
                .eq("indicator_id", rootIndicatorId).eq("CALC_TYPE", ModuleType.METHOD_MODEL.id)
                .eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id).orderByDesc("CREATE_TIME"));
        if (CollUtil.isEmpty(all)) {
            return null;
        }
        String rootMethodId = all.get(0).getMethodId();

        //获得当前pg任务的pg算法
        EvaluationType typeByName = EvaluationType.getTypeByName(rootMethodId);
        List<IndicatorValuesWithNameResultDTO> indicatorValuesWithNameResultDTOS = new ArrayList<>();
        Map<String, List<IndicatorStandardVO>> indicatorStandardLabelsMap = new HashMap<>();
        List<CalcorEntity> calcorEntities = new ArrayList<>();
        Map<String, String> enNameMap = Maps.newHashMap();
        Map<String, String> chNameMap = Maps.newHashMap();
        Set<String> defaultModuleIds = new HashSet<>();
        loadDataForResult(sid, indicatorStandardLabelsMap, calcorEntities, enNameMap, chNameMap, defaultModuleIds);
        for (String s : sceneIds) {
            IndicatorValuesWithNameResultDTO indicatorValuesWithNameResultDTO = getIndicatorValuesWithNameDTO(
                    sid,
                    s,
                    StringUtils.join(indicators, ","),
                    new IndicatorValuesWithNameResultDTO(),
                    typeByName,indicatorStandardLabelsMap,calcorEntities,enNameMap,chNameMap, defaultModuleIds);
            indicatorValuesWithNameResultDTOS.add(indicatorValuesWithNameResultDTO);
        }
        IndicatorValuesWithNameDTO indicatorValuesWithNameDTO = indicatorValuesWithNameResultDTOS.stream()
                .flatMap(dto -> dto.getDataList().stream())
                .filter(bo -> bo.getPid().equals("0"))
                .max(Comparator.comparing(IndicatorValuesWithNameDTO::getNormalizedValue))
                .get();
        for (IndicatorValuesWithNameResultDTO dto : indicatorValuesWithNameResultDTOS) {
            if (dto.getDataList().contains(indicatorValuesWithNameDTO)) {
                return dto;
            }
        }
        return null;
    }


    /**
     * 查询指标计算结果V6(对比分析：支持想定场景、试样样本、任务级别级别进行对比)
     *
     * @param schemeId        方案id
     * @param indicatorTreeId 指标树id
     * @param idList          任务id/想定id
     * @param level           层级 1 任务 2 想定
     * @return
     */
    @Override
    public List<Map<String, Object>> findIndicatorResultV6(String schemeId, String indicatorTreeId, List<String> idList, int level) {

        List<String> sceneIds;
        List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = new ArrayList<>();
        List<Map<String, Object>> mapList = new ArrayList<>();

        for (String id : idList) {
            Map<String, Object> resultUnit = new HashMap<>();

            if (level == 1) {
                contributionRateAlgorithmEntities = contributionRateAlgorithmService
                        .list(
                                new QueryWrapper<ContributionRateAlgorithmEntity>()
                                        .select("SCENE_ID")
                                        .eq("MISSION_TASK_ID", id));
                resultUnit.put("missionTaskId", id);
            } else if (level == 2 || level == 3) {
                contributionRateAlgorithmEntities = contributionRateAlgorithmService
                        .list(
                                new QueryWrapper<ContributionRateAlgorithmEntity>()
                                        .select("SCENE_ID")
                                        .in("SCENE_SCENARIO_ID", idList));
                resultUnit.put("sceneScenarioId", id);
            }
            if (contributionRateAlgorithmEntities.isEmpty()) {
                throw new BusinessException("该方案下无样本数据");
            }
            sceneIds = contributionRateAlgorithmEntities
                    .stream()
                    .map(ContributionRateAlgorithmEntity::getSceneId)
                    .collect(Collectors.toList());

            IndicatorValuesWithNameResultDTO dto = findIndicatorMaxResult(schemeId, indicatorTreeId, sceneIds);

            resultUnit.put("sceneId", dto.getDataList().get(0).getSceneId());
            resultUnit.put("sceneName", dto.getDataList().get(0).getSceneName());
            resultUnit.put("indicatorResult", dto.getDataList().stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getLevel)));

            mapList.add(resultUnit);

        }

        return mapList;
    }


    @Override
    public List<Map<String, Object>> findIndicatorResultRx(String sid) {

        List<Map<String, Object>> result = new ArrayList<>();

//        indicatorTreeService.find() 这边查出来是所有的指标树
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).pid("0").build());
        if (CollUtil.isEmpty(indicatorEntities)) {
            return result;
        }
        String rootIndicatorId = indicatorEntities.get(0).getId();

        List<CalcModuleEntity> all = calcModuleService.list(new LambdaQueryWrapper<CalcModuleEntity>().eq(CalcModuleEntity::getIndicatorId, rootIndicatorId).eq(CalcModuleEntity::getCalcType, ModuleType.METHOD_MODEL.id).eq(CalcModuleEntity::getDefaultCalModule, CalModuleType.DEFAULT_MODEL.id));
        if (CollUtil.isEmpty(all)) {
            return result;
        }
        String rootMethodId = all.get(0).getMethodId();

        //获得当前pg任务的pg算法
        EvaluationType typeByName = EvaluationType.getTypeByName(rootMethodId);

        List<SceneEntity> sceneEntities = sceneService.findBySchemeId(sid);
        Map<String, List<IndicatorStandardVO>> indicatorStandardLabelsMap = new HashMap<>();
        List<CalcorEntity> calcorEntities = new ArrayList<>();
        Map<String, String> enNameMap = Maps.newHashMap();
        Map<String, String> chNameMap = Maps.newHashMap();
        Set<String> defaultModuleIds = new HashSet<>();
        loadDataForResult(sid, indicatorStandardLabelsMap, calcorEntities, enNameMap, chNameMap, defaultModuleIds);

        for (SceneEntity s : sceneEntities) {
            Map<String, Object> resultUnit = new HashMap<>();
            IndicatorValuesWithNameResultDTO indicatorValuesWithNameResultDTO =
                    getIndicatorValuesWithNameDTO(sid, s.getId(), StringUtils.EMPTY, new IndicatorValuesWithNameResultDTO(), typeByName,
                            indicatorStandardLabelsMap,calcorEntities,enNameMap,chNameMap, defaultModuleIds);
            List<IndicatorValuesWithNameDTO> indicatorValuesWithNameDTOList = indicatorValuesWithNameResultDTO.getDataList().isEmpty() ? new ArrayList<>() : indicatorValuesWithNameResultDTO.getDataList();
            List<Map<String, Object>> indicatorResultWithChildren = new ArrayList<>();
            for (IndicatorValuesWithNameDTO iv : indicatorValuesWithNameDTOList) {
                Map<String, Object> map = BeanUtil.beanToMap(iv);
                List<IndicatorValuesWithNameDTO> children = listAllChildrenNode(iv.getIndicatorId(), indicatorValuesWithNameDTOList);
                if (CollUtil.isNotEmpty(children)) {
                    map.put("children", children);
                    indicatorResultWithChildren.add(map);
                }
            }

            resultUnit.put("sceneId", s.getId());
            resultUnit.put("sceneName", s.getName());
            resultUnit.put("indicatorResult", indicatorResultWithChildren);
            result.add(resultUnit);
        }
        return result;

    }

    /**
     * pg计算，计算装备抗毁性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean calculateEquInvulnerability(EquInvulnerabilityDTO equInvulnerabilityDTO) {
        //存储装备抗毁性结果
        List<EquInvulnerabilityEntity> equInvulnerabilityEntityList = Lists.newArrayList();
        //待清除的历史数据
        List<String> equRemoveIds = Lists.newArrayList();
        //执行计算
        calculateExecute(equInvulnerabilityDTO.getSid(), equInvulnerabilityDTO.getEquIdList(), equInvulnerabilityEntityList, equRemoveIds, false);
        //清除原有数据
        if(CollUtil.isNotEmpty(equRemoveIds)) {
            equInvulnerabilityService.remove(new LambdaQueryWrapper<EquInvulnerabilityEntity>().in(EquInvulnerabilityEntity::getEquId, equRemoveIds));
        }
        //保存计算结果
        if(CollUtil.isNotEmpty(equInvulnerabilityEntityList)) {
            equInvulnerabilityService.saveBatch(equInvulnerabilityEntityList);
        }
        return true;
    }

    /**
     * 查询装备抗毁性图表
     * @param sid 方案id
     * @param sortOrder 排序方式
     */
    @Override
    public EquInvulnerabilityVO findEquInvulnerability(String sid, Integer sortOrder) {
        //根据方案id查询装备抗毁性结果
        List<EquInvulnerabilityEntity> equInvulnerabilityEntityList = equInvulnerabilityService.list(
                new LambdaQueryWrapper<EquInvulnerabilityEntity>().eq(EquInvulnerabilityEntity::getSid, sid)
                        .orderByAsc(EquInvulnerabilityEntity::getEquRemove));
        //返回前端数据
        return getEquInvulnerabilityVO(equInvulnerabilityEntityList, sortOrder);
    }

    /**
     * 清空装备抗毁性图表
     * @param sid 方案id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteEquInvulnerability(String sid) {
        equInvulnerabilityService.remove(new LambdaQueryWrapper<EquInvulnerabilityEntity>()
                .eq(EquInvulnerabilityEntity::getSid, sid));
        return "数据已清除";
    }

    /**
     * 获取装备抗毁性计算方法
     */
    @Override
    public List<ScriptMethodVO> findEquCalcModule() {
        List<ScriptMethodVO> vo = new ArrayList<>();
        //加权求和
        EvaluationType weightSum = EvaluationType.WEIGHT_SUM;
        //ahp法
        EvaluationType ahpMeans = EvaluationType.AHP_MEANS;
        vo.add(ScriptMethodVO.valueOf(weightSum.getName(), weightSum.getChName(), null, null, null));
        vo.add(ScriptMethodVO.valueOf(ahpMeans.getName(), ahpMeans.getChName(), null, null, null));
        return vo;
    }

    /**
     * pg计算，计算装备随机性抗毁性
     */
    @Override
    public EquInvulnerabilityVO calculateEquInvulnerabilityRandom(String sid, Integer sortOrder) {
        //存储装备抗毁性结果
        List<EquInvulnerabilityEntity> equInvulnerabilityEntityList = Lists.newArrayList();
        //计算装备抗毁性
        calculateExecute(sid, null, equInvulnerabilityEntityList, null, true);
        //返回前端数据
        return getEquInvulnerabilityVO(equInvulnerabilityEntityList, sortOrder);
    }

    /**
     * 获取所有子孙节点
     *
     * @param id
     * @param list
     * @return
     */
    private List<IndicatorValuesWithNameDTO> listAllChildrenNode(String id, List<IndicatorValuesWithNameDTO> list) {

        List<IndicatorValuesWithNameDTO> children = new ArrayList<>();
        List<String> nodeIds = new ArrayList<>();
        List<String> childNodeIds = new ArrayList<>();
        nodeIds.add(id);
        boolean flag = true;

        while (flag) {
            flag = false;
            for (String nodeId : nodeIds) {
                List<IndicatorValuesWithNameDTO> tmp = listChildrenNode(nodeId, list);
                if (!ObjectUtils.isEmpty(tmp)) {
                    children.addAll(tmp);
                    childNodeIds.addAll(tmp.stream().map(IndicatorValuesWithNameDTO::getIndicatorId).collect(Collectors.toList()));
                    flag = true;
                }
            }
            nodeIds.clear();
            nodeIds.addAll(childNodeIds);
            childNodeIds.clear();
        }

        return children;
    }

    /**
     * 获取子节点
     *
     * @param id
     * @param list
     * @return
     */
    private List<IndicatorValuesWithNameDTO> listChildrenNode(String id, List<IndicatorValuesWithNameDTO> list) {
        return list.stream().filter(i -> i.getPid().equals(id)).collect(Collectors.toList());
    }


    /**
     * 获取装备抗毁性图表VO
     */
    private EquInvulnerabilityVO getEquInvulnerabilityVO(List<EquInvulnerabilityEntity> equInvulnerabilityEntityList, Integer sortOrder) {
        //返回结果对象
        EquInvulnerabilityVO equInvulnerabilityVO = new EquInvulnerabilityVO();
        if(CollUtil.isEmpty(equInvulnerabilityEntityList)) {
            return equInvulnerabilityVO;
        }
        //获取装备id集合
        List<String> collect = equInvulnerabilityEntityList.stream().map(EquInvulnerabilityEntity::getEquId).collect(Collectors.toList());
        //根据装备ID获取排序方式值
        List<EquipmentImportanceResult> equipmentImportanceResults = equipmentImportanceResultService.list(new LambdaQueryWrapper<EquipmentImportanceResult>().in(EquipmentImportanceResult::getNodeId, collect));
        List<EquInvulnerabilityVO.EquipmentImportantVo> equipmentImportVos = Lists.newArrayList();

        if(CollUtil.isNotEmpty(equipmentImportanceResults)){
            equipmentImportVos.add(new EquInvulnerabilityVO.EquipmentImportantVo(
                    "无装备摧毁","-1",-1d,-1d,-1d,-1d,
                    equInvulnerabilityEntityList.get(0).getEquInvulnerability(),equInvulnerabilityEntityList.get(0).getSslNumber()));

            for (EquInvulnerabilityEntity equInvulnerabilityEntity : equInvulnerabilityEntityList) {
                EquipmentImportanceResult equipmentImportance = null;
                for (EquipmentImportanceResult equipmentImportanceResult : equipmentImportanceResults){
                    if (equInvulnerabilityEntity.getEquName().equals(equipmentImportanceResult.getNodeName())){
                        equipmentImportance = equipmentImportanceResult;
                        break;
                    }
                }
                double importance = -1d;
                double bc = -1d;
                double dc = -1d;
                double cc = -1d;
                if (!Objects.isNull(equipmentImportance)){
                    importance = equipmentImportance.getImportance();
                    bc = equipmentImportance.getBcValue();
                    cc = equipmentImportance.getCcValue();
                    dc = equipmentImportance.getDcValue();
                }
                equipmentImportVos.add(new EquInvulnerabilityVO.EquipmentImportantVo(
                        equInvulnerabilityEntity.getEquName(),equInvulnerabilityEntity.getEquId(),importance,bc,dc,cc,
                        equInvulnerabilityEntity.getEquInvulnerability(),equInvulnerabilityEntity.getSslEquNumber()));
            }
        }
        equInvulnerabilityVO.setEquipmentImportantVos(equipmentImportVos);
        return equInvulnerabilityVO;
    }


    /**
     * 执行装备抗毁性计算
     * @param sid 方案id
     * @param equIdList 计算抗毁性装备id集合
     * @param equInvulnerabilityEntityList 装备抗毁性结果存储集合
     * @param equRemoveIds 需要清空原数集合
     */
    private void calculateExecute(String sid, List<String> equIdList, List<EquInvulnerabilityEntity> equInvulnerabilityEntityList, List<String> equRemoveIds, boolean isRandom) {
        //根据方案ID查询想定信息
        List<SceneScenarioEntity> sceneScenarioEntities = sceneScenarioService.list(new LambdaQueryWrapper<SceneScenarioEntity>().eq(SceneScenarioEntity::getSid, sid));
        if(CollUtil.isEmpty(sceneScenarioEntities)) {
            throw new BusinessException("未查询到想定信息");
        }
        //获取想定ID，目前有关方案下仅有一个想定id
        String sceneScenarioId = sceneScenarioEntities.get(0).getId();
        //根据想定ID，获取装备信息
        List<AtlasEntity> atlasEntities = atlasEntityService.list(new LambdaQueryWrapper<AtlasEntity>().eq(AtlasEntity::getSceneScenarioId, sceneScenarioId));
        if(CollUtil.isEmpty(atlasEntities)) {
            throw new BusinessException("未查询到装备信息");
        }
        //估计方案id查询出根指标id
        List<IndicatorTreeEntity> indicatorTreeEntities = indicatorTreeService.list(new LambdaQueryWrapper<IndicatorTreeEntity>().eq(IndicatorTreeEntity::getSid, sid).eq(IndicatorTreeEntity::getIndicatorTreeCategory, 3));
        if(CollUtil.isEmpty(indicatorTreeEntities)) {
            throw new BusinessException("未查询到相关指标树");
        }
        //根据指标树id查询根指标id
        List<IndicatorEntity> indicatorEntities = indicatorService.list(new LambdaQueryWrapper<IndicatorEntity>()
                .eq(IndicatorEntity::getTreeId, indicatorTreeEntities.get(0).getId())
                .eq(IndicatorEntity::getPid, "0"));
        //根指标id
        String rootIndicatorId = indicatorEntities.get(0).getId();
        //根据方案id查询出样本
        List<SceneEntity> sceneEntityList = sceneService.list(new LambdaQueryWrapper<SceneEntity>()
                .eq(SceneEntity::getSid, sid)
                .eq(SceneEntity::getTreeId, indicatorEntities.get(0).getTreeId()));
        if(CollUtil.isEmpty(sceneEntityList)) {
            throw new BusinessException("未查询到样本");
        }
        //查询计算模型
        List<CalcModuleEntity> calcModuleIdList = calcModuleService.list(new LambdaQueryWrapper<CalcModuleEntity>().eq(CalcModuleEntity::getSid, sid)
                .eq(CalcModuleEntity::getIndicatorId, rootIndicatorId)
                .eq(CalcModuleEntity::getDefaultCalModule, 1));
        if(CollUtil.isEmpty(calcModuleIdList)) {
            throw new BusinessException("未设置计算模型");
        }
        String calcModuleId = calcModuleIdList.get(0).getId();
        //默认只计算第一个样本
        SceneEntity sceneEntity = sceneEntityList.get(0);
        String sceneId = sceneEntity.getId();
        //获取装备id name map
        Map<String,String> atlasIdNameMap = atlasEntities.stream().collect(Collectors.toMap(AtlasEntity::getId, AtlasEntity::getEntityName));
        if(isRandom && CollUtil.isEmpty(equIdList)){
            equIdList = Lists.newArrayList(atlasIdNameMap.keySet());
        }
        //计算所有指标
        List<IndicatorValuesEntity> indicatorValuesEntities = evaluationCalculateV2(sid, sceneId, rootIndicatorId, calcModuleId, null);
        //获取根指标结果
        String rootValue = getIndicatorValues(sceneId, rootIndicatorId, indicatorValuesEntities);
        DamageCalcResultDTO rootDamageCalcResultDTO = sslCalc.getLinked(sceneScenarioId, null,0);
        // 被第几次移除
        int removeEqu = 0;
        if (!isRandom){
            List<String> removeEquId = Lists.newArrayList();
            List<EquInvulnerabilityEntity> equInvulnerabilityEntities = equInvulnerabilityService.list(new LambdaQueryWrapper<EquInvulnerabilityEntity>().eq(EquInvulnerabilityEntity::getSid,sid));
            if (CollUtil.isNotEmpty(equInvulnerabilityEntities)){
                removeEquId.addAll(equInvulnerabilityEntities.stream().filter(s->StringUtils.isNotEmpty(s.getEquId())).map(EquInvulnerabilityEntity::getEquId).collect(Collectors.toSet()));
                // 取出一个最大值
                for (EquInvulnerabilityEntity equInvulnerabilityEntity : equInvulnerabilityEntities) {
                    if (removeEqu < equInvulnerabilityEntity.getEquRemove()){
                        removeEqu = equInvulnerabilityEntity.getEquRemove();
                    }
                }
                scenarioIdRemoveEquIdMap.put(sid,removeEquId);
            }
        }
        //计算装备抗毁性
        if(CollUtil.isNotEmpty(equIdList)){
            try{
                for (String equId : equIdList) {
                    //存储装备抗毁性实体
                    EquInvulnerabilityEntity equInvulnerabilityEntity = new EquInvulnerabilityEntity();
                    //添加想定id 装备id map
                    scenarioIdEquIdMap.put(sid, equId);
                    //计算装备抗毁性
                    List<IndicatorValuesEntity> valuesEntityList = evaluationCalculateV2(sid, sceneId, rootIndicatorId, calcModuleId, null);
                    //获取装备抗毁性
                    String equValue = getIndicatorValues(sceneId, rootIndicatorId, valuesEntityList);
                    equInvulnerabilityEntity.setEquId(equId);
                    equInvulnerabilityEntity.setEquName(ObjectUtil.isNull(atlasIdNameMap.get(equId)) ? equId : atlasIdNameMap.get(equId));
                    equInvulnerabilityEntity.setSid(sid);
                    equInvulnerabilityEntity.setEquInvulnerability(equValue);
                    equInvulnerabilityEntity.setEquRemove(++removeEqu);
                    equInvulnerabilityEntity.setOriginallyValue(rootValue);
                    List<String> removeEquId = Lists.newArrayList(equId);
                    if (scenarioIdRemoveEquIdMap.containsKey(sid)) {
                        removeEquId.addAll(EvaluationCalculationServiceImpl.scenarioIdRemoveEquIdMap.get(sid));
                        EvaluationCalculationServiceImpl.scenarioIdRemoveEquIdMap.get(sid).add(equId);
                    }
                    DamageCalcResultDTO damageCalcResultDTO = sslCalc.getLinked(sceneScenarioId, removeEquId,0);
                    equInvulnerabilityEntity.setSslEquNumber(damageCalcResultDTO.getLinked().size());
                    equInvulnerabilityEntity.setSslNumber(rootDamageCalcResultDTO.getLinked().size());
                    //保存结果
                    equInvulnerabilityEntityList.add(equInvulnerabilityEntity);
                    if(ObjectUtil.isNotNull(equRemoveIds)){
                        equRemoveIds.add(equId);
                    }
                }
            }finally{
                scenarioIdEquIdMap.remove(sid);
                scenarioIdRemoveEquIdMap.remove(sid);
            }
        }
    }



    /**
     * 获取指标值
     * @param sceneId 样本id
     * @param indicatorId 指标id
     * @param indicatorValuesEntities 所有指标结果集合
     */
    private String getIndicatorValues(String sceneId, String indicatorId, List<IndicatorValuesEntity> indicatorValuesEntities) {
        for (IndicatorValuesEntity indicatorValuesEntity : indicatorValuesEntities) {
            if(sceneId.equals(indicatorValuesEntity.getSceneId()) && indicatorId.equals(indicatorValuesEntity.getIndicatorId())) {
                return indicatorValuesEntity.getNormalizedValue();
            }
        }
        return "0";
    }


}
