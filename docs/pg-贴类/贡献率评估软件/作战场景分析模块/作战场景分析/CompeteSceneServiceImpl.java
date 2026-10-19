package com.yw.module.missionScene.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.common.defines.constants.SysConstant;
import com.yw.common.defines.enums.SchemaEvaluateType;
import com.yw.common.utils.UUIDGenerator;
import com.yw.framework.backMgr.dict.service.DictService;
import com.yw.framework.common.defines.constant.IntegerPool;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.framework.common.defines.exception.custom.DataNotFoundException;
import com.yw.module.calculation.contributionRate.domain.dto.SceneScenarioParam;
import com.yw.module.calculation.contributionRate.service.EquipmentSceneService;
import com.yw.module.equipmentResource.domain.entity.EquipmentAttrEntity;
import com.yw.module.equipmentResource.mapper.EquipmentAttrMapper;
import com.yw.module.missionScene.domain.dto.AddSceneParamDTO;
import com.yw.module.missionScene.domain.dto.CompeteSceneDTO;
import com.yw.module.missionScene.domain.dto.ConfigScenarioParamDTO;
import com.yw.module.missionScene.domain.dto.TroopsInfoDTO;
import com.yw.module.missionScene.domain.entity.*;
import com.yw.module.missionScene.domain.vo.CompeteSceneInfoVO;
import com.yw.module.missionScene.domain.vo.SceneParseV2VO;
import com.yw.module.missionScene.domain.vo.SceneScenarioTreeVo;
import com.yw.module.missionScene.excel.ImportScenarioExecelListener;
import com.yw.module.missionScene.excel.ScenarioExcelData;
import com.yw.module.missionScene.mapper.CompeteSceneMapper;
import com.yw.module.missionScene.mapper.TaskSceneMapper;
import com.yw.module.missionScene.service.*;
import com.yw.module.plan.domain.entity.SceneEntity;
import com.yw.module.plan.domain.entity.SchemaEntity;
import com.yw.module.plan.mapper.ISceneMapper;
import com.yw.module.plan.mapper.ISchemaMapper;
import com.yw.module.system.domain.entity.EquipmentEntity;
import com.yw.module.system.mapper.EquipmentMapper;
import com.yw.sys.api.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 场景实现类
 */
@Service
@Slf4j
public class CompeteSceneServiceImpl extends ServiceImpl<CompeteSceneMapper, CompeteSceneEntity> implements CompeteSceneService {

    /**
     * 使命菜单控制字典值
     */
    private static final String TREE_DICT_CODE = "dataTree";
    private static final String TREE_DICT_KEY = "tree";
    /**
     * 想定样本关联仓储
     */
    @Autowired
    @Lazy
    private ContributionRateAlgorithmService contributionRateAlgorithmService;
    /**
     * 场景仓储
     */
    @Autowired
    private CompeteSceneMapper competeSceneMapper;
    /**
     * 任务场景关联仓储
     */
    @Autowired
    private TaskSceneMapper taskSceneMapper;
    /**
     * 想定服务
     */
    @Autowired
    @Lazy
    private SceneScenarioService sceneScenarioService;
    /**
     * 武器装备仓储
     */
    @Resource
    private EquipmentMapper equipmentMapper;
    /**
     * 装备属性 Mapper
     */
    @Autowired
    private EquipmentAttrMapper equipmentAttrMapper;
    /**
     * 装备场景服务
     */
    @Autowired
    private EquipmentSceneService equipmentSceneService;
    /**
     * 想定样本关联表
     */
    @Autowired
    private IMissionTaskService iMissionTaskService;
    /**
     * 想定样本关联表
     */
    @Autowired
    private ISchemaMapper iSchemaMapper;
    /**
     * 想定样本关联表
     */
    @Autowired
    private ISceneMapper sceneMapper;
    /**
     * 想定样本关联表
     */
    @Autowired
    private DictService dictService;
    /**
     * 想定样本关联表
     */
    @Autowired
    private IMissionTaskService missionTaskService;
    /**
     * 想定样本关联表
     */
    @Autowired
    private CompeteSceneService competeSceneService;

    /**
     * 场景详情信息service接口
     */
    @Autowired
    private ICompeteSceneEntityDetailsService competeSceneEntityDetailsService;

    /**
     * 查询场景信息
     *
     * @param name   名称
     * @param sid    名称
     * @param offSet 偏移量
     * @param limit  页大小
     */
    @Override
    public PageInfo<CompeteSceneDTO> showCompeteScene(String name, String sid, Integer offSet, Integer limit, Integer taskModel) {
//        if (offSet != 0) {
        PageHelper.startPage(offSet, limit);
//        }
        List<CompeteSceneDTO> result = competeSceneMapper.showCompeteScene(name, sid, taskModel);
        MissionTaskEntity taskEntity = new MissionTaskEntity();
        taskEntity.setSid(sid);
        List<MissionTaskEntity> list = iMissionTaskService.find(taskEntity);
        Map<String, String> missionTaskNameMap = Maps.newHashMap();
        for (MissionTaskEntity entity : list) {
            missionTaskNameMap.put(entity.getId(), entity.getName());
        }
        for (CompeteSceneDTO competeSceneDTO : result) {
            List<String> taskIdLists = competeSceneDTO.getTaskIdLists();
            List<Pair<String, String>> taskNameList = Lists.newArrayList();
            if (CollUtil.isEmpty(taskIdLists)) {
                continue;
            }
            for (String id : taskIdLists) {
                taskNameList.add(new Pair<>(id, missionTaskNameMap.get(id)));
            }
            competeSceneDTO.setMissionTasks(taskNameList);

        }
        return new PageInfo<>(result);
    }

    /**
     * 添加修改场景信息
     *
     * @param competeSceneDTO 场景信息
     */
    @Transactional
    @Override
    public void saveCompeteScene(CompeteSceneDTO competeSceneDTO) {
        String sceneId = competeSceneDTO.getId();
        String name = competeSceneDTO.getName();
        checkName(sceneId, name, competeSceneDTO.getSid());
        sceneId = StringUtils.isEmpty(sceneId) ? UUIDGenerator.getUUID() : sceneId;

        //添加属性 作战对手 作战时间 作战区域 任务样式 不为实体添加装备ID的集合 单独获取装备ID的集合 在保存到装备关联场景表中 id 装备ID 场景ID 装备名称
        CompeteSceneEntity competeSceneEntity = new CompeteSceneEntity();
        BeanUtils.copyProperties(competeSceneDTO, competeSceneEntity);
        competeSceneEntity.setId(sceneId);

        //获取装备IDs集合 为装备场景关联表进行添加数据
        List<Integer> equipListIds = competeSceneDTO.getEquipListIds();
        if (CollUtil.isNotEmpty(equipListIds)) {
            //创建装备场景关联集合
            ArrayList<EquipmentSceneEntity> tagerList = Lists.newArrayList();
            for (Integer id : equipListIds) {
                Optional<EquipmentEntity> optional = Optional.ofNullable(equipmentMapper.selectById(id));
                if (optional.isPresent()) {
                    EquipmentEntity equipmentEntity = optional.get();
                    String equName = equipmentEntity.getEquName();
                    String category = equipmentEntity.getCategory();
                    EquipmentSceneEntity equipmentScene = new EquipmentSceneEntity(UUIDGenerator.getUUID(), sceneId, equName, equipmentEntity.getId(), category);
                    tagerList.add(equipmentScene);
                }
            }
            //保存装备信息关联信息 ,先进行删除在重新进行保存
//            equipmentSceneRepository.deleteBySceneId(sceneId);
            equipmentSceneService.remove(new QueryWrapper<EquipmentSceneEntity>().eq("SCENE_ID", sceneId));
            equipmentSceneService.saveBatch(tagerList);
//            equipmentSceneRepository.saveAll(tagerList);
        }

        List<String> taskIds = competeSceneDTO.getTaskIdLists();//获取使命任务及关联任务的集合 保存到使命任务作战场景关联表中
        if (CollUtil.isNotEmpty(taskIds)) {
            competeSceneEntity.setTaskCount(taskIds.size());
            competeSceneEntity.setTaskIdLists(taskIds);
            List<TaskSceneEntity> saveLists = Lists.newArrayList();
            for (String taskId : taskIds) {//使命任务ID集合
                TaskSceneEntity saveEntity = new TaskSceneEntity();
                saveEntity.setId(UUIDGenerator.getUUID());
                saveEntity.setTaskId(taskId);//使命任务ID
                saveEntity.setSceneId(sceneId);//场景ID
                saveLists.add(saveEntity);
            }
//            taskSceneRepository.deleteBySceneId(sceneId);
            taskSceneMapper.delete(new QueryWrapper<TaskSceneEntity>().eq("SCENE_ID", sceneId));
//            taskSceneRepository.saveAll(saveLists);
            saveLists.forEach(item -> {
                taskSceneMapper.insert(item);
            });

        }
//        Optional<CompeteSceneEntity> optional = competeSceneRepository.findAll(
//                Example.of(CompeteSceneEntity.builder().sourceId(competeSceneDTO.getSourceId()).build()));
//        if (optional.isPresent()) {
//            //去重
//            String id = optional.get().getId();
//            competeSceneEntity.setId(id);
//            //关联使命任务
//            List<MissionTaskEntity> list = iMissionTaskService.find(MissionTaskEntity.builder()
//                    .sourceId(competeSceneDTO.getSourceId()).build());
//            List<String> stringList = list.stream().map(MissionTaskEntity::getId).collect(Collectors.toList());
//            competeSceneEntity.setTaskIds(stringList.toString());
//        }
//        competeSceneRepository.saveAndFlush(competeSceneEntity);
        this.saveOrUpdate(competeSceneEntity);
    }

    /**
     * 查询当前方案下的所有场景
     *
     * @param sid 方案
     * @return
     */
    @Override
    public List<CompeteSceneEntity> findScenarioBySid(String sid) {
        //根据方案id查出当前方案下场景
//        List<CompeteSceneEntity> competeSceneList = competeSceneRepository.findBySid(sid);
        List<CompeteSceneEntity> competeSceneList = this.list(new QueryWrapper<CompeteSceneEntity>().eq("SID", sid));
        for (CompeteSceneEntity competeSceneEntity : competeSceneList) {
            SceneScenarioEntity sceneScenarioEntity = new SceneScenarioEntity();
            sceneScenarioEntity.setCompeteSceneId(competeSceneEntity.getId());
            Example<SceneScenarioEntity> sceneScenarioEntityExample = Example.of(sceneScenarioEntity);
//            competeSceneEntity.setSceneScenarioList(sceneScenarioRepository.findAll(sceneScenarioEntityExample));
            competeSceneEntity.setSceneScenarioList(sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().setEntity(sceneScenarioEntity)));
        }
        return competeSceneList;
    }

    private void checkName(String sceneId, String name, String sid) {
//        List<CompeteSceneEntity> competeSceneEntities = competeSceneRepository.findAll(Example.of(CompeteSceneEntity.builder().sid(sid).name(name).build()));
        List<CompeteSceneEntity> competeSceneEntities = this.list(new QueryWrapper<CompeteSceneEntity>().eq("SID", sid).eq("NAME", name));
        if (StringUtils.isEmpty(sceneId) && CollUtil.isNotEmpty(competeSceneEntities)) {
            throw new BusinessException("重复的名称");
        }
        if (StringUtils.isNotEmpty(sceneId) && CollUtil.isNotEmpty(competeSceneEntities) && competeSceneEntities.size() > 1) {
            throw new BusinessException("重复的名称");
        }

        if (StringUtils.isNotEmpty(sceneId) && CollUtil.isNotEmpty(competeSceneEntities) && !competeSceneEntities.get(0).getId().equals(sceneId)) {
            throw new BusinessException("重复的名称");
        }
    }


    /**
     * 删除场景信息
     *
     * @param id 场景ID
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteCompeteScene(String id) {
//        taskSceneRepository.deleteBySceneId(id);
        taskSceneMapper.delete(new QueryWrapper<TaskSceneEntity>().eq("SCENE_ID", id));
//        competeSceneRepository.deleteById(id);
        this.removeById(id);
//        sceneScenarioRepository.deleteByCompeteSceneId(id);
        sceneScenarioService.deleteByCompeteSceneId(id);
//        equipmentSceneRepository.deleteBySceneId(id);
        equipmentSceneService.remove(new QueryWrapper<EquipmentSceneEntity>().eq("SCENE_ID", id));
        //删除场景详情信息
        competeSceneEntityDetailsService.removeByCompeteSceneId(id);
    }

    /**
     * 修改装备个数
     *
     * @param competeSceneId 场景ID
     * @param size           装备个数
     */
    @Transactional
    @Override
    public void updateEquipmentCount(String competeSceneId, int size) {
        competeSceneMapper.updateEquipmentCount(competeSceneId, size);
    }

    /**
     * 根据id获取场景信息
     *
     * @param id 场景ID
     * @return
     */
    @Override
    public CompeteSceneEntity selectById(String id) {
//        Optional<CompeteSceneEntity> optional = competeSceneRepository.findById(id);
        Optional<CompeteSceneEntity> optional = Optional.ofNullable(competeSceneMapper.selectById(id));
        if (optional.isPresent()) {
            CompeteSceneEntity competeSceneEntity = optional.get();
//            List<EquipmentSceneEntity> equipmentSceneEntityList = equipmentSceneRepository.findBySceneId(id);
            List<EquipmentSceneEntity> equipmentSceneEntityList = equipmentSceneService.list(new QueryWrapper<EquipmentSceneEntity>().eq("SCENE_ID", id));
            ArrayList<EquipmentSceneEntity> list = Lists.newArrayList();
            for (EquipmentSceneEntity equipmentSceneEntity : equipmentSceneEntityList) {
                Integer equipmentId = equipmentSceneEntity.getEquipmentId();
                String equipmentName = equipmentSceneEntity.getEquipmentName();
                String category = equipmentSceneEntity.getCategory();
                EquipmentSceneEntity entity = new EquipmentSceneEntity(null, null, equipmentName, equipmentId, category);
                list.add(entity);
            }
            Map<String, List<EquipmentSceneEntity>> collect = list.stream().collect(Collectors.groupingBy(EquipmentSceneEntity::getCategory));
            competeSceneEntity.setEquipmentMap(collect);
            return competeSceneEntity;

        }
        return null;

    }

    /**
     * 查询所有的场景信息
     *
     * @param example 场景对象
     * @return
     */
    @Override
    public List<CompeteSceneEntity> findAll(Example<CompeteSceneEntity> example) {
//        List<CompeteSceneEntity> list = competeSceneRepository.findAll(example, Sort.by("createTime").descending());
        List<CompeteSceneEntity> list = this.list(new QueryWrapper<CompeteSceneEntity>().setEntity(example.getProbe()).orderByDesc("CREATE_TIME"));
        for (CompeteSceneEntity competeSceneEntity : list) {
            Map<String, String> result = Maps.newHashMap();
            List<String> taskIdLists = competeSceneEntity.getTaskIdLists();
            Map<String, MissionTaskEntity> stringMissionTaskEntityMap = iMissionTaskService.selectByIdIn(taskIdLists);
            if (CollUtil.isEmpty(taskIdLists)) {
                continue;
            }
            for (String taskIdList : taskIdLists) {
                MissionTaskEntity missionTaskEntity = stringMissionTaskEntityMap.get(taskIdList);
                if (Objects.isNull(missionTaskEntity)) {
                    continue;
                }
                String name = missionTaskEntity.getName();
                String entityId = missionTaskEntity.getId();
                result.put(entityId, name);
            }
//
            competeSceneEntity.setTaskIdAndNameMap(result);
        }
        return list;
    }

    /**
     * 保存所有的场景列表
     *
     * @param competeSceneEntityList 场景列表
     */
    @Override
    public void saveAll(List<CompeteSceneEntity> competeSceneEntityList) {
//        competeSceneRepository.saveAll(competeSceneEntityList);
        this.saveBatch(competeSceneEntityList);
    }

    /**
     * 获取场景任务目录树
     *
     * @param sid             方案
     * @param indicatorTreeId 指标树ID
     * @return
     */
    @Override
    public List<SceneScenarioTreeVo> getTree(String sid, String indicatorTreeId) {
        //所有场景的信息
//        List<CompeteSceneEntity> competeSceneEntityList = competeSceneRepository.findAll(Example.of(CompeteSceneEntity.builder().sid(sid).build()), Sort.by("createTime").descending());
        List<CompeteSceneEntity> competeSceneEntityList = this.list(new QueryWrapper<CompeteSceneEntity>().eq("SID", sid).orderByDesc("CREATE_TIME"));
        SchemaEntity schemaEntity = iSchemaMapper.selectById(sid);
        if (!Objects.isNull(schemaEntity) && schemaEntity.getSchemaEvaluationType() == SchemaEvaluateType.COMPREHENSIVE_EVALUATE.id) {
            indicatorTreeId = null;
        }
        List<SceneScenarioTreeVo> treeList = Lists.newArrayList();
        for (CompeteSceneEntity competeSceneEntity : competeSceneEntityList) {
            //开始 暴力构建树
            String competeSceneId = competeSceneEntity.getId();
            List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findAllByCompeteSceneId(competeSceneId);
            Map<String/*competeSceneId+missionTaskId+sceneScenarioId*/, List<SceneScenarioTreeVo>> sceneEntityMap = Maps.newHashMap();
            if (CollUtil.isNotEmpty(contributionRateAlgorithmEntities)) {
                _buildSceneData(contributionRateAlgorithmEntities, sceneEntityMap, competeSceneEntity.getSid(), indicatorTreeId);
            }

            String name = competeSceneEntity.getName();
            SceneScenarioTreeVo node = new SceneScenarioTreeVo(competeSceneId, name, 0 + "");
            //1 是场景 2 任务  3 是想定
            node.setNodeType(1);
            treeList.add(node);
            //初始化子集合
            node.setChildren(new ArrayList<SceneScenarioTreeVo>());
            List<SceneScenarioTreeVo> competeSceneChildren = node.getChildren();
            List<String> taskIdLists = competeSceneEntity.getTaskIdLists();
            if (CollUtil.isEmpty(taskIdLists)) {
                continue;
            }
            List<MissionTaskEntity> list = iMissionTaskService.listBatch(taskIdLists);
            for (MissionTaskEntity missionTaskEntity : list) {
                String missionTaskId = missionTaskEntity.getId();
                String missionTaskName = missionTaskEntity.getName();
                SceneScenarioTreeVo missionTask = new SceneScenarioTreeVo(missionTaskId, missionTaskName, competeSceneId);
                missionTask.setNodeType(2);
                missionTask.setChildren(new ArrayList<SceneScenarioTreeVo>());
                List<SceneScenarioTreeVo> missionTaskChildren = missionTask.getChildren();
//                List<SceneScenarioEntity> sceneScenarioList = sceneScenarioRepository.findAll(
//                        Example.of(SceneScenarioEntity.builder().taskId(missionTaskId).competeSceneId(competeSceneId).sid(sid).build()));
                List<SceneScenarioEntity> sceneScenarioList = sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().eq("TASK_ID", missionTaskId)
                        .eq("COMPETE_SCENE_ID", competeSceneId).eq("SID", sid));
                for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioList) {
                    String sceneScenarioId = sceneScenarioEntity.getId();
                    String scenarioName = sceneScenarioEntity.getScenarioName();
                    Integer addType = sceneScenarioEntity.getAddType();
                    SceneScenarioTreeVo scenarioNode = new SceneScenarioTreeVo(sceneScenarioId, scenarioName, missionTaskId);
                    scenarioNode.setAddType(addType);
                    scenarioNode.setNodeType(3);
                    scenarioNode.setCompeteId(competeSceneId);
                    scenarioNode.setChildren(sceneEntityMap.get(competeSceneId + missionTaskId + sceneScenarioId));
                    missionTaskChildren.add(scenarioNode);
                }
                competeSceneChildren.add(missionTask);
            }
        }
        return treeList;
    }

    /**
     * 获取场景任务目录树
     *
     * @param sid             方案
     * @param indicatorTreeId 指标树ID
     * @return
     */
    @Override
    public SceneScenarioTreeVo getTreeV2(String sid, String indicatorTreeId) {
        //所有场景的信息
        SchemaEntity schemaEntity = iSchemaMapper.selectById(sid);
        if (!Objects.isNull(schemaEntity) && schemaEntity.getSchemaEvaluationType() == SchemaEvaluateType.COMPREHENSIVE_EVALUATE.id) {
            indicatorTreeId = null;
        }
        //构建树结构的顶点(方案)
        SceneScenarioTreeVo schemaTreeVo = new SceneScenarioTreeVo();
        schemaTreeVo.setId(schemaEntity.getId());
        schemaTreeVo.setName(schemaEntity.getName());
        schemaTreeVo.setNodeType(1);
        //根据方案id查询出下面所有的任务类型
        List<MissionTaskEntity> list = iMissionTaskService.list(new QueryWrapper<MissionTaskEntity>().select("ID,TASK_TYPE").eq("SID", sid).groupBy("TASK_TYPE,ID"));
        //用于存储循环中构建的树结构
        List<SceneScenarioTreeVo> taskTypeList = new ArrayList<>();
        for (MissionTaskEntity taskType : list) {
            //方案下的树节点（任务类型）
            SceneScenarioTreeVo missionTaskTreeVo = new SceneScenarioTreeVo();
            missionTaskTreeVo.setId(UUID.randomUUID().toString().replace("-", ""));
            missionTaskTreeVo.setName(taskType.getTaskType());
            missionTaskTreeVo.setNodeType(2);
            missionTaskTreeVo.setParentId(schemaTreeVo.getId());
            List<Object> treeList = Lists.newArrayList();
//            List<CompeteSceneEntity> competeSceneEntityList = competeSceneRepository.findAll(Example.of(CompeteSceneEntity.builder()
//                    .sid(sid).build()), Sort.by("createTime").descending());
            //根据方案id和任务id查询出父级场景
            List<CompeteSceneEntity> competeSceneEntityList = competeSceneMapper.selectList(new QueryWrapper<CompeteSceneEntity>()
                    .eq("SID", sid).like("TASK_IDS", taskType.getId()).orderByDesc("CREATE_TIME"));
            //遍历查询出的场景
            for (CompeteSceneEntity competeSceneEntity : competeSceneEntityList) {
                //开始 暴力构建树
                String competeSceneId = competeSceneEntity.getId();
                List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findAllByCompeteSceneId(competeSceneId);
                Map<String/*competeSceneId+missionTaskId+sceneScenarioId*/, List<SceneScenarioTreeVo>> sceneEntityMap = Maps.newHashMap();
                if (CollUtil.isNotEmpty(contributionRateAlgorithmEntities)) {
                    _buildSceneData(contributionRateAlgorithmEntities, sceneEntityMap, competeSceneEntity.getSid(), indicatorTreeId);
                }

//                String name = competeSceneEntity.getName();
//                SceneScenarioTreeVo node = new SceneScenarioTreeVo(competeSceneId, name, 0 + "");
//                node.setNodeType(3);
//                node.setParentId(missionTaskTreeVo.getId());
//                treeList.add(node);
//                //初始化子集合
//                node.setChildren(new ArrayList<Object>());
                List<SceneScenarioTreeVo> competeSceneChildren = missionTaskTreeVo.getChildren();
                List<String> taskIdLists = competeSceneEntity.getTaskIdLists();
                if (CollUtil.isEmpty(taskIdLists)) {
                    continue;
                }
                List<MissionTaskEntity> missionTaskList = iMissionTaskService.listBatch(taskIdLists);
                for (MissionTaskEntity missionTaskEntity : missionTaskList) {
                    String missionTaskId = missionTaskEntity.getId();
                    String missionTaskName = missionTaskEntity.getName();
                    SceneScenarioTreeVo missionTask = new SceneScenarioTreeVo(missionTaskId, missionTaskName, missionTaskTreeVo.getId());
                    missionTask.setNodeType(3);
                    missionTask.setChildren(new ArrayList<SceneScenarioTreeVo>());
                    List<SceneScenarioTreeVo> missionTaskChildren = missionTask.getChildren();
//                    List<SceneScenarioEntity> sceneScenarioList = sceneScenarioRepository.findAll(
//                            Example.of(SceneScenarioEntity.builder().taskId(missionTaskId).competeSceneId(competeSceneId).sid(sid).build()));
                    List<SceneScenarioEntity> sceneScenarioList = sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().eq("TASK_ID", missionTaskId)
                            .eq("COMPETE_SCENE_ID", competeSceneId).eq("SID", sid));
                    for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioList) {
                        // 实验样本在基础想定的下面
                        String sceneScenarioId = sceneScenarioEntity.getId();
                        String scenarioName = sceneScenarioEntity.getScenarioName();
                        Integer addType = sceneScenarioEntity.getAddType();
                        SceneScenarioTreeVo scenarioNode = new SceneScenarioTreeVo(sceneScenarioId, scenarioName, missionTask.getId());
                        scenarioNode.setAddType(addType);
                        scenarioNode.setNodeType(4);
                        scenarioNode.setCompeteId(competeSceneId);
                        scenarioNode.setChildren(sceneEntityMap.get(competeSceneId + missionTaskId + sceneScenarioId));
                        missionTaskChildren.add(scenarioNode);
                    }
                    competeSceneChildren.add(missionTask);
                }
            }
            taskTypeList.add(missionTaskTreeVo);
        }
        schemaTreeVo.setChildren(taskTypeList);
        return schemaTreeVo;
    }

    /**
     * 获取场景任务目录树
     *
     * @param sid 方案
     * @return
     */
    @Override
    public List<SceneScenarioTreeVo> getTreeV4(String sid) {
        if (StringUtils.isEmpty(sid) || "null".equalsIgnoreCase(sid)) {
            throw new BusinessException("请先打开方案");
        }
        SchemaEntity schemaEntity = iSchemaMapper.selectById(sid);
        if (Objects.isNull(schemaEntity)) {
            throw new BusinessException("请先打开方案");
        }
        if (schemaEntity.getSchemaEvaluationType() != null && schemaEntity.getSchemaEvaluationType() != SchemaEvaluateType.SYSTEM_CONTRIBUTION_RATE_EVALUATE.id) {
            return obtainGeneralSchemeTreeVo(sid);
        }
        try {
            String value = dictService.getValue(TREE_DICT_CODE, TREE_DICT_KEY);
            //无使命任务场景时不需要构建使命任务和想定,1为无使命任务场景值，0为默认值有使命任务场景
            if (SysConstant.NO_SCENE_VALUE.equals(value)) {
                return obtainGeneralSchemeTreeVo(sid);
            }
        } catch (DataNotFoundException ignored) {
        }
        List<SceneScenarioTreeVo> scenarioEntities = new ArrayList<>();
        SceneScenarioTreeVo schemaTreeVo = new SceneScenarioTreeVo();
        schemaTreeVo.setDesc("方案描述");
        schemaTreeVo.setId(schemaEntity.getId());
        schemaTreeVo.setName(schemaEntity.getName());
        schemaTreeVo.setNodeType(1);
        List<CompeteSceneEntity> list = competeSceneMapper.selectList(new QueryWrapper<CompeteSceneEntity>().eq("SID", sid));
        Map<String/*使命任务ID*/, List<SceneScenarioEntity>> scenarioDataUnderMissionTasksMap = Maps.newHashMap();
        Map<String/*competeSceneId*/, List<SceneScenarioEntity>> scenarioDataUnderCompeteSceneMap = Maps.newHashMap();
        Map<String/*想定PID*/, List<SceneScenarioEntity>> desiredExperimentalSampleDataMap = Maps.newHashMap();
        List<SceneScenarioEntity> sceneScenarioEntityLists = sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().eq("SID", sid));
        Map<String/*想定ID*/, SceneScenarioEntity> sceneScenarioEntityMap = Maps.newHashMap();
        if (!CollUtil.isEmpty(sceneScenarioEntityLists)) {
            for (SceneScenarioEntity scenarioEntity : sceneScenarioEntityLists) {
                sceneScenarioEntityMap.put(scenarioEntity.getId(), scenarioEntity);
                String pid = scenarioEntity.getPid();
                if (StringUtils.isNotEmpty(pid) && desiredExperimentalSampleDataMap.containsKey(pid)) {
                    desiredExperimentalSampleDataMap.get(pid).add(scenarioEntity);
                }
                if (StringUtils.isNotEmpty(pid) && !desiredExperimentalSampleDataMap.containsKey(pid)) {
                    desiredExperimentalSampleDataMap.put(pid, Lists.newArrayList(scenarioEntity));
                }
                String taskId = scenarioEntity.getTaskId();
                if (StringUtils.isNotEmpty(taskId) && scenarioDataUnderMissionTasksMap.containsKey(taskId) && ("0".equals(scenarioEntity.getPid()) || StringUtils.isEmpty(scenarioEntity.getPid()))) {
                    scenarioDataUnderMissionTasksMap.get(taskId).add(scenarioEntity);
                }
                if (StringUtils.isNotEmpty(taskId) && !scenarioDataUnderMissionTasksMap.containsKey(taskId) && ("0".equals(scenarioEntity.getPid()) || StringUtils.isEmpty(scenarioEntity.getPid()))) {
                    scenarioDataUnderMissionTasksMap.put(taskId, Lists.newArrayList(scenarioEntity));
                }
                String competeSceneId = scenarioEntity.getCompeteSceneId();
                if (StringUtils.isNotEmpty(competeSceneId) && scenarioDataUnderCompeteSceneMap.containsKey(competeSceneId) && ("0".equals(scenarioEntity.getPid()) || StringUtils.isEmpty(scenarioEntity.getPid()))) {
                    scenarioDataUnderCompeteSceneMap.get(competeSceneId).add(scenarioEntity);
                }
                if (StringUtils.isNotEmpty(competeSceneId) && !scenarioDataUnderCompeteSceneMap.containsKey(competeSceneId) && ("0".equals(scenarioEntity.getPid()) || StringUtils.isEmpty(scenarioEntity.getPid()))) {
                    scenarioDataUnderCompeteSceneMap.put(competeSceneId, Lists.newArrayList(scenarioEntity));
                }
            }
        }
        List<CompeteSceneEntity> competeSceneEntityList = list.stream().filter(var0 -> StringUtils.isNotEmpty(var0.getTaskModel()) && "0".equals(var0.getTaskModel())).collect(Collectors.toList());

        List<SceneScenarioTreeVo> competeSceneTypeList = schemaTreeVo.getChildren();
//            SceneScenarioTreeVo taskTypeVo = new SceneScenarioTreeVo();
//            taskTypeVo.setId(item.getId());
//            taskTypeVo.setName(item.getName());
//            taskTypeVo.setNodeType(2);
//
//            taskTypeVo.setDesc("场景描述");
//            taskTypeVo.setParentId(schemaTreeVo.getId());
        List<MissionTaskEntity> missionTaskList = iMissionTaskService.list(new LambdaQueryWrapper<MissionTaskEntity>()
                .eq(MissionTaskEntity::getSid, sid));
        //            List<SceneScenarioTreeVo> missTaskList = taskTypeVo.getChildren();
        SceneEntity baseSceneEntities = sceneMapper.selectOne(new LambdaQueryWrapper<SceneEntity>()
                .eq(SceneEntity::getSid, sid)
                .eq(SceneEntity::getSceneType, IntegerPool.THREE)
                .last(" limit 1"));
        for (CompeteSceneEntity competeSceneEntity : competeSceneEntityList) {
            List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findAllByCompeteSceneId(competeSceneEntity.getId());
            Map<String/*sceneScenarioId*/, List<SceneScenarioTreeVo>> sceneEntityMap = Maps.newHashMap();
            if (CollUtil.isNotEmpty(contributionRateAlgorithmEntities)) {
                _buildSceneDataV2(contributionRateAlgorithmEntities, sceneEntityMap, competeSceneEntity.getSid(), baseSceneEntities,sceneScenarioEntityMap);
            }
            SceneScenarioTreeVo competeScene = new SceneScenarioTreeVo();
            competeScene.setId(competeSceneEntity.getId());
            competeScene.setName(competeSceneEntity.getName());
            competeScene.setChildren(Lists.newArrayList());
            competeScene.setNodeType(-1);
            competeScene.setDesc(competeSceneEntity.getDescs());
            competeScene.setParentId(schemaTreeVo.getId());
            competeSceneTypeList.add(competeScene);

            // 场景下面是任务
            List<SceneScenarioTreeVo> missionTaskTypeList = competeScene.getChildren();
            if (CollUtil.isNotEmpty(missionTaskList)) {
                for (MissionTaskEntity missItem : missionTaskList) {
                    if (missItem.getId().equals(competeSceneEntity.getTaskIds()) || (CollUtil.isNotEmpty(competeSceneEntity.getTaskIdLists()) && competeSceneEntity.getTaskIdLists().contains(missItem.getId()))) {
                        SceneScenarioTreeVo missTaskVo = new SceneScenarioTreeVo();
                        missTaskVo.setId(missItem.getId());
                        missTaskVo.setName(missItem.getName());
                        missTaskVo.setNodeType(3);
                        missTaskVo.setDesc(missItem.getDescs());
                        missTaskVo.setParentId(competeScene.getId());
                        missionTaskTypeList.add(missTaskVo);

                        if (!scenarioDataUnderMissionTasksMap.containsKey(missItem.getId())) {
                            continue;
                        }
                        List<SceneScenarioEntity> sceneScenarioEntities = scenarioDataUnderMissionTasksMap.get(missItem.getId());
                        sceneScenarioEntities = sceneScenarioEntities.stream().filter(sceneScenarioEntity -> competeSceneEntity.getId().equals(sceneScenarioEntity.getCompeteSceneId())).collect(Collectors.toList());
                        _buildScenarioDataByCompeteScene(missItem, sceneEntityMap, missTaskVo.getChildren(), sceneScenarioEntities, desiredExperimentalSampleDataMap, 2);
                    }
                }
            } else {
                if (!scenarioDataUnderCompeteSceneMap.containsKey(competeSceneEntity.getId())) {
                    break;
                }
                List<SceneScenarioEntity> sceneScenarioEntities = scenarioDataUnderCompeteSceneMap.get(competeSceneEntity.getId());
                _buildScenarioData2(competeSceneEntity, sceneEntityMap, missionTaskTypeList, sceneScenarioEntities, desiredExperimentalSampleDataMap, 4);
            }

        }

//            competeSceneTypeList.add(taskTypeVo);
        schemaTreeVo.setChildren(competeSceneTypeList);
        scenarioEntities.add(schemaTreeVo);
        return scenarioEntities;
    }

    /**
     * 按场景构建想定目录树
     *
     * @param competeSceneEntity               作战场景
     * @param sceneEntityMap                   想定父子集合
     * @param scenarioList                     想定集合
     * @param sceneScenarioEntities            想定集合
     * @param desiredExperimentalSampleDataMap 试验样本
     * @param nodeType                         节点类型
     */
    private void _buildScenarioData2(CompeteSceneEntity competeSceneEntity, Map<String, List<SceneScenarioTreeVo>> sceneEntityMap, List<SceneScenarioTreeVo> scenarioList, List<SceneScenarioEntity> sceneScenarioEntities, Map<String, List<SceneScenarioEntity>> desiredExperimentalSampleDataMap, int nodeType) {
        for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntities) {
            SceneScenarioTreeVo sceneScenarioTreeVo = new SceneScenarioTreeVo();
            sceneScenarioTreeVo.setId(sceneScenarioEntity.getId());
            sceneScenarioTreeVo.setName(sceneScenarioEntity.getScenarioName());
            sceneScenarioTreeVo.setNodeType(nodeType);
            sceneScenarioTreeVo.setDesc(nodeType == 4 || nodeType == 8 ? "想定(实验样本)描述" : "试验工程");
            sceneScenarioTreeVo.setParentId(StringUtils.isEmpty(sceneScenarioEntity.getPid()) || "0".equals(sceneScenarioEntity.getPid()) ? competeSceneEntity.getId() : sceneScenarioEntity.getPid());
            sceneScenarioTreeVo.setChildren(Lists.newArrayList());
            scenarioList.add(sceneScenarioTreeVo);
            if (!desiredExperimentalSampleDataMap.containsKey(sceneScenarioEntity.getId())) {
                sceneScenarioTreeVo.setChildren(sceneEntityMap.get(sceneScenarioEntity.getId()));
            } else {
                _buildScenarioData2(competeSceneEntity, sceneEntityMap, sceneScenarioTreeVo.getChildren(), desiredExperimentalSampleDataMap.get(sceneScenarioEntity.getId()), desiredExperimentalSampleDataMap, nodeType + 2);
            }
        }
    }


    /**
     * 效能或者能力评估方案获取样本数据
     *
     * @param sid 方案id
     */
    private List<SceneScenarioTreeVo> obtainGeneralSchemeTreeVo(String sid) {

        SchemaEntity schemaEntity = iSchemaMapper.selectById(sid);
        SceneScenarioTreeVo schemaTreeVo = new SceneScenarioTreeVo();
        schemaTreeVo.setDesc("方案描述");
        schemaTreeVo.setId(schemaEntity.getId());
        schemaTreeVo.setName(schemaEntity.getName());
        schemaTreeVo.setNodeType(1);

        List<SceneScenarioTreeVo> schemaChildren = new ArrayList<>();
        schemaTreeVo.setChildren(schemaChildren);

        List<SceneEntity> sceneEntities = sceneMapper.selectList(new QueryWrapper<SceneEntity>().eq("SID", sid));
        Map<String, SceneScenarioTreeVo> one = Maps.newHashMap();
        List<String> taskIds = Lists.newArrayList();
        // 样本
        for (SceneEntity sceneEntity : sceneEntities) {
            String sceneId = sceneEntity.getId();
            String sceneName = sceneEntity.getName();
            String[] split = sceneName.split("/");
            String taskId = split[0];
            String key = "轮次" + taskId.split("-")[1];
            if (!taskIds.contains(key)) {
                taskIds.add(key);
            }
            if (!one.containsKey(key)) {
                SceneScenarioTreeVo sceneNodeParent = new SceneScenarioTreeVo(sceneId, key, schemaEntity.getId());
                sceneNodeParent.setNodeType(5);
                sceneNodeParent.setChildren(Lists.newArrayList());
                one.put(key, sceneNodeParent);
            }
            // todo 处理只有一个轮次标识时候
            if (split.length > 1) {
                SceneScenarioTreeVo sceneScenarioTreeVo = one.get(key);
                sceneScenarioTreeVo.setNodeType(2);
                String ip = "仿真样本:" + split[1].split("-")[1];
                SceneScenarioTreeVo sceneNode = new SceneScenarioTreeVo(sceneId, ip, key);
                sceneNode.setNodeType(5);
                sceneScenarioTreeVo.getChildren().add(sceneNode);
            }
        }
        for (String taskId : taskIds) {
            schemaChildren.add(one.get(taskId));
        }
        // todo 排个序
        schemaChildren.sort((o1, o2) -> {
            String o1Name = o1.getName().replace("轮次", "");
            String o2Name = o2.getName().replace("轮次", "");
            try {
                return Integer.compare(Integer.parseInt(o1Name), Integer.parseInt(o2Name));
            } catch (Exception e) {
                return o1Name.compareTo(o2Name);
            }
        });
        return Collections.singletonList(schemaTreeVo);
    }

    /**
     * 按任务构建想定目录树
     *
     * @param missItem                         使命任务
     * @param sceneEntityMap                   想定父子集合
     * @param scenarioList                     想定集合
     * @param sceneScenarioEntities            想定集合
     * @param desiredExperimentalSampleDataMap 试验样本
     * @param nodeType                         节点类型
     */
    private void _buildScenarioData(MissionTaskEntity missItem, Map<String, List<SceneScenarioTreeVo>> sceneEntityMap,
                                    List<SceneScenarioTreeVo> scenarioList, List<SceneScenarioEntity> sceneScenarioEntities,
                                    Map<String, List<SceneScenarioEntity>> desiredExperimentalSampleDataMap, int nodeType) {
        for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntities) {
            SceneScenarioTreeVo sceneScenarioTreeVo = new SceneScenarioTreeVo();
            sceneScenarioTreeVo.setId(sceneScenarioEntity.getId());
            sceneScenarioTreeVo.setName(sceneScenarioEntity.getScenarioName());
            sceneScenarioTreeVo.setNodeType(nodeType);
            sceneScenarioTreeVo.setDesc(nodeType == 4 || nodeType == 8 ? "想定(实验样本)描述" : "试验工程");
            sceneScenarioTreeVo.setParentId(StringUtils.isEmpty(sceneScenarioEntity.getPid()) || "0".equals(sceneScenarioEntity.getPid()) ? missItem.getId() : sceneScenarioEntity.getPid());
            sceneScenarioTreeVo.setChildren(Lists.newArrayList());
            scenarioList.add(sceneScenarioTreeVo);
            if (!desiredExperimentalSampleDataMap.containsKey(sceneScenarioEntity.getId())) {
                sceneScenarioTreeVo.setChildren(sceneEntityMap.get(sceneScenarioEntity.getId()));
            } else {
                _buildScenarioData(missItem, sceneEntityMap, sceneScenarioTreeVo.getChildren(), desiredExperimentalSampleDataMap.get(sceneScenarioEntity.getId()), desiredExperimentalSampleDataMap, nodeType + 2);
            }
        }
    }

    /**
     * 按场景构建想定目录树
     *
     * @param missionTask                      场景
     * @param sceneEntityMap                   想定父子集合
     * @param scenarioList                     想定集合
     * @param sceneScenarioEntities            想定集合
     * @param desiredExperimentalSampleDataMap <想定pid，子想定>
     * @param nodeType                         节点类型
     */
    private void _buildScenarioDataByCompeteScene(MissionTaskEntity missionTask, Map<String, List<SceneScenarioTreeVo>> sceneEntityMap,
                                                  List<SceneScenarioTreeVo> scenarioList, List<SceneScenarioEntity> sceneScenarioEntities,
                                                  Map<String, List<SceneScenarioEntity>> desiredExperimentalSampleDataMap, int nodeType) {
        int i = 1;
        for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntities) {
            SceneScenarioTreeVo sceneScenarioTreeVo = new SceneScenarioTreeVo();
            sceneScenarioTreeVo.setId(sceneScenarioEntity.getId());
            sceneScenarioTreeVo.setName(sceneScenarioEntity.getScenarioName());
            sceneScenarioTreeVo.setNodeType(nodeType);
            Integer sceneScType = sceneScenarioEntity.getBaseScenarioType();
            if (sceneScType != null && sceneScType == 1) {
                // 想定
                sceneScenarioTreeVo.setNodeType(4);
            }
            i++;
            sceneScenarioTreeVo.setDesc(nodeType == 4 || nodeType == 8 ? "作战方案" : "子方案");
            sceneScenarioTreeVo.setParentId(StringUtils.isEmpty(sceneScenarioEntity.getPid()) || "0".equals(sceneScenarioEntity.getPid()) ? missionTask.getId() : sceneScenarioEntity.getPid());
            sceneScenarioTreeVo.setChildren(Lists.newArrayList());
            scenarioList.add(sceneScenarioTreeVo);
            if (!desiredExperimentalSampleDataMap.containsKey(sceneScenarioEntity.getId())) {
                sceneScenarioTreeVo.setChildren(sceneEntityMap.get(sceneScenarioEntity.getId()));
            } else {
                _buildScenarioDataByCompeteScene(missionTask, sceneEntityMap, sceneScenarioTreeVo.getChildren(), desiredExperimentalSampleDataMap.get(sceneScenarioEntity.getId()), desiredExperimentalSampleDataMap, 7);
            }
        }
    }


    /**
     * 构建样本数据
     *
     * @param contributionRateAlgorithmEntities contributionRateAlgorithmEntities
     * @param sceneEntityMap                    sceneEntityMap
     * @param sid                               sid
     */
    private void _buildSceneData(List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities, Map<String, List<SceneScenarioTreeVo>> sceneEntityMap, String sid, String indicatorTreeId) {
//        List<SceneEntity> sceneEntities = iSceneRepository.findBySidIn(Lists.newArrayList(sid));
        List<SceneEntity> sceneEntities = sceneMapper.selectList(new QueryWrapper<SceneEntity>().eq("SID", sid));
        Map<String, SceneEntity> stringSceneEntityMap = Maps.newHashMap();
        for (SceneEntity sceneEntity : sceneEntities) {
//            if (StringUtils.isEmpty(indicatorTreeId) || indicatorTreeId.equals(sceneEntity.getTreeId()))
            stringSceneEntityMap.put(sceneEntity.getId(), sceneEntity);
        }
        for (ContributionRateAlgorithmEntity algorithmEntity : contributionRateAlgorithmEntities) {
            final String key = algorithmEntity.getCompeteSceneId() + algorithmEntity.getMissionTaskId() + algorithmEntity.getSceneScenarioId();
            if (!sceneEntityMap.containsKey(key)) {
                sceneEntityMap.put(key, Lists.newArrayList());
            }
            SceneEntity sceneEntity = stringSceneEntityMap.get(algorithmEntity.getSceneId());
            if (Objects.isNull(sceneEntity)) {
                continue;
            }
            SceneScenarioTreeVo sceneScenarioTreeVo = new SceneScenarioTreeVo(sceneEntity.getId(), sceneEntity.getName(), algorithmEntity.getSceneScenarioId());
            sceneScenarioTreeVo.setNodeType(4);
            sceneEntityMap.get(key).add(sceneScenarioTreeVo);
        }
    }

    private void _buildSceneDataV2(List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities, Map<String, List<SceneScenarioTreeVo>> sceneEntityMap, String sid, SceneEntity baseSceneEntities, Map<String/*想定ID*/, SceneScenarioEntity> sceneScenarioEntityMap) {
        List<SceneEntity> sceneEntities = sceneMapper.selectList(new QueryWrapper<SceneEntity>().eq("SID", sid));
        Map<String, SceneEntity> stringSceneEntityMap = Maps.newHashMap();
        for (SceneEntity sceneEntity : sceneEntities) {
            stringSceneEntityMap.put(sceneEntity.getId(), sceneEntity);
        }
        for (ContributionRateAlgorithmEntity algorithmEntity : contributionRateAlgorithmEntities) {
            final String key = algorithmEntity.getSceneScenarioId();
            if (!sceneEntityMap.containsKey(key)) {
                sceneEntityMap.put(key, Lists.newArrayList());
            }
            SceneEntity sceneEntity = stringSceneEntityMap.get(algorithmEntity.getSceneId());
            try {
                if (Objects.isNull(sceneEntity)) {
                    continue;
                }
                Map<String, String> sampleTaskId = sceneEntity.getSampleTaskMap();
                String name = sceneEntity.getName();
                SceneScenarioEntity sceneScenarioEntity = sceneScenarioEntityMap.get(algorithmEntity.getSceneScenarioId());
                if (Objects.isNull(sceneScenarioEntity)) {
                    continue;
                }
                SceneScenarioTreeVo simulationVo = new SceneScenarioTreeVo();
                simulationVo.setId(sceneEntity.getId());
                simulationVo.setName(getSceneName(sceneEntity.getName()));
                simulationVo.setParentId(sceneScenarioEntity.getId());
                if (ObjectUtil.isNotNull(baseSceneEntities) && baseSceneEntities.getId().equals(sceneEntity.getId())) {
                    simulationVo.setHighlight(IntegerPool.ONE);
                }
                simulationVo.setNodeType(5);
                sceneEntityMap.get(key).add(simulationVo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
     * 获取当前场景下的任务信息
     *
     * @param competeId 场景ID
     * @return
     */
    @Override
    public List<MissionTaskEntity> getTaskInfo(String competeId) {
        if (StringUtils.isEmpty(competeId)) {
            return null;
        }
        Optional<CompeteSceneEntity> optional = Optional.ofNullable(this.getById(competeId));
        if (optional.isPresent()) {
            List<String> taskIdLists = optional.get().getTaskIdLists();
            List<MissionTaskEntity> list = iMissionTaskService.listBatch(taskIdLists);
            return list;
        }
        return null;
    }

    /**
     * 想定集合
     *
     * @param competeId 场景ID
     * @return
     */
    @Override
    public List<SceneScenarioEntity> findScenarioById(String competeId) {
        SceneScenarioEntity sceneScenarioEntity = new SceneScenarioEntity();
        sceneScenarioEntity.setCompeteSceneId(competeId);
        Example<SceneScenarioEntity> example = Example.of(sceneScenarioEntity);
        return sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().setEntity(sceneScenarioEntity));
    }

    /**
     * 获取场景任务目录树
     *
     * @param sid             方案
     * @return
     */
    @Override
    @Deprecated
    public List<SceneScenarioTreeVo> getTreeV5(String sid) throws Exception {
        List<SceneScenarioTreeVo> resultTreeNodes = new ArrayList<>();

        // 任务
        List<MissionTaskEntity> missionTaskEntities = iMissionTaskService.list(new QueryWrapper<MissionTaskEntity>().eq("SID", sid));
        for (MissionTaskEntity missionTaskEntity : missionTaskEntities) {

            String missionTaskId = missionTaskEntity.getId();
            String missionTaskName = missionTaskEntity.getName();
            SceneScenarioTreeVo missionNode = new SceneScenarioTreeVo(missionTaskId, missionTaskName, "0");
            missionNode.setNodeType(1);

            List<SceneScenarioTreeVo> missionTaskChildren = new ArrayList<>();
            missionNode.setChildren(missionTaskChildren);

//            List<SceneScenarioEntity> sceneScenarioList = sceneScenarioRepository.findAll(
//                    Example.of(SceneScenarioEntity.builder().taskId(missionTaskId).sid(sid).build()));
            List<SceneScenarioEntity> sceneScenarioList = sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().eq("TASK_ID", missionTaskId).eq("SID", sid));
            // 想定
            for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioList) {
                // 实验样本在基础想定的下面
                String sceneScenarioId = sceneScenarioEntity.getId();
                String scenarioName = sceneScenarioEntity.getScenarioName();
                SceneScenarioTreeVo scenarioNode = new SceneScenarioTreeVo(sceneScenarioId, scenarioName, missionNode.getId());
                scenarioNode.setNodeType(2);

                List<SceneScenarioTreeVo> scenarioChildren = new ArrayList<>();
                scenarioNode.setChildren(scenarioChildren);
//
//                List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.list(
//                        new LambdaQueryWrapper<ContributionRateAlgorithmEntity>()
//                                .eq(ContributionRateAlgorithmEntity::getMissionTaskId, taskId)
//                                .eq(ContributionRateAlgorithmEntity::getSceneScenarioId, sceneScenarioId));
//                List<String> sceneIds = contributionRateAlgorithmEntities.stream()
//                        .map(ContributionRateAlgorithmEntity::getSceneId)
//                        .collect(Collectors.toList());
//                if (sceneIds.isEmpty()) {
//                    continue;
//                }
//                List<SceneScenarioEntity> sceneScenarioEntities = sceneScenarioRepository.findAll(Example.of(SceneScenarioEntity.builder().sid(sid).pid(sceneScenarioId).build()));
                List<SceneScenarioEntity> sceneScenarioEntities = sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().eq("SID", sid).eq("PID", sceneScenarioId));
                // 样本
                for (SceneScenarioEntity scenarioEntity : sceneScenarioEntities) {
                    SceneScenarioTreeVo sceneNode = new SceneScenarioTreeVo(scenarioEntity.getId(), scenarioEntity.getScenarioName(), scenarioNode.getId());
                    sceneNode.setNodeType(3);
                    scenarioChildren.add(sceneNode);
                }
                missionTaskChildren.add(scenarioNode);
            }
            resultTreeNodes.add(missionNode);
        }
        return resultTreeNodes;
    }

    /**
     * 获取场景任务目录树
     *
     * @param sid             方案
     * @return
     */
    @Override
    public List<SceneScenarioTreeVo> getTreeV6(String sid) throws Exception {
        if (StringUtils.isEmpty(sid) || "null".equalsIgnoreCase(sid)) {
            throw new BusinessException("请先打开方案");
        }
        SchemaEntity schemaEntity = iSchemaMapper.selectById(sid);
        if (Objects.isNull(schemaEntity)) {
            throw new BusinessException("请先打开方案");
        }
        if (schemaEntity.getSchemaEvaluationType() != null && schemaEntity.getSchemaEvaluationType() != SchemaEvaluateType.SYSTEM_CONTRIBUTION_RATE_EVALUATE.id) {
            return obtainGeneralSchemeTreeVo(sid);
        }
        try {
            String value = dictService.getValue(TREE_DICT_CODE, TREE_DICT_KEY);
            //无使命任务场景时不需要构建使命任务和想定,1为无使命任务场景值，0为默认值有使命任务场景
            if (SysConstant.NO_SCENE_VALUE.equals(value)) {
                return obtainGeneralSchemeTreeVo(sid);
            }
        } catch (DataNotFoundException ignored) {
        }
        List<SceneScenarioTreeVo> scenarioEntities = new ArrayList<>();
        SceneScenarioTreeVo schemaTreeVo = new SceneScenarioTreeVo();
        schemaTreeVo.setDesc("方案描述");
        schemaTreeVo.setId(schemaEntity.getId());
        schemaTreeVo.setName(schemaEntity.getName());
        schemaTreeVo.setNodeType(1);
        List<CompeteSceneEntity> list = competeSceneMapper.selectList(new QueryWrapper<CompeteSceneEntity>().eq("SID", sid));
        List<SceneScenarioEntity> sceneScenarioEntityLists = sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().eq("SID", sid));
        Map<String/*使命任务ID*/, List<SceneScenarioEntity>> scenarioDataUnderMissionTasksMap = Maps.newHashMap();
        Map<String/*competeSceneId*/, List<SceneScenarioEntity>> scenarioDataUnderCompeteSceneMap = Maps.newHashMap();
        Map<String/*想定PID*/, List<SceneScenarioEntity>> desiredExperimentalSampleDataMap = Maps.newHashMap();
        Map<String/*想定ID*/, SceneScenarioEntity> sceneScenarioEntityMap = Maps.newHashMap();
        if (!CollUtil.isEmpty(sceneScenarioEntityLists)) {
            for (SceneScenarioEntity scenarioEntity : sceneScenarioEntityLists) {
                String pid = scenarioEntity.getPid();
                if (StringUtils.isNotEmpty(pid) && desiredExperimentalSampleDataMap.containsKey(pid)) {
                    desiredExperimentalSampleDataMap.get(pid).add(scenarioEntity);
                }
                if (StringUtils.isNotEmpty(pid) && !desiredExperimentalSampleDataMap.containsKey(pid)) {
                    desiredExperimentalSampleDataMap.put(pid, Lists.newArrayList(scenarioEntity));
                }
                String taskId = scenarioEntity.getTaskId();
                if (StringUtils.isNotEmpty(taskId) && scenarioDataUnderMissionTasksMap.containsKey(taskId) && ("0".equals(scenarioEntity.getPid()) || StringUtils.isEmpty(scenarioEntity.getPid()))) {
                    scenarioDataUnderMissionTasksMap.get(taskId).add(scenarioEntity);
                }
                if (StringUtils.isNotEmpty(taskId) && !scenarioDataUnderMissionTasksMap.containsKey(taskId) && ("0".equals(scenarioEntity.getPid()) || StringUtils.isEmpty(scenarioEntity.getPid()))) {
                    scenarioDataUnderMissionTasksMap.put(taskId, Lists.newArrayList(scenarioEntity));
                }
                String competeSceneId = scenarioEntity.getCompeteSceneId();
                if (StringUtils.isNotEmpty(competeSceneId) && scenarioDataUnderCompeteSceneMap.containsKey(competeSceneId) && ("0".equals(scenarioEntity.getPid()) || StringUtils.isEmpty(scenarioEntity.getPid()))) {
                    scenarioDataUnderCompeteSceneMap.get(competeSceneId).add(scenarioEntity);
                }
                if (StringUtils.isNotEmpty(competeSceneId) && !scenarioDataUnderCompeteSceneMap.containsKey(competeSceneId) && ("0".equals(scenarioEntity.getPid()) || StringUtils.isEmpty(scenarioEntity.getPid()))) {
                    scenarioDataUnderCompeteSceneMap.put(competeSceneId, Lists.newArrayList(scenarioEntity));
                }
            }
        }
        List<CompeteSceneEntity> competeSceneEntityList = list.stream().filter(var0 -> StringUtils.isNotEmpty(var0.getTaskModel()) && "0".equals(var0.getTaskModel())).collect(Collectors.toList());

        List<SceneScenarioTreeVo> competeSceneTypeList = schemaTreeVo.getChildren();
        List<MissionTaskEntity> missionTaskList = iMissionTaskService.list(new LambdaQueryWrapper<MissionTaskEntity>()
                .eq(MissionTaskEntity::getSid, sid));
        SceneEntity baseSceneEntities = sceneMapper.selectOne(new LambdaQueryWrapper<SceneEntity>()
                .eq(SceneEntity::getSid, sid)
                .eq(SceneEntity::getSceneType, IntegerPool.THREE)
                .last(" limit 1"));
        for (CompeteSceneEntity competeSceneEntity : competeSceneEntityList) {
            List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findAllByCompeteSceneId(competeSceneEntity.getId());
            Map<String/*sceneScenarioId*/, List<SceneScenarioTreeVo>> sceneEntityMap = Maps.newHashMap();
            if (CollUtil.isNotEmpty(contributionRateAlgorithmEntities)) {
                _buildSceneDataV2(contributionRateAlgorithmEntities, sceneEntityMap, competeSceneEntity.getSid(), baseSceneEntities, sceneScenarioEntityMap);
            }
            SceneScenarioTreeVo competeScene = new SceneScenarioTreeVo();
            competeScene.setId(competeSceneEntity.getId());
            competeScene.setName(competeSceneEntity.getName());
            competeScene.setChildren(Lists.newArrayList());
            competeScene.setNodeType(-1);
            competeScene.setDesc(competeSceneEntity.getDescs());
            competeScene.setParentId(schemaTreeVo.getId());
            competeSceneTypeList.add(competeScene);

            // 场景下面是任务
            List<SceneScenarioTreeVo> missionTaskTypeList = competeScene.getChildren();
            if (CollUtil.isNotEmpty(missionTaskList)) {
                for (MissionTaskEntity missItem : missionTaskList) {
                    if (missItem.getId().equals(competeSceneEntity.getTaskIds()) || (CollUtil.isNotEmpty(competeSceneEntity.getTaskIdLists()) && competeSceneEntity.getTaskIdLists().contains(missItem.getId()))) {
                        SceneScenarioTreeVo missTaskVo = new SceneScenarioTreeVo();
                        missTaskVo.setId(missItem.getId());
                        missTaskVo.setName(missItem.getName());
                        missTaskVo.setNodeType(3);
                        missTaskVo.setDesc(missItem.getDescs());
                        missTaskVo.setParentId(competeScene.getId());
                        missTaskVo.setChildren(Lists.newArrayList());
                        missionTaskTypeList.add(missTaskVo);

                        if (!scenarioDataUnderMissionTasksMap.containsKey(missItem.getId())) {
                            continue;
                        }
                        List<SceneScenarioEntity> sceneScenarioEntities = scenarioDataUnderMissionTasksMap.get(missItem.getId());
                        sceneScenarioEntities = sceneScenarioEntities.stream().filter(sceneScenarioEntity -> competeSceneEntity.getId().equals(sceneScenarioEntity.getCompeteSceneId())).collect(Collectors.toList());
                        List<SceneScenarioTreeVo> sceneScenarioTreeVoList = missTaskVo.getChildren();
                        for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntities) {
                            SceneScenarioTreeVo sceneScenarioTreeVo = new SceneScenarioTreeVo();
                            sceneScenarioTreeVo.setId(sceneScenarioEntity.getId());
                            sceneScenarioTreeVo.setName(sceneScenarioEntity.getScenarioName());
                            sceneScenarioTreeVo.setNodeType(2);
                            sceneScenarioTreeVo.setDesc("方案");
                            sceneScenarioTreeVo.setParentId(StringUtils.isEmpty(sceneScenarioEntity.getPid()) || "0".equals(sceneScenarioEntity.getPid()) ? missItem.getId() : sceneScenarioEntity.getPid());
                            sceneScenarioTreeVo.setChildren(Lists.newArrayList());
                            sceneScenarioTreeVoList.add(sceneScenarioTreeVo);
                        }
                        //_buildScenarioDataByCompeteScene(missItem, sceneEntityMap, missTaskVo.getChildren(), sceneScenarioEntities, desiredExperimentalSampleDataMap, 2);
                    }
                }
            } else {
                if (!scenarioDataUnderCompeteSceneMap.containsKey(competeSceneEntity.getId())) {
                    break;
                }
                List<SceneScenarioEntity> sceneScenarioEntities = scenarioDataUnderCompeteSceneMap.get(competeSceneEntity.getId());
                _buildScenarioData2(competeSceneEntity, sceneEntityMap, missionTaskTypeList, sceneScenarioEntities, desiredExperimentalSampleDataMap, 4);
            }

        }

        schemaTreeVo.setChildren(competeSceneTypeList);
        scenarioEntities.add(schemaTreeVo);
        return scenarioEntities;
    }

    /**
     * 添加场景操作
     *
     * @param param 请求参数
     * @return
     */
    @Override
    public TaskSceneEntity addScene(AddSceneParamDTO param) {
        String sid = param.getSid();
        String sceneName = param.getName();
        String desc = param.getDesc();
        String missId = param.getMissTaskId();
        List<String> tageMissionIds = Lists.newArrayList(missId);
        long count = competeSceneService.count(Wrappers.lambdaQuery(CompeteSceneEntity.class)
                .eq(CompeteSceneEntity::getSid, sid)
                .eq(CompeteSceneEntity::getName, sceneName));
        if (StringUtils.isEmpty(param.getCompeteSceneId()) && count > 0) {
            throw new BusinessException("想定名称重复");
        }
        if (StringUtils.isNotEmpty(param.getCompeteSceneId()) && count >= 2) {
            throw new BusinessException("想定名称重复");
        }
        // 场景
        String uuid = StringUtils.isEmpty(param.getCompeteSceneId()) ? UUIDGenerator.getUUID() : param.getCompeteSceneId();
        competeSceneService.saveOrUpdate(CompeteSceneEntity.builder()
                .id(uuid)
                .sid(sid)
                .name(sceneName)
                .descs(desc)
                .taskIds(JSON.toJSONString(tageMissionIds))
                .taskModel("0")
                .build());
        TaskSceneEntity saveEntity = new TaskSceneEntity();
        saveEntity.setId(UUIDGenerator.getUUID());
        //使命任务ID
        saveEntity.setTaskId(missId);
        //场景ID
        saveEntity.setSceneId(uuid);
        taskSceneMapper.insert(saveEntity);

        return saveEntity;
    }

    @Override
    public TaskSceneEntity addSceneV2(AddSceneParamDTO param) {
        return null;
    }

    /**
     * 添加想定信息
     *
     * @param paramList 请求参数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void configScenario(List<ConfigScenarioParamDTO> paramList) {
        for (ConfigScenarioParamDTO param : paramList) {
            //String sceneId = param.getSceneId();
            String missionTaskId = param.getMissionTaskId();
            String name = param.getName();
            String desc = param.getDesc();
            int sceneScType = param.getSceneScType();
            List<ConfigScenarioParamDTO.EquParam> redParamList = param.getRedParamList();
            List<ConfigScenarioParamDTO.EquParam> blueParamList = param.getBlueParamList();

            if (StrUtil.hasBlank(missionTaskId)) {
                throw new BusinessException("作战任务id不能为空");
            }
            if (StrUtil.hasBlank(name)) {
                throw new BusinessException("作战方案名称不能为空");
            }

//            CompeteSceneEntity sceneEntity = competeSceneService.getById(sceneId);
//            String sid = sceneEntity.getSid();
            MissionTaskEntity missionTaskEntity = missionTaskService.getById(missionTaskId);
            String sid = missionTaskEntity.getSid();
            TaskSceneEntity taskSceneEntity = taskSceneMapper.selectList(new LambdaQueryWrapper<TaskSceneEntity>().eq(TaskSceneEntity::getTaskId, missionTaskId)).get(0);

//            TaskSceneEntity taskSceneEntity = taskSceneMapper.selectOne(Wrappers.lambdaQuery(TaskSceneEntity.class).eq(TaskSceneEntity::getSceneId, sceneEntity.getId()));
//            MissionTaskEntity taskEntity = missionTaskService.getById(taskSceneEntity.getTaskId());

            long count = sceneScenarioService.count(Wrappers.lambdaQuery(SceneScenarioEntity.class)
                    .eq(SceneScenarioEntity::getSid, sid)
                    .eq(SceneScenarioEntity::getScenarioName, name));
            // 基础想定判断
            if (param.getSceneScType() == 1) {
                List<SceneScenarioEntity> exists = sceneScenarioService.getBaseMapper().selectList(Wrappers.lambdaQuery(SceneScenarioEntity.class)
                        .eq(SceneScenarioEntity::getSid, sid)
                        .eq(SceneScenarioEntity::getBaseScenarioType, 1));
                if (CollUtil.isNotEmpty(exists) && StringUtils.isNotEmpty(param.getSceneScenarioId()) && !param.getSceneScenarioId().equals(exists.get(0).getId())) {
                    throw new BusinessException("基础想定只能有一个");
                }
                if (CollUtil.isNotEmpty(exists) && StringUtils.isEmpty(param.getSceneScenarioId())) {
                    throw new BusinessException("基础想定只能有一个");
                }
            }
            if (StringUtils.isEmpty(param.getSceneScenarioId()) && count > 0) {
                throw new BusinessException("想定名称重复");
            }
            if (StringUtils.isNotEmpty(param.getSceneScenarioId()) && count >= 2) {
                throw new BusinessException("想定名称重复");
            }
            SceneScenarioEntity baseScenario;
            String sceneScenarioId = param.getSceneScenarioId();
            if (!StrUtil.hasBlank(sceneScenarioId) && ((baseScenario = sceneScenarioService.getById(sceneScenarioId)) != null)) {
                // 修改想定
                baseScenario.setScenarioName(name);
                baseScenario.setDescs(desc);
                //想定名称修改后导致数据导入不会关联样本 buildContributionRateAlgorithm sceneScenarioService.getSourceId(sceneScenarioSourceFlag, sid);
                baseScenario.setSourceId(name);
                if (ObjectUtil.isNotNull(sceneScType)) {
                    // 默认都是对比想定
                    baseScenario.setBaseScenarioType(sceneScType);
                }
                //想定背景
                baseScenario.setScenarioBackground(param.getScenarioBackground());
                //想定企图
                baseScenario.setScenarioAttempt(param.getScenarioAttempt());
                //想定态势图片路径(jpg/png)
                baseScenario.setScenarioSituation(param.getScenarioSituation());
                //想定文件
                baseScenario.setScenarioFile(param.getScenarioFile());
            } else {
                // 新增想定
                sceneScenarioId = UUIDGenerator.getUUID();
                baseScenario = new SceneScenarioEntity();
                baseScenario.setSid(sid);
                baseScenario.setId(sceneScenarioId);
                baseScenario.setPid("0");
                baseScenario.setScenarioName(name);
                baseScenario.setDescs(desc);
                baseScenario.setCompeteSceneId(taskSceneEntity.getSceneId());
                baseScenario.setTaskId(taskSceneEntity.getTaskId());
                baseScenario.setSourceId(name);
                // 如果未设置默认给非基础想定
                baseScenario.setSceneScType(sceneScType == 0 ? 2 : sceneScType);
                baseScenario.setBaseScenarioType(sceneScType == 0 ? 2 : sceneScType);
                //想定背景
                baseScenario.setScenarioBackground(param.getScenarioBackground());
                //想定企图
                baseScenario.setScenarioAttempt(param.getScenarioAttempt());
                //想定态势图片路径(jpg/png)
                baseScenario.setScenarioSituation(param.getScenarioSituation());
                //想定文件
                baseScenario.setScenarioFile(param.getScenarioFile());

            }
            sceneScenarioService.deleteParam(sceneScenarioId);
            sceneScenarioService.saveOrUpdate(baseScenario);
            // 构建装备参数
            // 构建装备参数
            List<SceneParseV2VO> redSideJson = buildSideJson(redParamList, "1");
            List<SceneParseV2VO> blueSideJson = buildSideJson(blueParamList, "2");
            SceneScenarioParam sceneScenarioParam = new SceneScenarioParam();
            sceneScenarioParam.setSid(sid);
            sceneScenarioParam.setId(baseScenario.getId());
            sceneScenarioService.addData(sceneScenarioParam, baseScenario.getId(), redSideJson, "1");
            sceneScenarioService.addData(sceneScenarioParam, baseScenario.getId(), blueSideJson, "2");
        }
    }

    private List<SceneParseV2VO> buildSideJson(List<ConfigScenarioParamDTO.EquParam> paramList, String type) {
        List<SceneParseV2VO> sceneParseV2VOList = new ArrayList<>();
        for (ConfigScenarioParamDTO.EquParam groupParam : paramList) {
            // 1
            SceneParseV2VO sceneVO = new SceneParseV2VO();
            if ("1".equals(type)) {
                // 0-阶段任务
                SceneParseV2VO sceneTaskVO = new SceneParseV2VO();
                sceneParseV2VOList.add(sceneTaskVO);
                sceneTaskVO.setSidesName(groupParam.getTaskName());
                sceneTaskVO.setSidesType("0");
                List<SceneParseV2VO> groupTaskChild = new ArrayList<>();
                sceneTaskVO.setChild(groupTaskChild);
                groupTaskChild.add(sceneVO);
            } else {
                sceneParseV2VOList.add(sceneVO);
            }
            sceneVO.setSidesName(groupParam.getName());
            sceneVO.setSidesType("1");
            List<SceneParseV2VO> groupChild = new ArrayList<>();
            sceneVO.setChild(groupChild);
            for (ConfigScenarioParamDTO.EquParam equParam : groupParam.getChildList()) {
                // 2
                SceneParseV2VO equVO = new SceneParseV2VO();
                groupChild.add(equVO);
                equVO.setSidesType("2");
                equVO.setSidesName(equParam.getName());
                List<SceneParseV2VO> equChild = new ArrayList<>();
                equVO.setChild(equChild);

                // 3
                SceneParseV2VO partVO = new SceneParseV2VO();
                equChild.add(partVO);
                partVO.setSidesType("3");
                partVO.setSidesName("数量(" + equParam.getUnit() + ")");
                List<SceneParseV2VO> paramChild = new ArrayList<>();
                partVO.setChild(paramChild);

                // 4
                SceneParseV2VO paramVO = new SceneParseV2VO();
                paramChild.add(paramVO);
                paramVO.setSidesName(equParam.getNum());
                paramVO.setSidesType("4");
                List<SceneParseV2VO> paramValChild = new ArrayList<>();
                paramVO.setChild(paramValChild);
//
//                // 5
//                SceneParseV2VO unitVo = new SceneParseV2VO();
//                paramValChild.add(unitVo);
//                unitVo.setSidesType("5");
//                unitVo.setSidesName("单位");
//
//                List<SceneParseV2VO> unitChild = new ArrayList<>();
//                unitVo.setChild(unitChild);
//                // 6
//                SceneParseV2VO unitValue = new SceneParseV2VO();
//                unitChild.add(unitValue);
//                unitValue.setSidesType("6");
//                unitValue.setSidesName(equParam.getUnit());
            }
        }
        return sceneParseV2VOList;
    }

    @Override
    public void getConfigScenario(String id) {

    }

    /**
     * 场景想定文件导入
     *
     * @param id   作战任务id
     * @param file 场景文件
     */
    @Override
    public void importTemplate(String id, MultipartFile file) {
        try {
            EasyExcel.read(file.getInputStream(), ScenarioExcelData.class, new ImportScenarioExecelListener(this, id)).sheet().doRead();
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 添加想定信息
     *
     * @param param 请求参数
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addScene2(AddSceneParamDTO param) {
        //场景下方案选配装备校验，选配装备不能删除
        checkValid(param.getCompeteSceneId(), param.getOurSideTroopsList());

        //数据长度校验
        lengthCheck(param);
        String sid = param.getSid();
        String sceneName = param.getName();
        String desc = param.getDesc();
        long count = competeSceneService.count(Wrappers.lambdaQuery(CompeteSceneEntity.class)
                .ne(StrUtil.isNotBlank(param.getCompeteSceneId()), CompeteSceneEntity::getId, param.getCompeteSceneId())
                .eq(CompeteSceneEntity::getSid, sid)
                .eq(CompeteSceneEntity::getName, sceneName));
        if (count > 0) {
            log.error("场景名称已存在");
            throw new BusinessException("场景名称已存在");
        }
        // 场景
        if (StrUtil.isBlank(param.getCompeteSceneId())) {
            param.setCompeteSceneId(IdUtil.simpleUUID());
        }
        competeSceneService.saveOrUpdate(CompeteSceneEntity.builder()
                .id(param.getCompeteSceneId())
                .sid(sid)
                .name(sceneName)
                .descs(desc)
                //.taskIds(JSON.toJSONString(tageMissionIds))
                .taskModel("0")
                .build());

        //新增/编辑场景详情信息
        competeSceneEntityDetailsService.saveOrUpdateCompeteSceneDetails(param);
        return param.getCompeteSceneId();
    }

    /**
     * 场景下方案选配装备校验，选配装备不能删除
     * @param competeSceneId 场景id
     * @param ourSideTroopsList 我方装备
     */
    private void checkValid(String competeSceneId, List<TroopsInfoDTO> ourSideTroopsList) {
        try {
            List<String> equIdList = new ArrayList<>();
            for (TroopsInfoDTO troopsInfoDTO : ourSideTroopsList) {
                equIdList.add(troopsInfoDTO.getEquId());
            }
            List<SceneScenarioEntity> list = sceneScenarioService.list(new LambdaQueryWrapper<SceneScenarioEntity>()
                    .eq(SceneScenarioEntity::getCompeteSceneId, competeSceneId).eq(SceneScenarioEntity::getPid, "0"));
            for (SceneScenarioEntity sceneScenarioEntity : list) {
                String s = sceneScenarioEntity.getOurSideTroops();
                List<TroopsInfoDTO> troopsInfoDTOS = JSON.parseArray(s, TroopsInfoDTO.class);
                for (TroopsInfoDTO troopsInfoDTO : troopsInfoDTOS) {
                    if (!equIdList.contains(troopsInfoDTO.getEquId())){
                        throw new BusinessException("被方案选配的装备不能删除："+troopsInfoDTO.getEquipmentName());
                    }
                }
            }
        } catch (BusinessException e) {
            throw new BusinessException(e.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 数据长度校验
     */
    private void lengthCheck(AddSceneParamDTO param) {
        if(ObjectUtil.isNull(param)){
            return;
        }
        if(StrUtil.length(param.getName()) > 50){
            throw new BusinessException("作战场景名称长度超限制，最大长度50，当前长度 " + StrUtil.length(param.getName()));
        }
        if(StrUtil.length(param.getDesc()) > 2000){
            throw new BusinessException("作战场景描述长度超限制，最大长度2000，当前长度 " + StrUtil.length(param.getDesc()));
        }
        if(StrUtil.length(param.getOperationPurpose()) > 2000){
            throw new BusinessException("作战目的长度超限制，最大长度2000，当前长度 " + StrUtil.length(param.getOperationPurpose()));
        }
        if(StrUtil.length(param.getOpponentFeatures()) > 2000){
            throw new BusinessException("对手特征长度超限制，最大长度2000，当前长度 " + StrUtil.length(param.getOpponentFeatures()));
        }
        if(StrUtil.length(param.getTerrainType()) > 50){
            throw new BusinessException("地形类型长超限制，最大长度50，当前长度 " + StrUtil.length(param.getTerrainType()));
        }
        if(StrUtil.length(param.getWeather()) > 50){
            throw new BusinessException("天气长超限制，最大长度50，当前长度 " + StrUtil.length(param.getWeather()));
        }
        if(StrUtil.length(param.getVisibility()) > 50){
            throw new BusinessException("能见度长超限制，最大长度50，当前长度 " + StrUtil.length(param.getVisibility()));
        }
        if(StrUtil.length(param.getWindSpeed()) > 50){
            throw new BusinessException("风速长超限制，最大长度50，当前长度 " + StrUtil.length(param.getWindSpeed()));
        }
    }

    /**
     * 获取场景详细信息
     */
    @Override
    public CompeteSceneInfoVO getCompeteSceneById(String id) {
        log.info("根据ID-【{}】查询场景信息", id);
        CompeteSceneEntity competeSceneEntity = this.getById(id);
        if (ObjectUtil.isNull(competeSceneEntity)) {
            log.error("未查询到场景信息");
            throw new BusinessException("未查询到场景信息");
        }

        log.info("根据场景ID-【{}】查询场景详情信息", competeSceneEntity.getId());
        CompeteSceneEntityDetailsEntity competeSceneEntityDetailsEntity = competeSceneEntityDetailsService.getByCompeteSceneId(competeSceneEntity.getId());

        log.info("构建VO返回");
        return buildCompeteSceneInfoVO(competeSceneEntity, competeSceneEntityDetailsEntity);
    }

//    @Transactional(rollbackFor = Exception.class)
//    @Override
//    public String receiveInfo(CompeteInfoDTO dto, SidesRelationshipEntity sidesRelationshipEntity) throws Exception {
//
//        long count = sceneScenarioService.count(new LambdaQueryWrapper<SceneScenarioEntity>().eq(SceneScenarioEntity::getSourceId, dto.getScenarioParamDTO().getSourceId()).last("limit 1"));
//        if (count>0){
//            throw new BusinessException("当前想定已经存在");
//        }
//        long count2 = this.count(new QueryWrapper<CompeteSceneEntity>().eq("NAME", dto.getCompeteSceneName()).last("limit 1"));
//        if (count2>0) {
//            throw new BusinessException("当前场景已存在");
//        }
//
//        // 内部构建方案
//        SchemaController schemaController = SpringUtils.getBean(SchemaController.class);
//        SchemaDTO schemaDTO = new SchemaDTO();
//        schemaDTO.setName(UUIDGenerator.getUUID());
//        schemaDTO.setSchemaEvaluationType(3);
//        R<String> stringR = null;
//        ConfigScenarioParamDTO dataList = dto.getScenarioParamDTO();
//        stringR = schemaController.editV2(schemaDTO, "1c1be94f-618f-42bf-a2ff-eddffc681112");
//        String schemaId = stringR.getData();
//        // 构建默认 抗毁性树
//        builderDestroyIndicatorTree(schemaId);
//        // 不需要任务层级
//        String competeId = UUIDGenerator.getUUID();
//        //checkName(competeId, dto.getCompeteSceneName());
//        competeSceneService.saveOrUpdate(CompeteSceneEntity.builder()
//                .id(competeId).sid(schemaId).name(dto.getCompeteSceneName()).descs(dto.getCompeteSceneRemake())
//                .taskIds(null).taskModel("0").sourceId(dto.getCompeteSceneName())
//                .build());
//        // 关联想定场景  调用想定配置接口
//        dataList.setSceneId(competeId);
//        configScenario(Lists.newArrayList(dto.getScenarioParamDTO()));
//
//        sidesRelationshipEntity.setSid(schemaId);
//        sidesRelationshipService.save(sidesRelationshipEntity);
//
//
//        return schemaId;
//    }
//
//    public void builderDestroyIndicatorTree(String schemaId) {
//        // 查询抗毁性指标树
//        IndicatorTreeEntity indicatorTreeEntity = indicatorTreeService.
//                findBySidAndIndicatorTreeType(schemaId, SchemaEvaluateType.HIT_EVALUATE.id);
//        String json = DestroyDTO.json.replaceAll("2ff5e01952ed485e85a148e60de2a673", indicatorTreeEntity.getId());
////        String json = indicatorTreeEntity.getJson();
//        JsonDTO jsonDTO = JSONObject.parseObject(json, JsonDTO.class);
//        List<NodeDataDTO> nodeDataArray = jsonDTO.getNodeDataArray();
//        // 构造指标数的数据类型         // 导入模板树
//        List<IndicatorSaveDTO> indicatorSaveDTOS = Lists.newArrayList();
//        Map<String, String> oldAndNewIndicatorMaps = new HashMap<>();
//        String newJson = "";
//        String rootName = "";
//        for (NodeDataDTO nodeDataDTO : nodeDataArray) {
//            DataDTO dataDTO = nodeDataDTO.getData();
//            if (nodeDataDTO.getPid().equals("0")) {
//                rootName = dataDTO.getName();
//                oldAndNewIndicatorMaps.put("0", "0");
//            }
//            String key = nodeDataDTO.getData().getId();
//            String newIndicatorId = UUIDGenerator.getUUID();
//
//            newJson = StringUtils.isEmpty(newJson) ? json.replaceAll(key, newIndicatorId) : newJson.replaceAll(key, newIndicatorId);
//            oldAndNewIndicatorMaps.put(key, newIndicatorId);
//            indicatorSaveDTOS.add(IndicatorSaveDTO.valueOf(dataDTO.getName(), newIndicatorId, oldAndNewIndicatorMaps.get(dataDTO.getPid()), schemaId, indicatorTreeEntity.getId(), dataDTO.getLevel(), 1));
//        }
//        IndicatorAddDTO indicatorAddDTO = new IndicatorAddDTO();
//        indicatorAddDTO.setIndicatorTreeDto(IndicatorTreeDTO.valuesOf(schemaId, rootName, newJson, indicatorTreeEntity.getId()));
//        indicatorAddDTO.setIndicatorDtos(indicatorSaveDTOS);
//        //调用indicatorTreeService 编辑指标树
//        IndicatorTreeController indicatorTreeController = SpringUtils.getBean(IndicatorTreeController.class);
//        indicatorTreeController.editV2(indicatorAddDTO);
//    }

    /**
     * 构建场景信息VO返回
     */
   private CompeteSceneInfoVO buildCompeteSceneInfoVO(CompeteSceneEntity competeSceneEntity, CompeteSceneEntityDetailsEntity competeSceneEntityDetailsEntity) {
        CompeteSceneInfoVO competeSceneInfoVO = new CompeteSceneInfoVO();
        competeSceneInfoVO.setName(competeSceneEntity.getName());
        competeSceneInfoVO.setDesc(competeSceneEntity.getDescs());
        if (ObjectUtil.isNotNull(competeSceneEntityDetailsEntity)) {
            BeanUtil.copyProperties(competeSceneEntityDetailsEntity, competeSceneInfoVO);

            //设置兵力信息
            if (StrUtil.isNotBlank(competeSceneEntityDetailsEntity.getOurSideTroops())) {
                competeSceneInfoVO.setOurSideTroopsList(JSONUtil.toList(JSONUtil.parseArray(competeSceneEntityDetailsEntity.getOurSideTroops()), TroopsInfoDTO.class));
            }

            if (StrUtil.isNotBlank(competeSceneEntityDetailsEntity.getEnemyTroops())) {
                competeSceneInfoVO.setEnemyTroopsList(JSONUtil.toList(JSONUtil.parseArray(competeSceneEntityDetailsEntity.getEnemyTroops()), TroopsInfoDTO.class));
            }
        }
        competeSceneInfoVO.setId(competeSceneEntity.getId());
        return competeSceneInfoVO;
    }

    /**
     * 根据场景名称更新我方兵力配置 - 从 equipment 表查询"性能测试装备1-100"
     *
     * @param id 场景名称
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSceneTroopsFromEquipment(String id) {
        log.info("开始更新场景兵力配置，场景名称：{}", id);

        // 1. 根据场景名称查询场景
        CompeteSceneEntity competeSceneEntity = this.getOne(
                new LambdaQueryWrapper<CompeteSceneEntity>()
                        .eq(CompeteSceneEntity::getId, id)
                        .last("limit 1")
        );
        if (ObjectUtil.isNull(competeSceneEntity)) {
            log.error("未找到场景：{}", id);
            throw new BusinessException("未找到场景：" + id);
        }

        String competeSceneId = competeSceneEntity.getId();
        log.info("找到场景，ID：{}，名称：{}", competeSceneId, id);

        // 2. 查询场景详情
        CompeteSceneEntityDetailsEntity detailsEntity = competeSceneEntityDetailsService.getByCompeteSceneId(competeSceneId);
        if (ObjectUtil.isNull(detailsEntity)) {
            log.error("未找到场景详情，场景ID：{}", competeSceneId);
            throw new BusinessException("未找到场景详情");
        }

       // 3. 从 equipment 表查询"性能测试装备1-100"
        List<EquipmentAttrEntity> testEquipments = equipmentAttrMapper.selectList(
                new LambdaQueryWrapper<EquipmentAttrEntity>()
                        .like(EquipmentAttrEntity::getEquipmentName, "性能测试装备")
                        .orderByAsc(EquipmentAttrEntity::getEquipmentName)
        );

        if (CollUtil.isEmpty(testEquipments)) {
            log.warn("未找到性能测试装备数据");
            throw new BusinessException("未找到性能测试装备数据");
        }

        log.info("查询到性能测试装备数量：{}", testEquipments.size());

        // 4. 获取原有我方兵力配置
        List<TroopsInfoDTO> troopsList = CollUtil.newArrayList();
        if (StrUtil.isNotBlank(detailsEntity.getOurSideTroops())) {
            troopsList = JSONUtil.toList(JSONUtil.parseArray(detailsEntity.getOurSideTroops()), TroopsInfoDTO.class);
        }
        log.info("原有我方兵力配置数量：{}", troopsList.size());

        // 5. 将性能测试装备添加到兵力配置
        for (EquipmentAttrEntity equipment : testEquipments) {
            TroopsInfoDTO troop = new TroopsInfoDTO();
            troop.setEquId(equipment.getId());
            troop.setEquipmentName(equipment.getEquipmentName());
            troop.setEquType(equipment.getEquipmentType());
            troop.setEquipmentProperty(equipment.getEquipmentType());
            troop.setDomain(equipment.getDomain());
            troop.setEquipmentQuantity(1); // 默认数量为 1
            troop.setMaxQuantity(10); // 默认最大数量为 10
            troopsList.add(troop);
            log.debug("添加装备：{}，ID：{}", equipment.getEquipmentName(), equipment.getId());
        }
        log.info("添加{}个装备后，我方兵力配置总数：{}", testEquipments.size(), troopsList.size());

        // 6. 更新场景详情
        detailsEntity.setOurSideTroops(JSONUtil.toJsonStr(troopsList));
        competeSceneEntityDetailsService.saveOrUpdate(detailsEntity,
                new LambdaQueryWrapper<CompeteSceneEntityDetailsEntity>()
                        .eq(CompeteSceneEntityDetailsEntity::getCompeteSceneId, competeSceneId));

     log.info("更新场景兵力配置完成，场景ID：{}，新增装备数：{}", competeSceneId, testEquipments.size());
    }

    /**
     * 根据场景 ID 或名称删除"性能测试装备 1-100"
     *
     * @param sceneId 场景 ID（可选）
     * @param sceneName 场景名称（可选，与 sceneId 二选一）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTestTroops(String sceneId, String sceneName) {
        log.info("开始删除性能测试装备，场景 ID：{}，场景名称：{}", sceneId, sceneName);

        // 1. 根据场景 ID 或名称查询场景
        CompeteSceneEntity competeSceneEntity;
        if (StrUtil.isNotBlank(sceneId)) {
            competeSceneEntity = this.getById(sceneId);
        } else {
            competeSceneEntity = this.getOne(
                    new LambdaQueryWrapper<CompeteSceneEntity>()
                            .eq(CompeteSceneEntity::getName, sceneName)
                            .last("limit 1")
            );
        }

        if (ObjectUtil.isNull(competeSceneEntity)) {
            log.error("未找到场景，场景 ID：{}，场景名称：{}", sceneId, sceneName);
            throw new BusinessException("未找到场景");
        }

        String competeSceneId = competeSceneEntity.getId();
        log.info("找到场景，ID：{}，名称：{}", competeSceneId, competeSceneEntity.getName());

        // 2. 查询场景详情
        CompeteSceneEntityDetailsEntity detailsEntity = competeSceneEntityDetailsService.getByCompeteSceneId(competeSceneId);
        if (ObjectUtil.isNull(detailsEntity)) {
            log.error("未找到场景详情，场景 ID：{}", competeSceneId);
            throw new BusinessException("未找到场景详情");
        }

        // 3. 获取原有我方兵力配置
        String ourSideTroopsJson = detailsEntity.getOurSideTroops();
        if (StrUtil.isBlank(ourSideTroopsJson) || "[]".equals(ourSideTroopsJson.trim())) {
            log.warn("我方兵力配置为空，无需删除");
            return;
        }

        List<TroopsInfoDTO> troopsList = JSONUtil.toList(JSONUtil.parseArray(ourSideTroopsJson), TroopsInfoDTO.class);
        int beforeCount = troopsList.size();
        log.info("删除前我方兵力配置数量：{}", beforeCount);

        // 4. 过滤掉"性能测试装备"开头的装备
        List<TroopsInfoDTO> filteredList = troopsList.stream()
                .filter(t -> t.getEquipmentName() == null || !t.getEquipmentName().startsWith("性能测试装备"))
                .collect(Collectors.toList());

        int removedCount = beforeCount - filteredList.size();
        log.info("删除性能测试装备数量：{}", removedCount);

        if (removedCount > 0) {
            // 5. 更新场景详情
            detailsEntity.setOurSideTroops(JSONUtil.toJsonStr(filteredList));
            competeSceneEntityDetailsService.saveOrUpdate(detailsEntity,
                    new LambdaQueryWrapper<CompeteSceneEntityDetailsEntity>()
                            .eq(CompeteSceneEntityDetailsEntity::getCompeteSceneId, competeSceneId));

            log.info("删除性能测试装备完成，场景 ID：{}，删除数量：{}，剩余数量：{}",
                    competeSceneId, removedCount, filteredList.size());
        } else {
            log.info("未找到需要删除的性能测试装备");
        }
    }

    @Override
    public PageInfo<SceneScenarioTreeVo> getTreeChildScenario(String sid, String sceneScenarioId, int offset, int limit) {
        List<SceneScenarioEntity> sceneScenarioEntityList = sceneScenarioService.list(new QueryWrapper<SceneScenarioEntity>().eq("SID", sid));
        Map<String/*想定ID*/, SceneScenarioEntity> sceneScenarioEntityMap = Maps.newHashMap();
        for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntityList) {
            sceneScenarioEntityMap.put(sceneScenarioEntity.getId(), sceneScenarioEntity);
        }

        PageHelper.startPage(offset, limit);
        List<SceneScenarioTreeVo> sceneScenarioTreeVoList = new ArrayList<>();
        List<SceneScenarioEntity> sceneScenarioEntities = sceneScenarioService.list(new LambdaQueryWrapper<SceneScenarioEntity>()
                .eq(SceneScenarioEntity::getPid, sceneScenarioId));
        PageInfo<SceneScenarioEntity> sceneScenarioEntityPageInfo = new PageInfo<>(sceneScenarioEntities);


/*        SceneEntity baseSceneEntities = sceneMapper.selectOne(new LambdaQueryWrapper<SceneEntity>()
                .eq(SceneEntity::getSid, sid)
                .eq(SceneEntity::getSceneType, IntegerPool.THREE)
                .last(" limit 1"));
        List<CompeteSceneEntity> list = competeSceneMapper.selectList(new QueryWrapper<CompeteSceneEntity>().eq("SID", sid));
        List<CompeteSceneEntity> competeSceneEntityList = list.stream().filter(var0 -> StringUtils.isNotEmpty(var0.getTaskModel()) && "0".equals(var0.getTaskModel())).collect(Collectors.toList());
        Map<String*//*sceneScenarioId*//*, List<SceneScenarioTreeVo>> sceneEntityMap = Maps.newHashMap();
        for (CompeteSceneEntity competeSceneEntity : competeSceneEntityList) {
            List<ContributionRateAlgorithmEntity> contributionRateAlgorithmEntities = contributionRateAlgorithmService.findAllByCompeteSceneId(competeSceneEntity.getId());
            if (CollUtil.isNotEmpty(contributionRateAlgorithmEntities)) {
                _buildSceneDataV2(contributionRateAlgorithmEntities, sceneEntityMap, competeSceneEntity.getSid(), baseSceneEntities, sceneScenarioEntityMap);
            }
        }*/

        for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntityPageInfo.getList()) {
            SceneScenarioTreeVo sceneScenarioTreeVo = new SceneScenarioTreeVo();
            sceneScenarioTreeVo.setId(sceneScenarioEntity.getId());
            sceneScenarioTreeVo.setName(sceneScenarioEntity.getScenarioName());
            sceneScenarioTreeVo.setNodeType(7);
            sceneScenarioTreeVo.setDesc("子方案");
            sceneScenarioTreeVo.setParentId(sceneScenarioId);
            //sceneScenarioTreeVo.setChildren(sceneEntityMap.get(sceneScenarioEntity.getId()));
            sceneScenarioTreeVoList.add(sceneScenarioTreeVo);
        }

        PageInfo<SceneScenarioTreeVo> sceneScenarioTreeVoPageInfo = new PageInfo<>();
        sceneScenarioTreeVoPageInfo.setList(sceneScenarioTreeVoList);
        sceneScenarioTreeVoPageInfo.setTotal(sceneScenarioEntityPageInfo.getTotal());
        sceneScenarioTreeVoPageInfo.setPageNum(sceneScenarioEntityPageInfo.getPageNum());
        sceneScenarioTreeVoPageInfo.setPageSize(sceneScenarioEntityPageInfo.getPageSize());
        return sceneScenarioTreeVoPageInfo;
    }

    @Override
    public PageInfo<SceneScenarioTreeVo> getTreeChildScene(String sid, String sceneScenarioId, int offset, int limit) {
        SceneEntity baseSceneEntities = sceneMapper.selectOne(new LambdaQueryWrapper<SceneEntity>()
                .eq(SceneEntity::getSid, sid)
                .eq(SceneEntity::getSceneType, IntegerPool.THREE)
                .last(" limit 1"));
        List<CompeteSceneEntity> competeSceneEntityList = this.list(new LambdaQueryWrapper<CompeteSceneEntity>()
                .eq(CompeteSceneEntity::getSid, sid).last("limit 1"));
        if (CollectionUtil.isNotEmpty(competeSceneEntityList)){
            PageHelper.startPage(offset, limit);
            List<SceneScenarioTreeVo> sceneScenarioTreeVoList = competeSceneMapper.selectChildScene(competeSceneEntityList.get(0).getId(), sceneScenarioId);
            PageInfo<SceneScenarioTreeVo> sceneScenarioTreeVoPageInfo = new PageInfo<>(sceneScenarioTreeVoList);
            List<SceneScenarioTreeVo> list = sceneScenarioTreeVoPageInfo.getList();
            for (SceneScenarioTreeVo sceneScenarioTreeVo : list) {
                sceneScenarioTreeVo.setParentId(sceneScenarioId);
                if (ObjectUtil.isNotNull(baseSceneEntities) && baseSceneEntities.getId().equals(sceneScenarioTreeVo.getId())) {
                    sceneScenarioTreeVo.setHighlight(IntegerPool.ONE);
                }
                sceneScenarioTreeVo.setNodeType(5);
            }
            return sceneScenarioTreeVoPageInfo;
        }else {
            return new PageInfo<>();
        }
    }
}
