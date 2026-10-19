package com.yw.module.indicator.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.yw.common.defines.constants.ServiceConstant;
import com.yw.common.defines.enums.ModuleType;
import com.yw.common.utils.FileUtil;
import com.yw.common.utils.TreeToolHelp;
import com.yw.common.utils.TreeToolUtils;
import com.yw.common.utils.ValidValue;
import com.yw.framework.common.base.support.BaseServiceImpl;
import com.yw.framework.common.defines.constant.IntegerPool;
import com.yw.framework.common.defines.constant.StringPool;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.domain.dto.ChildWeightResult;
import com.yw.module.calculation.domain.entity.CalcModuleEntity;
import com.yw.module.calculation.domain.entity.WeightModelEntity;
import com.yw.module.calculation.indicatorCalculation.mapper.ICalcModuleMapper;
import com.yw.module.calculation.indicatorCalculation.mapper.ITIndicatorWeightMapper;
import com.yw.module.calculation.indicatorCalculation.mapper.IWeightModelMapper;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorTreeEntity;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import com.yw.module.indicator.mapper.IndicatorMapper;
import com.yw.module.indicator.mapper.IndicatorValuesMapper;
import com.yw.module.indicator.service.*;
import com.yw.module.missionScene.domain.dto.EvaluationIndicatorDTO;
import com.yw.module.plan.domain.dto.JsonDTO;
import com.yw.module.plan.domain.dto.LinkDataDTO;
import com.yw.module.plan.domain.dto.NodeDataDTO;
import com.yw.module.plan.mapper.ISceneMapper;
import com.yw.module.plan.mapper.ISchemaMapper;
import com.yw.module.plan.service.CfgTaskConditionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 指标项实现类
 *
 * @Author admin
 * @Description: 指标项实现类
 * @Date: 2020/11/17 15:41
 */
@Service
@Slf4j
public class IndicatorServiceImpl extends BaseServiceImpl<IndicatorMapper, IndicatorEntity> implements IIndicatorService {
//    protected static final Logger LOGGER = LoggerFactory.getLogger(IndicatorServiceImpl.class);
    /**
     * 保存地址
     */
    @Value("${pg-general.module.indicator-calculation.basic-file-path}")
    private String basicFilePath;
    /**
     * 指标仓储
     */
    @Resource
    private IndicatorMapper indicatorMapper;
    /**
     * 指标树服务
     */
    @Autowired
    private IIndicatorTreeService indicatorTreeService;
    /**
     * 算法模型
     */
    @Autowired
    private ICalcModuleMapper calcModuleMapper;
    /**
     * 权重模型
     */
    @Autowired
    private IWeightModelMapper weightModelMapper;
    /**
     * 指标值服务
     */
    @Autowired
    private ITIndicatorValueService indicatorValueService;
    /**
     * 指标值
     */
    @Autowired
    private IndicatorValuesMapper indicatorValuesMapper;
    /**
     * 指标权重仓储
     */
    @Autowired
    private ITIndicatorWeightMapper weightMapper;
    /**
     * 权重模型
     */
    @Autowired
    private ITIndicatorStandardValuesService indicatorStandardValuesService;
    /**
     * 指标准则
     */
    @Autowired
    private ITIndicatorStandardService indicatorstandard;


    /**
     * 查询指标项
     *
     * @param id 指标项ID
     */
    @Override
    public Optional<IndicatorEntity> findById(String id) {
        return Optional.ofNullable(indicatorMapper.selectById(id));
    }

    /**
     * 新增编辑单个指标项
     *
     * @param data 指标项
     * @Author 刘乐法
     * @Description 新增编辑单个指标项
     * @Return com.yw.common.response.ApiResult
     * @Date 2020/11/17 16:44
     */
    @Override
    public boolean edit(IndicatorEntity data) {
        //判断指标树是否存在
//        Optional<IndicatorTreeEntity> treeEntity = treeRepository.findById(data.getTreeId());
        Optional<IndicatorTreeEntity> treeEntity = Optional.ofNullable(indicatorTreeService.getById(data.getTreeId()));
        if (!treeEntity.isPresent()) {
            throw new BusinessException("指标项所属指标树不存在");
        }

//        Date date = new Date();
        if (StringUtils.isBlank(data.getId())) {
            data.setCreateDate(LocalDateTime.now());
        }
        data.setUpdateDate(LocalDateTime.now());
        return indicatorMapper.insert(data) > 0;
    }

    /**
     * 删除指标项
     *
     * @param id 指标项ID
     * @return
     */
    @Override
    @Transactional
    public void delete(String id) {
        Optional<IndicatorEntity> data = this.findById(id);
        if (data.isPresent()) {
            IndicatorEntity entity = data.get();
            indicatorMapper.deleteById(id);

            //删除算子相关
            CalcModuleEntity query = new CalcModuleEntity();
            query.setIndicatorId(id);
//            List<CalcModuleEntity> calModules = calcModuleService.findAll(Example.of(query));
            List<CalcModuleEntity> calModules = calcModuleMapper.selectList(new QueryWrapper<CalcModuleEntity>().setEntity(query));
            if (calModules != null) {
                calModules.forEach(f -> {
                    if (!entity.getPid().equals("0")) {
                        calcModuleMapper.deleteById(f.getId());
                    }
                    if (entity.getPid().equals("0") && f.getCalcType() != ModuleType.METHOD_MODEL.id) {
                        calcModuleMapper.deleteById(f.getId());
                    }
                });
            }

            //删除权重相关
            WeightModelEntity queryWeight = new WeightModelEntity();
            queryWeight.setIndicatorId(id);
//            List<WeightModelEntity> weightModels = weightModelRepository.findAll(Example.of(queryWeight));
            List<WeightModelEntity> weightModels = weightModelMapper.selectList(new QueryWrapper<WeightModelEntity>().setEntity(queryWeight));
            if (weightModels != null) {
                weightModels.forEach(f -> {
//                    weightModelRepository.deleteById(f.getId());
                    weightModelMapper.deleteById(f.getId());
                });
            }
            //根据指标删除指标标准值
            indicatorStandardValuesService.deleteByIndicatorId(id);
            //删除指标准则相关
            indicatorstandard.delete(id);
            //删除指标值
            IndicatorValuesEntity indicatorValuesQuery = new IndicatorValuesEntity();
            indicatorValuesQuery.setIndicatorId(id);
            List<IndicatorValuesEntity> indicatorValuesEntities = indicatorValueService.find(indicatorValuesQuery);
            indicatorValuesEntities.forEach(indicatorValueService::delete);
        } else {
            throw new BusinessException(ServiceConstant.NO_SUCK_OBJECT);
        }

    }

    /**
     * 查询所有指标项
     *
     * @param entity 指标对象
     * @return
     */
    @Override
    public List<IndicatorEntity> find(IndicatorEntity entity) {
//        return indicatorMapper.find(entity);
        LambdaQueryWrapper<IndicatorEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StringUtils.isNotBlank(entity.getId()), IndicatorEntity::getId, entity.getId());
        lqw.eq(StringUtils.isNotBlank(entity.getName()), IndicatorEntity::getName, entity.getName());
        lqw.eq(StringUtils.isNotBlank(entity.getTreeId()), IndicatorEntity::getTreeId, entity.getTreeId());
        lqw.eq(StringUtils.isNotBlank(entity.getPid()), IndicatorEntity::getPid, entity.getPid());
        lqw.eq(StringUtils.isNotBlank(entity.getDescribes()), IndicatorEntity::getDescribes, entity.getDescribes());
        lqw.eq(entity.getLevel() != null, IndicatorEntity::getLevel, entity.getLevel());
        lqw.eq(entity.getCreateDate() != null, IndicatorEntity::getCreateDate, entity.getCreateDate());
        lqw.eq(StringUtils.isNotBlank(entity.getCreator()), IndicatorEntity::getCreator, entity.getCreator());
        lqw.eq(entity.getUpdateDate() != null, IndicatorEntity::getUpdateDate, entity.getUpdateDate());
        lqw.eq(StringUtils.isNotBlank(entity.getSid()), IndicatorEntity::getSid, entity.getSid());
        lqw.eq(entity.getSortno() != null, IndicatorEntity::getSortno, entity.getSortno());
        lqw.eq(StringUtils.isNotBlank(entity.getIsUsedCalcorTemplate()), IndicatorEntity::getIsUsedCalcorTemplate, entity.getIsUsedCalcorTemplate());
        lqw.eq(StringUtils.isNotBlank(entity.getAlgParam()), IndicatorEntity::getAlgParam, entity.getAlgParam());
        lqw.eq(StringUtils.isNotBlank(entity.getDimension()), IndicatorEntity::getDimension, entity.getDimension());
        lqw.eq(StringUtils.isNotBlank(entity.getMaxValue()), IndicatorEntity::getMaxValue, entity.getMaxValue());
        lqw.eq(StringUtils.isNotBlank(entity.getMinValue()), IndicatorEntity::getMinValue, entity.getMinValue());
        lqw.eq(StringUtils.isNotBlank(entity.getConnotation()), IndicatorEntity::getConnotation, entity.getConnotation());
        lqw.eq(StringUtils.isNotBlank(entity.getMethod()), IndicatorEntity::getMethod, entity.getMethod());
        lqw.eq(StringUtils.isNotBlank(entity.getIndicatorScore()), IndicatorEntity::getIndicatorScore, entity.getIndicatorScore());
        lqw.eq(StringUtils.isNotBlank(entity.getRelationIndicatorStr()), IndicatorEntity::getRelationIndicatorStr, entity.getRelationIndicatorStr());
        lqw.eq(StringUtils.isNotBlank(entity.getTableName()), IndicatorEntity::getTableName, entity.getTableName());

        lqw.orderBy(true, true, IndicatorEntity::getSortno).orderByDesc(IndicatorEntity::getCreateDate);
        List<IndicatorEntity> list = indicatorMapper.selectList(lqw);
        String id = entity.getPid();
        if (id != null) {
            Optional<IndicatorEntity> optional = findById(id);
            if (optional.isPresent()) {
                IndicatorEntity indicatorEntity = optional.get();
                if (indicatorEntity.getRelationIndicators() != null) {
                    for (String rid : indicatorEntity.getRelationIndicators()) {
                        Optional<IndicatorEntity> op = findById(rid);
                        if (op.isPresent()) {
                            list.add(op.get());
                        }
                    }
                }
            }
        }
        return list;
    }

    /**
     * 重复名称校验
     *
     * @param entity 指标项
     * @Author 刘乐法
     * @Description 验证
     * @Return void
     * @Date 2020/11/30 14:58
     */
    @Override
    public void validValue(IndicatorEntity entity) {
        IndicatorEntity query = new IndicatorEntity();
        query.setPid(entity.getPid());
        query.setId(entity.getId());
        List<IndicatorEntity> data = find(query);

        try {
            ValidValue.valid(entity.getId(), "该方案下指标树名称", data);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 存储多个实例
     *
     * @param indicatorEntities 指标项集合
     * @Author 刘乐法
     * @Description 存储多个实例
     * @Return void
     * @Date 2021/1/4 11:22
     */
    @Override
    public void saveAll(List<IndicatorEntity> indicatorEntities) {
        this.saveOrUpdateBatch(indicatorEntities);
    }

    /**
     * 通过指标树id查询指标树形结构
     *
     * @param treeId 指标树ID
     * @return
     */
    @Override
    public List<TreeToolHelp> findTreeByTreeId(String treeId) {
        return findTree(IndicatorEntity.builder().treeId(treeId).build());
    }

    /**
     * 通过指标实例查询指标树形结构
     *
     * @param entity 指标项
     * @return
     */
    @Override
    public List<TreeToolHelp> findTree(IndicatorEntity entity) {
        List<IndicatorEntity> indicatorEntityList = find(entity);
        List<TreeToolHelp> rootList = new ArrayList<>();
        List<TreeToolHelp> bodyList = new ArrayList<>();
        indicatorEntityList.forEach(f -> {
            if ("0".equals(f.getPid())) {
                TreeToolHelp toolHelp = new TreeToolHelp();
                toolHelp.setName(f.getName());
                toolHelp.setNodeId(f.getId());
                String id = f.getTreeId();
//                Optional<IndicatorTreeEntity> optional = treeRepository.findById(id);
                Optional<IndicatorTreeEntity> optional = Optional.ofNullable(indicatorTreeService.getById(id));
                Integer indicatorTreeType = optional.get().getIndicatorTreeType();
                //1是能力 2 是效能
                toolHelp.setIndicatorTreeType(indicatorTreeType);
                toolHelp.setSort(0);
                toolHelp.setPid(f.getPid());
                rootList.add(toolHelp);
            } else {
                TreeToolHelp toolHelp = new TreeToolHelp();
                toolHelp.setName(f.getName());
                toolHelp.setNodeId(f.getId());
                toolHelp.setSort(0);
                toolHelp.setPid(f.getPid());
                bodyList.add(toolHelp);
            }
        });

        //生成树形结构
        TreeToolUtils treeToolUtils = new TreeToolUtils(rootList, bodyList);
        List<TreeToolHelp> list = treeToolUtils.getTree();
        return list;
    }

    /**
     * 保存指标图片
     *
     * @param data 图片
     * @param sid  方案ID
     * @return
     */
    @Override
    public void saveImage(String data, String sid) {
        String base64Data = data.split(",")[1];
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] bytes = decoder.decode(base64Data);
        FileOutputStream fos = null;
        try {
            File fileMkdir = new File(basicFilePath + FileUtil.INDICATOR_IMAGE);// 判断文件目录是否存在
            if (!fileMkdir.exists()) {
                fileMkdir.mkdirs();
            }

            fos = new FileOutputStream(basicFilePath + FileUtil.INDICATOR_IMAGE + sid + ".jpg");
            fos.write(bytes);
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 保存指标图片
     *
     * @param data          图片
     * @param sid           方案ID
     * @param indicatorName 指标名称
     *                      文件保存地址 D:\workFile\indicator_image\sid + indicatorName + ".jpg"
     */
    @Override
    public void saveHgImage(String data, String sid, String indicatorName) {
        String base64Data = data.split(",")[1];
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] bytes = decoder.decode(base64Data);
        //创建文件输出流
        FileOutputStream out = null;
        try {
            File fileMkdir = new File(basicFilePath + FileUtil.INDICATOR_IMAGE);// 判断文件目录是否存在
            if (!fileMkdir.exists()) {
                fileMkdir.mkdirs();
            }
            out = new FileOutputStream(basicFilePath + FileUtil.INDICATOR_IMAGE + sid + indicatorName + ".jpg");
            out.write(bytes);
            out.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 给指定指标进行打分
     *
     * @param indicatorId 指标ID
     * @param score       打分
     */
    @Override
    public void score(String indicatorId, String score) {
        //根据指标id获取value值以及权重值
        String indicatorValue = indicatorValuesMapper.findByIndicatorId(indicatorId);
        Double indicatorWeight = weightMapper.showIndicatorWeight(indicatorId);
        if (StringUtils.isEmpty(indicatorValue) || indicatorWeight == null) {
            log.error("The current index has not been calculated yet");
            throw new BusinessException("当前指标暂未得出计算值或暂未设置权重,请先给当前指标进行指标算法计算设置权重");
        }
        double totalValue = BigDecimal.valueOf(indicatorWeight).multiply(BigDecimal.valueOf(100.00)).doubleValue();
        if (Double.parseDouble(score) > 0 && Double.parseDouble(score) < totalValue) {
            return;
        }
        log.info("score range:{} - {}", 0, totalValue);
        throw new BusinessException("打分不在范围之内, 该范围为: 0 - " + totalValue);
    }

    @Override
    public IndicatorEntity findRoot(String treeId) {
        return this.getOne(new LambdaQueryWrapper<IndicatorEntity>().eq(IndicatorEntity::getTreeId, treeId).eq(IndicatorEntity::getPid, "0").last(" limit 1 "));
    }

    /**
     * 获取指标列表
     *
     * @param indicatorId 指标ID
     * @param sid         方案ID
     * @return
     */
    @Override
    public List<IndicatorEntity> selectByIndicatorId(String indicatorId, String sid) {
        List<IndicatorEntity> result = Lists.newArrayList();

        LambdaQueryWrapper<IndicatorEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(IndicatorEntity::getSid, sid);
        lqw.orderByDesc(IndicatorEntity::getCreateDate);
        List<IndicatorEntity> indicatorEntities = indicatorMapper.selectList(lqw);
        Optional<IndicatorEntity> indicatorEntityOptional = this.findById(indicatorId);
        if (!indicatorEntityOptional.isPresent()) {
            return result;
        }
        Map<String, IndicatorEntity> indicatorEntityMap = Maps.newHashMap();
        for (IndicatorEntity entity : indicatorEntities) {
            indicatorEntityMap.put(entity.getId(), entity);
        }
        Map<String, List<IndicatorEntity>> groupByPid = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        if (!indicatorEntityOptional.get().getPid().equals("0")) {
            //todo 现在是三层指标
            getChildIndicator(indicatorId, groupByPid, result, indicatorEntityMap);
            return result;
        }
        for (IndicatorEntity entity : indicatorEntities) {
            String id = entity.getId();
            if (CollUtil.isEmpty(groupByPid.get(id))) {
                // 叶子指标
                result.add(entity);
            }
        }
        return result;
    }

    private void getChildIndicator(String indicatorId, Map<String, List<IndicatorEntity>> groupByPid,
                                   List<IndicatorEntity> result, Map<String, IndicatorEntity> indicatorEntityMap) {
        List<IndicatorEntity> indicatorEntities = groupByPid.get(indicatorId);
        if (CollUtil.isEmpty(indicatorEntities)) {
            result.add(indicatorEntityMap.get(indicatorId));
            return;
        }
        for (IndicatorEntity entity : indicatorEntities) {
            getChildIndicator(entity.getId(), groupByPid, result, indicatorEntityMap);
        }
    }

    /**
     * 根据指标项ID获取方案ID
     *
     * @param indicatorId 指标项ID
     * @return
     */
    @Override
    public String selectResultSId(String indicatorId) {
        Optional<IndicatorEntity> indicatorEntity = this.findById(indicatorId);
        return indicatorEntity.map(IndicatorEntity::getSid).orElse(null);
    }

    /**
     * 根据算法模型ID获取指标集合
     *
     * @param calcModuleId 算法模型ID
     * @return
     */
    @Override
    public List<IndicatorEntity> selectByCalcModuleId(String calcModuleId) {
        List<IndicatorEntity> result = Lists.newArrayList();
        Optional<CalcModuleEntity> calcModuleEntityOptional = Optional.ofNullable(calcModuleMapper.selectById(calcModuleId));
        if (!calcModuleEntityOptional.isPresent()) {
            throw new BusinessException("获取指定对象不存在");
        }
        CalcModuleEntity calcModuleEntity = calcModuleEntityOptional.get();
        if (ModuleType.valueOf(calcModuleEntity.getCalcType()) != ModuleType.METHOD_MODEL) {
            throw new BusinessException("当前计算模型不为方法模型");
        }
        String indicatorId = calcModuleEntity.getIndicatorId();
        Optional<IndicatorEntity> indicatorEntityOptional = this.findById(indicatorId);
        if (!indicatorEntityOptional.isPresent()) {
            throw new BusinessException("指标不存在");
        }
        IndicatorEntity indicatorEntity = indicatorEntityOptional.get();
        //根据指标树Id获取所有指标并根据pid进行分组
        String treeId = indicatorEntity.getTreeId();
        LambdaQueryWrapper<IndicatorEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(IndicatorEntity::getTreeId, treeId);
        List<IndicatorEntity> indicatorEntities = indicatorMapper.selectList(lqw);

        Map<String, List<IndicatorEntity>> groupByPid = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        result.add(indicatorEntity);

        recursion(groupByPid, result, indicatorId);
        // 排序响应前端
        result = result.stream().sorted(new Comparator<IndicatorEntity>() {
            @Override
            public int compare(IndicatorEntity o1, IndicatorEntity o2) {
                Integer o1Level = o1.getLevel();
                Integer o2Level = o2.getLevel();
                if (o1Level > o2Level) {
                    return -1;
                } else if (o1Level.equals(o2Level)) {
                    return 0;
                } else {
                    return 1;
                }
            }
        }).collect(Collectors.toList());
        return result;
    }

    private void recursion(Map<String, List<IndicatorEntity>> groupByPid, List<IndicatorEntity> result, String indicatorId) {
        List<IndicatorEntity> indicatorEntityList = groupByPid.get(indicatorId);
        if (CollUtil.isEmpty(indicatorEntityList)) {
            return;
        }
        for (IndicatorEntity entity : indicatorEntityList) {
            String id = entity.getId();
//            当前指标是否存在方法模型 false 表示存在 true表示不存在
            if (whetherOrNotExistMethodModel(id)) {
                result.add(entity);
            }
            //递归构建指标树信息
            recursion(groupByPid, result, id);
        }
    }

    public boolean whetherOrNotExistMethodModel(String indicatorId) {
//        List<CalcModuleEntity> calcModuleEntityList = calcModuleService.findAll(Example
//                .of(CalcModuleEntity.builder().indicatorId(indicatorId).build()));
        List<CalcModuleEntity> calcModuleEntityList = calcModuleMapper.selectList(new QueryWrapper<CalcModuleEntity>().setEntity(CalcModuleEntity.builder().indicatorId(indicatorId).build()));
        if (CollUtil.isEmpty(calcModuleEntityList)) {
            return false;
        }
        return calcModuleEntityList.stream().noneMatch(var -> var.getCalcType() == ModuleType.METHOD_MODEL.id);
    }

    /**
     * 根据方案id获取根指标ID
     *
     * @param sid 方案id
     * @return
     */
    @Override
    public String indicatorRoot(String sid) {
        LambdaQueryWrapper<IndicatorEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(IndicatorEntity::getSid, sid);
        lqw.eq(IndicatorEntity::getPid, "0");
        List<IndicatorEntity> indicatorEntityList = indicatorMapper.selectList(lqw);

        if (indicatorEntityList.size() > 0) {
            return indicatorEntityList.get(0).getId();
        }
        return "1";
    }

    /**
     * 根据父节点ID获取指标列表子集
     *
     * @param pid 父节点
     * @return
     */
    @Override
    public List<IndicatorEntity> findByPid(String pid) {
        LambdaQueryWrapper<IndicatorEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(IndicatorEntity::getPid, pid);
        lqw.orderByDesc(IndicatorEntity::getCreateDate);
        return indicatorMapper.selectList(lqw);
    }

    /**
     * 根据方案id获取指标集合
     *
     * @param sid 方案
     * @return
     */
    @Override
    public List<IndicatorEntity> findBySid(String sid) {
        LambdaQueryWrapper<IndicatorEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(IndicatorEntity::getSid, sid);
        lqw.orderByDesc(IndicatorEntity::getCreateDate);
        return indicatorMapper.selectList(lqw);
    }

    /**
     * 根据指标树ID获取指标集合
     *
     * @param treeId 指标树
     * @return
     */
    @Override
    public List<IndicatorEntity> findByTreeId(String treeId) {
        LambdaQueryWrapper<IndicatorEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(IndicatorEntity::getTreeId, treeId);
        lqw.orderByDesc(IndicatorEntity::getCreateDate);
        List<IndicatorEntity> indicatorEntities = indicatorMapper.selectList(lqw);
        if (CollUtil.isEmpty(indicatorEntities)) {
            log.error("根据指标树ID未获取到指标信息");
            throw new BusinessException("根据指标树ID未获取到指标信息");
        }
        return indicatorEntities;
    }

    /**
     * 根据算法模型ID获取指标项数据
     *
     * @param calcModuleId 算法模型ID
     * @return
     */
    @Override
    public IndicatorEntity findByCalcModuleId(String calcModuleId) {
        Optional<CalcModuleEntity> calcModuleEntityOptional = Optional.ofNullable(calcModuleMapper.selectById(calcModuleId));

        if (!calcModuleEntityOptional.isPresent()) {
            return null;
        }
        CalcModuleEntity calcModuleEntity = calcModuleEntityOptional.get();
        String indicatorId = calcModuleEntity.getIndicatorId();
        Optional<IndicatorEntity> indicatorEntityOptional = this.findById(indicatorId);
        return indicatorEntityOptional.orElse(null);
    }

    /**
     * 根据指标树ID获取叶子指标集合
     *
     * @param treeId 指标树ID
     * @return
     */
    @Override
    public List<IndicatorEntity> getLeafIndicator(String treeId) {
        List<IndicatorEntity> indicatorEntities = this.findByTreeId(treeId);
        Set<String> result = Sets.newHashSet();
        if (CollUtil.isEmpty(indicatorEntities)) {
            throw new BusinessException("未创建效能指标");
        }
        Map<String, List<IndicatorEntity>> map = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        doGetLeafIndicatorIds(map, result);
        return indicatorEntities.stream().filter(s -> result.contains(s.getId())).collect(Collectors.toList());
    }

    public void doGetLeafIndicatorIds(Map<String, List<IndicatorEntity>> indicatorMaps, Set<String> indicatorId) {
        setLeafIndicatorIds(indicatorMaps.get("0"), indicatorMaps, indicatorId);
    }

    public void setLeafIndicatorIds(List<IndicatorEntity> list, Map<String, List<IndicatorEntity>> indicatorMaps, Set<String> indicatorId) {
        for (IndicatorEntity indicatorEntity : list) {
            List<IndicatorEntity> indicatorEntities = indicatorMaps.get(indicatorEntity.getId());
            if (CollUtil.isNotEmpty(indicatorEntities)) {
                setLeafIndicatorIds(indicatorEntities, indicatorMaps, indicatorId);
            } else {
                indicatorId.add(indicatorEntity.getId());
            }
        }
    }

    /**
     * 根据方案集合获取指标项集合
     *
     * @param schemaIds 方案
     * @return
     */
    @Override
    public List<IndicatorEntity> findBySchemaIds(List<String> schemaIds) {
//        return iIndicatorRepository.findBySidIn(schemaIds);
        return this.list(new QueryWrapper<IndicatorEntity>().in(!schemaIds.isEmpty(), "SID", schemaIds));
    }

    /**
     * 根据指标项ID获取指标项详情
     *
     * @param indicatorEntities
     * @return
     */
    @Override
    public List<IndicatorEntity> findByBacthId(List<String> indicatorEntities) {
//        return iIndicatorRepository.findAllById(indicatorEntities);
        return this.listByIds(indicatorEntities);
    }

    /**
     * 分页查询指标项集合
     *
     * @param page  当前页
     * @param limit 页大小
     * @return
     */
    @Override
    public List<IndicatorEntity> findList(Integer page, Integer limit) {
//        Pageable pageable = PageRequest.of(page,limit);
//        Page<IndicatorEntity> indicatorEntities = iIndicatorRepository.findAll(pageable);
        PageHelper.startPage(page, limit);
        PageInfo<IndicatorEntity> pageInfo = new PageInfo<>(this.list());
        return pageInfo.getList();
    }

    /**
     * 获取指标项集合
     *
     * @param ids 指标树集合
     * @return
     */
    @Override
    public List<IndicatorEntity> findTreeIds(List<String> ids) {
//        List<IndicatorEntity> byTreeIdIn = iIndicatorRepository.findByTreeIdIn(ids);
        List<IndicatorEntity> byTreeIdIn = this.list(new QueryWrapper<IndicatorEntity>().in(!ids.isEmpty(), "TREE_ID", ids));
        return byTreeIdIn;
    }

    /**
     * 修改当前指标项表名称
     *
     * @param tableName   表名称
     * @param directoryId 目录ID
     * @param id          指标项ID
     */
    @Override
    public void saveTbAndDid(String tableName, String directoryId, String id) {
        indicatorMapper.saveTbAndDid(tableName, directoryId, id);
    }

    /**
     * 指标ID,并且该指标ID不会是叶子结点指标
     *
     * @param indicatorId 指标项ID
     * @return
     */
    @Override
    public List<ChildWeightResult> getChildWeight(String indicatorId) {
        return indicatorMapper.selectChildWeight(indicatorId);
    }

    /**
     * 根据方案id查询指定层级指标
     *
     * @param sid 方案id
     */
    @Override
    public List<IndicatorEntity> findTwoIndicator(String sid, Integer indicatorType, Integer level) {
        List<IndicatorTreeEntity> indicatorTreeEntities = indicatorTreeService.list(new LambdaQueryWrapper<IndicatorTreeEntity>().eq(IndicatorTreeEntity::getSid, sid).eq(IndicatorTreeEntity::getIndicatorTreeType, indicatorType));
        if (CollUtil.isEmpty(indicatorTreeEntities)) {
            throw new BusinessException("未查询到指标树");
        }
        //获取指标树id
        String treeId = indicatorTreeEntities.get(0).getId();
        return this.list(new LambdaQueryWrapper<IndicatorEntity>().eq(IndicatorEntity::getTreeId, treeId).eq(IndicatorEntity::getLevel, level));
    }

    /**
     * 新增任务时，新增任务完成度评价指标
     *
     * @param evaluationIndicatorDTOList 任务完成度评价指标Dto集合
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveEvaluationIndicator(String sid, String missionTaskId, List<EvaluationIndicatorDTO> evaluationIndicatorDTOList) {
        if (CollUtil.isEmpty(evaluationIndicatorDTOList)) {
            log.warn("待新增的指标集合为空");
            return false;
        }
        //获取任务完成度指标树对象
        IndicatorTreeEntity indicatorTreeEntity = indicatorTreeService.getOne(new LambdaQueryWrapper<IndicatorTreeEntity>()
                .eq(IndicatorTreeEntity::getSid, sid)
                .eq(IndicatorTreeEntity::getIndicatorTreeType, IntegerPool.ONE)
                .last(" limit 1"));

        //删除来源于当前任务的指标
        this.remove(new LambdaQueryWrapper<IndicatorEntity>()
                .eq(IndicatorEntity::getIndicatorSourceId, missionTaskId)
                .eq(IndicatorEntity::getIndicatorSource, IntegerPool.ONE));

        //DTO 转 Entity
        List<IndicatorEntity> indicatorEntities = toIndicatorEntities(indicatorTreeEntity, sid, missionTaskId, evaluationIndicatorDTOList);

        //保存数据入库
        boolean b = this.saveOrUpdateBatch(indicatorEntities);

        //重新构建指标树json
        String json = buildIndicatorTreeJson(indicatorTreeEntity);
        //更新指标树
        indicatorTreeEntity.setJson(json);
        indicatorTreeService.updateById(indicatorTreeEntity);
        return b;
    }

    /**
     * 删除任务相关的指标
     *
     * @param missionTaskId 任务ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteEvaluationIndicator(String missionTaskId) {
        //删除来源于当前任务的指标
        List<IndicatorEntity> indicatorEntities = this.list(new LambdaQueryWrapper<IndicatorEntity>()
                .eq(IndicatorEntity::getIndicatorSourceId, missionTaskId)
                .eq(IndicatorEntity::getIndicatorSource, IntegerPool.ONE));
        if (CollUtil.isEmpty(indicatorEntities)) {
            log.info("当前任务下没有需要删除的指标");
            return true;
        }
        String sid = indicatorEntities.get(0).getSid();
        //获取指标树
        IndicatorTreeEntity indicatorTreeEntity = indicatorTreeService.getOne(new LambdaQueryWrapper<IndicatorTreeEntity>()
                .eq(IndicatorTreeEntity::getSid, sid)
                .eq(IndicatorTreeEntity::getIndicatorTreeType, IntegerPool.ONE)
                .last(" limit 1"));

        //删除指标
        this.removeBatchByIds(indicatorEntities);

        //重新构建指标树json
        String json = buildIndicatorTreeJson(indicatorTreeEntity);
        //更新指标树
        indicatorTreeEntity.setJson(json);
        return indicatorTreeService.updateById(indicatorTreeEntity);
    }

    /**
     * 重新构建指标树json
     */
    private String buildIndicatorTreeJson(IndicatorTreeEntity indicatorTreeEntity) {
        //查询出所有指标
        List<IndicatorEntity> indicatorEntities = this.list(new LambdaQueryWrapper<IndicatorEntity>().eq(IndicatorEntity::getTreeId, indicatorTreeEntity.getId()));
        //构建指标树json
        List<LinkDataDTO> linkDataArray = Lists.newArrayList();
        List<NodeDataDTO> nodeDataArray = Lists.newArrayList();
        indicatorEntities.forEach(s -> {
            if (ObjectUtil.isNotNull(s.getLevel()) && s.getLevel() != 0) {
                linkDataArray.add(LinkDataDTO.valueOf(s.getPid(), s.getId()));
            }
            nodeDataArray.add(NodeDataDTO.valueOf(s, indicatorTreeEntity.getId()));
        });
        JsonDTO jsonDTO = JsonDTO.valueOf(linkDataArray, nodeDataArray);
        return JSON.toJSONString(jsonDTO);
    }

    /**
     * 获取需要新增的指标对象
     *
     * @param evaluationIndicatorDTOList 任务完成度评价指标Dto集合
     * @return 指标实体对象集合
     */
    private List<IndicatorEntity> toIndicatorEntities(IndicatorTreeEntity indicatorTreeEntity, String sid, String missionTaskId, List<EvaluationIndicatorDTO> evaluationIndicatorDTOList) {
        //查询根指标ID
        IndicatorEntity rootIndicatorEntity = this.getOne(new LambdaQueryWrapper<IndicatorEntity>()
                .eq(IndicatorEntity::getPid, StringPool.ZERO)
                .eq(IndicatorEntity::getTreeId, indicatorTreeEntity.getId()));

        //查询当前已有的二级指标
        Map<String, IndicatorEntity> towIndicatorEntityMap = this.list(new LambdaQueryWrapper<IndicatorEntity>()
                        .eq(IndicatorEntity::getTreeId, indicatorTreeEntity.getId())
                        .eq(IndicatorEntity::getPid, rootIndicatorEntity.getId()))
                .stream().collect(Collectors.toMap(IndicatorEntity::getName, Function.identity(), (a, b) -> a));

        List<IndicatorEntity> indicatorEntities = Lists.newArrayList();
        for (EvaluationIndicatorDTO evaluationIndicatorDTO : evaluationIndicatorDTOList) {
            if (StrUtil.isBlank(evaluationIndicatorDTO.getIndicatorName())) {
                log.error("指标名称不能为空");
                throw new BusinessException("指标名称不能为空");
            }
            if (StrUtil.length(evaluationIndicatorDTO.getIndicatorName()) > 50) {
                throw new BusinessException("指标名称长度超限制，最大长度50，当前长度 " + StrUtil.length(evaluationIndicatorDTO.getIndicatorName()));
            }
            IndicatorEntity indicatorEntity = new IndicatorEntity();
            indicatorEntity.setName(evaluationIndicatorDTO.getIndicatorName());
            indicatorEntity.setIndicatorType(evaluationIndicatorDTO.getIndicatorType());
            indicatorEntity.setTreeId(indicatorTreeEntity.getId());
            indicatorEntity.setPid(rootIndicatorEntity.getId());
            indicatorEntity.setLevel(IntegerPool.ONE);
            indicatorEntity.setIndicatorSource(IntegerPool.ONE);
            indicatorEntity.setIndicatorSourceId(missionTaskId);
            indicatorEntity.setSid(sid);
            indicatorEntity.setCreateDate(LocalDateTime.now());

            //判断指标名称是否已存在，存在并且是从任务那块来则更新
            if (towIndicatorEntityMap.containsKey(indicatorEntity.getName())) {
                IndicatorEntity oldIndicatorEntity = towIndicatorEntityMap.get(indicatorEntity.getName());
                if (IntegerPool.ONE.equals(oldIndicatorEntity.getIndicatorSource())) {
                    //来源于任务，则更新（考虑其他任务下的同名指标）
                    indicatorEntity.setId(oldIndicatorEntity.getId());
                } else {
                    //手动新建的已存在同名的，则跳过
                    continue;
                }
            }
            indicatorEntities.add(indicatorEntity);
        }
        return indicatorEntities;
    }


}
