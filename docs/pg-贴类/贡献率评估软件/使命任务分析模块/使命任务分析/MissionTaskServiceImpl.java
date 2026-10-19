package com.yw.module.missionScene.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Maps;
import com.yw.common.defines.constants.ServiceConstant;
import com.yw.common.utils.BuildTree;
import com.yw.common.utils.Node;
import com.yw.common.utils.UUIDGenerator;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.contributionRate.domain.dto.MissionTaskParam;
import com.yw.module.missionScene.domain.dto.*;
import com.yw.module.missionScene.domain.entity.*;
import com.yw.module.missionScene.domain.vo.GraphEdgeVO;
import com.yw.module.missionScene.domain.vo.GraphNodeVO;
import com.yw.module.missionScene.domain.vo.MissionGraphVO;
import com.yw.module.missionScene.domain.vo.MissionTaskEntityVO;
import com.yw.module.missionScene.mapper.MissionTaskMapper;
import com.yw.module.missionScene.mapper.TaskSceneMapper;
import com.yw.module.missionScene.service.CompeteSceneService;
import com.yw.module.missionScene.service.IMissionRelationTypeService;
import com.yw.module.missionScene.service.IMissionTaskDetailsService;
import com.yw.module.missionScene.service.IMissionTaskService;
import com.yw.module.system.domain.vo.MissionTaskNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MissionTaskServiceImpl extends ServiceImpl<MissionTaskMapper, MissionTaskEntity> implements IMissionTaskService {


    @Autowired
    private CompeteSceneService competeSceneService;
    @Autowired
    private TaskSceneMapper taskSceneMapper;
    @Autowired
    private IMissionTaskDetailsService missionTaskDetailsService;
    @Autowired
    private IMissionRelationTypeService missionRelationTypeService;


    @Override
    public String edit(MissionTaskEntity entity) {
        String id = entity.getId();
        String name = entity.getName();
        checkName(id, name, entity.getSid());
        String uuid = StringUtils.isNotBlank(id) ? entity.getId() : UUIDGenerator.getUUID();
        entity.setSourceId(uuid);
        entity.setSourceId(entity.getName());
        entity.setPid(StringUtils.isNotEmpty(entity.getPid()) ? entity.getPid() : "0");
        if (entity.getPid().equals("0")) {
            entity.setFlag(2);
        } else {
            List<MissionTaskEntity> missionTaskEntities = find(MissionTaskEntity.builder().pid(uuid).build());
            if (CollUtil.isEmpty(missionTaskEntities)) {
                entity.setFlag(2);
            } else {
                entity.setFlag(1);
            }
        }
        if (StringUtils.isNotBlank(entity.getId())) {
//            Optional<MissionTaskEntity> misOptional = missionTaskRepository.findById(entity.getId());
            Optional<MissionTaskEntity> misOptional = Optional.ofNullable(this.getById(entity.getId()));
            if (!misOptional.isPresent()) {
                throw new BusinessException(ServiceConstant.NO_SUCK_OBJECT);
            }
            entity.setCreateTime(misOptional.get().getCreateTime());
        }

        entity.setId(uuid);
        if (!StringUtils.isEmpty(entity.getPid())) {
            //修改父节点为分类标识
            Optional<MissionTaskEntity> missionTaskEntityOptional = findById(entity.getPid());
            if (missionTaskEntityOptional.isPresent()) {
                MissionTaskEntity missionTaskEntity = missionTaskEntityOptional.get();
                missionTaskEntity.setFlag(1);
//                missionTaskRepository.saveAndFlush(missionTaskEntity);
                this.saveOrUpdate(missionTaskEntity);
            }
        }

//        missionTaskRepository.saveAndFlush(entity);
        this.saveOrUpdate(entity);
        return uuid;
    }

    private void checkName(String id, String name, String sid) {
        List<MissionTaskEntity> missionTaskEntityList = find(MissionTaskEntity.builder().name(name).sid(sid).build());
        if (StringUtils.isEmpty(id) && CollUtil.isNotEmpty(missionTaskEntityList)) {
            throw new BusinessException("重复的名称");
        }
        if (StringUtils.isNotEmpty(id) && CollUtil.isNotEmpty(missionTaskEntityList) && missionTaskEntityList.size() > 1) {
            throw new BusinessException("重复的名称");
        }
        if (StringUtils.isNotEmpty(id) && CollUtil.isNotEmpty(missionTaskEntityList) && !missionTaskEntityList.get(0).getId().equals(id)) {
            throw new BusinessException("重复的名称");
        }
    }

    @Override
    public Optional<MissionTaskEntity> findById(String id) {
//        return missionTaskRepository.findById(id);
        return Optional.ofNullable(this.getById(id));
    }

    @Override
    public List<MissionTaskEntity> find(MissionTaskEntity entity) {
        Example<MissionTaskEntity> query = Example.of(entity);
//        return missionTaskRepository.findAll(query, Sort.by("createTime").descending());
        return this.list(new QueryWrapper<MissionTaskEntity>().setEntity(entity).orderByDesc("CREATE_TIME"));
    }

    @Override
    public void delete(String id, boolean flag) {
        //进行关联校验  作战场景管理
        PageInfo<CompeteSceneDTO> pageInfo = competeSceneService.showCompeteScene(null, null, 0, 0, null);
        List<CompeteSceneDTO> list = pageInfo.getList();
        for (CompeteSceneDTO competeSceneDTO : list) {
            List<String> taskIdLists = competeSceneDTO.getTaskIdLists();
            if (CollUtil.isEmpty(taskIdLists)) {
                continue;
            }
            if (taskIdLists.contains(id)) {
                throw new BusinessException("使命任务已被关联！取消关联后在进行删除。");
            }
        }
        //
        Optional<MissionTaskEntity> evaOptional = findById(id);
        if (!evaOptional.isPresent()) {
            return;
        }
        MissionTaskEntity missionTaskEntity = evaOptional.get();
//        List<String> missionTaskIds = missionTaskRepository.findByPid(id);
        List<String> missionTaskIds = new ArrayList<>();
        for (MissionTaskEntity missionTask : this.list(new QueryWrapper<MissionTaskEntity>().eq("PID", id))) {
            missionTaskIds.add(missionTask.getId());
        }
        if (!CollUtil.isEmpty(missionTaskIds)) {
            for (String missionTaskId : missionTaskIds) {
                if (missionTaskId.equals(id)) {
                    continue;
                }
                delete(missionTaskId, false);
            }
        }
//        missionTaskRepository.deleteByPid(id);
//        missionTaskRepository.deleteById(id);
        this.removeById(id);
        String pid = StringUtils.isEmpty(missionTaskEntity.getPid()) ? "0" : missionTaskEntity.getPid();
        if (flag && !pid.equals("0")) {
            Optional<MissionTaskEntity> byId = findById(missionTaskEntity.getPid());
            if (byId.isPresent()) {
                MissionTaskEntity missionTaskEntity1 = byId.get();
                if (find(MissionTaskEntity.builder().pid(missionTaskEntity1.getId()).build()).size() == 0) {
                    missionTaskEntity1.setFlag(2);
                    edit(missionTaskEntity1);
                }
            }
        }

    }

    @Override
    public List<Node> treeList() {
        List<MissionTaskEntity> list = find(MissionTaskEntity.builder().build());
        List<MissionTaskNode> missionTaskNodeList = new ArrayList<>();
        for (MissionTaskEntity missionTask : list) {
            MissionTaskNode missionTaskNode = new MissionTaskNode();
            BeanUtil.copyProperties(missionTask, missionTaskNode);
            missionTaskNodeList.add(missionTaskNode);
        }
        return BuildTree.buildTree(missionTaskNodeList);
    }

    /**
     * 实现通过ID获取对应使命任务数据
     *
     * @param taskIdLists 任务集合
     * @return
     */
    @Override
    public Map<String, MissionTaskEntity> selectByIdIn(List<String> taskIdLists) {
        if (CollUtil.isEmpty(taskIdLists)) {
            return null;
        }
//        List<MissionTaskEntity> missionTaskEntities = missionTaskRepository.findAllById(taskIdLists);
        List<MissionTaskEntity> missionTaskEntities = this.listByIds(taskIdLists);
        Map<String, MissionTaskEntity> result = Maps.newHashMapWithExpectedSize(missionTaskEntities.size());
        for (MissionTaskEntity missionTask : missionTaskEntities) {
            //todo 这里会出现空指针异常
            Integer flag = missionTask.getFlag() == null ? 0 : missionTask.getFlag();
            if (flag == 2) {
                result.put(missionTask.getId(), missionTask);
            }
        }
        return result;
    }

    @Override
    public void saveAll(List<MissionTaskEntity> missionTaskEntityList) {
//        missionTaskRepository.saveAll(missionTaskEntityList);
        this.saveBatch(missionTaskEntityList);
    }

    @Override
    public MissionTaskEntity getSourceId(String id, String sid) {
//        Optional<MissionTaskEntity> byId = missionTaskRepository.findOne(Example.of(MissionTaskEntity.builder().sourceId(id).sid(sid).build()));
        Optional<MissionTaskEntity> byId = Optional.ofNullable(this.getOne(new QueryWrapper<MissionTaskEntity>().setEntity(MissionTaskEntity.builder().sourceId(id).sid(sid).build())));
        if (byId.isPresent()) {
            MissionTaskEntity missionTaskEntity = byId.get();
            return missionTaskEntity;
        }
        return null;
    }

    /**
     * 导入使命任务接口
     *
     * @param param
     * @return
     */
    @Override
    public MissionTaskEntity importMissionData(MissionTaskParam param) {
        MissionTaskEntity entity = MissionTaskEntity.builder()
                .id(param.getId())
                .name(param.getName())
                .backGround(param.getBackGround())
                .descs(param.getDescription())
                .createTime(LocalDateTime.now())
                .sid(param.getSid())
                .pid(0 + "")
                .sourceId(param.getSourceId())
                .build();
//        Optional<MissionTaskEntity> optional = missionTaskRepository.findOne(
//                Example.of(MissionTaskEntity.builder().sourceId(param.getSourceId()).build()));
        Optional<MissionTaskEntity> optional = Optional.ofNullable(this.getOne(new QueryWrapper<MissionTaskEntity>().setEntity(MissionTaskEntity.builder().sourceId(param.getSourceId()).build())));
        if (optional.isPresent()) {
            String id = optional.get().getId();
            entity.setId(id);
        }

//        missionTaskRepository.saveAndFlush(entity);
        this.saveOrUpdate(entity);
        return entity;
    }

    @Override
    public String editV2(MissionTaskEntity entity) {
        if (StringUtils.isEmpty(entity.getId())) {
            String uuid = UUIDGenerator.getUUID();
            entity.setId(uuid);
            entity.setSourceId(entity.getName());
        }
        entity.setSourceId(entity.getName());
//        MissionTaskEntity missionTaskEntity = missionTaskRepository.saveAndFlush(entity);
        this.saveOrUpdate(entity);
        return entity.getId();
    }

    @Override
    public List<MissionTaskEntity> listBatch(List<String> ids) {
        if (CollUtil.isNotEmpty(ids)) {
//            List<MissionTaskEntity> allById = missionTaskRepository.findAllById(ids);
            List<MissionTaskEntity> allById = this.listByIds(ids);
            return allById != null ? allById : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String edit2(MissionTaskEntity entity, String competeSceneId, MissionTaskDetailsDTO missionTaskDetailsDTO) {
        String id = entity.getId();
        String name = entity.getName();
        checkName(id, name, entity.getSid());
        String uuid = StringUtils.isNotBlank(id) ? entity.getId() : UUIDGenerator.getUUID();
        entity.setSourceId(uuid);
        entity.setName(name);
        entity.setPid(StringUtils.isNotEmpty(entity.getPid()) ? entity.getPid() : "0");
        if (entity.getPid().equals("0")) {
            entity.setFlag(2);
        } else {
            List<MissionTaskEntity> missionTaskEntities = find(MissionTaskEntity.builder().pid(uuid).build());
            if (CollUtil.isEmpty(missionTaskEntities)) {
                entity.setFlag(2);
            } else {
                entity.setFlag(1);
            }
        }
        if (StringUtils.isNotBlank(entity.getId())) {
//            Optional<MissionTaskEntity> misOptional = missionTaskRepository.findById(entity.getId());
            Optional<MissionTaskEntity> misOptional = Optional.ofNullable(this.getById(entity.getId()));
            if (!misOptional.isPresent()) {
                throw new BusinessException(ServiceConstant.NO_SUCK_OBJECT);
            }
            entity.setCreateTime(misOptional.get().getCreateTime());
        }

        entity.setId(uuid);
        entity.setCompeteId(competeSceneId);
        if (!StringUtils.isEmpty(entity.getPid())) {
            //修改父节点为分类标识
            Optional<MissionTaskEntity> missionTaskEntityOptional = findById(entity.getPid());
            if (missionTaskEntityOptional.isPresent()) {
                MissionTaskEntity missionTaskEntity = missionTaskEntityOptional.get();
                missionTaskEntity.setFlag(1);
                this.saveOrUpdate(missionTaskEntity);
            }
        }
        this.saveOrUpdate(entity);

        CompeteSceneEntity competeSceneEntity = competeSceneService.getById(competeSceneId);
        if (ObjectUtil.isNull(competeSceneEntity)) {
            throw new BusinessException("未查询到任务所属场景信息");
        }
        competeSceneEntity.setTaskIdsList(uuid);
        competeSceneService.saveOrUpdate(competeSceneEntity);

        TaskSceneEntity saveEntity = new TaskSceneEntity();
        saveEntity.setId(UUIDGenerator.getUUID());
        //使命任务ID
        saveEntity.setTaskId(uuid);
        //场景ID
        saveEntity.setSceneId(competeSceneId);
        taskSceneMapper.insert(saveEntity);

        //新增任务详情
        if (ObjectUtil.isNotNull(missionTaskDetailsDTO)) {
            this.saveOrUpdateMissionTaskDetails(uuid, entity.getSid(), missionTaskDetailsDTO);
        }
        return uuid;
    }

    /**
     * 新增/编辑任务详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveOrUpdateMissionTaskDetails(String missionTaskId, String sid, MissionTaskDetailsDTO missionTaskDetailsDTO) {
        return missionTaskDetailsService.saveOrUpdateMissionTaskDetails(missionTaskId, sid, missionTaskDetailsDTO);
    }

    /**
     * 查询任务详情
     */
    @Override
    public MissionTaskEntityVO findTaskById(String id) {
        log.info("根据ID-【{}】查询任务", id);
        MissionTaskEntity missionTaskEntity = this.getById(id);
        if (ObjectUtil.isNull(missionTaskEntity)) {
            log.error("未查询到任务信息");
            throw new BusinessException("未查询到任务信息");
        }

        log.info("查询任务详情");
        MissionTaskDetailsEntity missionTaskDetailsEntity = missionTaskDetailsService.findByMissionTaskId(id);

        log.info("Entity TO VO 返回前端信息");
        return buildMissionTaskVO(missionTaskEntity, missionTaskDetailsEntity);
    }

    /**
     * 删除任务详情
     *
     * @param id 任务ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDetails(String id) {
        //删除任务详情
        missionTaskDetailsService.deleteByMissionTaskId(id);
        //删除任务相关的指标
        return missionTaskDetailsService.deleteEvaluationIndicator(id);
    }


    /**
     * Entity TO VO 返回前端信息
     */
    private MissionTaskEntityVO buildMissionTaskVO(MissionTaskEntity missionTaskEntity, MissionTaskDetailsEntity missionTaskDetailsEntity) {
        MissionTaskEntityVO missionTaskEntityVO = new MissionTaskEntityVO();
        missionTaskEntityVO.setName(missionTaskEntity.getName());
        missionTaskEntityVO.setDescs(missionTaskEntity.getDescs());
        missionTaskEntityVO.setComponentSceneId(missionTaskEntity.getCompeteId());
        if (ObjectUtil.isNotNull(missionTaskDetailsEntity)) {
            BeanUtil.copyProperties(missionTaskDetailsEntity, missionTaskEntityVO);
            if (StrUtil.isNotBlank(missionTaskDetailsEntity.getEvaluationIndicators())) {
                missionTaskEntityVO.setEvaluationIndicatorDTOList(JSONUtil.toList(JSONUtil.parseArray(missionTaskDetailsEntity.getEvaluationIndicators()), EvaluationIndicatorDTO.class));
            }
            // 解析任务关系列表
            if (StrUtil.isNotBlank(missionTaskDetailsEntity.getRelationshipsJson())) {
                missionTaskEntityVO.setRelationships(JSONUtil.toList(JSONUtil.parseArray(missionTaskDetailsEntity.getRelationshipsJson()), RelationshipItemDTO.class));
            }
        }
        missionTaskEntityVO.setId(missionTaskEntity.getId());

        return missionTaskEntityVO;
    }

    /**
     * 获取任务关系图
     *
     * @param sid 方案 ID
     * @return 任务关系图列表
     */
    @Override
    public List<MissionGraphVO> getRelationGraph(String sid) {
        log.info("获取任务关系图，方案 ID: {}", sid);

        // 1. 查询所有关系类型，构建 ID 到名称的映射
        List<MissionRelationTypeEntity> relationTypes = missionRelationTypeService.list();
        Map<String, String> relationTypeMap = relationTypes.stream()
                .filter(t -> t != null && StrUtil.isNotBlank(t.getId()))
                .collect(Collectors.toMap(MissionRelationTypeEntity::getId, MissionRelationTypeEntity::getTypeName, (v1, v2) -> v1));

        List<MissionTaskEntity> tasks = find(MissionTaskEntity.builder().sid(sid).build());
        if (CollUtil.isEmpty(tasks)) {
            throw new BusinessException("方案下没有关联的任务信息！");
        }

        // 2. 批量查询所有任务详情
        List<String> missionTaskIds = tasks.stream()
                .filter(t -> t != null && StrUtil.isNotBlank(t.getId()))
                .map(MissionTaskEntity::getId)
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(missionTaskIds)) {
            return Collections.emptyList();
        }

        List<MissionTaskDetailsEntity> detailsList = missionTaskDetailsService.list(
                new LambdaQueryWrapper<MissionTaskDetailsEntity>()
                        .in(MissionTaskDetailsEntity::getMissionTaskId, missionTaskIds)
        );

        // 3. 构建任务详情映射
        Map<String, MissionTaskDetailsEntity> detailMap = detailsList.stream()
                .filter(d -> d != null && StrUtil.isNotBlank(d.getMissionTaskId()))
                .collect(Collectors.toMap(MissionTaskDetailsEntity::getMissionTaskId, e -> e, (v1, v2) -> v1));

        // 4. 构建任务 ID 到名称的映射
        Map<String, String> taskNameMap = tasks.stream()
                .filter(t -> t != null && StrUtil.isNotBlank(t.getId()) && StrUtil.isNotBlank(t.getName()))
                .collect(Collectors.toMap(MissionTaskEntity::getId, MissionTaskEntity::getName, (v1, v2) -> v1));

        // 5. 组装关系图
        List<MissionGraphVO> result = new ArrayList<>();
        MissionGraphVO graphVO = new MissionGraphVO();
        List<GraphNodeVO> nodes = new ArrayList<>();
        List<GraphEdgeVO> edges = new ArrayList<>();
        graphVO.setNodes(nodes);
        graphVO.setEdges(edges);
        for (MissionTaskEntity task : tasks) {
            if (task == null) {
                continue;
            }
            // 构建节点
            GraphNodeVO node = new GraphNodeVO();
            String taskId = task.getId();
            node.setId(taskId);
            // 如果有关联任务名称映射，使用映射的名称；否则使用 taskId 作为 label
            node.setLabel(taskNameMap.getOrDefault(taskId, taskId));
            // 设置坐标

            nodes.add(node);
            if (StrUtil.isNotBlank(task.getId())) {
                MissionTaskDetailsEntity detail = detailMap.get(task.getId());
                if (detail != null){
                    node.setX(Objects.isNull(detail.getNodeX()) ? null : detail.getNodeX());
                    node.setY(Objects.isNull(detail.getNodeY()) ? null : detail.getNodeY());
                }
                if (detail != null && StrUtil.isNotBlank(detail.getRelationshipsJson())) {
                    try {
                        // 反序列化 JSON 为 RelationshipItemDTO 列表
                        JSONArray jsonArray = JSONUtil.parseArray(detail.getRelationshipsJson());
                        if (!jsonArray.isEmpty()) {
                            List<RelationshipItemDTO> relationships = JSONUtil.toList(jsonArray, RelationshipItemDTO.class);
                            if (CollUtil.isNotEmpty(relationships)) {

                                for (RelationshipItemDTO rel : relationships) {
                                    if (rel == null) {
                                        continue;
                                    }
                                    String toTaskId = rel.getCorrelationTaskId();
                                    if (StrUtil.isBlank(toTaskId)) {
                                        continue;
                                    }
                                    // 构建连线
                                    GraphEdgeVO edge = new GraphEdgeVO();
                                    edge.setId(UUIDGenerator.getUUID());
                                    edge.setFrom(task.getId());  // 从当前任务出发
                                    edge.setTo(toTaskId);  // 到关联任务
                                    // 关系标签：从关系类型表中查询
                                    String edgeLabel = relationTypeMap.getOrDefault(rel.getCorrelationType(), rel.getCorrelationType());
                                    edge.setLabel(edgeLabel);
                                    edges.add(edge);
                                }

                            }
                        }
                    } catch (Exception e) {
                        log.error("解析任务关系 JSON 失败，任务 ID: {}", task.getId(), e);
                    }
                }
            }

        }
        result.add(graphVO);

        log.info("任务关系图构建完成，共 {} 个任务", result.size());
        return result;
    }

    /**
     * 批量更新任务关系项坐标（支持拖动节点后保存坐标，按方案批量保存）
     * 这里保存坐标了
     *
     * @param competeSceneId 方案ID
     * @param request        更新请求（包含多个任务的坐标组）
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRelationshipItems(String competeSceneId, UpdateRelationshipItemsRequest request) {
        if (StrUtil.isBlank(competeSceneId)) {
            log.error("方案 ID 不能为空");
            throw new BusinessException("方案 ID 不能为空");
        }
        if (ObjectUtil.isNull(request) || CollUtil.isEmpty(request.getGraphList())) {
            log.error("关系图列表不能为空");
            throw new BusinessException("关系图列表不能为空");
        }

        log.info("批量更新任务关系项坐标，方案 ID: {}, 关系图组数: {}", competeSceneId, request.getGraphList().size());

        int totalUpdatedCount = 0;

        // 遍历每个关系图组（对应一个任务）
        for (MissionGraphVO graphVO : request.getGraphList()) {
            if (ObjectUtil.isNull(graphVO) || CollUtil.isEmpty(graphVO.getNodes())) {
                log.warn("跳过无效关系图组");
                continue;
            }
            int updatedCount = 0;
            for (GraphNodeVO node : graphVO.getNodes()) {
                // 第一个 node 是当前任务本身，id 即为任务 ID
                String missionTaskId = node.getId();
                // 1. 查询任务详情
                MissionTaskDetailsEntity details = missionTaskDetailsService.findByMissionTaskId(missionTaskId);
                if (ObjectUtil.isNull(details)) {
                    log.error("未查询到任务详情，任务 ID: {}", missionTaskId);
                    continue;
                }
                details.setNodeX(node.getX());
                details.setNodeY(node.getY());
//                    details.setRelationshipsJson(jsonArray.toString());
                missionTaskDetailsService.saveOrUpdate(details);
                totalUpdatedCount += updatedCount;
                log.info("任务关系项更新成功，任务 ID: {}, 更新数量: {}", missionTaskId, updatedCount);
            }


//            // 2. 解析关系 JSON
//            String relationshipsJson = details.getRelationshipsJson();
//            JSONArray jsonArray;
//            if (StrUtil.isNotBlank(relationshipsJson)) {
//                try {
//                    jsonArray = JSONUtil.parseArray(relationshipsJson);
//                } catch (Exception e) {
//                    log.error("解析任务关系 JSON 失败，任务 ID: {}", missionTaskId, e);
//                    continue;
//                }
//            } else {
//                jsonArray = new JSONArray();
//            }
//
//            // 3. 更新关系项坐标（跳过第一个 node，它是当前任务本身）
//            int updatedCount = 0;
//            for (int i = 1; i < graphVO.getNodes().size(); i++) {
//                GraphNodeVO node = graphVO.getNodes().get(i);
//                if (ObjectUtil.isNull(node) || StrUtil.isBlank(node.getId())) {
//                    continue;
//                }
//
//                // 在 relationships JSON 中查找匹配的 correlationTaskId
//                for (int j = 0; j < jsonArray.size(); j++) {
//                    JSONObject jsonObj = jsonArray.getJSONObject(j);
//                    if (jsonObj == null) {
//                        continue;
//                    }
//                    Object corrTaskId = jsonObj.get("correlationTaskId");
//                    if (node.getId().equals(corrTaskId)) {
//                        // 更新坐标
//                        jsonObj.put("x", node.getX());
//                        jsonObj.put("y", node.getY());
//                        updatedCount++;
//                        break;
//                    }
//                }
//            }
//
//            // 3.1 保存当前任务节点（第一个 node）的坐标到数据库
//            GraphNodeVO firstNode = graphVO.getNodes().get(0);
//            boolean nodeCoordChanged = false;
//            if (ObjectUtil.isNotNull(firstNode) && StrUtil.isNotBlank(firstNode.getId())) {
//                Double newNodeX = firstNode.getX() != null ? firstNode.getX() : 0.0;
//                Double newNodeY = firstNode.getY() != null ? firstNode.getY() : 0.0;
//                Double oldNodeX = details.getNodeX() != null ? details.getNodeX() : 0.0;
//                Double oldNodeY = details.getNodeY() != null ? details.getNodeY() : 0.0;
//                if (!newNodeX.equals(oldNodeX) || !newNodeY.equals(oldNodeY)) {
//                    details.setNodeX(newNodeX);
//                    details.setNodeY(newNodeY);
//                    nodeCoordChanged = true;
//                }
//            }


        }

        log.info("批量更新任务关系项坐标完成，方案 ID: {}, 总更新数量: {}", competeSceneId, totalUpdatedCount);
        return totalUpdatedCount > 0;
    }
}
