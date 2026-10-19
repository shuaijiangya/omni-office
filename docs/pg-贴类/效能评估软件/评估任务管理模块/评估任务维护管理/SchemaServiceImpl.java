package com.yw.module.plan.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.common.defines.base.Query;
import com.yw.common.defines.enums.*;
import com.yw.common.utils.PageHelperUtil;
import com.yw.common.utils.ValidValue;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.framework.common.defines.exception.custom.DataValidException;
import com.yw.framework.common.module.login.mapper.UserMapper;
import com.yw.framework.common.util.Func;
import com.yw.gather.dbinfomgr.IDatasrcService;
import com.yw.gather.gatherscheme.schemeMgr.service.GatherSchemeMgrService;
import com.yw.mediator.input.PlanInputMediator;
import com.yw.module.analysis.domain.entity.TrendForecastEntity;
import com.yw.module.analysis.service.ITrendForecastService;
import com.yw.module.calculation.contributionRate.service.ContributionRateCalculatedValueService;
import com.yw.module.calculation.domain.entity.CalCorValuesEntity;
import com.yw.module.calculation.domain.entity.CalcModuleEntity;
import com.yw.module.calculation.domain.entity.EvaluateAlgEntity;
import com.yw.module.calculation.domain.entity.ScriptMethodEntity;
import com.yw.module.calculation.indicatorCalculation.mapper.ICalcModuleMapper;
import com.yw.module.calculation.indicatorCalculation.service.ICalcorValuesService;
import com.yw.module.calculation.mapper.EvaluateAlgMapper;
import com.yw.module.calculation.mapper.ScriptMethodMapper;
import com.yw.module.equipmentResource.domain.entity.DynamicColumnConfigEntity;
import com.yw.module.equipmentResource.domain.entity.ResourceAttributeValueEntity;
import com.yw.module.equipmentResource.domain.entity.ResourceEquipmentEntity;
import com.yw.module.equipmentResource.service.IDynamicColumnService;
import com.yw.module.equipmentResource.service.IResourceAttributeValueService;
import com.yw.module.equipmentResource.service.IResourceService;
import com.yw.module.equnode.domain.entity.RelationshipInfoEntity;
import com.yw.module.equnode.domain.entity.SslResultEntity;
import com.yw.module.equnode.service.IRelationshipInfoService;
import com.yw.module.equnode.service.SslResultService;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorTreeEntity;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import com.yw.module.indicator.domain.enums.EvaluationType;
import com.yw.module.indicator.domain.vo.IndicatorStandardVO;
import com.yw.module.indicator.mapper.IndicatorMapper;
import com.yw.module.indicator.service.IIndicatorTreeService;
import com.yw.module.indicator.service.IStandardLabelsService;
import com.yw.module.indicator.service.ITIndicatorStandardValuesService;
import com.yw.module.indicator.service.ITIndicatorValueService;
import com.yw.module.missionScene.domain.entity.ContributionRateCalculatedValueEntity;
import com.yw.module.missionScene.domain.entity.SceneScenarioEntity;
import com.yw.module.missionScene.domain.entity.SidesDirecEntity;
import com.yw.module.missionScene.service.SceneScenarioService;
import com.yw.module.missionScene.service.SidesDirecService;
import com.yw.module.plan.domain.dto.SceneDTO;
import com.yw.module.plan.domain.dto.SchemaCheckDTO;
import com.yw.module.plan.domain.entity.*;
import com.yw.module.plan.domain.vo.CmSchemaVO;
import com.yw.module.plan.domain.vo.SchemaInfoVO;
import com.yw.module.plan.domain.vo.SchemaResultVO;
import com.yw.module.plan.mapper.ISchemaMapper;
import com.yw.module.plan.mapper.ITIssuedTaskMapper;
import com.yw.module.plan.service.*;
import com.yw.module.system.domain.dto.LogEvaluationPlanDto;
import com.yw.module.system.domain.entity.EquipmentEntity;
import com.yw.module.system.domain.entity.EquipmentSystemEntity;
import com.yw.module.system.mapper.PGLogMapper;
import com.yw.module.system.service.IEquipmentService;
import com.yw.module.system.service.IEquipmentSystemService;
import com.yw.source.importDataDirectory.entity.DataDirectory;
import com.yw.source.importDataDirectory.service.IDataDirectoryService;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 方案管理服务实现类
 * <p>负责方案的创建、查询、编辑、删除、指标树管理、关联场景想定及计算结果展示等核心业务逻辑</p>
 *
 * @author admin
 * @date 2020/11/17 16:00
 * @since 1.0.0
 */
@Service
public class SchemaServiceImpl extends ServiceImpl<ISchemaMapper, SchemaEntity> implements ISchemaService {

    /** 正则表达式，用于匹配方案名称格式（如：方案1、方案10等） */
    private static final Pattern pattern = Pattern.compile("^方案[0-9]*[0-9]$");

    /**
     * 装备信息服务层
     */
    @Resource
    private IEquipmentService equipmentService;

    /**
     * 装备体系服务层
     */
    @Resource
    private IEquipmentSystemService equipmentSystemService;

    /**
     * 场景表服务层
     */
    @Autowired
    @Lazy
    private ISceneTableService sceneTableService;

    /**
     * 指标数服务层
     */
    @Autowired
    private IIndicatorTreeService indicatorTreeService;

    /**
     * 指标标准值服务层
     */
    @Autowired
    private ITIndicatorStandardValuesService indicatorStandardValuesService;

    /**
     * 下达任务mapper
     */
    private final ITIssuedTaskMapper issuedTaskMapper;

    /**
     * 样本服务接口
     */
    private final ISceneService sceneService;

    /**
     * 方案Mapper
     */
    @Autowired
    private ISchemaMapper schemaMapper;

    /**
     * pg算法mapper
     */
    @Autowired
    private EvaluateAlgMapper evaluateAlgMapper;
    /**
     * 指标算法管理
     */
    @Autowired
    private ICalcModuleMapper iCalcModuleMapper;

    /**
     * 算法脚本dao层
     */
    private final ScriptMethodMapper scriptMethodMapper;
    @Autowired
    private IndicatorMapper indicatorMapper;
    @Autowired
    private ITIndicatorValueService indicatorValueService;
    @Autowired
    private IStandardLabelsService standardLabelsService;

    @Autowired
    @Lazy
    private ITaskService iTaskService;
    @Autowired
    private IDatasrcService datasrcService;
    @Autowired
    private IDataDirectoryService iDataDirectoryService;

    @Autowired
    private SceneScenarioService sceneScenarioService;

    @Autowired
    private PlanInputMediator planInputMediator;

    @Resource
    private CmSchemaScenarioRelationService schemaScenarioRelationService;

    @Resource
    private CmSchemaScenarioService schemaScenarioService;

    @Resource
    private CmSimulationVariablesService simulationVariablesService;

    /** 采集方案管理服务层 */
    @Autowired
    private GatherSchemeMgrService gatherSchemeMgrService;

    /** 想定装备管理服务层 */
    @Autowired
    private SidesDirecService sidesDirecService;
    /** 杀伤链结果管理服务层 */
    @Lazy
    @Autowired
    private SslResultService sslResultService;
    /** 趋势预测分析服务层 */
    @Autowired
    private ITrendForecastService trendForecastService;
    /** 作战资源-装备数据动态表头服务层 */
    @Autowired
    private IDynamicColumnService dynamicColumnService;
    /** 作战资源-装备数据服务层 */
    @Autowired
    private IResourceService resourceEquipmentService;
    /** 作战资源-装备要素属性值服务层 */
    @Autowired
    private IResourceAttributeValueService resourceAttributeValueService;
    /** 作战资源-装备动态关系服务层 */
    @Autowired
    private IRelationshipInfoService relationshipInfoService;
    /** 作战资源-算子计算结果服务层 */
    @Autowired
    private ICalcorValuesService calcorValuesService;
    /** 贡献率计算结果服务层 */
    @Autowired
    private ContributionRateCalculatedValueService contributionRateCalculatedValueService;

    /** 方案日志记录 Mapper */
    @Resource
    private PGLogMapper pgLogMapper;
    /** 用户信息 Mapper */
    @Resource
    private UserMapper userMapper;

    /**
     * 构造器注入依赖组件
     *
     * @param issuedTaskMapper 已下发任务 Mapper
     * @param sceneService     场景服务（延迟加载）
     * @param iSchemaMapper    方案 Mapper
     * @param scriptMethodMapper 脚本方法 Mapper
     */
    public SchemaServiceImpl(ITIssuedTaskMapper issuedTaskMapper, @Lazy ISceneService sceneService, ISchemaMapper iSchemaMapper, ScriptMethodMapper scriptMethodMapper) {
        this.issuedTaskMapper = issuedTaskMapper;
        this.sceneService = sceneService;
        this.schemaMapper = iSchemaMapper;
        this.scriptMethodMapper = scriptMethodMapper;
    }

    /**
     * 保存方案及关联文件
     *
     * @param entity 方案实体
     * @param file   上传的文件流（通常包含方案配置JSON）
     * @return 保存成功返回 true，否则返回 false
     * @throws BusinessException 业务校验异常
     * @throws IOException       文件读取异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(SchemaEntity entity, MultipartFile file) {
        boolean f = false;
        try {
            String s = readTextFileToString(file.getInputStream());
            com.alibaba.fastjson.JSONObject object = JSON.parseObject(s);
            entity.setF1(object.getString("schemaType"));
            entity.setCreator(object.getString("schemaBuilderUser"));
            entity.setF2(object.getString("schemaResearchObjectives"));

            //初始化数据源信息
            try {
                DataDirectory dataDirectory = new DataDirectory();
                dataDirectory.setParentId("0");
                //以方案名称命名目录
                dataDirectory.setName(entity.getName());
                String dataDirectoryId = iDataDirectoryService.addDirectory(dataDirectory);
                String dataSrcId = datasrcService.createNewDataSrc(dataDirectoryId);
                entity.setDataDirectoryId(dataDirectoryId);
                entity.setF3(dataSrcId);
                entity.setDataDirectoryName(entity.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            f = save(entity);

            List<CmSchemaScenario> schemaScenarioList = object.getObject("schemaScenarioList", List.class);
            List<CmSimulationVariables> cmSimulationVariablesList = object.getObject("simulationVariablesList", List.class);

            //保存场景想定和仿真变量
            f = save(entity, schemaScenarioList, cmSimulationVariablesList);

            // 新增指标树
            planInputMediator.getIndicatorTreeService().addV3(entity.getId());
            // 通过因果变量生成样本结构
            //planInputMediator.getSceneService().variableToSceneTable(entity.getObjId());

        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    /**
     * 根据方案ID打开/查询方案详情
     *
     * @param schemaId 方案唯一标识
     * @return 方案实体对象，不存在时返回 null
     */
    @Override
    public SchemaEntity openSchema(String schemaId) {
        return schemaMapper.openSchema(schemaId);
    }

    /**
     * 分页查询方案列表
     *
     * @param query     分页查询参数
     * @param params    查询条件参数
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 分页结果
     * @throws DataValidException 当 query 参数为空时抛出
     */
    @Override
    public PageInfo<SchemaEntity> selectPage(Query query, CmSchemaVO params, LocalDateTime startTime, LocalDateTime endTime) {
        if (Objects.isNull(query)) {
            throw new DataValidException("分页参数为空，请传入！");
        }
        //开启分页查询
        PageHelper.startPage(Func.toInt(query.getCurrent(), 1), Func.toInt(query.getSize(), 10));

        List<SchemaEntity> list = this.list(new LambdaQueryWrapper<SchemaEntity>()
                .like(params != null && com.yw.framework.common.util.StringUtils.isNotEmpty(params.getName()), SchemaEntity::getName, params.getName())
                .ge(startTime != null, SchemaEntity::getCreateDate, startTime)
                .le(endTime != null, SchemaEntity::getCreateDate, endTime)
        );
        return new PageInfo<>(list);
    }

    /**
     * 根据ID删除方案及其关联数据
     *
     * @param id 方案唯一标识
     * @return 删除成功返回 true，否则返回 false
     * @throws BusinessException 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteById(String id) {

        SchemaEntity schema = this.getById(id);
        //场景下数据源信息删除
        datasrcService.delete(schema.getF3());
        //删除目录
        iDataDirectoryService.deleteDirectoryById(schema.getDataDirectoryId());
        //删除场景下所有采集方案（一起删除了采集任务）
        gatherSchemeMgrService.deleteBySchemaId(schema.getId());

        boolean f = this.removeById(id);
        //仿真变量删除
        simulationVariablesService.remove(new LambdaQueryWrapper<CmSimulationVariables>().eq(CmSimulationVariables::getSchemaId, id));
        List<CmSchemaScenarioRelation> list = schemaScenarioRelationService.list(new LambdaQueryWrapper<CmSchemaScenarioRelation>().eq(CmSchemaScenarioRelation::getSchemaId, id));
        //schemaScenarioService.deleteBySchemaId(id);
        //场景想定删除
        schemaScenarioService.removeByIds(list.stream().map(CmSchemaScenarioRelation::getScenarioId).collect(Collectors.toList()));
        schemaScenarioRelationService.remove(new LambdaQueryWrapper<CmSchemaScenarioRelation>().eq(CmSchemaScenarioRelation::getSchemaId, id));


        return f;
    }

    /**
     * 批量保存方案关联的场景想定与仿真变量
     *
     * @param entity                 方案实体
     * @param schemaScenarioList     场景想定列表
     * @param simulationVariablesList 仿真变量列表
     * @return 保存成功返回 true，否则返回 false
     */
    public boolean save(SchemaEntity entity, List<CmSchemaScenario> schemaScenarioList, List<CmSimulationVariables> simulationVariablesList) {

        boolean f = false;

        List<CmSchemaScenarioRelation> schemaScenarioRelationList = new ArrayList<>();
        for (int i = 0; i < schemaScenarioList.size(); i++) {
            CmSchemaScenario cmSchemaScenario = JSON.parseObject(JSON.toJSONString(schemaScenarioList.get(i)), CmSchemaScenario.class);
            cmSchemaScenario.setObjId(IdUtil.simpleUUID());
            schemaScenarioList.set(i, cmSchemaScenario);

            CmSchemaScenarioRelation schemaScenarioRelation = new CmSchemaScenarioRelation();
            schemaScenarioRelation.setSchemaId(entity.getId());
            schemaScenarioRelation.setScenarioId(cmSchemaScenario.getObjId());
            schemaScenarioRelationList.add(schemaScenarioRelation);
        }
        for (int i = 0; i < simulationVariablesList.size(); i++) {
            CmSimulationVariables cmSimulationVariables = JSON.parseObject(JSON.toJSONString(simulationVariablesList.get(i)), CmSimulationVariables.class);
            cmSimulationVariables.setSchemaId(entity.getId());
            simulationVariablesList.set(i, cmSimulationVariables);
        }

        f = schemaScenarioService.saveBatch(schemaScenarioList);
        simulationVariablesService.saveBatch(simulationVariablesList);
        schemaScenarioRelationService.saveBatch(schemaScenarioRelationList);
        return f;
    }

    /**
     * 将文本文件读取为字符串
     *
     * @param inputStream 文件输入流
     * @return 读取到的字符串内容，读取失败返回 null
     * @throws IOException 流关闭异常
     */
    public String readTextFileToString(InputStream inputStream) {
        BufferedReader reader = null;
        try (InputStreamReader read = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            reader = new BufferedReader(read);
            return reader.lines().collect(Collectors.joining());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 根据方案ID查询方案实体（返回Optional包装）
     *
     * @param id 方案唯一标识
     * @return 包含方案实体的Optional对象
     */
    @Override
    public Optional<SchemaEntity> findById(String id) {
        Optional<SchemaEntity> schemaEntity = Optional.ofNullable(schemaMapper.selectById(id));
        return schemaEntity;
    }

    /**
     * 根据方案ID查询关联的指标树列表
     *
     * @param sid 方案ID
     * @return 指标树实体列表
     */
    @Override
    public List<IndicatorTreeEntity> findIndicatorTreeBySid(String sid) {
        QueryWrapper<IndicatorTreeEntity> indicatorTreeEntityQueryWrapper = new QueryWrapper<>();
        indicatorTreeEntityQueryWrapper.eq("SID", sid);
        return indicatorTreeService.list(indicatorTreeEntityQueryWrapper);
    }

    /**
     * 编辑方案基础信息（新增/修改）
     *
     * @param data   方案实体数据
     * @param treeId 指标树ID（预留参数）
     * @return 保存后的方案ID
     * @throws BusinessException 参数校验异常或业务规则冲突异常
     */
    @Override
    @Transactional
    public String edit(SchemaEntity data, String treeId) {
        if (StringUtils.isNotBlank(data.getId()) && StringUtils.isBlank(data.getName())) {
            throw new BusinessException("方案名称不能为空");
        }//给方案设置默认名字
        if (StringUtils.isBlank(data.getId()) && StringUtils.isBlank(data.getName())) {
            List<SchemaEntity> schemaEntityList = schemaMapper.selectList(new QueryWrapper<>());
            Optional<Long> schemaNameNum = schemaEntityList
                    .stream()
                    .filter(f -> pattern.matcher(f.getName()).find())
                    .map(m -> Long.parseLong(m.getName().replace("方案", "")))
                    .max(Long::compareTo);
            long num = !schemaNameNum.isPresent() ? 1 : (schemaNameNum.get() + 1);
            data.setName("方案" + num);
        }
        String uuid = StringUtils.isBlank(data.getId()) ? com.yw.common.utils.UUIDGenerator.getUUID() : data.getId();
        LocalDateTime createDate = LocalDateTime.now();
        //日志相关数据
        String userId = com.yw.common.utils.UserUtil.getUserId();
        LogEvaluationPlanDto logDto = new LogEvaluationPlanDto();

        if (StringUtils.isBlank(data.getId())) {
            data.setCreateDate(LocalDateTime.now());
        } else {
            Optional<SchemaEntity> schemaOp = Optional.ofNullable(schemaMapper.selectById(data.getId()));
            if (schemaOp.isPresent()) {
                SchemaEntity schemaEntity = schemaOp.get();
                if (EvaluationType.ANP_MEANS.getName().equals(schemaEntity.getEvaluationMethod()) &&
                        !data.getEvaluationMethod().equals(schemaEntity.getEvaluationMethod())) {
                    throw new BusinessException("网络分析法不适配多个方法模型");
                }
                String evaluationMethod = StringUtils.isEmpty(data.getEvaluationMethod()) ? "" : data.getEvaluationMethod();
                if (!EvaluationType.ANP_MEANS.getName().equals(schemaEntity.getEvaluationMethod()) &&
                        evaluationMethod.equals(EvaluationType.ANP_MEANS.getName())) {
                    List<IndicatorEntity> indicatorEntities = indicatorMapper.selectList(new QueryWrapper<IndicatorEntity>()
                            .eq("SID", data.getId()).eq("PID", "0"));
                    if (CollUtil.isNotEmpty(indicatorEntities)) {
                        IndicatorEntity rootIndicator = indicatorEntities.get(0);
                        String indicatorId = rootIndicator.getId();
                        List<CalcModuleEntity> calcModuleEntities = iCalcModuleMapper.selectList(new QueryWrapper<CalcModuleEntity>()
                                .eq("CALC_TYPE", ModuleType.METHOD_MODEL.id).eq("indicator_id", indicatorId).eq("SID", data.getId()));
                        if (CollUtil.isNotEmpty(calcModuleEntities) && calcModuleEntities.size() > 1) {
                            throw new BusinessException("网络分析法不适配多个方法模型");
                        }
                    }
                }
                createDate = schemaEntity.getCreateDate();
                data.setSummary(schemaEntity.getSummary());
                data.setEvaluationMethod(StringUtils.isEmpty(data.getEvaluationMethod()) ? schemaEntity.getEvaluationMethod() : data.getEvaluationMethod());
                //编辑操作日志信息
                logDto = LogEvaluationPlanDto.getEditLog(userId, userMapper.getUserNameById(userId), data, schemaEntity,
                        null, schemaEntity.getCreator(), schemaEntity.getCreateDate());
            } else {
                //新增操作日志新增
                logDto = LogEvaluationPlanDto.getLog(data.getName(), userId, userMapper.getUserNameById(userId),
                        BusinessType.INSERT.name(), data.getSchemaEvaluationType(),
                        "创建评估方案", userId, userMapper.getUserNameById(userId), LocalDateTime.now());
            }
        }
        data.setCreator(userMapper.getUserNameById(userId));
        data.setCreateDate(createDate);
        data.setTaskId("00000000000000000000000000000000");
        data.setId(uuid);
        data.setUpdateDate(LocalDateTime.now());
        this.saveOrUpdate(data);
        return data.getId();
    }

    /**
     * 全量编辑方案（含基础信息、算法配置、场景数据）
     *
     * @param data        方案基础数据
     * @param treeId      指标树ID
     * @param sceneDtoList 场景DTO列表
     * @return 方案ID
     * @throws BusinessException 业务异常
     */
    @Override
    @Transactional
    public String editAll(SchemaEntity data, String treeId, List<SceneDTO> sceneDtoList) {
        //校验pg方案名字是否为空并给其设置基本参数保存进行保存
        String sid = edit(data, treeId);
        //根据sid查询任务对应的方法是否需要被修改
        Optional<EvaluateAlgEntity> one = Optional.ofNullable(evaluateAlgMapper.selectOne(new QueryWrapper<EvaluateAlgEntity>().eq("SID", sid)));
        if (one.isPresent()) {
            EvaluateAlgEntity algEntity = one.get();
            String evaluationMethod = data.getEvaluationMethod();
            //取出算法英文名
            EvaluationType typeByName = EvaluationType.getTypeByName(evaluationMethod);
            String name = null;
            //算法名不为空则取出中文名
            if (typeByName != EvaluationType.NONE) {
                name = typeByName.getChName();
            } else {
                //根据算法名取出脚本算法信息
                ScriptMethodEntity entity = scriptMethodMapper.queryById(evaluationMethod);
                if (entity != null) {
                    //取出脚本名称
                    name = entity.getScriptName();
                }
            }
            algEntity.setName(name);
            algEntity.setCode(evaluationMethod);
            //保存pg算法实体
            if (evaluateAlgMapper.selectById(algEntity.getId()) != null) {
                evaluateAlgMapper.updateById(algEntity);
            } else {
                evaluateAlgMapper.insert(algEntity);
            }
        }
        List<SceneEntity> entityList = JSONArray.parseArray(JSONArray.toJSONString(sceneDtoList), SceneEntity.class);
        //校验并保存样本数据
        sceneService.editMoreBySid(entityList, sid);
        return data.getId();
    }

    /**
     * 全量编辑方案 V2（增强版，自动处理指标树与默认算法模型）
     *
     * @param data        方案基础数据
     * @param treeId      指标树ID
     * @param sceneDtoList 场景DTO列表
     * @return 方案ID
     * @throws Exception 处理异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String editAllV2(SchemaEntity data, String treeId, List<SceneDTO> sceneDtoList) throws Exception {
        //现在没有所谓的手动模式和自动模式了，默认创建该方案任务
        DataDirectory dataDirectory = new DataDirectory();
        dataDirectory.setParentId("0");
        //以方案名称命名目录
        dataDirectory.setName(data.getName());

        //校验pg方案名字是否为空并给其设置基本参数保存进行保存
        String sid = edit(data, treeId);
        //创建完方案之后，调用指标模块接口，创建指标树
        List<IndicatorTreeEntity> indicatorTreeEntityLists = indicatorTreeService.find(IndicatorTreeEntity.builder().sid(sid).build());
        if (CollUtil.isEmpty(indicatorTreeEntityLists)) {
            indicatorTreeService.addV2(sid, data.getSchemaEvaluationType());
            indicatorTreeEntityLists = indicatorTreeService.find(IndicatorTreeEntity.builder().sid(sid).build());
        }
        String abilityTreeId = null;

        //根据sid查询任务对应的方法是否需要被修改
        if (indicatorTreeEntityLists != null) {
            for (IndicatorTreeEntity entity : indicatorTreeEntityLists) {
                if (entity.getIndicatorTreeType() == SchemaEvaluateType.ABILITY_EVALUATE.id) {
                    abilityTreeId = entity.getId();
                }
            }
            List<IndicatorEntity> indicatorEntities = indicatorMapper.selectList(new QueryWrapper<IndicatorEntity>()
                    .eq("SID", sid).eq("TREE_ID", abilityTreeId).eq("PID", "0"));
            if (!CollUtil.isEmpty(indicatorEntities)) {
                IndicatorEntity indicatorEntity = indicatorEntities.get(0);
                List<CalcModuleEntity> calcModuleEntities = iCalcModuleMapper.selectList(new QueryWrapper<CalcModuleEntity>()
                        .eq("CALC_TYPE", ModuleType.METHOD_MODEL.id).eq("indicator_id", indicatorEntity.getId())
                        .eq("SID", sid).eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id));
                if (!CollectionUtils.isEmpty(calcModuleEntities)) {
                    CalcModuleEntity calcModuleEntity = calcModuleEntities.get(0);
                    if (calcModuleEntity == null) {
                        // 自己创建默认方法模型
                        calcModuleEntity = CalcModuleEntity.builder()
                                .id(com.yw.common.utils.UUIDGenerator.getUUID())
                                .calcType(ModuleType.METHOD_MODEL.id)
                                .indicatorId(indicatorEntity.getId())
                                .defaultCalModule(CalModuleType.DEFAULT_MODEL.id)
                                .sid(data.getId())
                                .createTime(LocalDateTime.now()).build();
                    }
                    String evaluationMethod = data.getEvaluationMethod();
                    //取出算法英文名
                    EvaluationType typeByName = EvaluationType.getTypeByName(evaluationMethod);
                    String name = null;
                    //算法名不为空则取出中文名
                    if (typeByName != EvaluationType.NONE) {
                        name = typeByName.getChName();
                    } else {
                        //根据算法名取出脚本算法信息
                        ScriptMethodEntity entity = scriptMethodMapper.queryById(evaluationMethod);
                        if (entity != null) {
                            //取出脚本名称
                            name = entity.getScriptName();
                        }
                    }
                    calcModuleEntity.setName(name);
                    calcModuleEntity.setMethodId(evaluationMethod);
                    //保存pg算法实体
                    if (iCalcModuleMapper.selectList(new QueryWrapper<CalcModuleEntity>().eq("SID", sid)).isEmpty()) {
                        for (CalcModuleEntity moduleEntity : calcModuleEntities) {
                            iCalcModuleMapper.insert(moduleEntity);
                        }
                    }

                }
            }

        }
        List<SceneEntity> entityList = JSONArray.parseArray(JSONArray.toJSONString(sceneDtoList), SceneEntity.class);
        //校验并保存样本数据
        if (CollUtil.isNotEmpty(entityList)) {
            sceneService.editMoreBySid(entityList, sid);
        }
        return data.getId();
    }

    /**
     * 删除方案及所有关联数据（指标树、数据源、场景、想定、算子结果等）
     *
     * @param id 方案唯一标识
     * @throws RuntimeException 数据源或目录删除失败时抛出
     */
    @Override
    @Transactional
    public void delete(String id) {
        Optional<SchemaEntity> data = Optional.ofNullable(schemaMapper.selectById(id));
        if (data.isPresent()) {
            SchemaEntity schema = data.get();
            //删除指标及指标树下所有子指标和指标算法
            List<IndicatorTreeEntity> treeEntityList = indicatorTreeService.find(IndicatorTreeEntity.builder().sid(id).build());
            indicatorTreeService.removeBatchByIds(treeEntityList.stream().map(IndicatorTreeEntity::getId).collect(Collectors.toList()));
            //删除方案对应的数据目录
            if (StringUtils.isNotEmpty(schema.getDataDirectoryId())) {
                DataDirectory dataDirectoryServiceById = iDataDirectoryService.findById(data.get().getDataDirectoryId());
                if (!Objects.isNull(dataDirectoryServiceById)) {
                    try {
                        JSONObject jsonObject = new JSONObject(dataDirectoryServiceById.getMark());
                        String dataSrcId = jsonObject.getString("dataSrcId");
                        //删除数据源
                        datasrcService.delete(dataSrcId);
                        iDataDirectoryService.deleteDirectoryById(data.get().getDataDirectoryId(), true, true);
                    } catch (Exception e) {
                        throw new RuntimeException("服务器异常");
                    }
                }
            }

            List<SceneEntity> sceneEntityList = sceneService.list(new LambdaQueryWrapper<SceneEntity>().eq(SceneEntity::getSid, id));
            List<String> sceneIds = sceneEntityList.stream().map(t -> t.getId()).collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(sceneIds)){
                //删除算子结果
                calcorValuesService.remove(new LambdaQueryWrapper<CalCorValuesEntity>().in(CalCorValuesEntity::getSceneId, sceneIds));
            }

            List<ResourceEquipmentEntity> resourceEquipmentEntityList = resourceEquipmentService.list(new LambdaQueryWrapper<ResourceEquipmentEntity>().eq(ResourceEquipmentEntity::getBizId, id));
            List<String> resourceEquipmentIds = resourceEquipmentEntityList.stream().map(t -> t.getId()).collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(resourceEquipmentIds)){
                //删除装备要素值
                resourceAttributeValueService.remove(new LambdaQueryWrapper<ResourceAttributeValueEntity>().in(ResourceAttributeValueEntity::getResourceId, resourceEquipmentIds));
            }
            //删除杀伤链装备要素
            resourceEquipmentService.remove(new LambdaQueryWrapper<ResourceEquipmentEntity>().eq(ResourceEquipmentEntity::getBizId, id));

            //删除pg任务下所有的样本数据
            sceneService.deleteBySid(id);
            //删除pg任务下所有数据样本中需要用到的演习数据表字段
            sceneTableService.deleteBySid(id);
            //删除pg任务下保存的对应的pg算法
            //删除指标标准值
            indicatorStandardValuesService.deleteBySid(id);

            //删除想定装备
            sidesDirecService.remove(new LambdaQueryWrapper<SidesDirecEntity>().eq(SidesDirecEntity::getSchemaId, id));
            //删除指标计算结果
            indicatorValueService.remove(new LambdaQueryWrapper<IndicatorValuesEntity>().eq(IndicatorValuesEntity::getSid, id));
            //删除bp算法结果
            trendForecastService.remove(new LambdaQueryWrapper<TrendForecastEntity>().eq(TrendForecastEntity::getSid, id));
            //删除装备动态表头
            dynamicColumnService.remove(new LambdaQueryWrapper<DynamicColumnConfigEntity>().eq(DynamicColumnConfigEntity::getBizId, id));
            //删除装备动态关系
            relationshipInfoService.remove(new LambdaQueryWrapper<RelationshipInfoEntity>().eq(RelationshipInfoEntity::getBizId, id));


            schemaMapper.deleteById(id);


            //方案
            List<SceneScenarioEntity> sceneScenarioEntityList = sceneScenarioService.list(new LambdaQueryWrapper<SceneScenarioEntity>().eq(SceneScenarioEntity::getSid, id)
                    .eq(SceneScenarioEntity::getPid, "0"));
            List<String> sceneScenarioIds = sceneScenarioEntityList.stream().map(t -> t.getId()).collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(sceneScenarioIds)){
                //删除杀伤链
                sslResultService.remove(new LambdaQueryWrapper<SslResultEntity>().in(SslResultEntity::getSceneScenarioId,sceneScenarioIds));
                contributionRateCalculatedValueService.remove(new LambdaQueryWrapper<ContributionRateCalculatedValueEntity>().in(ContributionRateCalculatedValueEntity::getSceneScenarioId, sceneScenarioIds));
            }


            //删除想定
            try {
                sceneScenarioService.remove(new LambdaQueryWrapper<SceneScenarioEntity>().eq(SceneScenarioEntity::getSid, id));
            } catch (Exception e) {
                log.error("{}", e);
            }

        }
    }

    /**
     * 根据条件查询方案列表
     *
     * @param entity 查询条件封装对象
     * @return 匹配的方案实体列表
     */
    @Override
    public List<SchemaEntity> find(SchemaEntity entity) {
        QueryWrapper<SchemaEntity> wrapper = new QueryWrapper<SchemaEntity>();
        if (entity.getId() != null) {
            wrapper.eq("id", entity.getId());
        }
        if (entity.getName() != null) {
            wrapper.eq("name", entity.getName());
        }
        if (entity.getCode() != null) {
            wrapper.eq("code", entity.getCode());
        }

        if (entity.getTaskSource() != null) {
            wrapper.eq("task_source", entity.getTaskSource());
        }
        if (entity.getEquipmentOverview() != null) {
            wrapper.eq("equipment_overview", entity.getEquipmentOverview());
        }
        if (entity.getEquipmentKey() != null) {
            wrapper.eq("equipment_key", entity.getEquipmentKey());
        }
        if (entity.getTaskId() != null) {
            wrapper.eq("TASK_ID", entity.getTaskId());
        }
        if (entity.getVersion() != null) {
            wrapper.eq("VERSION", entity.getVersion());
        }
        if (entity.getTaskDescribe() != null) {
            wrapper.eq("TASK_DESCRIBE", entity.getDescribes());
        }
        if (entity.getModel() != null) {
            wrapper.eq("MODEL", entity.getModel());
        }
        if (entity.getExperimentId() != null) {
            wrapper.eq("EXPERIMENT_ID", entity.getExperimentId());
        }
        if (entity.getIsManualCreate() != null) {
            wrapper.eq("IS_MANUAL_CREATE", entity.getIsManualCreate());
        }
        if (entity.getIssuedTaskId() != null) {
            wrapper.eq("ISSUED_TASK_ID", entity.getIssuedTaskId());
        }
        if (entity.getEquipmentId() != null) {
            wrapper.eq("EQUIPMENT_ID", entity.getEquipmentId());
        }
        if (entity.getEquipmentSysId() != null) {
            wrapper.eq("EQUIPMENT_SYS_ID", entity.getEquipmentSysId());
        }
        if (entity.getEvaluationMethod() != null) {
            wrapper.eq("EVALUATION_METHOD", entity.getEvaluationMethod());
        }
        if (entity.getEvaluationType() != null) {
            wrapper.eq("EVALUATION_TYPE", entity.getEvaluationType());
        }
        if (entity.getEvaluationObject() != null) {
            wrapper.eq("EVALUATION_OBJECT", entity.getEvaluationObject());
        }
        if (entity.getEvaluationDate() != null) {
            wrapper.eq("EVALUATION_DATE", entity.getEvaluationDate());
        }
        if (entity.getSchemaCata() != null) {
            wrapper.eq("SCHEMA_CATA", entity.getSchemaCata());
        }
        if (entity.getSchemaCheck() != null) {
            wrapper.eq("SCHEMA_CHECK", entity.getSchemaCheck());
        }
        if (entity.getSchemaEvaluationType() != null) {
            wrapper.eq("SCHEMA_EVALUATION_TYPE", entity.getSchemaEvaluationType());
        }
        if (entity.getDataDirectoryName() != null) {
            wrapper.eq("DATA_DIRECTORY_ID", entity.getDataDirectoryId());
        }
        if (entity.getDataDirectoryName() != null) {
            wrapper.eq("DATA_DIRECTORY_NAME", entity.getDataDirectoryName());
        }
        if (entity.getUserId() != null) {
            wrapper.eq("USER_ID", entity.getUserId());
        }
        wrapper.orderByDesc("create_time");
        return schemaMapper.selectList(wrapper);
    }

    /**
     * 校验方案名称/ID唯一性
     *
     * @param entity 待校验方案实体
     * @throws BusinessException 校验失败时抛出
     */
    @Override
    public void validValue(SchemaEntity entity) {
        SchemaEntity query = new SchemaEntity();
        query.setTaskId(entity.getTaskId());
        query.setName(entity.getName());
        List<SchemaEntity> data = find(query);

        try {
            ValidValue.valid(entity.getId(), "方案名称", data);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 直接更新方案实体
     *
     * @param schemaEntity 方案实体
     */
    @Transactional
    @Override
    public void edit(SchemaEntity schemaEntity) {
        saveOrUpdate(schemaEntity);
    }

    /**
     * 查询方案关联的任务模型
     *
     * @param id 方案ID
     * @return 模型ID列表
     */
    @Override
    public List<String> findModel(String id) {
        return issuedTaskMapper.findModelBySid(id);
    }

    /**
     * 查询方案、场景、任务的关联关系分页数据
     *
     * @param pageNo   当前页码
     * @param pageSize 每页条数
     * @return 关联关系分页信息
     */
    @Override
    public PageInfo findSchameSceneTask(Integer pageNo, Integer pageSize) {
        List<Map<String, Object>> data = schemaMapper.findSSI();
        return PageHelperUtil.inventedPage(data, pageNo, pageSize);
    }

    /**
     * 根据装备/装备体系ID查询关联方案的指标列表
     *
     * @param obj 装备/体系ID映射集合（包含trunkList等）
     * @return 指标信息列表
     * @throws BusinessException 无匹配指标时抛出
     */
    @Override
    public List<Map<String, Object>> findIndicatorByEquId(Map<String, Object> obj) {
        List<String> ids = Lists.newArrayList();
        if (obj.get("trunkList") != null) {
            List<String> trunkList = com.yw.common.utils.ArrayUtils.stringToList(obj.get("trunkList").toString().replace("[", "").replace("]", ""));
            String judge = trunkList.get(trunkList.size() - 1);
            ids = trunkList.subList(0, trunkList.size() - 1);
            if (Integer.valueOf(judge.trim()) <= 2) {
                for (int i = 0; i < ids.size(); i++) {
                    ids.set(i, "eSys" + ids.get(i).trim());
                }
            } else {
                for (int i = 0; i < ids.size(); i++) {
                    ids.set(i, "equ" + ids.get(i).trim());
                }
            }
        }
        int treeNum = schemaMapper.showcount(ids);
        List<Map<String, Object>> indicatorList = schemaMapper.findIndicatorByEquipmentId(ids, null);

        if (CollectionUtils.isEmpty(indicatorList)) {
            throw new BusinessException("没有对应的指标！");
        }
        // 按照指标名进行一次去重
        List<Map<String, Object>> indicatorListNew = indicatorList.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(item -> item.get("NAME").toString()))), ArrayList::new)
                );

        if (CollUtil.isEmpty(indicatorListNew)) {
            return Lists.newArrayList();
        }
        List<Map<String, Object>> one = new ArrayList<>();
        Set<String> setIds = new HashSet<>();
        // 根指标
        List<Map<String, Object>> root = indicatorListNew.stream().filter(item -> {
            Object level = item.get("LEVEL");
            if (level == null || "0".equals(item.get("LEVEL").toString())) {
                item.put("LEVEL", 0);
                return true;
            }
            return false;
        }).map(item -> {
            List<Map<String, Object>> cList = new ArrayList<>();
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("field", item.get("ID"));
            rm.put("name", item.get("NAME"));
            rm.put("childFiled", Arrays.asList());
            cList.add(rm);
            setIds.add(item.get("ID").toString());
            return rm;
        }).collect(Collectors.toList());

        // 获取叶层级
        String max = String.valueOf(indicatorListNew.stream().mapToInt(item -> Integer.parseInt(item.get("LEVEL").toString())).max().getAsInt());
        String maxLevel = max;
        List<Map<String, Object>> middleIndicator = Lists.newArrayList(indicatorListNew);
        // 筛选中间指标
        middleIndicator = indicatorListNew.stream()
                .filter(item -> !("0".equals(item.get("LEVEL").toString())) && !(max.equals(item.get("LEVEL").toString())))
                .collect(Collectors.toList());
        List<Map<String, Object>> middelTree = new ArrayList<>();
        for (Map<String, Object> item : middleIndicator) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", item.get("ID"));
            m.put("name", item.get("NAME"));
            List<Map<String, Object>> child = toTree(middleIndicator, item.get("ID").toString());
            m.put("childField", child);
            middelTree.add(m);
        }
        String finalMaxLevel = maxLevel;
        List<Map<String, Object>> leafIndicator = indicatorListNew.stream()
                .filter(item -> item.get("LEVEL").toString().equals(finalMaxLevel) && !setIds.contains(item.get("ID").toString()))
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("field", item.get("ID"));
                    map.put("name", item.get("NAME"));
                    map.put("childField", Arrays.asList());
                    return map;
                })
                .collect(Collectors.toList());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", "ROOT");
        m.put("name", "根指标");
        m.put("childField", root);
        one.add(m);

        if (CollUtil.isNotEmpty(middelTree)) {
            Map<String, Object> middle = new LinkedHashMap<>();
            middle.put("field", "MIDDLE");
            middle.put("name", "中间指标");
            middle.put("childField", middelTree);
            one.add(middle);
        }

        if (CollUtil.isNotEmpty(leafIndicator)) {
            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("field", "LEAF");
            leaf.put("name", "叶子指标");
            leaf.put("childField", leafIndicator);
            one.add(leaf);
        }
        return one;
    }

    /**
     * 根据装备/装备体系ID查询关联方案的指标列表（V2版本，支持按指标树分组）
     *
     * @param obj 装备/体系ID映射集合
     * @return 分组后的指标信息列表
     * @throws BusinessException 无匹配指标时抛出
     */
    @Override
    public List<Map<String, Object>> findIndicatorByEquIdV2(Map<String, Object> obj) {
        List<String> ids = null;
        if (obj.get("trunkList") != null) {
            List<String> trunkList = com.yw.common.utils.ArrayUtils.stringToList(obj.get("trunkList").toString().replace("[", "").replace("]", ""));
            String judge = trunkList.get(trunkList.size() - 1);
            ids = trunkList.subList(0, trunkList.size() - 1);
            if (Integer.valueOf(judge.trim()) <= 2) {
                for (int i = 0; i < ids.size(); i++) {
                    ids.set(i, "eSys" + ids.get(i).trim());
                }
            } else {
                for (int i = 0; i < ids.size(); i++) {
                    ids.set(i, "equ" + ids.get(i).trim());
                }
            }
        }

        List<Map<String, Object>> indicatorList = schemaMapper.findIndicatorByEquipmentIdV2(ids);

        if (CollectionUtils.isEmpty(indicatorList)) {
            throw new BusinessException("没有对应的指标！");
        }

        if (CollUtil.isEmpty(indicatorList)) {
            return Lists.newArrayList();
        }

        Map<String, List<Map<String, Object>>> treeList = indicatorList.stream().collect(Collectors.groupingBy(item -> item.get("TREE_ID").toString() + "-" + item.get("TREE_NAME")));

        List<Map<String, Object>> one = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> indicatorListNewEntry : treeList.entrySet()) {
            String key = indicatorListNewEntry.getKey();
            String[] split = key.split("-");
            String treeId = split[0];
            String treeName = split[1];

            List<Map<String, Object>> indicatorListNew = indicatorListNewEntry.getValue();
            // 根指标
            List<Map<String, Object>> root = indicatorListNew.stream().filter(item -> {
                Object level = item.get("LEVEL");
                if (level == null || "0".equals(item.get("LEVEL").toString())) {
                    item.put("LEVEL", 0);
                    return true;
                }
                return false;
            }).map(item -> {
                List<Map<String, Object>> cList = new ArrayList<>();
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("field", item.get("ID"));
                rm.put("name", item.get("NAME"));
                rm.put("childFiled", Arrays.asList());
                cList.add(rm);
                return rm;
            }).collect(Collectors.toList());

            // 获取叶层级
            String max = String.valueOf(indicatorListNew.stream().mapToInt(item -> Integer.parseInt(item.get("LEVEL").toString())).max().getAsInt());
            String maxLevel = max;
            List<Map<String, Object>> middleIndicator = Lists.newArrayList(indicatorListNew);
            // 筛选中间指标
            middleIndicator = indicatorListNew.stream()
                    .filter(item -> !("0".equals(item.get("LEVEL").toString())) && !(max.equals(item.get("LEVEL").toString())))
                    .collect(Collectors.toList());
            // 中间指标建树
            List<Map<String, Object>> middelTree = new ArrayList<>();
            for (Map<String, Object> item : middleIndicator) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("field", item.get("ID"));
                m.put("name", item.get("NAME"));
                List<Map<String, Object>> child = toTree(middleIndicator, item.get("ID").toString());
                m.put("childField", child);
                middelTree.add(m);
            }
            // 叶指标
            String finalMaxLevel = maxLevel;
            List<Map<String, Object>> leafIndicator = indicatorListNew.stream().filter(item -> item.get("LEVEL").toString().equals(finalMaxLevel))
                    .map(item -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("field", item.get("ID"));
                        map.put("name", item.get("NAME"));
                        map.put("childField", Arrays.asList());
                        return map;
                    })
                    .collect(Collectors.toList());
            Map<String, Object> m = new LinkedHashMap<>();

            m.put("field", "ROOT");
            m.put("name", "根指标");
            m.put("childField", root);
            Map<String, Object> middle = new LinkedHashMap<>();
            middle.put("field", "MIDDLE");
            middle.put("name", "中间指标");
            middle.put("childField", middelTree);
            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("field", "LEAF");
            leaf.put("name", "叶子指标");
            leaf.put("childField", leafIndicator);

            List<Map<String, Object>> childField = Lists.newArrayList();

            childField.add(m);
            childField.add(middle);
            childField.add(leaf);

            Map<String, Object> map = Maps.newHashMap();

            map.put("childField", childField);
            map.put("field", treeId);
            map.put("name", treeName);
            one.add(map);
        }
        return one;
    }

    /**
     * 递归构建指标树结构
     *
     * @param list 指标列表
     * @param pid  父节点ID
     * @return 子节点树结构列表
     */
    private List<Map<String, Object>> toTree(List<Map<String, Object>> list, String pid) {
        List<Map<String, Object>> result = new ArrayList<>();
        list.stream().forEach(item -> {
            if (item.get("PID").toString().equals(pid)) {
                Map<String, Object> map = new HashMap<>();
                map.put("field", item.get("ID"));
                map.put("name", item.get("NAME"));
                List<Map<String, Object>> child = toTree(list, item.get("ID").toString());
                map.put("childField", child);
                result.add(map);
            }
        });
        return result;
    }

    /**
     * 查询任务管理列表（用于前端展示）
     *
     * @return 任务信息视图对象列表
     */
    @Override
    public List<SchemaInfoVO> findList() {
        List<SchemaInfoVO> vo = schemaMapper.findList();
        vo.stream().forEach(s -> {
            String scriptId = s.getScriptId();
            String scriptName = s.getScriptName();
            //判断脚本名是否为空 为空则根据脚背id返回脚本名
            if (StringUtils.isEmpty(scriptName)) {
                EvaluationType byName = EvaluationType.getTypeByName(scriptId);
                if (byName != EvaluationType.NONE) {
                    s.setScriptName(byName.getChName());
                }
            }
            String evaluationObject = s.getEvaluationObject();
            s.setEvaluationObjectId(s.getEvaluationObject());
            //根据pg对象返回对象名
            String theName = theName(evaluationObject);
            s.setEvaluationObject(theName);
            Date evaluationDate = s.getCreateDate();
            if (evaluationDate == null) {
                s.setCreateDate(new Date());
            }
        });

        return vo;
    }

    /**
     * 根据名称及时间范围分页查询任务信息
     *
     * @param name      任务名称
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param offSet    起始页
     * @param limit     每页条数
     * @param type      任务类型
     * @param userId    用户ID
     * @return 分页任务视图对象
     */
    @Override
    public com.github.pagehelper.PageInfo<SchemaInfoVO> findByName(String name, String startTime, String endTime, Integer offSet, Integer limit, Integer type, String userId) {
        if (StringUtils.isNotEmpty(startTime)) {
            startTime = startTime + " 00:00:00";

        }
        if (StringUtils.isNotEmpty(endTime)) {
            endTime = endTime + " 23:59:59";
        }
        if (offSet != null && offSet > 0) {
            PageHelper.startPage(offSet, limit);
        }
        //根据任务名称和开始结束时间查询任务基本信息
        List<SchemaInfoVO> vo = schemaMapper.findByName(name, startTime, endTime, type, null, null, null);
        //设置基础信息
        vo.stream().forEach(s -> {
            String scriptId = s.getScriptId();
            String scriptName = s.getScriptName();
            if (StringUtils.isEmpty(scriptName)) {
                EvaluationType byName = EvaluationType.getTypeByName(scriptId);
                if (byName != EvaluationType.NONE) {
                    s.setScriptName(byName.getChName());
                }
            }
            String evaluationObject = s.getEvaluationObject();
            s.setEvaluationObjectId(s.getEvaluationObject());
            String theName = theName(evaluationObject);
            s.setEvaluationObject(theName);
        });
        com.github.pagehelper.PageInfo<SchemaInfoVO> info = new com.github.pagehelper.PageInfo<>(vo);

        return info;
    }

    /**
     * 根据名称及时间范围分页查询任务信息（V2增强版，支持多ID过滤与排序）
     *
     * @param name      任务名称
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param offSet    起始页
     * @param limit     每页条数
     * @param type      任务类型
     * @param ids       过滤ID列表
     * @param userId    用户ID
     * @param sortType  排序类型
     * @return 分页任务视图对象
     */
    @Override
    public PageInfo<SchemaInfoVO> findByNameV2(String name, String startTime, String endTime, Integer offSet, Integer limit, Integer type, List<String> ids, String userId, String sortType) {
        if (StringUtils.isNotEmpty(startTime)) {
            startTime = startTime + " 00:00:00";
        }
        if (StringUtils.isNotEmpty(endTime)) {
            endTime = endTime + " 23:59:59";
        }
        if (offSet != null && offSet > 0) {
            PageHelper.startPage(offSet, limit);
        }
        //根据任务名称和开始结束时间查询任务基本信息
        List<String> idLists = null;
        if (!CollUtil.isEmpty(ids)) {
            idLists = ids;
        }
        List<SchemaInfoVO> vo = schemaMapper.findByName(name, startTime, endTime, type, idLists, userId, sortType);
        //设置基础信息
        vo.stream().forEach(s -> {
            String scriptId = s.getScriptId();
            String scriptName = s.getScriptName();
            if (StringUtils.isEmpty(scriptName)) {
                EvaluationType byName = EvaluationType.getTypeByName(scriptId);
                if (byName != EvaluationType.NONE) {
                    s.setScriptName(byName.getChName());
                }
            }
            s.setEvaluationObjectId(s.getEvaluationObject());
        });
        com.github.pagehelper.PageInfo<SchemaInfoVO> info = new com.github.pagehelper.PageInfo<>(vo);

        return new PageInfo<>(vo);
    }

    /**
     * 根据评估对象标识获取装备或装备体系的名称
     *
     * @param evaluationObject 评估对象标识（以 equ 开头为装备，以 eSys 开头为体系）
     * @return 装备名称或装备体系名称
     */
    public String theName(String evaluationObject) {
        if (StringUtils.isNotEmpty(evaluationObject)) {
            String equName = null;
            if (evaluationObject.contains("equ")) {
                // 装备
                evaluationObject = evaluationObject.replace("equ", "");
                //根据id查询名字
                Optional<EquipmentEntity> repository = equipmentService.findById(Integer.valueOf(evaluationObject));
                if (repository.isPresent()) {
                    equName = repository.get().getEquName();
                }
            } else {
                // 装备体系
                evaluationObject = evaluationObject.replace("eSys", "");
                //根据id查询名字
                Optional<EquipmentSystemEntity> entity = equipmentSystemService.findById(Integer.valueOf(evaluationObject));
                if (entity.isPresent()) {
                    equName = entity.get().getEquSysName();
                }
            }
            return equName;
        }
        return new String();
    }

    /**
     * 展示任务评估结果详情
     *
     * @param sid     方案ID
     * @param sceneId 场景ID
     * @return 任务结果视图对象
     */
    @Override
    public SchemaResultVO showTaskResult(String sid, String sceneId) {
        SchemaResultVO vo = SchemaResultVO.valueOf();
//        Optional<SchemaEntity> schemaEntity = schemaRepository.findById(sid);
        Optional<SchemaEntity> schemaEntity = Optional.ofNullable(schemaMapper.selectById(sid));
        if (schemaEntity.isPresent()) {
            String name = new String();
            SchemaEntity entity = schemaEntity.get();
            String assessObject = entity.getEvaluationObject();
            String method = new String();
            List<String> equipmentNames = new ArrayList<>();
            EvaluationType evaluationType = EvaluationType.getTypeByName(entity.getEvaluationMethod());
            if (evaluationType != EvaluationType.NONE) {
                method = evaluationType.getChName();
            } else {
                ScriptMethodEntity methodEntity = scriptMethodMapper.queryById(entity.getEvaluationMethod());
                if (methodEntity != null) {
                    method = methodEntity.getScriptName();
                }
            }

            //获取pg对象名称
            if (StringUtils.isNotEmpty(assessObject)) {
                if (assessObject.contains("equ")) {
                    String substring = assessObject.substring(3, assessObject.length());
                    Optional<EquipmentEntity> equ = equipmentService.findById(Integer.valueOf(substring));
                    if (equ.isPresent()) {
                        name = equ.get().getEquName();
                        equipmentNames.add(name);
                    }
                } else {
                    Optional<EquipmentSystemEntity> eSys = equipmentSystemService.
                            findById(Integer.valueOf(assessObject.substring(4)));
                    if (eSys.isPresent()) {
                        name = eSys.get().getEquSysName();
                        List<Integer> equipmentIds = JSONArray.parseArray(eSys.get().getComposition(), Integer.class);
                        //查询体系中所有装备名称
                        equipmentNames = equipmentService.listByIds(equipmentIds)
                                .stream().map(EquipmentEntity::getEquName).collect(Collectors.toList());
                    }
                }
            }
            //获取当前任务下的根指标
            String value = "";
            String standardLabelName = "";
            String range = "";
            List<IndicatorEntity> indicatorEntityLists = indicatorMapper.selectList(new QueryWrapper<IndicatorEntity>().eq("level", 0).eq("SID", sid));
            IndicatorEntity abilityIndicatorEntity = null;
            List<IndicatorTreeEntity> indicatorTreeEntities = indicatorTreeService.find(IndicatorTreeEntity.builder().sid(sid).build());
            String abilityIndicatorTreeId = null;
            for (IndicatorTreeEntity treeEntity : indicatorTreeEntities) {
                if (treeEntity.getIndicatorTreeType() == SchemaEvaluateType.ABILITY_EVALUATE.id) {
                    abilityIndicatorTreeId = treeEntity.getId();
                    for (IndicatorEntity indicatorEntity : indicatorEntityLists) {
                        if (indicatorEntity.getTreeId().equals(abilityIndicatorTreeId)) {
                            abilityIndicatorEntity = indicatorEntity;
                        }
                    }
                }
            }
            if (StringUtils.isNotEmpty(abilityIndicatorTreeId)) {
                // 效能指标
                Optional<IndicatorEntity> indicatorEntityOptional = Optional.ofNullable(abilityIndicatorEntity);
                return SchemaResultVO.valueOf(entity, name, value, indicatorEntityOptional, method, standardLabelName, range, equipmentNames);
            }
            for (IndicatorEntity indicatorEntity : indicatorEntityLists) {
                if (indicatorEntity.getTreeId().equals(abilityIndicatorTreeId)) {
                    abilityIndicatorEntity = indicatorEntity;
                    break;
                }
            }
            if (!Objects.isNull(abilityIndicatorEntity)) {
                //根据当前指标获取对应结果
                Optional<IndicatorValuesEntity> valuesEntity = Optional.ofNullable(indicatorValueService.getOne(new QueryWrapper<IndicatorValuesEntity>().eq("scene_id", sceneId).eq("indicator_id", abilityIndicatorEntity.getId())));
                if (valuesEntity.isPresent()) {
                    IndicatorValuesEntity indicatorValuesEntity = valuesEntity.get();
                    value = indicatorValuesEntity.getNormalizedValue();
                    standardLabelName = indicatorValuesEntity.getStandardLabelName();
                    if (!StringUtils.isEmpty(standardLabelName)) {
                        // 获取评估准则范围
                        List<IndicatorStandardVO> byIndicatorIdData = standardLabelsService.findByIndicatorId(indicatorValuesEntity.getIndicatorId());
                        if (CollUtil.isNotEmpty(byIndicatorIdData)) {
                            for (IndicatorStandardVO indicatorStandardVO : byIndicatorIdData) {
                                if (standardLabelName.equals(indicatorStandardVO.getLableName())) {
                                    range = String.format("%s - %s", indicatorStandardVO.getMin(), indicatorStandardVO.getMax());
                                }
                            }
                        }
                    }

                }
            }
            if (!Objects.isNull(abilityIndicatorEntity)) {
                // 获取当前节点的指标准则值
                Optional<IndicatorEntity> indicatorEntityOptional = Optional.of(abilityIndicatorEntity);
                return SchemaResultVO.valueOf(entity, name, value, indicatorEntityOptional, method, standardLabelName, range, equipmentNames);
            }
        }
        return vo;
    }


    /**
     * 根据ID列表批量查询方案
     *
     * @param ids 方案ID集合
     * @return 方案实体列表
     */
    @Override
    public List<SchemaEntity> findListByIds(List<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Lists.newArrayList();
        }
        return schemaMapper.selectBatchIds(ids);
    }

    /**
     * 批量调整方案选中状态
     *
     * @param dto 包含目标ID列表的调整参数
     * @throws BusinessException 参数为空时抛出
     */
    @Transactional
    @Override
    public void adjustSelectedStatus(SchemaCheckDTO dto) {
        Assert.notNull(dto, "请选择调整项");
        List<String> putIds = dto.getPutIds();
        if (!CollUtil.isEmpty(putIds)) {
            // 修改
            schemaMapper.adjustSelectedStatusAll(SchemaCheckType.UNSELECTED_STATUS.id);
            schemaMapper.adjustSelectedStatus(putIds, SchemaCheckType.SELECTED_STATUS.id);
        }

    }

    /**
     * 获取数据关联回溯分析结果（V2）
     *
     * @param id 节点ID
     * @return 关联回溯树节点列表
     */
    @Override
    public List<com.yw.common.utils.TreeToolHelp> queryAssociatedBacktrackInfo2(String id) {
        //获取左侧任务树
        String defaultTaskId = "00000000000000000000000000000000";
        List<com.yw.common.utils.TreeToolHelp> mainNavigateTree = iTaskService.findMainNavigateTreeTwo(defaultTaskId, null, null, null, SchemaCheckType.NODE.id);
        List<com.yw.common.utils.TreeToolHelp> result = mainNavigateTree.stream().filter(item -> Objects.equals(id, item.getNodeId()))
                .collect(Collectors.toList());
        return result;
    }

    /**
     * 获取数据关联回溯分析结果
     *
     * @param id 任务条件ID
     * @return 关联回溯树节点列表
     */
    @Override
    public List<com.yw.common.utils.TreeToolHelp> queryAssociatedBacktrackInfo(String id) {
        //获取左侧任务树
        String defaultTaskId = "00000000000000000000000000000000";
        List<com.yw.common.utils.TreeToolHelp> mainNavigateTree = iTaskService.findMainNavigateTreeTwo(defaultTaskId, null, null, null, SchemaCheckType.NODE.id);
        List<com.yw.common.utils.TreeToolHelp> result = mainNavigateTree.stream().filter(item -> Objects.equals(id, item.getNodeId()))
                .collect(Collectors.toList());
        return result;
    }
}
