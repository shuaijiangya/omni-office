package com.yw.module.indicator.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.yw.framework.common.base.api.R;
import com.yw.framework.common.component.quartz.domain.enums.YesOrNo;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.indicator.domain.entity.*;
import com.yw.module.indicator.service.*;
import com.yw.module.missionScene.service.CompeteSceneService;
import com.yw.module.missionScene.service.IMissionTaskService;
import com.yw.module.indicator.mapper.IndicatorCriteriaMapper;
import com.yw.module.missionScene.domain.entity.CompeteSceneEntity;
import com.yw.module.missionScene.domain.entity.MissionTaskEntity;
import com.yw.module.indicator.domain.enums.MergeTablesFiled;
import com.yw.common.defines.base.FixedColumnVo;
import com.yw.common.defines.base.MergeTablesVo;
import com.yw.framework.common.util.StringUtils;


import com.yw.common.utils.UUIDGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description:指标准则服务实现
 * @version: v1.0
 * @author: superman
 * @time: 2023-04-12 15:15:28
 */

@RequiredArgsConstructor
@Service
public class IndicatorCriteriaServiceImpl extends ServiceImpl<IndicatorCriteriaMapper, IndicatorCriteria> implements IIndicatorCriteriaService {

//    private final IndicatorCriteriaRepsitory indicatorCriteriaRepsitory;

    private final CompeteSceneService competeSceneService;

    private final IMissionTaskService missionTaskService;

    private final IIndicatorService indicatorService;

    private final IIndicatorTreeService indicatorTreeService;

    /**
     * 注入指标准则Service接口
     */
    private final ITIndicatorStandardService indicatorStandardService;


    @Override
    public IndicatorCriteria getById(String id) {
//        Optional<IndicatorCriteria> indicatorCriteria = indicatorCriteriaRepsitory.findById(id);
        Optional<IndicatorCriteria> indicatorCriteria = Optional.ofNullable(baseMapper.selectById(id));
        return indicatorCriteria.orElse(null);
    }


    @Override
    public List<IndicatorCriteria> list(IndicatorCriteria indicatorCriteria) {

        Example<IndicatorCriteria> example = Example.of(indicatorCriteria);
//        return indicatorCriteriaRepsitory.findAll(example, Sort.by("type").ascending());
        return this.list(new QueryWrapper<IndicatorCriteria>().setEntity(indicatorCriteria).orderByAsc("TYPE"));
    }

    /**
     * @param sceneId       场景id
     * @param missionTaskId
     * @description: 实现根据场景id分类获取评估准则
     * @return: Map  key：任务id_指标id，value：准则的集合
     * @author: tqh
     * @time: 2023-04-12 15:15:28
     */
    public Map<String, List<IndicatorCriteria>> getClassifyDataBySceneId(String sceneId, String missionTaskId) {
        Map<String, List<IndicatorCriteria>> map = new HashMap<>();
        //根据场景id获取所有的任务id
        List<String> taskIds = new ArrayList<>();
        //根据场景id获取所有的指标id
        List<String> indicatorIds = new ArrayList<>();
        List<IndicatorCriteria> list = this.list(new LambdaQueryWrapper<IndicatorCriteria>().eq(StringUtils.isNotEmpty(missionTaskId), IndicatorCriteria::getTaskId, missionTaskId));
        list.forEach(indicatorCriteria -> {
            taskIds.add(indicatorCriteria.getTaskId());
            indicatorIds.add(indicatorCriteria.getIndicatorId());
        });
//        List<String> taskIds = indicatorCriteriaRepsitory.findDistinctTaskIdBySceneId(sceneId);

//        List<String> indicatorIds = indicatorCriteriaRepsitory.findDistinctIndicatorIdBySceneId(sceneId);

        //双重循环获取任务和指标下的指标准则
        for (String taskId : taskIds) {
            for (String indicatorId : indicatorIds) {
//                map.put(taskId + "_" + indicatorId, indicatorCriteriaRepsitory.findByTaskIdAndIndicatorIdOrderByTypeAsc(taskId, indicatorId));
                map.put(taskId + "_" + indicatorId, this.list(new QueryWrapper<IndicatorCriteria>().eq("TASK_ID", taskId).eq("INDICATOR_ID", indicatorId).orderByAsc("TYPE")));
            }
        }
        return map;
    }


    @Override
    public void saveAndFlush(IndicatorCriteria indicatorCriteria) {
        if (StringUtils.isBlank(indicatorCriteria.getId())) {
            indicatorCriteria.setId(UUIDGenerator.getUUID());
            indicatorCriteria.setCreateDate(LocalDateTime.now());
            indicatorCriteria.setUpdateDate(indicatorCriteria.getCreateDate());
        } else {
            indicatorCriteria.setUpdateDate(LocalDateTime.now());
        }
//        indicatorCriteriaRepsitory.saveAndFlush(indicatorCriteria);
        this.saveOrUpdate(indicatorCriteria);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public R saveAllAndFlushAllV2(List<IndicatorCriteria> indicatorCriterias) {
        //数据校验
        checkIndicatorCriteria(indicatorCriterias);
        // 对不同指标矩阵分组，得到每一组矩阵
        Map<String, List<IndicatorCriteria>> groupMap = new HashMap<>();
        for (IndicatorCriteria indicatorCriteria : indicatorCriterias) {
            String key = indicatorCriteria.getTaskId() + "-" + indicatorCriteria.getSceneId() + "-"
                    + indicatorCriteria.getIndicatorId();
            if (groupMap.containsKey(key)) {
                groupMap.get(key).add(indicatorCriteria);
            } else {
                List<IndicatorCriteria> indicatorCriteriaList = new ArrayList<>();
                indicatorCriteriaList.add(indicatorCriteria);
                groupMap.put(key, indicatorCriteriaList);
            }
        }

        R<List<Map<String, Object>>> apiResultData = new R();
        //每一组矩阵分别判断区间冲突
        for (String key : groupMap.keySet()) {
            List<Map<String, Object>> mapList = new ArrayList<>();
            List<IndicatorCriteria> indicatorCriteriaList = groupMap.get(key);
            //矩阵区间从小到大排序
            indicatorCriteriaList = indicatorCriteriaList.stream().sorted(Comparator.comparing(IndicatorCriteria::getType, ((o1, o2) -> o2.compareTo(o1)))).collect(Collectors.toList());

            for (int i = 0; i < indicatorCriteriaList.size(); i++) {
                IndicatorCriteria criteria = indicatorCriteriaList.get(i);

                //左右区间都为null,表示未设置
                if (StringUtils.isEmpty(criteria.getMinValue()) && StringUtils.isEmpty(criteria.getMaxValue())) {
                    mapList.add(getMatrixErrorMsg(criteria));
                    apiResultData.setData(mapList);
                    apiResultData.setCode(0);
                    apiResultData.setMsg("矩阵区间未设置");
                    return apiResultData;
                }

                if (org.apache.commons.lang3.StringUtils.isEmpty(criteria.getMaxValue()) || "+".equals(criteria.getMaxValue())) {
                    criteria.setMaxValue("+");
                }
                if (org.apache.commons.lang3.StringUtils.isEmpty(criteria.getMinValue()) || "-".equals(criteria.getMinValue())) {
                    criteria.setMinValue("-");
                }

                //左区间大于右区间，区间冲突
                if (!"+".equals(criteria.getMaxValue()) && !"-".equals(criteria.getMinValue()) &&
                        Double.compare(Double.parseDouble(criteria.getMinValue()), Double.parseDouble(criteria.getMaxValue())) == 1) {
                    mapList.add(getMatrixErrorMsg(criteria));
                    apiResultData.setData(mapList);
                    apiResultData.setCode(0);
                    apiResultData.setMsg("矩阵区间冲突:左区间大于右区间");
                    return apiResultData;
                }
            }

            boolean flagLargeToSmall = true;
            boolean flagSmallToLarge = true;
            for (int i = 0; i < indicatorCriteriaList.size(); i++) {
                IndicatorCriteria criteria = indicatorCriteriaList.get(i);
                String min1 = criteria.getMinValue();
                String max1 = criteria.getMaxValue();
                if ("-".equals(criteria.getMinValue())){
                    min1="-99999999999999";
                }
                if ("+".equals(criteria.getMaxValue())){
                    max1 = "999999999999";
                }
                //判断设置输出值是否合理
//                if(criteria.getMinValue() != null && criteria.getMaxValue() != null && criteria.getFunc() != null &&  (Double.compare(criteria.getMinValue(),Double.parseDouble(criteria.getFunc())) == 1 || Double.compare(Double.parseDouble(criteria.getFunc()),criteria.getMaxValue()) == 1)){
//                    mapList.add(getMatrixErrorMsg(criteria));
//                    apiResultData.setData(mapList);
//                    apiResultData.setCode(0);
//                    apiResultData.setMsg("输出值设置有误");
//                    return apiResultData;
//                }


                //和别的区间做对比
                for (int j = i + 1; j < indicatorCriteriaList.size(); j++) {
                    IndicatorCriteria criteria2 = indicatorCriteriaList.get(j);
                    String min2 = criteria2.getMinValue();
                    String max2 = criteria2.getMaxValue();
                    if ("-".equals(criteria2.getMinValue())){
                        min2="-99999999999999";
                    }
                    if ("+".equals(criteria2.getMaxValue())){
                        max2 = "999999999999";
                    }

                    if (!isOverlappingLargeToSmall(min1,max2) && flagSmallToLarge) {
                        flagLargeToSmall = false;
                        continue;
                    }
                    if (!isOverlappingSmallToLarge(max1, min2) && flagLargeToSmall) {
                        flagSmallToLarge = false;
                        continue;
                    }
                    mapList.add(getMatrixErrorMsg(criteria));
                    mapList.add(getMatrixErrorMsg(criteria2));
                    apiResultData.setData(mapList);
                    apiResultData.setCode(0);
                    apiResultData.setMsg("矩阵区间冲突");
                    return apiResultData;
                }
            }
        }
        // 场景id
//        String sceneId = indicatorCriterias.get(0).getSceneId();
        //按任务、指标ID删除
        List<String> taskId = indicatorCriterias.stream().map(IndicatorCriteria::getTaskId).distinct().collect(Collectors.toList());
        List<String> indicatorIdList = indicatorCriterias.stream().map(IndicatorCriteria::getIndicatorId).distinct().collect(Collectors.toList());
//        this.indicatorCriteriaRepsitory.deleteBySceneId(sceneId);
//        this.remove(new QueryWrapper<IndicatorCriteria>().eq("SCENE_ID",sceneId));
        this.remove(new LambdaQueryWrapper<IndicatorCriteria>()
                .in(IndicatorCriteria::getTaskId, taskId)
                .in(IndicatorCriteria::getIndicatorId, indicatorIdList));
        for (IndicatorCriteria indicatorCriteria : indicatorCriterias) {
            if (StringUtils.isBlank(indicatorCriteria.getId())) {
                indicatorCriteria.setId(UUIDGenerator.getUUID());
                indicatorCriteria.setCreateDate(LocalDateTime.now());
                indicatorCriteria.setUpdateDate(indicatorCriteria.getCreateDate());
            } else {
                indicatorCriteria.setUpdateDate(LocalDateTime.now());
            }
        }
        this.saveOrUpdateBatch(indicatorCriterias);

        return R.success();
    }

    /**
     * 优良中差--从小到大校验
     *
     * @return false：代表区间无冲突
     */
    public boolean isOverlappingSmallToLarge(String thisMaxValue, String otherMinValue) {
        if (thisMaxValue == null) {
            thisMaxValue = "+";
        }
        if (otherMinValue == null) {
            otherMinValue = "-";
        }

        /**
         * this.thisMaxValue > other.otherMinValue
         */

        try {
            return /*Double.compare(thisMaxValue, otherMinValue) == 0 ||*/ Double.compare(Double.parseDouble(thisMaxValue), Double.parseDouble(otherMinValue)) == 1;
        } catch (NumberFormatException e) {
            //e.printStackTrace();
            return false;
        }
    }

    /**
     * 优良中差--从大到小校验
     *
     * @return false：代表区间无冲突
     */
    public boolean isOverlappingLargeToSmall(String thisMinValue, String otherMaxValue) {
        if (thisMinValue == null) {
            thisMinValue = "-";
        }
        if (otherMaxValue == null) {
            otherMaxValue ="+";
        }

        try {
            /**
             * this.minValue < other.maxValue
             */
            return /*Double.compare(thisMinValue, otherMaxValue) == 0 ||*/ Double.compare(Double.parseDouble(thisMinValue), Double.parseDouble(otherMaxValue)) == -1;
        } catch (NumberFormatException e) {
            //e.printStackTrace();
            return false;
        }
    }


    /**
     * 校验添加的准则信息
     */
    private void checkIndicatorCriteria(List<IndicatorCriteria> indicatorCriterias) {
        if (CollUtil.isEmpty(indicatorCriterias)) {
            throw new BusinessException("待添加的准则信息为空");
        }
        for (IndicatorCriteria indicatorCriteria : indicatorCriterias) {
            if (StrUtil.isBlank(indicatorCriteria.getTaskId())) {
                throw new BusinessException("新增准则的任务ID不能为空");
            }
            if (StrUtil.isBlank(indicatorCriteria.getIndicatorId())) {
                throw new BusinessException("新增准则的指标ID不能为空");
            }
        }
    }

    private Map<String, Object> getMatrixErrorMsg(IndicatorCriteria criteria) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", criteria.getTaskId());
        map.put("sceneId", criteria.getSceneId());
        map.put("indicatorId", criteria.getIndicatorId());
        map.put("type", criteria.getType());
        return map;
    }

    @Override
    public void saveAllAndFlushAll(List<IndicatorCriteria> indicatorCriterias) {
        for (IndicatorCriteria indicatorCriteria : indicatorCriterias) {
            saveAndFlush(indicatorCriteria);
        }

    }

    @Override
    public MergeTablesVo getAbilityDataBySceneid(String sceneId) {
        MergeTablesVo tablesVo = new MergeTablesVo();
        List<FixedColumnVo> fixedColumn = new ArrayList<>();
        CompeteSceneEntity competeScene = competeSceneService.selectById(sceneId);
        //根据场景获取所有的任务
        if (null == competeScene || CollectionUtils.isEmpty(competeScene.getTaskIdLists())) {
            return null;
        }
        Map<String, MissionTaskEntity> missionTaskEntities = missionTaskService.selectByIdIn(competeScene.getTaskIdLists());
        //根据方案id获取能力指标树 todo 查询结果不唯一
        IndicatorTreeEntity indicatorTreeType = indicatorTreeService.findBySidAndIndicatorTreeType(competeScene.getSid(), 1);
        //根据指标树id获取所有的指标
        if (indicatorTreeType == null) return null;
        List<IndicatorEntity> indicatorEntities = indicatorService.findByTreeId(indicatorTreeType.getId());
        //获取指标层级
        Optional<IndicatorEntity> max = indicatorEntities.stream().max(Comparator.comparing(IndicatorEntity::getLevel));
        //根据指标层数构建表格头
        if (max.isPresent() && !Objects.isNull(max.get().getLevel())) {
            for (int i = 1; i <= max.get().getLevel() + 1; i++) {
                FixedColumnVo columnVo = FixedColumnVo.builder().isMerge(YesOrNo.YES.getCode()).label(i + MergeTablesFiled.GARTER.getDesc()).prop(MergeTablesFiled.GARTER.getFiled() + i).build();
                fixedColumn.add(columnVo);
            }
        }
        //根据zz任务构建表头
        FixedColumnVo columnVo = FixedColumnVo.builder().isMerge(YesOrNo.NO.getCode()).label(MergeTablesFiled.MISSION_TASK.getDesc()).prop(MergeTablesFiled.FIGTH_TASK.getFiled()).build();
        List<FixedColumnVo> childrenTaskFixedColumn = new ArrayList<>();
        int i = 1;
        for (String key : missionTaskEntities.keySet()) {
            MissionTaskEntity missionTaskEntity = missionTaskEntities.get(key);
            FixedColumnVo fixedColumnVo = FixedColumnVo.builder().isMerge(YesOrNo.NO.getCode()).label(missionTaskEntity.getName()).prop(MergeTablesFiled.FIGTH_TASK.getFiled() + i).build();
            childrenTaskFixedColumn.add(fixedColumnVo);
            i++;
        }
        columnVo.setPropsChildren(childrenTaskFixedColumn);
        fixedColumn.add(columnVo);
        tablesVo.setFixedColumn(fixedColumn);
        //构建数据
        List<Map<String, Object>> listData = new ArrayList<>();
        //获取最下级指标树数据  调整，当两边不对称时，层级小的一边会获取不到，调整为 指标ID没有被当作Pid的就是所有分支的最后一级
        List<String> pidList = indicatorEntities.stream().map(IndicatorEntity::getPid).collect(Collectors.toList());
        List<IndicatorEntity> lowestIndicator = indicatorEntities.stream().filter(indicatorEntity -> !pidList.contains(indicatorEntity.getId())).collect(Collectors.toList());
        // List<IndicatorEntity> lowestIndicator = indicatorEntities.stream().filter(indicatorEntitie -> indicatorEntitie.getLevel() == max.get().getLevel()).collect(Collectors.toList());
        for (IndicatorEntity indicator : lowestIndicator) {
            String pid = indicator.getPid();
            Map<String, Object> map = new HashMap<>();
            //遍历填充任务数据
            int c = 1;
            for (Map.Entry<String, MissionTaskEntity> entry : missionTaskEntities.entrySet()) {
                //构建4条数据
                ArrayList<IndicatorCriteria> arrayList = new ArrayList<>();
                for (int j = 1; j < 5; j++) {
//                    List<IndicatorCriteria> criteriaList = indicatorCriteriaRepsitory
//                            .findBySceneIdAndTaskIdAndIndicatorIdAndTypeOrderByTypeAsc(sceneId, entry.getKey(), indicator.getId(), j);
                    List<IndicatorCriteria> criteriaList = this.list(new QueryWrapper<IndicatorCriteria>().eq("SCENE_ID", sceneId).eq("TASK_ID", entry.getKey())
                            .eq("INDICATOR_ID", indicator.getId()).eq("TYPE", j).orderByAsc("TYPE"));
                    IndicatorCriteria criteria = null;
                    if (CollectionUtils.isEmpty(criteriaList)) {
                        IndicatorCriteria indicatorCriteria = IndicatorCriteria.builder().type(j).indicatorId(indicator.getId()).taskId(entry.getKey()).sceneId(sceneId).build();
                        criteria = indicatorCriteria;
                    } else {
                        criteria = criteriaList.get(criteriaList.size() - 1);
                    }

                    criteria.setType(j).setIndicatorId(indicator.getId()).setTaskId(entry.getKey()).setSceneId(sceneId);
                    arrayList.add(criteria);
                }
                for (FixedColumnVo fixedColumnVo : childrenTaskFixedColumn) {
                    if (org.apache.commons.lang3.StringUtils.equals(fixedColumnVo.getLabel(), entry.getValue().getName())) {
                        map.put(fixedColumnVo.getProp(), arrayList);
                    }
                }
                c++;
            }
            //遍历填充指标数据
            IndicatorEntity indicator1 = indicator;
            if (StringUtils.isNotEmpty(MergeTablesFiled.GARTER.getFiled()) && !Objects.isNull(indicator.getLevel())) {
                map.put(MergeTablesFiled.GARTER.getFiled() + (indicator.getLevel() + 1), indicator.getName());
            }
            int f = 0;
            while (indicator1 != null) {
                Optional<IndicatorEntity> indicatorEntity = indicatorService.findById(indicator1.getPid());
                if (indicatorEntity.isPresent()) {
                    map.put(MergeTablesFiled.GARTER.getFiled() + (indicator.getLevel() - f), indicatorEntity.get().getName());
                    indicator1 = indicatorEntity.get();
                } else {
                    break;
                }
                f++;
            }
            listData.add(map);
        }
        tablesVo.setListData(listData);
        return tablesVo;
    }

    @Override
    public MergeTablesVo getAbilityDataBySceneidV2(String schemaId, int indicatorTreeType) {
        MergeTablesVo tablesVo = new MergeTablesVo();
        List<FixedColumnVo> fixedColumn = new ArrayList<>();
        Map<String, MissionTaskEntity> missionTaskEntities = new HashMap<>();
        List<MissionTaskEntity> list = missionTaskService.list(new QueryWrapper<MissionTaskEntity>().eq("SID", schemaId));
        for (MissionTaskEntity missionTaskEntity : list) {
            missionTaskEntities.put(missionTaskEntity.getId(), missionTaskEntity);
        }
        //根据方案id获取能力指标树 todo 查询结果不唯一
        IndicatorTreeEntity indicatorTreeEntity = indicatorTreeService.findBySidAndIndicatorTreeType(schemaId, indicatorTreeType);
        //根据指标树id获取所有的指标
        if (indicatorTreeEntity == null) {
            return null;
        }
        List<IndicatorEntity> indicatorEntities = indicatorService.findByTreeId(indicatorTreeEntity.getId());
        //获取指标层级
        Optional<IndicatorEntity> max = indicatorEntities.stream().max(Comparator.comparing(IndicatorEntity::getLevel));
        //根据指标层数构建表格头
        if (max.isPresent() && !Objects.isNull(max.get().getLevel())) {
            for (int i = 1; i <= max.get().getLevel() + 1; i++) {
                FixedColumnVo columnVo = FixedColumnVo.builder().isMerge(YesOrNo.YES.getCode()).label(i + MergeTablesFiled.GARTER.getDesc()).prop(MergeTablesFiled.GARTER.getFiled() + i).build();
                fixedColumn.add(columnVo);
            }
        }
        //根据zz任务构建表头
        FixedColumnVo columnVo = FixedColumnVo.builder().isMerge(YesOrNo.NO.getCode()).label(MergeTablesFiled.MISSION_TASK.getDesc()).prop(MergeTablesFiled.FIGTH_TASK.getFiled()).build();
        List<FixedColumnVo> childrenTaskFixedColumn = new ArrayList<>();
        int i = 1;
        List<String> taskIds = Lists.newArrayList();
        for (String key : missionTaskEntities.keySet()) {
            MissionTaskEntity missionTaskEntity = missionTaskEntities.get(key);
            taskIds.add(missionTaskEntity.getId());
            FixedColumnVo fixedColumnVo = FixedColumnVo.builder().isMerge(YesOrNo.NO.getCode()).label(missionTaskEntity.getName()).prop(MergeTablesFiled.FIGTH_TASK.getFiled() + i).build();
            childrenTaskFixedColumn.add(fixedColumnVo);
            i++;
        }
        columnVo.setPropsChildren(childrenTaskFixedColumn);
        fixedColumn.add(columnVo);
        tablesVo.setFixedColumn(fixedColumn);
        //构建数据
        List<Map<String, Object>> listData = new ArrayList<>();
        //获取最下级指标树数据  调整，当两边不对称时，层级小的一边会获取不到，调整为 指标ID没有被当作Pid的就是所有分支的最后一级
        List<String> pidList = indicatorEntities.stream().map(IndicatorEntity::getPid).collect(Collectors.toList());
        List<IndicatorEntity> lowestIndicator = indicatorEntities.stream().filter(indicatorEntity -> !pidList.contains(indicatorEntity.getId())).collect(Collectors.toList());
        // List<IndicatorEntity> lowestIndicator = indicatorEntities.stream().filter(indicatorEntitie -> indicatorEntitie.getLevel() == max.get().getLevel()).collect(Collectors.toList());
        if (CollectionUtil.isEmpty(taskIds)) {
            return null;
        }
        List<IndicatorCriteria> indicatorCriteriaList = list(new LambdaQueryWrapper<IndicatorCriteria>().in(IndicatorCriteria::getTaskId, taskIds));
        Map<String, List<IndicatorCriteria>> indicatorCriteriaMap = indicatorCriteriaList.stream().collect(Collectors.groupingBy(s -> s.getTaskId() + s.getIndicatorId()));
        for (IndicatorEntity indicator : lowestIndicator) {
            Map<String, Object> map = new HashMap<>();
            //遍历填充任务数据
            int c = 1;
            for (Map.Entry<String, MissionTaskEntity> entry : missionTaskEntities.entrySet()) {
                //构建4条数据
                ArrayList<IndicatorCriteria> arrayList = new ArrayList<>();
                String taskId = entry.getKey();
                String indicatorId = indicator.getId();
                for (int j = 1; j < 5; j++) {
                    List<IndicatorCriteria> criteriaList = indicatorCriteriaMap.get(taskId + indicatorId);
                    IndicatorCriteria criteria = null;
                    if (CollectionUtils.isEmpty(criteriaList)) {
                        criteria = IndicatorCriteria.builder().type(j)
                                .indicatorId(indicator.getId()).taskId(entry.getKey()).build();
                    } else {
                        for (IndicatorCriteria indicatorCriteria : criteriaList) {
                            if (indicatorCriteria.getType() == j) {
                                criteria = indicatorCriteria;
                                break;
                            }
                        }
                    }
                    arrayList.add(criteria);
                }
                for (FixedColumnVo fixedColumnVo : childrenTaskFixedColumn) {
                    if (org.apache.commons.lang3.StringUtils.equals(fixedColumnVo.getLabel(), entry.getValue().getName())) {
                        map.put(fixedColumnVo.getProp(), arrayList);
                    }
                }
                c++;
            }
            //遍历填充指标数据
            IndicatorEntity indicator1 = indicator;
            if (StringUtils.isNotEmpty(MergeTablesFiled.GARTER.getFiled()) && !Objects.isNull(indicator.getLevel())) {
                map.put(MergeTablesFiled.GARTER.getFiled() + (indicator.getLevel() + 1), indicator.getName());
            }
            int f = 0;
            while (indicator1 != null) {
                Optional<IndicatorEntity> indicatorEntity = indicatorService.findById(indicator1.getPid());
                if (indicatorEntity.isPresent()) {
                    map.put(MergeTablesFiled.GARTER.getFiled() + (indicator.getLevel() - f), indicatorEntity.get().getName());
                    indicator1 = indicatorEntity.get();
                } else {
                    break;
                }
                f++;
            }
            listData.add(map);
        }
        tablesVo.setListData(listData);
        return tablesVo;
    }

    @Override
    public MergeTablesVo getPotencyDataBySceneid(String sceneId) {
        MergeTablesVo tablesVo = new MergeTablesVo();
        List<FixedColumnVo> fixedColumn = new ArrayList<>();
        CompeteSceneEntity competeScene = competeSceneService.selectById(sceneId);
        //根据场景获取所有的任务
        if (null == competeScene || CollectionUtils.isEmpty(competeScene.getTaskIdLists())) return null;
        Map<String, MissionTaskEntity> missionTaskEntities = missionTaskService.selectByIdIn(competeScene.getTaskIdLists());
        //根据方案id获取能力指标树
        IndicatorTreeEntity indicatorTreeType = indicatorTreeService.findBySidAndIndicatorTreeType(competeScene.getSid(), 2);
        //根据指标树id获取所有的指标
        if (indicatorTreeType == null) return null;
        List<IndicatorEntity> indicatorEntities = indicatorService.findByTreeId(indicatorTreeType.getId());
        //最顶层指标
        Optional<IndicatorEntity> max = indicatorEntities.stream().max(Comparator.comparing(IndicatorEntity::getLevel));
        Optional<IndicatorEntity> first = indicatorEntities.stream().filter(indicatorEntitie -> null == indicatorEntitie.getLevel() || indicatorEntitie.getLevel() == 0).findFirst();
        //根据指标层数构建表格头
        for (int i = 1; i <= 2; i++) {
            FixedColumnVo columnVo = FixedColumnVo.builder().isMerge(YesOrNo.YES.getCode()).label(i + MergeTablesFiled.GARTER.getDesc()).prop(MergeTablesFiled.GARTER.getFiled() + i).build();
            fixedColumn.add(columnVo);
        }
        //根据zz任务构建表头
        FixedColumnVo columnVo = FixedColumnVo.builder().isMerge(YesOrNo.NO.getCode()).label(MergeTablesFiled.MISSION_TASK.getDesc()).prop(MergeTablesFiled.FIGTH_TASK.getFiled()).build();
        List<FixedColumnVo> childrenTaskFixedColumn = new ArrayList<>();
        int i = 1;
        for (String key : missionTaskEntities.keySet()) {
            MissionTaskEntity missionTaskEntity = missionTaskEntities.get(key);
            FixedColumnVo fixedColumnVo = FixedColumnVo.builder().isMerge(YesOrNo.NO.getCode()).label(missionTaskEntity.getName()).prop(MergeTablesFiled.FIGTH_TASK.getFiled() + i).build();
            childrenTaskFixedColumn.add(fixedColumnVo);
            i++;
        }
        columnVo.setPropsChildren(childrenTaskFixedColumn);
        fixedColumn.add(columnVo);
        tablesVo.setFixedColumn(fixedColumn);
        //构建数据
        List<Map<String, Object>> listData = new ArrayList<>();
        //获取最下级指标树数据
        List<IndicatorEntity> lowestIndicator = indicatorEntities.stream().filter(indicatorEntitie -> indicatorEntitie.getLevel().equals(max.get().getLevel())).collect(Collectors.toList());
        for (IndicatorEntity indicator : lowestIndicator) {
            String pid = indicator.getPid();
            Map<String, Object> map = new HashMap<>();
            //遍历填充任务数据
            int c = 1;
            for (Map.Entry<String, MissionTaskEntity> entry : missionTaskEntities.entrySet()) {
                //构建4条数据
                ArrayList<IndicatorCriteria> arrayList = new ArrayList<>();
                for (int j = 1; j < 5; j++) {
//                    List<IndicatorCriteria> criteriaList = indicatorCriteriaRepsitory
//                    .findBySceneIdAndTaskIdAndIndicatorIdAndTypeOrderByTypeAsc(sceneId, entry.getKey(), indicator.getId(), j);
                    List<IndicatorCriteria> criteriaList = this.list(new QueryWrapper<IndicatorCriteria>().eq("SCENE_ID", sceneId).eq("TASK_ID", entry.getKey())
                            .eq("INDICATOR_ID", indicator.getId()).eq("TYPE", j).orderByAsc("TYPE"));
                    IndicatorCriteria criteria = null;
                    if (CollectionUtils.isEmpty(criteriaList)) {
                        IndicatorCriteria indicatorCriteria = IndicatorCriteria.builder().type(j).indicatorId(indicator.getId()).taskId(entry.getKey()).sceneId(sceneId).build();
                        criteria = indicatorCriteria;
                    } else {
                        criteria = criteriaList.get(criteriaList.size() - 1);
                    }
                    criteria.setType(j).setIndicatorId(indicator.getId()).setTaskId(entry.getKey()).setSceneId(sceneId);
                    arrayList.add(criteria);
                }
                for (FixedColumnVo fixedColumnVo : childrenTaskFixedColumn) {
                    if (org.apache.commons.lang3.StringUtils.equals(fixedColumnVo.getLabel(), entry.getValue().getName())) {
                        map.put(fixedColumnVo.getProp(), arrayList);
                    }
                }
                //map.put(MergeTablesFiled.FIGTH_TASK.getFiled()+c,arrayList);
                c++;
            }
            //遍历填充指标数据
            map.put(MergeTablesFiled.GARTER.getFiled() + 2, indicator.getName());
            map.put(MergeTablesFiled.GARTER.getFiled() + 1, first.get().getName());
            listData.add(map);
        }
        tablesVo.setListData(listData);
        return tablesVo;
    }

    @Override
    public MergeTablesVo getPotencyDataBySceneidV2(String schemaId) {
        return buildingIndicatorData(schemaId, 2);
    }

    /**
     * @param schemaId 场景Id
     * @return 返回性能指标数据
     */
    @Override
    public MergeTablesVo getPerformanceDataBySceneidV2(String schemaId) {
        return buildingIndicatorData(schemaId, 5);
    }

    /**
     * 构建指标数据
     *
     * @param schemaId      方案id
     * @param indicatorType 指标类型
     * @return
     */
    private MergeTablesVo buildingIndicatorData(String schemaId, Integer indicatorType) {
        MergeTablesVo tablesVo = new MergeTablesVo();
        List<FixedColumnVo> fixedColumn = new ArrayList<>();
        Map<String, MissionTaskEntity> missionTaskEntities = new HashMap<>();
        List<MissionTaskEntity> list = missionTaskService.list(new QueryWrapper<MissionTaskEntity>().eq("SID", schemaId));
        for (MissionTaskEntity missionTaskEntity : list) {
            missionTaskEntities.put(missionTaskEntity.getId(), missionTaskEntity);
        }
        //根据方案id获取性能指标树
        IndicatorTreeEntity indicatorTreeType = indicatorTreeService.findBySidAndIndicatorTreeType(schemaId, indicatorType);
        //根据指标树id获取所有的指标
        if (indicatorTreeType == null) return null;
        List<IndicatorEntity> indicatorEntities = indicatorService.findByTreeId(indicatorTreeType.getId());
        //最顶层指标
        Optional<IndicatorEntity> max = indicatorEntities.stream().max(Comparator.comparing(IndicatorEntity::getLevel));
        Optional<IndicatorEntity> first = indicatorEntities.stream().filter(indicatorEntitie -> null == indicatorEntitie.getLevel() || indicatorEntitie.getLevel() == 0).findFirst();
        //根据指标层数构建表格头
        for (int i = 1; i <= max.get().getLevel() + 1; i++) {
            FixedColumnVo columnVo = FixedColumnVo.builder().isMerge(YesOrNo.YES.getCode()).label(i + MergeTablesFiled.GARTER.getDesc()).prop(MergeTablesFiled.GARTER.getFiled() + i).build();
            fixedColumn.add(columnVo);
        }
        //根据zz任务构建表头
        FixedColumnVo columnVo = FixedColumnVo.builder().isMerge(YesOrNo.NO.getCode()).label(MergeTablesFiled.MISSION_TASK.getDesc()).prop(MergeTablesFiled.FIGTH_TASK.getFiled()).build();
        List<FixedColumnVo> childrenTaskFixedColumn = new ArrayList<>();
        int i = 1;
        List<String> taskIds = Lists.newArrayList();
        for (String key : missionTaskEntities.keySet()) {
            MissionTaskEntity missionTaskEntity = missionTaskEntities.get(key);
            taskIds.add(missionTaskEntity.getId());
            FixedColumnVo fixedColumnVo = FixedColumnVo.builder().isMerge(YesOrNo.NO.getCode()).label(missionTaskEntity.getName()).prop(MergeTablesFiled.FIGTH_TASK.getFiled() + i).build();
            childrenTaskFixedColumn.add(fixedColumnVo);
            i++;
        }
        columnVo.setPropsChildren(childrenTaskFixedColumn);
        fixedColumn.add(columnVo);
        tablesVo.setFixedColumn(fixedColumn);
        //构建数据
        List<Map<String, Object>> listData = new ArrayList<>();
        //获取最下级指标树数据  调整，当两边不对称时，层级小的一边会获取不到，调整为 指标ID没有被当作Pid的就是所有分支的最后一级
        List<String> pidList = indicatorEntities.stream().map(IndicatorEntity::getPid).collect(Collectors.toList());
        List<IndicatorEntity> lowestIndicator = indicatorEntities.stream().filter(indicatorEntity -> !pidList.contains(indicatorEntity.getId())).collect(Collectors.toList());
        // List<IndicatorEntity> lowestIndicator = indicatorEntities.stream().filter(indicatorEntitie -> indicatorEntitie.getLevel() == max.get().getLevel()).collect(Collectors.toList());
        if (CollectionUtil.isEmpty(taskIds)) {
            return null;
        }
        List<IndicatorCriteria> indicatorCriteriaList = list(new LambdaQueryWrapper<IndicatorCriteria>().in(IndicatorCriteria::getTaskId, taskIds));
        Map<String, List<IndicatorCriteria>> indicatorCriteriaMap = indicatorCriteriaList.stream().collect(Collectors.groupingBy(s -> s.getTaskId() + s.getIndicatorId()));
        for (IndicatorEntity indicator : lowestIndicator) {
            Map<String, Object> map = new HashMap<>();
            //遍历填充任务数据
            int c = 1;
            for (Map.Entry<String, MissionTaskEntity> entry : missionTaskEntities.entrySet()) {
                //构建4条数据
                ArrayList<IndicatorCriteria> arrayList = new ArrayList<>();
                String taskId = entry.getKey();
                String indicatorId = indicator.getId();
                for (int j = 1; j < 5; j++) {
                    List<IndicatorCriteria> criteriaList = indicatorCriteriaMap.get(taskId + indicatorId);
                    IndicatorCriteria criteria = null;
                    if (CollectionUtils.isEmpty(criteriaList)) {
                        criteria = IndicatorCriteria.builder().type(j)
                                .indicatorId(indicator.getId()).taskId(entry.getKey()).build();
                    } else {
                        for (IndicatorCriteria indicatorCriteria : criteriaList) {
                            if (indicatorCriteria.getType() == j) {
                                criteria = indicatorCriteria;
                                break;
                            }
                        }
                    }
                    arrayList.add(criteria);
                }
                for (FixedColumnVo fixedColumnVo : childrenTaskFixedColumn) {
                    if (org.apache.commons.lang3.StringUtils.equals(fixedColumnVo.getLabel(), entry.getValue().getName())) {
                        map.put(fixedColumnVo.getProp(), arrayList);
                    }
                }
                c++;
            }
            //遍历填充指标数据
            IndicatorEntity indicator1 = indicator;
            if (StringUtils.isNotEmpty(MergeTablesFiled.GARTER.getFiled()) && !Objects.isNull(indicator.getLevel())) {
                map.put(MergeTablesFiled.GARTER.getFiled() + (indicator.getLevel() + 1), indicator.getName());
            }
            int f = 0;
            while (indicator1 != null) {
                Optional<IndicatorEntity> indicatorEntity = indicatorService.findById(indicator1.getPid());
                if (indicatorEntity.isPresent()) {
                    map.put(MergeTablesFiled.GARTER.getFiled() + (indicator.getLevel() - f), indicatorEntity.get().getName());
                    indicator1 = indicatorEntity.get();
                } else {
                    break;
                }
                f++;
            }
            listData.add(map);
        }
        tablesVo.setListData(listData);
        return tablesVo;
    }

    @Override
    public List<IndicatorCriteria> findAllByIndicatorIds(List<String> indicatorList) {
//        return indicatorCriteriaRepsitory.findByIndicatorIdIn(indicatorList);
        return this.list(new QueryWrapper<IndicatorCriteria>().in(!indicatorList.isEmpty(), "INDICATOR_ID", indicatorList));
    }

    @Override
    public List<IndicatorCriteria> findAllBySceneIdAndMissionTaskId(String sceneId, String missionTaskId, String indicatorId) {
        return this.list(new QueryWrapper<IndicatorCriteria>().eq("TASK_ID", missionTaskId)
                .eq("INDICATOR_ID", indicatorId).orderByAsc("TYPE"));
    }
}
