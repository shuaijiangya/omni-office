package com.yw.module.calculation.indicatorCalculation.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.common.defines.constants.ServiceConstant;
import com.yw.common.defines.enums.CalModuleType;
import com.yw.common.defines.enums.ModuleType;
import com.yw.common.utils.HttpUtils;
import com.yw.common.utils.UUIDGenerator;
import com.yw.common.utils.UserUtil;
import com.yw.framework.backMgr.dict.service.DictService;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.framework.common.module.login.mapper.UserMapper;
import com.yw.framework.common.module.table.response.BaseResponse;
import com.yw.framework.common.module.table.response.BaseTableHeaderBody;
import com.yw.module.calculation.domain.entity.CalcModuleEntity;
import com.yw.module.calculation.domain.entity.CalcorEntity;
import com.yw.module.calculation.domain.entity.ScriptMethodEntity;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.domain.vo.CalcModuleVo;
import com.yw.module.calculation.indicatorCalculation.domain.dto.DictionaryOrderParaDTO;
import com.yw.module.calculation.indicatorCalculation.mapper.ICalcModuleMapper;
import com.yw.module.calculation.indicatorCalculation.service.ICalcModuleService;
import com.yw.module.calculation.indicatorCalculation.service.ICalcorService;
import com.yw.module.calculation.indicatorCalculation.strategy.algParser.impl.BPParser;
import com.yw.module.calculation.mapper.ScriptMethodMapper;
import com.yw.module.indicator.domain.dto.BpModuleConfigDTO;
import com.yw.module.indicator.domain.dto.HeaderColumnsDTO;
import com.yw.module.indicator.domain.dto.MergeTableDTO;
import com.yw.module.indicator.domain.dto.TableDataDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorTreeEntity;
import com.yw.module.indicator.domain.vo.CalcModuleTreeParamRequest;
import com.yw.module.indicator.service.IIndicatorService;
import com.yw.module.indicator.service.IIndicatorTreeService;
import com.yw.module.plan.domain.entity.SchemaEntity;
import com.yw.module.plan.service.ISchemaService;
import com.yw.module.system.domain.dto.LogIndicatorSystemDto;
import com.yw.module.system.domain.entity.IndicatorTypeEnum;
import com.yw.module.system.domain.enums.BusinessType;
import com.yw.module.system.mapper.PGLogMapper;
import dm.jdbc.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 计算模型service
 *
 * @Author admin
 * @Description: 计算模型service
 * @Date: 2020/12/8 9:29
 */
@Service
public class CalcModuleServiceImpl extends ServiceImpl<ICalcModuleMapper, CalcModuleEntity> implements ICalcModuleService {

    /**
     * 字典服务
     */
    @Resource
    private DictService dictService;
    /**
     * python服务地址
     */
    private static final String URL_CODE = "url";
    /**
     * python服务
     */
    private static final String PYTHON_URL = "pythonUrl";

    /**
     * 算子服务
     */
    @Lazy
    @Autowired
    private ICalcorService calcorService;

    /**
     * 方案服务
     */
    @Autowired
    private ISchemaService schemaService;

    /**
     * 算法模型仓储
     */
    @Autowired
    private ICalcModuleMapper iCalcModuleMapper;


    /**
     * 指标树
     */
    @Autowired
    private IIndicatorTreeService indicatorTreeService;


    /**
     * 指标服务
     */
    @Autowired
    private IIndicatorService indicatorService;
    /**
     * 脚本配置
     */
    @Autowired
    private ScriptMethodMapper scriptMethodMapper;

    /**
     * 评估日志仓储
     */
    @Resource
    private PGLogMapper pgLogMapper;
    /**
     * 用户仓储
     */
    @Resource
    private UserMapper userMapper;

    public CalcModuleServiceImpl(@Lazy ISchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * 查询
     *
     * @param id 算法模型ID
     * @return
     */
    @Override
    public Optional<CalcModuleEntity> findById(String id) {
//        return calcModuleRepository.findById(id);
        return Optional.ofNullable(this.getById(id));
    }

    /**
     * 查询
     *
     * @param entity 算法模型对象
     * @return
     */
    @Override
    public List<CalcModuleEntity> find(CalcModuleEntity entity) {
        // 获取方案id
        Example<CalcModuleEntity> of = Example.of(entity);
//        List<CalcModuleEntity> all = calcModuleRepository.findAll(of, Sort.by("createTime").descending());
        List<CalcModuleEntity> all = this.list(new QueryWrapper<CalcModuleEntity>().setEntity(entity).orderByDesc("CREATE_TIME"));
        return all;
    }


    /**
     * 获取所有计算模型信息
     *
     * @return
     */
    @Override
    public List<CalcModuleVo> showCalcList() {
        // 获取任务信息
//        List<SchemaEntity> schemaEntities = schemaService.find(new SchemaEntity());
        List<SchemaEntity> schemaEntities = schemaService.list();
        Map<String, String> schemaNameMap = Maps.newHashMap();

        //获取指标信息
//        List<IndicatorEntity> indicatorEntities = indicatorRepository.findAll(Sort.by("CREATE_DATE").descending());
        List<IndicatorEntity> indicatorEntities = indicatorService.list(new QueryWrapper<IndicatorEntity>().orderByDesc("create_time"));
        //key 指标id value 指标名称
        Map<String, String> indicatorNameMap = Maps.newHashMap();
        //key 指标树id value 指标树名称
        Map<String, String> indicatorTreeNameMap = Maps.newHashMap();
        //key 指标id， value 所属指标树名称
        Map<String, String> indicatorIsTreeName = Maps.newHashMap();

        //获取指标树信心
//        List<IndicatorTreeEntity> treeRepositoryAll = indicatorTreeRepository.findAll(Sort.by("CREATE_DATE").descending());
        List<IndicatorTreeEntity> treeRepositoryAll = indicatorTreeService.list(new QueryWrapper<IndicatorTreeEntity>().orderByDesc("create_time"));
        //给上方map赋初始值
        if (CollUtil.isNotEmpty(schemaEntities)) {
            schemaEntities.forEach(sc -> {
                schemaNameMap.put(sc.getId(), sc.getName());
            });
        }
        if (CollUtil.isNotEmpty(treeRepositoryAll)) {
            treeRepositoryAll.forEach(tree -> {
                indicatorTreeNameMap.put(tree.getId(), tree.getName());
            });
        }
        if (CollUtil.isNotEmpty(indicatorEntities)) {
            indicatorEntities.forEach(in -> {
                indicatorNameMap.put(in.getId(), in.getName());
                indicatorIsTreeName.put(in.getId(), indicatorTreeNameMap.get(in.getTreeId()));
            });
        }
        //获取所有计算模型
        List<CalcModuleVo> all = iCalcModuleMapper.findAll();
        //组装响应计算模型数据
        if (all != null && !all.isEmpty()) {
            all.forEach(s -> {
                if (StringUtils.isEmpty(s.getCreateTime())) {
                    s.setCreateTime(new Date());
                }
                s.setsName(schemaNameMap.get(s.getSid()));
                s.setIndicatorName(indicatorNameMap.get(s.getIndicatorId()));
                s.setTreeName(indicatorIsTreeName.get(s.getIndicatorId()));
            });
        }
        return all;
    }

    /**
     * 计算模型新增与修改
     *
     * @param data 指标计算模型对象
     * @return
     */
    @Override
    @Transactional
    public String edit(CalcModuleEntity data) {

        // 校验对象是否存在
        if (StringUtils.isNotBlank(data.getId())) {
            Optional<CalcModuleEntity> oldData = findById(data.getId());
            if (!oldData.isPresent()) throw new BusinessException(ServiceConstant.NO_SUCK_OBJECT);
        }

        String uuid = StringUtils.isBlank(data.getId()) ? UUIDGenerator.getUUID() : data.getId();
        data.setCreateTime(LocalDateTime.now());
        data.setId(uuid);
        //存储
//        calcModuleRepository.saveAndFlush(data);
        this.saveOrUpdate(data);
        SchemaEntity querySchema = new SchemaEntity();
        querySchema.setId(data.getSid());
        List<SchemaEntity> schemaEntities = schemaService.find(querySchema);
        if (CollectionUtils.isEmpty(schemaEntities)) throw new BusinessException("方案不存在");
        // todo 不理解
//        SchemaEntity schemaEntity = schemaEntities.get(0);
//        schemaService.edit(schemaEntity, null);

//        try {
//            //记录操作日志
//            String userId = UserUtil.getUserId();
//            LogIndicatorSystemDto logEntity = LogIndicatorSystemDto.getLog(data.getName(), userId, userMapper.getUserNameById(userId),
//                    BusinessType.UPDATE.name(), IndicatorTypeEnum.ABILITY_INDICATOR.getId(), "编辑指标算法");
//            pgLogMapper.insert(logEntity);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return uuid;
    }

    /**
     * 算法模型编辑
     *
     * @param data       算法模型数据
     * @param moduleType 算法模型类型
     * @return
     */
    @Override
    public String editV2(CalcModuleEntity data, ModuleType moduleType) {
        //取出算法英文名
        if (moduleType == ModuleType.METHOD_MODEL) {
            EvaluationType typeByName = EvaluationType.getTypeByName(data.getMethodId());
            String name = null;
            //算法名不为空则取出中文名
            if (typeByName != EvaluationType.NONE) {
                name = typeByName.getChName();
            } else {
                //根据算法名取出脚本算法信息
                ScriptMethodEntity entity = scriptMethodMapper.queryById(data.getMethodId());
                if (entity != null) {
                    //取出脚本名称
                    name = entity.getScriptName();
                }
            }
            data.setName(name);
        }
        // 校验对象是否存在
        if (StringUtils.isNotBlank(data.getId())) {
            Optional<CalcModuleEntity> oldData = findById(data.getId());
            if (!oldData.isPresent()) throw new BusinessException(ServiceConstant.NO_SUCK_OBJECT);
        }

        String uuid = StringUtils.isBlank(data.getId()) ? UUIDGenerator.getUUID() : data.getId();
        data.setCreateTime(LocalDateTime.now());
        data.setId(uuid);
        //存储
//        calcModuleRepository.saveAndFlush(data);
        this.saveOrUpdate(data);
        SchemaEntity querySchema = new SchemaEntity();
        querySchema.setId(data.getSid());
        List<SchemaEntity> schemaEntities = schemaService.find(querySchema);
        if (CollectionUtils.isEmpty(schemaEntities)) throw new BusinessException("方案不存在");
//        SchemaEntity schemaEntity = schemaEntities.get(0);
//        schemaService.edit(schemaEntity, null);

//        try {
//            //记录操作日志
//            String userId = UserUtil.getUserId();
//            LogIndicatorSystemDto logEntity = LogIndicatorSystemDto.getLog(data.getName(), userId, userMapper.getUserNameById(userId),
//                    BusinessType.INSERT.name(), IndicatorTypeEnum.ABILITY_INDICATOR.getId(), "创建指标算法");
//            pgLogMapper.insert(logEntity);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return uuid;
    }

    /**
     * 设置默认计算模型
     *
     * @param id               主键id
     * @param defaultCalModule 默认计算模型
     * @param indicatorId      指标id
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateDefaultCalModule(String id, Integer defaultCalModule, String indicatorId) {

        // 获取所有指标模型
        List<CalcModuleEntity> calcModuleEntities = this.list(new QueryWrapper<CalcModuleEntity>().eq("indicator_id", indicatorId));
        // 日常判空
        if (CollUtil.isEmpty(calcModuleEntities)) {
            return;
        }

        // 循环判断是否属于指定id
        for (CalcModuleEntity entity : calcModuleEntities) {
            if (!id.equals(entity.getId())) {
                //false 设置非默认计算模型
                entity.setDefaultCalModule(CalModuleType.NON_DEFAULT_MODEL.id);
                continue;
            }

            //yes 设置默认计算模型
            entity.setDefaultCalModule(CalModuleType.DEFAULT_MODEL.id);

            if (entity.getCalcType() == ModuleType.METHOD_MODEL.id) {
                String sid = entity.getSid();
                String indicatorId1 = entity.getIndicatorId();
                Optional<IndicatorEntity> indicatorEntity = Optional.ofNullable(indicatorService.getById(indicatorId1));
                if (indicatorEntity.isPresent()) {
                    String pid = indicatorEntity.get().getPid();
                    if ("0".equals(pid)) {
                        Optional<SchemaEntity> schemaEntityOptional = schemaService.findById(sid);
                        if (schemaEntityOptional.isPresent()) {
                            SchemaEntity schemaEntity = schemaEntityOptional.get();
                            schemaEntity.setEvaluationMethod(entity.getMethodId());
                            schemaService.edit(schemaEntity);
                        }
                    }
                }
            }
            // 统一存储
            this.updateBatchById(calcModuleEntities);
        }
    }

    /**
     * 展示所有指标算法信息移除JSON
     *
     * @param id   算法模型ID
     * @param flag 标记
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, boolean flag) {

        Optional<CalcModuleEntity> data = findById(id);
        if (!data.isPresent()) return;
        CalcModuleEntity calcModuleEntity = data.get();

        if (!flag) {
            if (calcModuleEntity.getCalcType() == ModuleType.METHOD_MODEL.id && calcModuleEntity.getDefaultCalModule() == CalModuleType.DEFAULT_MODEL.id) {
                throw new BusinessException("默认方法模型无法被删除");
            }
        }
//        calcModuleRepository.deleteById(id);
        this.removeById(id);
        List<CalcorEntity> calcorEntityList = calcorService.find(CalcorEntity.builder().calcModuleId(id).build());
        calcorEntityList.forEach(f -> {
            // 删除算子
            calcorService.delete(f.getId());
        });
        String sid = calcModuleEntity.getSid();
//        List<CalcModuleEntity> bySidAndDefaultCalModule = calcModuleRepository
//                .findBySidAndDefaultCalModule(sid, CalModuleType.NON_DEFAULT_MODEL.id);
        List<CalcModuleEntity> bySidAndDefaultCalModule = this.list(new QueryWrapper<CalcModuleEntity>().eq("SID", sid).eq("DEFAULT_CAL_MODULE", CalModuleType.NON_DEFAULT_MODEL.id));
        if (calcModuleEntity.getDefaultCalModule() == CalModuleType.DEFAULT_MODEL.id && CollUtil.isNotEmpty(bySidAndDefaultCalModule)) {
            CalcModuleEntity moduleEntity = bySidAndDefaultCalModule.get(0);
            moduleEntity.setDefaultCalModule(CalModuleType.DEFAULT_MODEL.id);
//            calcModuleRepository.saveAndFlush(moduleEntity);
            this.saveOrUpdate(moduleEntity);
        }
//        try {
//            //记录操作日志
//            String userId = UserUtil.getUserId();
//            CalcModuleEntity entity = data.get();
//            LogIndicatorSystemDto logEntity = LogIndicatorSystemDto.getLog(entity.getName(), userId,
//                    userMapper.getUserNameById(userId), BusinessType.DELETE.name(), IndicatorTypeEnum.ABILITY_INDICATOR.getId(), "删除指标算法");
//            pgLogMapper.insert(logEntity);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    /**
     * 新增编辑
     *
     * @param calcModuleEntities 算法模型集合
     * @return
     */
    @Override
    public void saveAll(List<CalcModuleEntity> calcModuleEntities) {
//        calcModuleRepository.saveAll(calcModuleEntities);
        this.saveBatch(calcModuleEntities);
    }

    /**
     * 保存
     *
     * @param entity 算法模型ID
     * @return
     */
    @Transactional
    @Override
    public void saveWithoutValid(CalcModuleEntity entity) {
//        calcModuleRepository.saveAndFlush(entity);
        this.saveOrUpdate(entity);
    }

    /**
     * 删除算法模型
     *
     * @param entity 算法模型ID
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteByEntity(CalcModuleEntity entity) {
        Example<CalcModuleEntity> example = Example.of(entity);
//        List<CalcModuleEntity> calcModuleEntities = calcModuleRepository.findAll(example);
        List<CalcModuleEntity> calcModuleEntities = this.list(new QueryWrapper<CalcModuleEntity>().setEntity(entity));
        if (CollUtil.isNotEmpty(calcModuleEntities)) {
            for (CalcModuleEntity calcModuleEntity : calcModuleEntities) {
                String id = calcModuleEntity.getId();
                List<CalcorEntity> calcorEntityList = calcorService.find(CalcorEntity.builder().calcModuleId(id).build());
                calcorEntityList.forEach(f -> {
                    // 删除算子
                    calcorService.delete(f.getId());
                });
//                calcModuleRepository.deleteById(id);
                this.removeById(id);
            }
        }
    }

    /**
     * pg算法可视化
     *
     * @return
     */
    @Override
    public List<CalcModuleEntity> findAll() {
//        List<CalcModuleEntity> all = calcModuleRepository.findAll(Sort.by("createTime").descending());
        return this.list(new QueryWrapper<CalcModuleEntity>().orderByDesc("CREATE_TIME"));
    }

    /**
     * 查询是否存在默认算法模型
     * @param id 指标ID
     * @return
     */
    @Override
    public boolean whetherOrNotExistMethodModel(String id) {
//        List<CalcModuleEntity> calcModuleEntityList = calcModuleRepository.findAll(Example.of(
//                CalcModuleEntity.builder().indicatorId(id).build()),Sort.by("createTime").descending());
        List<CalcModuleEntity> calcModuleEntityList = this.list(new QueryWrapper<CalcModuleEntity>().eq("indicator_id", id).orderByDesc("CREATE_TIME"));
        if (CollUtil.isEmpty(calcModuleEntityList)) {
            return false;
        }
        return calcModuleEntityList.stream().noneMatch(var -> var.getCalcType() == ModuleType.METHOD_MODEL.id);
    }

    /**
     * 获取跟指标的算法模型ID
     * @param sid 方案
     * @return
     */
    @Override
    public CalcModuleEntity root(String sid) {
//        List<IndicatorEntity> indicatorEntities = indicatorRepository.findAll(Example.of(IndicatorEntity.builder().sid(sid).build())
//                ,Sort.by("createDate").descending());
        List<IndicatorEntity> indicatorEntities = indicatorService.list(new QueryWrapper<IndicatorEntity>().eq("SID", sid).orderByDesc("create_time"));
        if (CollUtil.isEmpty(indicatorEntities)) {
            return CalcModuleEntity.builder().build();
        }
        IndicatorEntity entity = null;
        for (IndicatorEntity entityOne : indicatorEntities) {
            if (entityOne.getPid().equals("0")) {
                entity = entityOne;
                break;
            }
        }
        if (entity == null) {
            return CalcModuleEntity.builder().build();
        }
        String indicatorId = entity.getId();
//        Optional<CalcModuleEntity> calcModuleEntityOptional = calcModuleRepository.findOne(Example.of(CalcModuleEntity.builder().sid(sid)
//                .indicatorId(indicatorId).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).calcType(ModuleType.METHOD_MODEL.id).build()));
        Optional<CalcModuleEntity> calcModuleEntityOptional = Optional.ofNullable(iCalcModuleMapper.selectOne(new QueryWrapper<CalcModuleEntity>()
                .eq("SID", sid).eq("indicator_id", indicatorId)
                .eq("DEFAULT_CAL_MODULE", CalModuleType.DEFAULT_MODEL.id).eq("CALC_TYPE", ModuleType.METHOD_MODEL.id)));
        return calcModuleEntityOptional.orElseGet(CalcModuleEntity::new);

    }

    /**
     * 设置算法模型参数
     * @param request 算法模型请求对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setParams(CalcModuleTreeParamRequest request) {
        String calcModuleId = request.getCalcModuleId();
        Optional<CalcModuleEntity> calcModuleEntity = Optional.ofNullable(this.getById(calcModuleId));
        if (calcModuleEntity.isPresent()) {
            // 修改总体算法参数问题
            CalcModuleEntity moduleEntity = calcModuleEntity.get();
            if (CollUtil.isNotEmpty(request.getTreeParams())) {
                moduleEntity.setTreeParamList(request.getTreeParams());
            }
            //字典序法参数配置
            if (CollUtil.isNotEmpty(request.getDictionaryOrderParaDTOS())) {
                //数据校验
                checkOrderParas(request.getDictionaryOrderParaDTOS());
                //设置指标类型
                setIndType(request.getDictionaryOrderParaDTOS());
                moduleEntity.setAlgParam(JSONUtil.toJsonStr(request.getDictionaryOrderParaDTOS()));
            }
            //灰色关联配置
            if (ObjectUtil.isNotNull(request.getGeaDTO())) {
                //数据校验
                moduleEntity.setAlgParam(JSONUtil.toJsonStr(request.getGeaDTO()));
            }
            this.saveOrUpdate(moduleEntity);
        }
    }

    /**
     * 设置指标类型
     */
    private void setIndType(List<DictionaryOrderParaDTO> dictionaryOrderParas) {
        //获取指标id
        Map<String, Integer> indicatorIdTypeMap = dictionaryOrderParas.stream().collect(Collectors.toMap(DictionaryOrderParaDTO::getIndicatorId, DictionaryOrderParaDTO::getIndicatorType));
        //根据指标id查询指标
        List<IndicatorEntity> indicatorEntities = indicatorService.listByIds(indicatorIdTypeMap.keySet());
        //设置指标类型
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            indicatorEntity.setIndicatorType(indicatorIdTypeMap.get(indicatorEntity.getId()));
        }
        //更新
        indicatorService.updateBatchById(indicatorEntities);
    }

    /**
     * 数据校验
     */
    private void checkOrderParas(List<DictionaryOrderParaDTO> dictionaryOrderParas) {
        for (DictionaryOrderParaDTO dictionaryOrderPara : dictionaryOrderParas) {
            //1、校验指标id非空
            if (ObjectUtil.isNull(dictionaryOrderPara.getIndicatorId())) {
                throw new BusinessException("指标id不能为空");
            }
            if (ObjectUtil.isNull(dictionaryOrderPara.getIndicatorType())) {
                throw new BusinessException("指标类型不能为空");
            }
            //2、校验数值字段为正数
            validatePositive("优先级", dictionaryOrderPara.getPriority());
            validatePositive("最大值", dictionaryOrderPara.getMaxValue());
            validatePositive("最小值", dictionaryOrderPara.getMinValue());

            //3、校验最大值 > 最小值
            if (dictionaryOrderPara.getMaxValue() <= dictionaryOrderPara.getMinValue()) {
                throw new BusinessException("最大值必须大于最小值");
            }
        }

    }

    /**
     * 校验数值字段为正数
     */
    private void validatePositive(String fieldName, double value) {
        if (StrUtil.isBlank(fieldName)) {
            throw new BusinessException(fieldName + "不能为空");
        }
        if (value <= 0) {
            throw new BusinessException(fieldName + "必须为正数，当前值：" + value);
        }
    }

    /**
     * 获取算法模型参数
     * @param calcModuleId 算法模型ID
     * @return
     */
    @Override
    public Object getParams(String calcModuleId) {
//        String result = calcModuleRepository.findTreeParamById(calcModuleId);
        CalcModuleEntity calcModuleEntity = this.getById(calcModuleId);

        String result = calcModuleEntity.getAlgParam();
        if (StringUtils.isNotEmpty(result) && calcModuleEntity.getMethodId().equals(com.yw.module.indicator.domain.enums.EvaluationType.DICTIONARY_ORDER_METHOD.getName())) {
            return JSONUtil.toList(JSONUtil.parseArray(result), DictionaryOrderParaDTO.class);
        }
        if (StringUtils.isNotEmpty(result)) {
            return JSON.parseArray(result, Float.class);
        }
        return Lists.newArrayList(1.0f, 1.0f);
    }

    /**
     * 获取当下方案所有指标算法信息
     *
     * @param sid 方案id
     * @return
     */
    @Override
    public List<CalcModuleVo> showMethodCalc(String sid) {
        // 获取放下方案所有指标名称
//        List<IndicatorEntity> indicatorEntities = indicatorRepository.findAll(Example.of(
//                IndicatorEntity.builder().sid(sid).build()),Sort.by("createTime").descending());
        List<IndicatorEntity> indicatorEntities = indicatorService.list(new QueryWrapper<IndicatorEntity>().eq("SID", sid).orderByDesc("create_time"));
        if (CollUtil.isEmpty(indicatorEntities)) {
            return Lists.newArrayList();
        }
        Map<String /*指标树id*/, String /*指标树名称*/> indicatorMap = Maps.newHashMap();
        indicatorEntities.forEach((var1) -> {
            indicatorMap.put(var1.getId(), var1.getName());
        });
        List<CalcModuleVo> result = iCalcModuleMapper.showMethodCalc(sid);
        if (CollUtil.isEmpty(result)) {
            return result;
        }
        for (CalcModuleVo vo : result) {
            vo.setIndicatorName(indicatorMap.get(vo.getIndicatorId()));
        }
        return result;
    }

    /**
     * 获取指标算法信息
     *
     * @param ids ids
     * @return
     */
    @Override
    public List<CalcModuleEntity> findBatchId(Set<String> ids) {
//        return calcModuleRepository.findAllById(ids);
        return this.listByIds(ids);
    }

    /**
     * 重命名
     * @param id ID
     * @param name 名称
     */
    @Override
    public void rename(String id, String name) {
//        Optional<CalcModuleEntity> moduleEntity = calcModuleRepository.findById(id);
        Optional<CalcModuleEntity> moduleEntity = Optional.ofNullable(this.getById(id));
        if (moduleEntity.isPresent()) {
            CalcModuleEntity calcModuleEntity = moduleEntity.get();
            calcModuleEntity.setName(name);
//            calcModuleRepository.saveAndFlush(calcModuleEntity);
            this.saveOrUpdate(calcModuleEntity);
            try {
                //记录操作日志
                String userId = UserUtil.getUserId();
                LogIndicatorSystemDto logEntity = LogIndicatorSystemDto.getLog(calcModuleEntity.getName(), userId,
                        userMapper.getUserNameById(userId), BusinessType.RENAME.name(), IndicatorTypeEnum.ABILITY_INDICATOR.getId(), "重命名指标算法");
                pgLogMapper.insert(logEntity);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取父节点的算法模型
     * @param indicatorId 指标ID
     * @return
     */
    public CalcModuleEntity parentIndicatorCalcModule(String indicatorId) {
//        Optional<IndicatorEntity> indicatorEntity = indicatorRepository.findById(indicatorId);
        Optional<IndicatorEntity> indicatorEntity = Optional.ofNullable(indicatorService.getById(indicatorId));
        if (indicatorEntity.isPresent()) {
            IndicatorEntity currentIndicator = indicatorEntity.get();
            List<CalcModuleEntity> calcModuleEntities = find(CalcModuleEntity.builder().indicatorId(currentIndicator.getId()).calcType(ModuleType.METHOD_MODEL.id).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build());
            if (CollUtil.isNotEmpty(calcModuleEntities)) {
                CalcModuleEntity calcModuleEntity = calcModuleEntities.get(0);
                if (calcModuleEntity.getCalcType() == 2) {
                    return calcModuleEntity;
                }
            }
            String pid = currentIndicator.getPid();
            List<CalcModuleEntity> calcModuleEntitiesTwo = find(CalcModuleEntity.builder().indicatorId(pid).calcType(ModuleType.METHOD_MODEL.id).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build());
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

    /**
     * 模型管理页面
     *
     * @param sid 方案id
     */
    @Override
    public MergeTableDTO showAlgorithmMode(String sid) {
        List<HeaderColumnsDTO> headerColumnsDTOS = Lists.newArrayList();
        // 表头
        headerColumnsDTOS.add(new HeaderColumnsDTO("indicatorName", "指标名称"));
        headerColumnsDTOS.add(new HeaderColumnsDTO("calcModuleName", "算法模型"));
        List<TableDataDTO> tableData = Lists.newArrayList();
//        List<CalcModuleEntity> calcModuleEntities = calcModuleRepository.findAll(Example.of(CalcModuleEntity.builder()
//                .sid(sid).build()),Sort.by("createTime").descending());
        List<CalcModuleEntity> calcModuleEntities = this.list(new QueryWrapper<CalcModuleEntity>().eq("SID", sid)
                .orderByDesc("CREATE_TIME"));
        if (CollUtil.isNotEmpty(calcModuleEntities)) {
//            List<IndicatorEntity> indicatorEntityList = indicatorRepository.findAll(Example.of(IndicatorEntity.builder()
//                    .sid(sid).build()));
            List<IndicatorEntity> indicatorEntityList = indicatorService.list(new QueryWrapper<IndicatorEntity>().eq("SID", sid));
            Map<String, List<CalcModuleEntity>> collect = calcModuleEntities.stream().filter(s -> StringUtils.isNotEmpty(s.getIndicatorId())).collect(Collectors.groupingBy(CalcModuleEntity::getIndicatorId));
            Map<String, String> indicatorTreeNameMaps = Maps.newHashMap();
            for (IndicatorEntity indicator : indicatorEntityList) {
                String indicatorId = indicator.getId();
                List<CalcModuleEntity> moduleEntities = collect.get(indicatorId);
                if (CollUtil.isEmpty(moduleEntities)) {
                    continue;
                }
                String name = indicator.getName();
                // 表数据
                for (CalcModuleEntity moduleEntity : moduleEntities) {
                    String moduleEntityName = moduleEntity.getName();
                    Integer defaultCalModule = moduleEntity.getDefaultCalModule();
                    if (!indicatorTreeNameMaps.containsKey(indicator.getTreeId())) {
//                        indicatorTreeNameMaps.put(indicator.getTreeId(), indicatorTreeRepository.findById(indicator.getTreeId()).get().getName());
                        indicatorTreeNameMaps.put(indicator.getTreeId(), indicatorTreeService.getById(indicator.getTreeId()).getName());
                    }
                    tableData.add(new TableDataDTO(moduleEntity.getId(), name, moduleEntityName,
                            defaultCalModule, indicatorId, indicator.getTreeId(), indicatorTreeNameMaps.get(indicator.getTreeId()), moduleEntity.getCalcType(), moduleEntity.getMethodId()));
                }
            }
        }
        return new MergeTableDTO(tableData, headerColumnsDTOS);
    }

    /**
     * 获取默认的算法模型
     * @param indicatorId 指标id
     * @return
     */
    @Override
    public CalcModuleEntity showDefaultCalcModule(String indicatorId) {
//        Optional<CalcModuleEntity> moduleRepositoryOne = calcModuleRepository.findOne(Example.of(CalcModuleEntity.builder()
//                .indicatorId(indicatorId).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build()));
        Optional<CalcModuleEntity> moduleRepositoryOne = Optional.ofNullable(this.getOne(new QueryWrapper<CalcModuleEntity>().setEntity(CalcModuleEntity.builder()
                .indicatorId(indicatorId).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build())));
        return moduleRepositoryOne.orElse(null);
    }

    /**
     * 获取BP算法配置信息
     * @param calcModuleEntity 算法模型信息
     * @return
     */
    @Override
    public BaseResponse<Map<String, String>> obtainBpConfigData(CalcModuleEntity calcModuleEntity) {
        List<IndicatorEntity> indicatorEntities = indicatorService.list(new LambdaQueryWrapper<IndicatorEntity>().eq(IndicatorEntity::getSid, calcModuleEntity.getSid()));
        if (CollUtil.isEmpty(indicatorEntities)) {
            throw new BusinessException("指标未创建无法进行BP算法模型配置");
        }
        Map<String, List<IndicatorEntity>> pidByChildMap = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        if (StringUtil.isEmpty(calcModuleEntity.getJson()) || calcModuleEntity.getJson().equals("0")) {
            return initBaseResponseTable(indicatorEntities, pidByChildMap);
        }
        // 验证是否都包含了叶子指标以及根指标数据
        try {
            return JSON.parseObject(calcModuleEntity.getJson(), new TypeReference<BaseResponse<Map<String, String>>>() {
            });
        } catch (Exception e) {
            return initBaseResponseTable(indicatorEntities, pidByChildMap);
        }
    }

    /**
     * bp神经网络配置信息保存并且进行模型训练
     *
     * @param dto dp信息
     */
    @Transactional
    @Override
    public String saveBpConfigData(BpModuleConfigDTO dto) {
        String calcModuleId = dto.getCalcModuleId();
        CalcModuleEntity calcModuleEntity = this.getById(calcModuleId);
        BaseResponse<Map<String, String>> dataConfig = dto.getDataConfig();
        calcModuleEntity.setJson(JSON.toJSONString(dataConfig));
        // 默认最后一个是因变量
        Map<String, List<Object>> independent = new LinkedHashMap<>();
        Map<String, List<Object>> dependent = new LinkedHashMap<>();
        List<BaseTableHeaderBody> tableHeader = dataConfig.getTableHeader();
        List<Map<String, String>> tableData = dataConfig.getTableData();
        for (int i = 0; i < tableHeader.size() - 1; i++) {
            BaseTableHeaderBody baseTableHeaderBody = tableHeader.get(i);
            String indicatorId = baseTableHeaderBody.getProp();
            independent.put(indicatorId, Lists.newArrayList());
            for (Map<String, String> tableDatum : tableData) {
                independent.get(indicatorId).add(tableDatum.get(indicatorId));
            }
        }
        int lastIndex = tableHeader.size() - 1;
        String lastIndicatorId = tableHeader.get(lastIndex).getProp();
        dependent.put(lastIndicatorId, Lists.newArrayList());
        for (Map<String, String> tableDatum : tableData) {
            dependent.get(lastIndicatorId).add(tableDatum.get(lastIndicatorId));
        }
        // 算法参数组装调用算法
        JSONObject object = new JSONObject();
        object.put("method", 104);
        JSONObject data = new JSONObject();
        object.put("data", data);

        data.put("independent", independent);
        data.put("modelConfig", new BPParser.ModelConfig());
        data.put("dependent", dependent);
        data.put("modelName", calcModuleEntity.getId());
        try {
            String pythonServer = dictService.getValue(URL_CODE, PYTHON_URL);
            HttpUtils.sendPost(pythonServer, JSON.toJSONString(object));
        } catch (Exception e) {
            throw new BusinessException("输入数据无法满足BP神经网络模型训练");
        }
        boolean b = saveOrUpdate(calcModuleEntity);
        return "success";
    }

    /**
     * 初始化基础相应信息
     * @param indicatorEntities 指标集合
     * @param pidByChildMap 父子指标关系集合
     * @return
     */
    @NotNull
    private BaseResponse<Map<String, String>> initBaseResponseTable(List<IndicatorEntity> indicatorEntities, Map<String, List<IndicatorEntity>> pidByChildMap) {
        List<BaseTableHeaderBody> tableHeader = new ArrayList<>();
        // 初始化表头
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            if (!pidByChildMap.containsKey(indicatorEntity.getId())) {
                tableHeader.add(new BaseTableHeaderBody(indicatorEntity.getName(), indicatorEntity.getId()));
            }
        }
        tableHeader.add(new BaseTableHeaderBody(pidByChildMap.get("0").get(0).getName(), pidByChildMap.get("0").get(0).getId()));
        return new BaseResponse<>(0L, Lists.newArrayList(), tableHeader);
    }
}
