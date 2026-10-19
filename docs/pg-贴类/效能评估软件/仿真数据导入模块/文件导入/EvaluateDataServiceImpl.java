package com.yw.module.datasource.service.impl;


import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.yw.framework.backMgr.dbTableGenerator.service.impl.DataBaseHandler;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.framework.common.util.UUIDGenerator;
import com.yw.gather.dbinfomgr.IDatasrcService;
import com.yw.gather.dbinfomgr.entity.DatasrcEntity;
import com.yw.gather.gatherscheme.schemeMgr.entity.GatherRoundRecord;
import com.yw.gather.gatherscheme.schemeMgr.service.IGatherRoundRecordService;
import com.yw.gather.gathertask.core.event.ApplicationGatherTaskCompleteEvent;
import com.yw.gather.gathertask.defines.CopyDataFromFileToDB;
import com.yw.gather.gathertask.defines.DataSourceExeclParam;
import com.yw.module.datasource.domain.dto.ManualInputDto;
import com.yw.module.datasource.domain.dto.ManualInputIndicatorDto;
import com.yw.module.datasource.service.EvaluateDataService;
import com.yw.module.plan.domain.entity.SceneTableEntity;
import com.yw.module.plan.domain.entity.SchemaEntity;
import com.yw.module.plan.service.ISceneTableService;
import com.yw.module.plan.service.ISchemaService;
import com.yw.source.importDataDirectory.entity.DataDirectory;
import com.yw.source.importDataDirectory.service.IDataDirectoryService;
import com.yw.sys.api.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.util.*;
/**
 * @BelongProject pg-general
 * @BelongPackage com.yw.module.datasource.service.impl
 * @Author HaiBo
 * @date 2024-09-2024/9/5
 * @description 评估数据模块Service实现
 */
@Service
@Slf4j
public class EvaluateDataServiceImpl implements EvaluateDataService, ApplicationContextAware {


    /**
     * 样本结构
     */
    @Autowired
    private ISceneTableService iSceneTableService;

    /**
     * 轮次标识
     */
    @Autowired
    private IGatherRoundRecordService gatherRoundRecordService;

    /**
     * 数据写入
     */
    @Autowired
    private CopyDataFromFileToDB copyDataFromFileToDB;

    /**
     * 数据库操作
     */
    @Autowired
    private DataBaseHandler dataBaseHandler;

    /**
     * 数据源
     */
    @Autowired
    private IDatasrcService datasrcService;


    /**
     * 数据目录
     */
    @Autowired
    private IDataDirectoryService iDataDirectoryService;

    /**
     * 方案
     */
    @Autowired
    private ISchemaService iSchemaService;



    private ApplicationContext applicationContext;

    /**
     * 多个指标同时手动录入数据，需要解决以下问题：
     * 1、样本标识 保证一个方案是只能有一个样本标识
     * 1.1：体系贡献率方案让用户选想定
     * 1.2：非贡献率方案让用户手动输入样本标识
     * 2、保证是同一个样本
     * 3、分批写入相应表
     * 4、如何保证一致性
     * 4.1 异常情况需要将数据都回滚
     *
     * @param manualInputDto 手动录入的数据
     * @return 成果的状态
     */
    @Override
    public boolean manualInput(ManualInputDto manualInputDto) {
        String dataSrcId = obtainDataSrcId(manualInputDto.getSchemaId());
        String roundKey = obtainRoundKey(dataSrcId);
        if (StringUtils.isNotEmpty(roundKey) && !manualInputDto.getRoundKey().equals(roundKey)) {
            throw new BusinessException("不同的轮次标识无法构建样本数据");
        }
        Map<String, Object> roundValue = Maps.newHashMap();
        String roundKeyValue = manualInputDto.getRoundKeyValue();
        if (manualInputDto.getRoundKey().contains("/")) {
            String[] values = roundKeyValue.split("/");
            String[] keys = manualInputDto.getRoundKey().split("/");
            if(values.length != keys.length){
                throw new BusinessException("样本名称不能为空");
            }
            for (int i = 0; i < keys.length; i++) {
                roundValue.put(keys[i], values[i]);
            }
        } else {
            roundValue.put(manualInputDto.getRoundKey(), roundKeyValue);
        }
        Map<String, List<String>> insertIdMaps = Maps.newHashMap();
        Map<String, SceneTableEntity> undoMap = Maps.newHashMap();
        Set<String> saveDbNames = Sets.newHashSet();
        for (ManualInputIndicatorDto indicatorValue : manualInputDto.getIndicatorValues()) {
            String indicatorId = indicatorValue.getIndicatorId();
            SceneTableEntity sceneTableEntity = iSceneTableService.getOne(new LambdaQueryWrapper<SceneTableEntity>().eq(SceneTableEntity::getIndicatorId, indicatorId));
            if (Objects.isNull(sceneTableEntity)) {
                continue;
            }
            insertIdMaps.put(indicatorId, Lists.newArrayList());
            undoMap.put(indicatorId, sceneTableEntity);
            List<Pair<String, List<String>>> columnValues = indicatorValue.getColumnValues();
            List<Map<String, Object>> rowsData = Lists.newArrayList();
            for (Pair<String, List<String>> columnValue : columnValues) {
                String column = columnValue.getKey();
                List<String> value = columnValue.getValue();
                for (int i = 0; i < value.size(); i++) {
                    Map<String, Object> row = null;
                    try {
                        row = rowsData.get(i);
                    } catch (Exception e) {
                        row = new HashMap<>(roundValue);
                        rowsData.add(row);
                    }
                    row.put(column, value.get(i));
                }
            }
            //如果密钥ID,则增加ID字段给予赋值
            rowsData.forEach(item -> {
                String id = UUIDGenerator.getUUID();
                if (item.containsKey("ID") && org.springframework.util.StringUtils.isEmpty(item.get("ID"))) {
                    item.put("ID", id);
                } else if (!item.containsKey("ID")) {
                    item.put("ID", id);
                }
                insertIdMaps.get(indicatorId).add(item.get("ID").toString());
            });
            DataSourceExeclParam data = new DataSourceExeclParam();
            data.setData(rowsData);
            data.setDataSrcId(dataSrcId);
            data.setRoundKeys(manualInputDto.getRoundKey());
            data.setDataBasesName(sceneTableEntity.getDbName());
            data.setTableName(sceneTableEntity.getTableName());
            saveDbNames.add(data.getTableName());
            try {
                copyDataFromFileToDB.copyDataFromFile(data);
            } catch (Exception e) {
                // undo 回滚
                DatasrcEntity dataSrc = datasrcService.getById(dataSrcId);
                for (Map.Entry<String, List<String>> stringListEntry : insertIdMaps.entrySet()) {
                    String oldIndicatorId = stringListEntry.getKey();
                    if (oldIndicatorId.equals(indicatorId)) {
                        continue;
                    }
                    SceneTableEntity tableEntity = undoMap.get(oldIndicatorId);
                    List<Map<String, String>> idsMap = Lists.newArrayList();
                    for (String id : stringListEntry.getValue()) {
                        idsMap.add(new HashMap<String,String>() {{
                            put("ID", id);
                        }});
                    }
                    dataBaseHandler.deleteData(tableEntity.getTableName(), idsMap, dataSrc.createDblinkDto().createDbLinkBo());
                }
                //删除轮次数据
                List<GatherRoundRecord> gatherRoundRecords = gatherRoundRecordService.queryList(dataSrcId);
                if (CollUtil.isNotEmpty(gatherRoundRecords)) {
                    for (GatherRoundRecord gatherRoundRecord : gatherRoundRecords) {
                        if (gatherRoundRecord.getTaskId().equals(manualInputDto.getRoundKeyValue())) {
                            gatherRoundRecordService.removeById(gatherRoundRecord.getId());
                            break;
                        }
                    }
                }
                return false;

            }
        }
        try {
            applicationContext.publishEvent(new ApplicationGatherTaskCompleteEvent("", dataSrcId, saveDbNames, ""));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return true;
    }

    /**
     * 获取数据源id
     *
     * @param schemaId 方案id
     * @return 数据源id
     */
    public final String obtainDataSrcId(String schemaId) {
        SchemaEntity schemaEntity = iSchemaService.getById(schemaId);
        if (Objects.isNull(schemaEntity)) {
            return null;
        }
        String dataDirectoryId = schemaEntity.getDataDirectoryId();
        if (StringUtils.isEmpty(schemaEntity.getDataDirectoryId())) {
            // todo  构建一个新的数据源目录 暂时不处理
            return null;
        }
        DataDirectory dataDirectory = iDataDirectoryService.getById(dataDirectoryId);

        if (Objects.isNull(dataDirectory)) {
            return null;
        }
        String mark = dataDirectory.getMark();
        if (StringUtils.isEmpty(mark)) {
            //todo 创建一个新的数据源
            return null;
        }
        JSONObject jsonObject = JSON.parseObject(mark);
        return jsonObject.getString("dataSrcId");
    }

    /**
     * 获取轮次标识
     *
     * @param dataSrcId 数据源id
     * @return 轮次标识
     */
    @Override
    public final String obtainRoundKey(String dataSrcId) {
        if (dataSrcId == null) return "";

        // 确保是同一个轮次标识，如果是第一次直接操作数据库即可
        List<GatherRoundRecord> gatherRoundRecords = gatherRoundRecordService.queryList(dataSrcId);
        if (CollUtil.isEmpty(gatherRoundRecords)) {
            return "";
        }
        return gatherRoundRecords.get(0).getRoundField();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}