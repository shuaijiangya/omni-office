package com.yw.module.datasource.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Sets;
import com.mongodb.client.MongoClient;
import com.yw.common.defines.constants.SysConstant;
import com.yw.common.defines.enums.DataBaseType;
import com.yw.framework.backMgr.dbTableGenerator.domain.bo.DbLinkBO;
import com.yw.framework.backMgr.dbTableGenerator.domain.bo.TableColsBO;
import com.yw.framework.backMgr.dbTableGenerator.domain.dto.TableColsDTO;
import com.yw.framework.backMgr.dbTableGenerator.service.impl.DataBaseHandler;
import com.yw.framework.backMgr.dict.service.DictService;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.framework.common.defines.exception.custom.DataNotFoundException;
import com.yw.gather.dbinfomgr.ISourceInfoService;
import com.yw.gather.dbinfomgr.entity.DataAnalysisEntity;
import com.yw.gather.gatherscheme.schemeMgr.entity.GatherLogsExpandEntity;
import com.yw.gather.gatherscheme.schemeMgr.entity.GatherRoundRecord;
import com.yw.gather.gatherscheme.schemeMgr.service.GatherLogsService;
import com.yw.gather.gatherscheme.schemeMgr.service.IGatherRoundRecordService;
import com.yw.module.datasource.domain.dto.CalcParamDBDTO;
import com.yw.module.datasource.domain.dto.TableColDTO;
import com.yw.module.datasource.domain.req.DbImportRequest;
import com.yw.module.datasource.service.DatasourceTaskService;
import com.yw.module.missionScene.domain.entity.CompeteSceneEntity;
import com.yw.module.missionScene.domain.entity.ContributionRateAlgorithmEntity;
import com.yw.module.missionScene.domain.entity.MissionTaskEntity;
import com.yw.module.missionScene.domain.entity.SceneScenarioEntity;
import com.yw.module.missionScene.service.CompeteSceneService;
import com.yw.module.missionScene.service.ContributionRateAlgorithmService;
import com.yw.module.missionScene.service.IMissionTaskService;
import com.yw.module.missionScene.service.SceneScenarioService;
import com.yw.module.plan.domain.entity.SceneEntity;
import com.yw.module.plan.domain.entity.SchemaEntity;
import com.yw.module.plan.mapper.ISceneTableMapper;
import com.yw.module.plan.service.ISceneService;
import com.yw.module.plan.service.ISchemaService;
import com.yw.source.common.enums.AnalysisType;
import com.yw.source.common.enums.DataParserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author admin
 * @since 2024/2/23
 */
@Service
@RequiredArgsConstructor
public class DatasourceTaskServiceImpl implements DatasourceTaskService {

    private final ISourceInfoService iSourceInfoService;
    private final DataBaseHandler dataBaseHandler;
    private final ISceneTableMapper sceneTableMapper;
    private final CompeteSceneService competeSceneService;
    private final SceneScenarioService sceneScenarioService;
    private final IMissionTaskService missionTaskService;
    private final ContributionRateAlgorithmService contributionRateAlgorithmService;
    private final ISceneService sceneService;
    private final IGatherRoundRecordService gatherRoundRecordService;
    private final DictService dictService;
    private final GatherLogsService gatherLogsService;
    private final ISchemaService iSchemaService;


    private static final String SCENESCENARIOID = "SCENESCENARIOID";
    private static final String EXPERIMENTALSAMPLES = "EXPERIMENTALSAMPLES";
    private static final String FZID = "FZID";

    /**
     * 使命菜单控制字典值
     */
    private static final String TREE_DICT_CODE = "dataTree";
    private static final String TREE_DICT_KEY = "tree";


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void dbImport(DbImportRequest request) {
        //捕获无字典值时场景
        try{
            //获取控制使命菜单的字典值
            String dictValue = dictService.getValue(TREE_DICT_CODE, TREE_DICT_KEY);
            //无使命任务场景时直接走另一个分支
            if(SysConstant.NO_SCENE_VALUE.equals(dictValue)){
                dbImportNoScene(request);
                return;
            }
        }catch (DataNotFoundException ignored){}

        DataAnalysisEntity dataAnalysisEntity = iSourceInfoService.findById(request.getAnalysisId());
        DbLinkBO remoteDbLinkBO = convertDbLink(dataAnalysisEntity);

        List<DbImportRequest.DbDataToCalcParam> dbDataToCalcParamList = request.getDbDataToCalcParamList();
        List<String> calcParamIdList = dbDataToCalcParamList.stream().map(DbImportRequest.DbDataToCalcParam::getCalcParamId).collect(Collectors.toList());
        List<CalcParamDBDTO> calcParamDbList = sceneTableMapper.listCalcParamDbById(calcParamIdList);
        String sid = calcParamDbList.get(0).getSid();
        String dSid = calcParamDbList.get(0).getDSid();

        // 指标计算参数id -> 参数数据库表字段信息
        Map<String, List<CalcParamDBDTO>> calcParamPerCalcParamId = calcParamDbList.stream().collect(Collectors.groupingBy(CalcParamDBDTO::getCalcParamId));

        List<CompeteSceneEntity> competeSceneList = competeSceneService.list(Wrappers.lambdaQuery(new CompeteSceneEntity()).eq(CompeteSceneEntity::getSid, sid).eq(CompeteSceneEntity::getTaskModel, "0"));
        CompeteSceneEntity competeSceneEntity = competeSceneList.get(0);

        MissionTaskEntity missionTask = missionTaskService.list(Wrappers.lambdaQuery(new MissionTaskEntity()).eq(MissionTaskEntity::getSid, sid)).get(0);

        // 关联仿真样本
        List<SceneScenarioEntity> sceneScenarioEntityList = sceneScenarioService.list(Wrappers.lambdaQuery(new SceneScenarioEntity())
                .eq(SceneScenarioEntity::getSid, sid));
        // 基础想定
        SceneScenarioEntity baseScenario = sceneScenarioEntityList.stream().filter(item -> competeSceneEntity.getId().equals(item.getCompeteSceneId()) && "0".equals(item.getPid())).findFirst().get();
        // 子想定
        SceneScenarioEntity childScenario = sceneScenarioEntityList.stream().filter(item -> item.getPid().equals(
                sceneScenarioEntityList.stream().filter(item2 -> item2.getPid().equals(baseScenario.getId())).findFirst().get().getId()
        )).findFirst().get();
        String scenarioName = baseScenario.getScenarioName();
        String childScenarioName = childScenario.getScenarioName();

        SceneEntity sceneEntity = new SceneEntity();
        sceneEntity.setId(IdUtil.fastSimpleUUID());
        sceneEntity.setName(String.format("%s/%s/%s", "一级轮次-" + scenarioName, "2级轮次-" + childScenarioName, "3级轮次-" + sceneEntity.getId()));
        sceneEntity.setSid(sid);
        sceneEntity.setSceneType(0);
        Map<String, String> sampleTaskId = new HashMap<>();
        sampleTaskId.put(dSid, String.format("%s/%s/%s", scenarioName, sceneEntity.getId(), childScenarioName));
        sceneEntity.setSampleTaskId(JSON.toJSONString(sampleTaskId));
        sceneService.save(sceneEntity);

        ContributionRateAlgorithmEntity contributionRateAlgorithmEntity = new ContributionRateAlgorithmEntity();
        contributionRateAlgorithmEntity.setId(IdUtil.fastSimpleUUID());
        contributionRateAlgorithmEntity.setMissionTaskId(missionTask.getId());
        contributionRateAlgorithmEntity.setCompeteSceneId(competeSceneEntity.getId());
        contributionRateAlgorithmEntity.setSceneScenarioId(childScenario.getId());
        contributionRateAlgorithmEntity.setSceneId(sceneEntity.getId());
        contributionRateAlgorithmEntity.setSceneOwner(0);
        contributionRateAlgorithmEntity.setOverFlag(0);
        contributionRateAlgorithmService.save(contributionRateAlgorithmEntity);

        GatherRoundRecord gatherRoundRecord = new GatherRoundRecord();
        gatherRoundRecord.setId(IdUtil.fastSimpleUUID());
        gatherRoundRecord.setName(String.format("%s/%s/%s", "一级轮次-" + scenarioName, "2级轮次-" + sceneEntity.getId(), "3级轮次-" + childScenarioName));
        gatherRoundRecord.setTaskId(String.format("%s/%s/%s", scenarioName, sceneEntity.getId(), childScenarioName));
        gatherRoundRecord.setDataSrcId(dSid);
        gatherRoundRecord.setRoundField("SCENESCENARIOID/FZID/EXPERIMENTALSAMPLES");
        gatherRoundRecordService.save(gatherRoundRecord);

        //记录采集表的数据量
        int count = 0;
        for (DbImportRequest.DbDataToCalcParam dbDataToCalcParam : dbDataToCalcParamList) {
            String tableName = dbDataToCalcParam.getTableName();
            String columnName = dbDataToCalcParam.getColumnName();
            String calcParamId = dbDataToCalcParam.getCalcParamId();

            CalcParamDBDTO calcParamDb = calcParamPerCalcParamId.get(calcParamId).get(0);
            DbLinkBO localDbLink = DbLinkBO.valueOf(calcParamDb.getType(),
                    calcParamDb.getIp(),
                    calcParamDb.getPort(),
                    calcParamDb.getDbName(),
                    calcParamDb.getPass(),
                    calcParamDb.getUser());

//            List<String> cols = CollUtil.newArrayList(columnName, SCENESCENARIOID, EXPERIMENTALSAMPLES, FZID);
            List<String> cols = CollUtil.newArrayList(columnName);
            List<Map<String, Object>> dataList = null;
            // 获取解析的表数据
            if (dataAnalysisEntity.getDbType().equals(DataBaseType.MONGODB.getName())) {
                dataList = mongodbOperation(cols, dataAnalysisEntity, tableName);
            } else {
                dataList = dataBaseHandler.findDataList(cols, tableName, remoteDbLinkBO);
            }
            if (!CollectionUtils.isEmpty(dataList)) {
                List<Map<String, String>> collect = dataList.stream().map(itemMap -> {
                    Map<String, String> data = new HashMap<>();
                    itemMap.forEach((key, value) -> {
                        if (key.equals(columnName)) {
                            data.put(calcParamDb.getColumnName(), ObjectUtil.isNull(value) ? null : String.valueOf(value));
                        } else {
                            data.put(key, ObjectUtil.isNull(value) ? null : String.valueOf(value));
                        }
                    });
                    data.put(SCENESCENARIOID, scenarioName);
                    data.put(EXPERIMENTALSAMPLES, childScenarioName);
                    data.put(FZID, sceneEntity.getId());
                    return data;
                }).collect(Collectors.toList());
                // 修改表结构
                editTableCol(localDbLink, calcParamDb.getTableName(),null,null);
                // 给计算参数绑定的表中插入数据
                dataBaseHandler.editTableData(calcParamDb.getTableName(), "ID",collect, localDbLink);
                count = count + collect.size();
            }
        }
        //记录采集日志
        String tableNames = dbDataToCalcParamList.stream().map(DbImportRequest.DbDataToCalcParam::getTableName).distinct().collect(Collectors.joining(","));
        SchemaEntity schema = iSchemaService.getById(sid);
        gatherLogsService.saveLogNoTask(schema.getName(),(double) count, DataParserType.DB_IMPORT, GatherLogsExpandEntity.builder().tableName(tableNames).build());
    }

    public void dbImportNoScene(DbImportRequest request){
        DataAnalysisEntity dataAnalysisEntity = iSourceInfoService.findById(request.getAnalysisId());
        DbLinkBO remoteDbLinkBO = convertDbLink(dataAnalysisEntity);

        List<DbImportRequest.DbDataToCalcParam> dbDataToCalcParamList = request.getDbDataToCalcParamList();
        List<String> calcParamIdList = dbDataToCalcParamList.stream().map(DbImportRequest.DbDataToCalcParam::getCalcParamId).collect(Collectors.toList());
        List<CalcParamDBDTO> calcParamDbList = sceneTableMapper.listCalcParamDbById(calcParamIdList);
        String sid = calcParamDbList.get(0).getSid();
        String dSid = calcParamDbList.get(0).getDSid();

        // 指标计算参数id -> 参数数据库表字段信息
        Map<String, List<CalcParamDBDTO>> calcParamPerCalcParamId = calcParamDbList.stream().collect(Collectors.groupingBy(CalcParamDBDTO::getCalcParamId));
        List<String> keys = dbDataToCalcParamList.stream().map(t -> t.getColumnName()).collect(Collectors.toList());
        String roundRecordKey = request.getRoundRecordKey();
        String[] split = roundRecordKey.split("/");
        //无使命任务场景
        for (String s : split) {
            if (keys.contains(s)) {
                throw new BusinessException("轮次标识字段不能做为指标参数数据");
            }
            if("ID".equals(s)){
                throw new BusinessException("ID列不能作为轮次标识");
            }
        }

        //记录采集表的数据量
        int count = 0;
        Set<String> diff = Sets.newHashSet();
        for (DbImportRequest.DbDataToCalcParam dbDataToCalcParam : dbDataToCalcParamList) {
            String tableName = dbDataToCalcParam.getTableName();
            String columnName = dbDataToCalcParam.getColumnName();
            String calcParamId = dbDataToCalcParam.getCalcParamId();

            CalcParamDBDTO calcParamDb = calcParamPerCalcParamId.get(calcParamId).get(0);
            DbLinkBO localDbLink = DbLinkBO.valueOf(calcParamDb.getType(),
                    calcParamDb.getIp(),
                    calcParamDb.getPort(),
                    calcParamDb.getDbName(),
                    calcParamDb.getPass(),
                    calcParamDb.getUser());

            List<String> cols = CollUtil.newArrayList(columnName);
            cols.addAll(Arrays.asList(split));
            // 获取解析的表数据
            List<Map<String, Object>> dataList = null;
            try {
                dataList = dataBaseHandler.findDataList(cols, tableName, remoteDbLinkBO);
            } catch (Exception e) {
                e.printStackTrace();
                throw new BusinessException("导入表中没有轮次唯一标识字段");
            }
            if (!CollectionUtils.isEmpty(dataList)) {
                List<Map<String, Object>> collect = dataList.stream().map(itemMap -> {
                    Map<String, Object> data = new HashMap<>();
                    itemMap.forEach((key, value) -> {
                        if (key.equals(columnName)) {
                            data.put(calcParamDb.getColumnName(), value);
                        } else {
                            data.put(key, value);
                        }
                    });
                    // 处理伦次  非体系贡献率的不用考虑场景任务想定挂载
                    StringBuilder sceneNameBuffer = new StringBuilder();
                    StringBuilder taskIdsBuffer = new StringBuilder();
                    for (int i = 0; i < split.length; i++) {
                        sceneNameBuffer.append(i + 1).append("级轮次-").append(data.get(split[i]).toString()).append("/");
                        taskIdsBuffer.append(data.get(split[i]).toString()).append("/");
                    }
                    String sceneName = sceneNameBuffer.substring(0,sceneNameBuffer.length()-1);
                    String taskIds =  taskIdsBuffer.substring(0,taskIdsBuffer.length()-1);
                    saveSceneAndGruessData(sid, dSid, diff, null, null, sceneName, taskIds,roundRecordKey);
                    return data;
                }).collect(Collectors.toList());
                // 修改表结构
                editTableCol(localDbLink, calcParamDb.getTableName(),split,"1");
                // 给计算参数绑定的表中插入数据
                List<Map<String, String>> upMapList = collect.stream().map(s -> {
                    Map<String, String> upCaseData = new HashMap<>();
                    for (Map.Entry<String, Object> entry : s.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        String newKey = key.toUpperCase();
                        upCaseData.put(newKey, ObjectUtil.isNull(value) ? null : String.valueOf(value));
                    }
                    return upCaseData;
                }).collect(Collectors.toList());
                dataBaseHandler.editTableData(calcParamDb.getTableName(), "ID", upMapList, localDbLink);
                count = count + upMapList.size();
            }
        }
        //记录采集日志
        String tableNames = dbDataToCalcParamList.stream().map(DbImportRequest.DbDataToCalcParam::getTableName).distinct().collect(Collectors.joining(","));
        SchemaEntity schema = iSchemaService.getById(sid);
        gatherLogsService.saveLogNoTask(schema.getName(),(double) count, DataParserType.DB_IMPORT, GatherLogsExpandEntity.builder().tableName(tableNames).build());
    }

    public List<Map<String, Object>> mongodbOperation(List<String> cols, DataAnalysisEntity dataAnalysisEntity, String tableName) {
        //获取到mongoDb连接客户端
        MongoClient mongoClient = MongoDbOperationHandle.getMongoClient(dataAnalysisEntity.getHost(),
                Integer.parseInt(dataAnalysisEntity.getPort()), dataAnalysisEntity.getUsername(),
                dataAnalysisEntity.getDbName(), dataAnalysisEntity.getPassword());
        //获取数据
        return MongoDbOperationHandle.findDataList(cols, tableName, mongoClient, dataAnalysisEntity);
    }

    /**
     * 构建样本并且关联体系贡献率的想定场景任务
     *
     * @param sid
     * @param dSid
     * @param diff
     * @param sceneScenarioEntity
     * @param missionTaskEntity
     * @param sceneName
     * @param taskIds
     */
    private void saveSceneAndGruessData(String sid, String dSid, Set<String> diff, SceneScenarioEntity sceneScenarioEntity, MissionTaskEntity missionTaskEntity, String sceneName, String taskIds, String roundField) {
        SceneEntity sceneEntity = new SceneEntity();
        sceneEntity.setId(IdUtil.fastSimpleUUID());
        sceneEntity.setName(sceneName);
        sceneEntity.setSid(sid);
        sceneEntity.setSceneType(0);
        Map<String, String> sampleTaskId = new HashMap<>();
        sampleTaskId.put(dSid, taskIds);
        sceneEntity.setSampleTaskId(JSON.toJSONString(sampleTaskId));
        List<SceneEntity> list = sceneService.list(new LambdaQueryWrapper<SceneEntity>().eq(SceneEntity::getSid, sid)
                .eq(SceneEntity::getName, sceneName).eq(SceneEntity::getSampleTaskId, JSON.toJSONString(sampleTaskId)));
        if (list != null && list.size() != 0) {
            return;
        }
        sceneService.save(sceneEntity);
        if (sceneScenarioEntity != null) {
            ContributionRateAlgorithmEntity contributionRateAlgorithmEntity = new ContributionRateAlgorithmEntity();
            contributionRateAlgorithmEntity.setId(IdUtil.fastSimpleUUID());
            contributionRateAlgorithmEntity.setMissionTaskId(missionTaskEntity.getId());
            contributionRateAlgorithmEntity.setCompeteSceneId(sceneScenarioEntity.getCompeteSceneId());
            contributionRateAlgorithmEntity.setSceneScenarioId(sceneScenarioEntity.getId());
            contributionRateAlgorithmEntity.setSceneId(sceneEntity.getId());
            contributionRateAlgorithmEntity.setSceneOwner(0);
            contributionRateAlgorithmEntity.setOverFlag(0);
            contributionRateAlgorithmService.save(contributionRateAlgorithmEntity);
        }

        GatherRoundRecord gatherRoundRecord = new GatherRoundRecord();
        gatherRoundRecord.setId(IdUtil.fastSimpleUUID());
        gatherRoundRecord.setName(sceneName);
        gatherRoundRecord.setTaskId(taskIds);
        gatherRoundRecord.setDataSrcId(dSid);
        gatherRoundRecord.setRoundField(roundField);
        gatherRoundRecordService.save(gatherRoundRecord);
        diff.add(taskIds);
    }

    private void editTableCol(DbLinkBO localDbLink, String tableName,String[] split,String value) {
        List<TableColsDTO> tableCols = dataBaseHandler.findTableCols(tableName, localDbLink);
        List<String> tableColCodeList = tableCols.stream().map(TableColsDTO::getColCode).collect(Collectors.toList());
        List<TableColsBO> editColsList = new ArrayList<>();
        //无使命任务场景时直接用数据唯一标识构建表字段
        if("1".equals(value)){
            for (String s : split) {
                if (!tableColCodeList.contains(s)) {
                    TableColsBO tableColsBO = new TableColsBO();
                    tableColsBO.setColCode(s);
                    tableColsBO.setColLen(255);
                    tableColsBO.setColType("VARCHAR");
                    tableColsBO.setEditType(1);
                    tableColsBO.setNullAble(1);
                    editColsList.add(tableColsBO);
                }
            }
        }else{
            if (!tableColCodeList.contains(SCENESCENARIOID)) {
                TableColsBO tableColsBO = new TableColsBO();
                tableColsBO.setColCode(SCENESCENARIOID);
                tableColsBO.setColLen(255);
                tableColsBO.setColType("VARCHAR");
                tableColsBO.setEditType(1);
                tableColsBO.setNullAble(1);
                editColsList.add(tableColsBO);
            }
            if (!tableColCodeList.contains(EXPERIMENTALSAMPLES)) {
                TableColsBO tableColsBO = new TableColsBO();
                tableColsBO.setColCode(EXPERIMENTALSAMPLES);
                tableColsBO.setColLen(255);
                tableColsBO.setColType("VARCHAR");
                tableColsBO.setEditType(1);
                tableColsBO.setNullAble(1);
                editColsList.add(tableColsBO);
            }
            if (!tableColCodeList.contains(FZID)) {
                TableColsBO tableColsBO = new TableColsBO();
                tableColsBO.setColCode(FZID);
                tableColsBO.setColLen(255);
                tableColsBO.setColType("VARCHAR");
                tableColsBO.setEditType(1);
                tableColsBO.setNullAble(1);
                editColsList.add(tableColsBO);
            }
        }
        if (!CollectionUtils.isEmpty(editColsList)) {
            dataBaseHandler.editTableCols(tableName, editColsList, localDbLink);
        }
    }

    @Override
    public List<TableColDTO> listAnalysisTableColumn(String analysisId, String tableNames) {
        List<TableColDTO> tableColsDTOList = new ArrayList<>();
        DataAnalysisEntity dataAnalysisEntity = iSourceInfoService.findById(analysisId);
        DbLinkBO dbLinkBO = convertDbLink(dataAnalysisEntity);

        if (dataAnalysisEntity.getDbType().equals(DataBaseType.MONGODB.getName())) {
            for (String mongoTableName : tableNames.split(",")) {
                List<TableColDTO> tableCols = MongoDbOperationHandle.findTableCols(mongoTableName, dataAnalysisEntity.getHost(),
                        dataAnalysisEntity.getPort(), dataAnalysisEntity.getUsername(),
                        dataAnalysisEntity.getDbName(), dataAnalysisEntity.getPassword());
                tableColsDTOList.addAll(tableCols);
            }
            return tableColsDTOList;
        }

        for (String tableName : tableNames.split(",")) {
            List<TableColsDTO> tableCols = dataBaseHandler.findTableCols(tableName, dbLinkBO);
            List<TableColDTO> collect = tableCols.stream().map(item -> new TableColDTO(tableName, item.getColCode())).collect(Collectors.toList());
            tableColsDTOList.addAll(collect);
        }
        return tableColsDTOList;
    }

    public static DbLinkBO convertDbLink(DataAnalysisEntity dataAnalysisEntity) {
        return DbLinkBO.valueOf(dataAnalysisEntity.getDbType(),
                dataAnalysisEntity.getHost(),
                dataAnalysisEntity.getPort(),
                dataAnalysisEntity.getDbName(),
                dataAnalysisEntity.getPassword(),
                dataAnalysisEntity.getUsername());
    }
}
