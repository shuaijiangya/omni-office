package com.yw.module.datasource.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yw.common.utils.StatisticalUtil;
import com.yw.framework.backMgr.dbTableGenerator.domain.bo.DbLinkBO;
import com.yw.framework.backMgr.dbTableGenerator.domain.dto.DbLinkDTO;
import com.yw.framework.backMgr.dbTableGenerator.domain.dto.ExerciseInfoDTO;
import com.yw.framework.backMgr.dbTableGenerator.service.impl.DataBaseHandler;
import com.yw.framework.backMgr.dict.service.DictService;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.framework.common.util.db.DMSqlStatementUtil;
import com.yw.framework.common.util.db.DbUtils;
import com.yw.gather.dbinfomgr.DataSrcManagerService;
import com.yw.gather.dbinfomgr.IDatasrcService;
import com.yw.gather.dbinfomgr.entity.DatasrcEntity;
import com.yw.module.datasource.domain.dto.MissingCheckDTO;
import com.yw.module.datasource.domain.dto.MissingFillDTO;
import com.yw.module.datasource.domain.dto.MissingFillModelDTO;
import com.yw.module.datasource.domain.enums.DataCleaningType;
import com.yw.module.datasource.domain.enums.MissItemCheckEnums;
import com.yw.module.datasource.domain.enums.MissingFillTypeEnum;
import com.yw.module.datasource.domain.vo.DataCleaningResultVO;
import com.yw.module.datasource.service.DataCleaningService;
import com.yw.module.plan.domain.dto.DataCleaningDTO;
import com.yw.source.dataManagement.entity.DataRecordEntity;
import com.yw.source.preprocessing.entity.CustomDataEntity;
import com.yw.source.preprocessing.service.CustomDataService;
import com.yw.source.preprocessing.service.DataRecordService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataCleaningServiceImpl implements DataCleaningService {


    @Autowired
    private CustomDataService customDataService;

    @Autowired
    private SimpleDataCleaningHandlerSupport simpleDataCleaningHandlerSupport;

    @Autowired
    private DataRecordService dataRecordService;

    @Autowired
    private DataSrcManagerService dataSrcManagerService;//dataSrcManagerService

    @Autowired
    private IDatasrcService datasrcService;

    @Autowired
    private DataBaseHandler dataBaseHandler;

    @Autowired
    private RestTemplate restTemplate;

//    @Value("${pg-general.module.python-file.executor}")
//    private String pythonServerPath;

    /**
     * 注入：字典服务
     */
    @Autowired
    private DictService dictService;

    /**
     * python服务地址
     */
    private static final String URL_CODE = "url";
    private static final String PYTHON_URL = "pythonUrl";

    /**
     * key：processor key. value： rowId ：Set<String> strs
     */
    private Map<String, Map<Integer, Set<String>>> redisMap = MissingItemCheckDataCleaningProcessor.redisMap;


    @Override
    public DataCleaningResultVO missingCheckData(MissingCheckDTO dto) {
        if (CollectionUtils.isEmpty(dto.getRoundFilterList()) && StringUtils.isEmpty(dto.getDataSrcId())) {
            // custom data
            CustomDataEntity customDataEntity = customDataService.getOne(new LambdaQueryWrapper<CustomDataEntity>().eq(CustomDataEntity::getCustomName, dto.getCustomId()));

            List<Map<String, Object>> listMap = getListMapFromEntity(dto.getCustomId());
            DataCleaningType dataCleaningType = DataCleaningType.getType(dto.getType());
            // 记录自定义数据
            customRecord(dto, customDataEntity, dataCleaningType);
            DataCleaningResultVO dataCleaningResultVO = simpleDataCleaningHandlerSupport.dataCleaning(listMap, dataCleaningType, dto.getCustomId() + "");
            List<Map<String, Object>> list = dataCleaningResultVO.getList();
            simpleDataCleaningHandlerSupport.putDataCleaningCache("key", JSON.toJSONString(list));
            return dataCleaningResultVO;
        }
        // obtain original data . dataSrcId table 确定表数据
        ExerciseInfoDTO exerciseInfoDTO = new ExerciseInfoDTO();
        exerciseInfoDTO.setDataSrcId(dto.getDataSrcId());
        exerciseInfoDTO.setDbName(dto.getTableName());
        exerciseInfoDTO.setRoundFilterList(dto.getRoundFilterList());
        //默认不分页
        exerciseInfoDTO.setOffSet(0);
        List<Map<String, Object>> obtainList = dataSrcManagerService.tableNameDefineField(exerciseInfoDTO).getList();
        return getDataCleaningResultVO(dto, obtainList);
    }

    @NotNull
    private DataCleaningResultVO getDataCleaningResultVO(MissingCheckDTO dto, List<Map<String, Object>> obtainList) {
        DataCleaningType dataCleaningType = DataCleaningType.getType(dto.getType());
        String name = dataCleaningType.name;
        if (name.equals(DataCleaningType.CLEANING_NORMALIZATION.name)) {
            for (Map<String, Object> stringObjectMap : obtainList) {
                stringObjectMap.remove("ID");
                stringObjectMap.remove("id");
                stringObjectMap.remove("TASK_ID");
                stringObjectMap.remove("task_id");
                List<ExerciseInfoDTO.RoundFilter> roundFilterList = dto.getRoundFilterList();
                if (CollectionUtils.isEmpty(roundFilterList)) {
                    continue;
                }
                for (ExerciseInfoDTO.RoundFilter roundFilter : roundFilterList) {
                    stringObjectMap.remove(roundFilter.getRoundField());
                }
            }
        }
        DataCleaningResultVO dataCleaningResultVO = simpleDataCleaningHandlerSupport.dataCleaning(obtainList, dataCleaningType,
                dto.getDataSrcId() + dto.getTableName() + dto.getVar1());


        List<Map<String, Object>> list = dataCleaningResultVO.getList();
        simpleDataCleaningHandlerSupport.putDataCleaningCache("key", JSON.toJSONString(list));
        return dataCleaningResultVO;
    }

    private void customRecord(MissingCheckDTO dto, CustomDataEntity customDataEntity, DataCleaningType dataCleaningType) {
        DataRecordEntity dataRecordEntity = new DataRecordEntity();
        dataRecordEntity.setUniId(dto.getCustomId() + "");
        dataRecordEntity.setOperation(dataCleaningType.name);
        dataRecordEntity.setTableName(customDataEntity.getCustomName());
        dataRecordEntity.setCreateTime(LocalDateTime.now());
//        LambdaQueryWrapper<DataRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
//        lambdaQueryWrapper.eq(DataRecordEntity::getCustomId, dto.getCustomId())
//                .eq(DataRecordEntity::getTableName, customDataEntity.getCustomName())
//                .eq(DataRecordEntity::getOperation, dataCleaningType.name);
//        DataRecordEntity one = dataRecordService.getOne(lambdaQueryWrapper);
        dataRecordService.saveOrUpdate(dataRecordEntity);
    }

    @Override
    public DataCleaningResultVO save(String number, int customId) {
        if (redisMap.get(customId + "") == null) {
            throw new BusinessException("只能填充一次");
        }
        DataCleaningResultVO dataCleaningResultVO = new DataCleaningResultVO();
        if (MissingItemCheckDataCleaningProcessor.MissingItemCheckFLAG != MissItemCheckEnums.CHECKED.getNumber()) {
            throw new BusinessException("请先进行缺失项检查！");
        }
        //缓存数据
        Map<Integer, Set<String>> setMap = redisMap.get(customId + "");
        //全部数据
        List<Map<String, Object>> listMap = getListMapFromEntity(customId);
        int row = 0;
        for (Map<String, Object> stringObjectMap : listMap) {
            Set<String> strings = setMap.get(row);
            if (CollectionUtils.isEmpty(strings)) {
                row++;
                continue;
            }
            Set<String> stringObject = stringObjectMap.keySet();
            for (String string : strings) {
                if (stringObject.contains(string)) {
                    stringObjectMap.put(string, number);
                }
            }
            row++;
        }
        //todo 记录操作
        customDataRecord(customId, "自动填充", customId + "");
        dataCleaningResultVO.setAllList(listMap);
        //设置返回空数组，方便前端校验
        dataCleaningResultVO.setList(new ArrayList<>());
        return dataCleaningResultVO;
    }

    //后台只处理筛选数据的记录 原始数据的操作给前端调用接口记录
    public void customDataRecord(int customId, String oprateName, String uniId) {
        CustomDataEntity entity = customDataService.getOne(new LambdaQueryWrapper<CustomDataEntity>().eq(CustomDataEntity::getCustomName, customId));

        DataRecordEntity dataRecordEntity = new DataRecordEntity();
        dataRecordEntity.setTableName(entity.getCustomName());
        dataRecordEntity.setOperation(oprateName);
        dataRecordEntity.setCreateTime(LocalDateTime.now());
        dataRecordEntity.setUniId(uniId);
        dataRecordService.save(dataRecordEntity);
    }

    @Transactional
    @Override
    public void update(DataCleaningDTO dto) throws Exception {
        if (StringUtils.isEmpty(dto.getCustomId())) {
            obtationDataCleaning(dto);
            return;
        }
        CustomDataEntity customDataEntity = customDataService.getOne(new LambdaQueryWrapper<CustomDataEntity>().eq(CustomDataEntity::getCustomName, dto.getCustomId()));
        customDataEntity.setCustomResult(dto.getCustomResult());
        if (MissingItemCheckDataCleaningProcessor.MissingItemCheckFLAG == MissItemCheckEnums.CHECKED.getNumber()
                || CleaningNormalizationDataCleaningProcessor.CleaningNormalizationFLAG == 1) {
            String customName = customDataEntity.getCustomName();
//            customName = customDataEntity.getStatus() == 0 ? customName + "【处理后】" : customName;
            Integer status = Objects.isNull(customDataEntity.getStatus()) ? 0 : customDataEntity.getStatus();
            //未处理过
            if (status.equals(0)) {
//                String tageName = customName + "【处理后】";
//                customDataEntity.setCustomName(tageName);
                customDataEntity.setStatus(1);
            }
        }
        customDataService.saveOrUpdate(customDataEntity);
        //清空缓存
        redisMap.remove(dto.getCustomId() + "");


    }

    private void obtationDataCleaning(DataCleaningDTO dto) throws SQLException {
        if (StringUtils.isEmpty(dto.getCustomResult())) {
            throw new BusinessException("数据为空，不能进行操作");
        }
        Optional<DatasrcEntity> optional = datasrcService.findById(dto.getDataSrcId());
        DatasrcEntity entity = null;
        if (optional.isPresent()) {
            entity = optional.get();
        }
        String ip = entity.getIp();
        String port = entity.getPort();
        String username = entity.getDbUser();
        String password = entity.getPass();
        String dbName = entity.getDatabaseName();
        String url = "jdbc:dm://" + ip + ":" + port + "/" + dbName;
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = DriverManager.getConnection(url, username, password);
            String customResult = dto.getCustomResult();
            List<Map<String, String>> listMap =
                    JSON.parseObject(customResult, new TypeReference<List<Map<String, String>>>() {
                    });
            for (Map<String, String> list : listMap) {
                Set<String> strings = list.keySet();
                String id = list.get("ID");
                for (String key : strings) {
                    if (key.equalsIgnoreCase("id")) {
                        continue;
                    }
                    String value = list.get(key);
//                        StringBuilder stringBuilder = new StringBuilder("UPDATE ");
//                        stringBuilder.append(dbName + "." + dto.getTableName() + " ");
//                        stringBuilder.append(" SET " + key + " = " + value);
//                        stringBuilder.append(" where ID =  " + id);
                    String sql = "UPDATE " + DMSqlStatementUtil.addPoint(dbName) + "." + DMSqlStatementUtil.addPoint(dto.getTableName()) + " SET " + key + " = '" + value + "'" + " where ID =  '" + id + "'";
                    log.error(sql);
                    statement = connection.prepareStatement(sql);
                    connection.commit();
                }

            }
            // todo 记录操作
            redisMap.remove(dto.getDataSrcId() + dto.getTableName() + dto.getVar1() + "");
            return;
        } finally {
            if (connection != null) {
                connection.close();
            }
            statement.close();

        }
    }

    @Override
    public List<Map<String, Object>> getById(Integer customId) {
        List<Map<String, Object>> listMapFromEntity = getListMapFromEntity(customId);
        return listMapFromEntity;
    }

    @Override
    public DataCleaningResultVO missingCheckDataV2(String listMap, int type) {
        Gson gson = new Gson();
        List<Map<String, Object>> tageListMap = gson.fromJson(listMap, new TypeToken<List<Map<String, Object>>>() {
        }.getType());
        DataCleaningType dataCleaningType = DataCleaningType.getType(type);

//        return simpleDataCleaningHandlerSupport.dataCleaning(tageListMap, dataCleaningType, 0);
        DataCleaningResultVO dataCleaningResultVO = simpleDataCleaningHandlerSupport.dataCleaning(tageListMap, dataCleaningType, 0 + "");
        Map<String, Object> tageMap = dataCleaningResultVO.getList().get(0);
        Set<String> tageMapKey = tageMap.keySet();
        List<Map<String, Object>> temp = dataCleaningResultVO.getList();
        temp.remove(0);
        for (Map<String, Object> stringObjectMap : temp) {
            Set<String> keys = stringObjectMap.keySet();
            for (String key : keys) {
                if (tageMapKey.contains(key)) {
                    String tempValue = stringObjectMap.get(key).toString();
                    String tageValue = tageMap.get(key).toString();
                    tageMap.put(key, tageValue + "," + tempValue);
                }

            }
        }
        List<Map<String, Object>> result = Lists.newArrayList();
        result.add(tageMap);
        dataCleaningResultVO.setList(result);
        return dataCleaningResultVO;

    }

    @Override
    public String missingValueFill(MissingFillDTO missingFillDTO) {
        // 原数据
        List<Map<String, Object>> dataList = getDataList(missingFillDTO);
        // 无数据处理
        if (dataList.isEmpty()) {
            return "暂无数据";
        }

        List<Map<String, Object>> peekDataList = handleOriginData(dataList, missingFillDTO.getRoundFilterList());
        String roundFieldData = "";
        //获取当前页面所有数据列（过滤无关列）
        //List<Map<String, Object>> handleData = getHandleData(missingFillDTO.getReq(), dataList);
        // 移除id, task_id
//        List<Map<String, Object>> peekDataList = handleOriginData(dataList, roundFilterList);


//        String roundFieldData = "";
//        if (!CollectionUtils.isEmpty(roundFilterList)) {
//            for (int i = 0; i < roundFilterList.size(); i++) {
//                roundFieldData += roundFilterList.get(i).getRoundData();
//            }
//        }

        //缺失值检查
        simpleDataCleaningHandlerSupport.dataCleaning(peekDataList, DataCleaningType.getType(1),
                missingFillDTO.getDataSrcId() + missingFillDTO.getTableName() + roundFieldData);

        Map<Integer, Set<String>> missingMap = redisMap.get(missingFillDTO.getDataSrcId() + missingFillDTO.getTableName());
        if (CollectionUtil.isEmpty(missingMap)){
            return "暂无缺失项，无需填充";
        }

        int missingFillType = missingFillDTO.getMissingFillType();
        try {
            //缺失项定值填充
            if (missingFillType == MissingFillTypeEnum.CONSTANT_VALUE.getCode()) {
                //缺失值填充
                constantValueFill(missingMap, dataList, missingFillDTO.getConstantValue(), missingFillDTO, roundFieldData.toString());
            } else if (missingFillType == MissingFillTypeEnum.COLUMN_AVG_VALUE.getCode()) {
                fillColumn(missingFillDTO, peekDataList, dataList, StatisticalUtil::average);
            } else if (missingFillType == MissingFillTypeEnum.ROW_AVG_VALUE.getCode()) {
                fillRow(missingFillDTO, dataList, missingMap, dataList, StatisticalUtil::average);
            } else if (missingFillType == MissingFillTypeEnum.GLOBAL_AVG_VALUE.getCode()) {
                fillGlobal(missingFillDTO, dataList, missingMap, dataList, roundFieldData, StatisticalUtil::average);
            } else if (missingFillType == MissingFillTypeEnum.COLUMN_MODE_VALUE.getCode()) {
                fillColumn(missingFillDTO, peekDataList, dataList, StatisticalUtil::mode);
            } else if (missingFillType == MissingFillTypeEnum.ROW_MODE_VALUE.getCode()) {
                fillRow(missingFillDTO, peekDataList, missingMap, dataList, StatisticalUtil::mode);
            } else if (missingFillType == MissingFillTypeEnum.GLOBAL_MODE_VALUE.getCode()) {
                fillGlobal(missingFillDTO, dataList, missingMap, dataList, roundFieldData, StatisticalUtil::mode);
            } else if (missingFillType == MissingFillTypeEnum.COLUMN_SD_VALUE.getCode()) {
                fillColumn(missingFillDTO, peekDataList, dataList, StatisticalUtil::standardDeviation);
            } else if (missingFillType == MissingFillTypeEnum.ROW_SD_VALUE.getCode()) {
                fillRow(missingFillDTO, peekDataList, missingMap, dataList, StatisticalUtil::standardDeviation);
            } else if (missingFillType == MissingFillTypeEnum.GLOBAL_SD_VALUE.getCode()) {
                fillGlobal(missingFillDTO, dataList, missingMap, dataList, roundFieldData, StatisticalUtil::standardDeviation);
            } else if (missingFillType == MissingFillTypeEnum.COLUMN_MAX_VALUE.getCode()) {
                fillColumn(missingFillDTO, peekDataList, dataList, StatisticalUtil::max);
            } else if (missingFillType == MissingFillTypeEnum.ROW_MAX_VALUE.getCode()) {
                fillRow(missingFillDTO, peekDataList, missingMap, dataList, StatisticalUtil::max);
            } else if (missingFillType == MissingFillTypeEnum.GLOBAL_MAX_VALUE.getCode()) {
                fillGlobal(missingFillDTO, dataList, missingMap, dataList, roundFieldData, StatisticalUtil::max);
            } else if (missingFillType == MissingFillTypeEnum.COLUMN_MIN_VALUE.getCode()) {
                fillColumn(missingFillDTO, peekDataList, dataList, StatisticalUtil::min);
            } else if (missingFillType == MissingFillTypeEnum.ROW_MIN_VALUE.getCode()) {
                fillRow(missingFillDTO, peekDataList, missingMap, dataList, StatisticalUtil::min);
            } else if (missingFillType == MissingFillTypeEnum.GLOBAL_MIN_VALUE.getCode()) {
                fillGlobal(missingFillDTO, dataList, missingMap, dataList, roundFieldData, StatisticalUtil::min);
            } else if (missingFillType == MissingFillTypeEnum.COLUMN_MEDIAN_VALUE.getCode()) {
                fillColumn(missingFillDTO, peekDataList, dataList, StatisticalUtil::median);
            } else if (missingFillType == MissingFillTypeEnum.ROW_MEDIAN_VALUE.getCode()) {
                fillRow(missingFillDTO, peekDataList, missingMap, dataList, StatisticalUtil::median);
            } else if (missingFillType == MissingFillTypeEnum.GLOBAL_MEDIAN_VALUE.getCode()) {
                fillGlobal(missingFillDTO, dataList, missingMap, dataList, roundFieldData, StatisticalUtil::median);
            }
            redisMap.remove(missingFillDTO.getDataSrcId() + missingFillDTO.getTableName() + roundFieldData);
        } catch (Exception e) {
            e.printStackTrace();
        }


        return "操作成功";
    }

    @Override
    public void missingValueFillModel(MissingFillModelDTO request) {
        // 模型训练使用的自变量值
        MissingFillModelDTO.DbTableColumn independent = request.getIndependent();
        DatasrcEntity independentDataSrc = datasrcService.getById(independent.getDataSrcId());
        DbLinkBO independentDbLinkBo = independentDataSrc.createDblinkDto().createDbLinkBo();
        List<Map<String, Object>> independentDataList = dataBaseHandler.findDataList(Collections.singletonList(independent.getColumnName()), independent.getTableName(), independentDbLinkBo);
        List<Object> independentValueList = independentDataList.stream().map(map -> map.get(independent.getColumnName())).collect(Collectors.toList());
        independentValueList = independentValueList.stream().filter(item -> NumberUtil.isNumber(String.valueOf(item))).map(item -> Double.parseDouble(String.valueOf(item))).collect(Collectors.toList());

        // 模型训练使用的因变量值
        MissingFillModelDTO.DbTableColumn dependent = request.getDependent();
        DatasrcEntity dependentDataSrc = datasrcService.getById(dependent.getDataSrcId());
        DbLinkBO dependentDbLinkBo = dependentDataSrc.createDblinkDto().createDbLinkBo();
        List<Map<String, Object>> dependentDataList = dataBaseHandler.findDataList(Collections.singletonList(dependent.getColumnName()), dependent.getTableName(), dependentDbLinkBo);
        List<Object> dependentValueList = dependentDataList.stream().map(map -> map.get(dependent.getColumnName())).collect(Collectors.toList());
        dependentValueList = dependentValueList.stream().filter(item -> NumberUtil.isNumber(String.valueOf(item))).map(item -> Double.parseDouble(String.valueOf(item))).collect(Collectors.toList());

        // 模型训练完成后预测使用的自变量值
        MissingFillModelDTO.DbTableColumn missing = request.getMissing();
        DatasrcEntity missingDataSrc = datasrcService.getById(missing.getDataSrcId());
        DbLinkBO missingDbLinkBo = missingDataSrc.createDblinkDto().createDbLinkBo();

        // 参数组装
        JSONObject pythonRequest = new JSONObject();
        pythonRequest.put("method", 0);
        JSONObject requestData = new JSONObject();
        pythonRequest.put("data", requestData);
        JSONObject independentData = new JSONObject();
        requestData.put("independent", independentData);
        independentData.put(JSON.toJSONString(independent), independentValueList);
        JSONObject dependentData = new JSONObject();
        requestData.put("dependent", dependentData);
        dependentData.put(JSON.toJSONString(dependent), dependentValueList);
        JSONObject validatedData = new JSONObject();
        requestData.put("validated", validatedData);
        validatedData.put(JSON.toJSONString(missing), independentValueList);

        // 调用python算法服务
        String pythonServerPath = dictService.getValue(URL_CODE, PYTHON_URL);
        String resultValueToBeCalculate = restTemplate.postForObject(pythonServerPath, pythonRequest, String.class);
        JSONObject pythonResponse = JSON.parseObject(resultValueToBeCalculate);
        if ("SUCCESS".equals(pythonResponse.getString("status"))) {
            JSONArray valueList = pythonResponse.getJSONObject("data").getJSONArray("validationsetPredictedValue");
            if (!CollectionUtils.isEmpty(valueList)) {
                List<Map<String, String>> dataList = new ArrayList<>();
                for (Object value : valueList) {
                    dataList.add(new HashMap<String, String>() {{
                        put(missing.getColumnName(), ObjectUtil.isNull(value) ? null : String.valueOf(value));
                    }});
                }
                // 插入预测结果数据
                dataBaseHandler.editTableData(missing.getTableName(), "ID", dataList, missingDbLinkBo);
            }
        } else {
            throw new BusinessException(pythonResponse.getString("errorInfo"));
        }
    }

    /**
     * @param missingFillDTO 缺失值填充请求类
     * @param peekDataList   处理后的数据
     * @param missingMap     缺失值map
     * @param dataList       原始数据
     * @param roundFieldData
     * @param function       计算函数
     */
    private void fillGlobal(MissingFillDTO missingFillDTO,
                            List<Map<String, Object>> peekDataList,
                            Map<Integer, Set<String>> missingMap,
                            List<Map<String, Object>> dataList,
                            String roundFieldData,
                            CalculateFunction function) {
        // 获取填充值
        Double fillValue = calculateGlobal(peekDataList, function);
        // 更新数据库
        constantValueFill(missingMap, dataList, fillValue, missingFillDTO, roundFieldData.toString());
    }

    /**
     * @param missingFillDTO 缺失值填充请求类
     * @param peekDataList   处理后的数据
     * @param dataList       原始数据
     * @param function       计算函数
     * @throws SQLException
     */
    private void fillColumn(MissingFillDTO missingFillDTO, List<Map<String, Object>> peekDataList, List<Map<String, Object>> dataList, CalculateFunction function) throws SQLException {
        // 获取填充值
        Map<String, Double> colValueMap = calculateColumn(peekDataList, function);
        // 填充原始数据
        filledWithColumn(dataList, colValueMap);
        // 更新数据库
        updateData(dataList, missingFillDTO);
    }

    /**
     * 处理原始数据
     *
     * @param dataList        原始数据
     * @param roundFilterList 待过滤字段
     * @return
     */
    @NotNull
    private static List<Map<String, Object>> handleOriginData(List<Map<String, Object>> dataList, List<ExerciseInfoDTO.RoundFilter> roundFilterList) {
        List<Map<String, Object>> peekDataList = dataList.stream().map(d -> {
            Map<String, Object> copiedMap = new HashMap<>(d);
            copiedMap.remove("ID");
            copiedMap.remove("id");
            copiedMap.remove("TASK_ID");
            copiedMap.remove("task_id");
//            if (!CollectionUtils.isEmpty(roundFilterList)) {
//                for (ExerciseInfoDTO.RoundFilter roundFilter : roundFilterList) {
//                    copiedMap.remove(roundFilter.getRoundField());
//                }
//            }
            return copiedMap;
        }).collect(Collectors.toList());
        // 待清洗字段名list
        List<String> fields = roundFilterList.stream().map(ExerciseInfoDTO.RoundFilter::getRoundField).collect(Collectors.toList());

        // 保留待清洗字段
        peekDataList.forEach(
                map -> map.entrySet()
                        .removeIf(
                                entry -> !fields.contains(entry.getKey())));
        // 移除非数字数据
        peekDataList.forEach(
                map -> map.entrySet()
                        .removeIf(
                                entry -> ObjectUtils.isNotEmpty(entry.getValue())
                                        && !NumberUtil.isNumber((String) entry.getValue())));
        // 转换double
        peekDataList.forEach(
                map -> map.replaceAll(
                        (key, value) -> ObjectUtils.isNotEmpty(value) ?
                                NumberUtil.parseNumber(value.toString()).doubleValue() :
                                value));
        return peekDataList;
    }

    /**
     * @param missingFillDTO 缺失值填充请求类
     * @param peekDataList   处理后的数据
     * @param missingMap     缺失值map
     * @param dataList       原始数据
     * @param function       计算函数
     * @throws SQLException
     */
    private void fillRow(MissingFillDTO missingFillDTO, List<Map<String, Object>> peekDataList, Map<Integer, Set<String>> missingMap, List<Map<String, Object>> dataList, CalculateFunction function) throws SQLException {
        // 待清洗字段名list
        List<String> fields = missingFillDTO.getRoundFilterList().stream().map(ExerciseInfoDTO.RoundFilter::getRoundField).collect(Collectors.toList());
        // 获取填充值
        Map<Integer, Double> indexMap = calculateRow(peekDataList, new ArrayList<>(missingMap.keySet()), function);
        // 填充原始数据
        filledWithRow(dataList, indexMap, fields);
        // 更新数据库
        updateData(dataList, missingFillDTO);
    }

    private List<Map<String, Object>> getDataList(MissingFillDTO missingFillDTO) {
        ExerciseInfoDTO exerciseInfoDTO = new ExerciseInfoDTO();
        exerciseInfoDTO.setDataSrcId(missingFillDTO.getDataSrcId());
        exerciseInfoDTO.setDbName(missingFillDTO.getTableName());
//        exerciseInfoDTO.setRoundFilterList(missingFillDTO.getRoundFilterList());
        //默认不分页
        exerciseInfoDTO.setOffSet(0);
        //原始数据
        return dataSrcManagerService.tableNameDefineField(exerciseInfoDTO).getList();
    }

    /**
     * 更新数据
     *
     * @param dataList       原数据
     * @param missingFillDTO
     * @throws SQLException
     */
    private void updateData(List<Map<String, Object>> dataList, MissingFillDTO missingFillDTO) throws SQLException {

        DbLinkDTO dbLinkDTO = datasrcService.creatLinkDtoByDsid(missingFillDTO.getDataSrcId());
        DbLinkBO dbLinkBO = dbLinkDTO.createDbLinkBo();

        Connection connection = DbUtils.getDataSource(dbLinkBO).getConnection();

        List<String> colNames = new ArrayList<>(dataList.get(0).keySet());

        StringBuilder updateSql = new StringBuilder();
        updateSql.append("UPDATE " + DbUtils.addPointHelp(dbLinkBO.getType(), dbLinkBO.getDbName()) + "." + DbUtils.addPointHelp(dbLinkBO.getType(), missingFillDTO.getTableName()) + " SET ");
        for (String colName : colNames) {
            // 排除id字段
            if (!"ID".equals(colName)) {
                updateSql.append(DbUtils.addPointHelp(dbLinkBO.getType(), colName)).append(" = ").append("?, ");
            }
        }
        updateSql.delete(updateSql.length() - 2, updateSql.length() - 1);
        updateSql.append(" WHERE ID = ?;");

        // 将id字段移到队尾
        List<String> queue = new LinkedList<>();
        int idIndex = -1;
        for (int i = 0; i < colNames.size(); i++) {
            if ("ID".equals(colNames.get(i))) {
                idIndex = i;
                continue;
            }
            queue.add(colNames.get(i));
        }
        if (idIndex < 0) {
            throw new BusinessException("缺少ID字段，清洗失败");
        }
        queue.add(colNames.get(idIndex));

        // todo 还有点问题
        PreparedStatement preparedStatement = connection.prepareStatement(updateSql.toString());
        try {
            for (Map<String, Object> data : dataList) {
                for (int i = 0; i < queue.size(); i++) {
                    //排查掉为null的数据，防止String.valueOf()将null转为字符串"null"
                    if (!Objects.isNull(data.get(queue.get(i)))) {
                        preparedStatement.setObject(i + 1, String.valueOf(data.get(queue.get(i))));
                        continue;
                    }
                    preparedStatement.setObject(i + 1, null);
                }
                preparedStatement.addBatch();
            }
            int[] rows = preparedStatement.executeBatch();
            connection.commit();
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        } finally {
            preparedStatement.close();
            connection.close();
        }
    }

    /**
     * 填充列值
     *
     * @param dataList    原数据
     * @param colValueMap key列名value填充值
     */
    private void filledWithColumn(List<Map<String, Object>> dataList, Map<String, Double> colValueMap) {
        for (Map<String, Object> data : dataList) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (ObjectUtils.isEmpty(entry.getValue())) {
                    entry.setValue(colValueMap.get(entry.getKey()));
                }
            }
        }
    }

    /**
     * 填充行值
     *
     * @param dataList 原数据
     * @param indexMap key行数value填充值
     */
    private void filledWithRow(List<Map<String, Object>> dataList, Map<Integer, Double> indexMap, List<String> fields) {
        for (Map.Entry<Integer, Double> entry : indexMap.entrySet()) {
            Integer key = entry.getKey();
            Double value = entry.getValue();
            Map<String, Object> data = dataList.get(key);
            for (String field : fields) {
                Object colValue = data.get(field);
                //只有为空才填充
                if (ObjectUtils.isEmpty(colValue)) {
                    data.put(field, value);
                }
            }
        }
    }


    /**
     * 行均值
     *
     * @param dataList  原数据
     * @param indexList 缺失值所在行的index
     * @return 缺失值所在行平均值
     */
    private Map<Integer, Double> calculateRow(List<Map<String, Object>> dataList, List<Integer> indexList, CalculateFunction func) {
        // key rowIndex value fillValue
        Map<Integer, Double> fillMap = new HashMap<>();
        // 遍历字段
        for (Integer index : indexList) {
            Map<String, Object> data = dataList.get(index);
            List<Object> filterList = data.values().stream().filter(t -> ObjectUtils.isNotEmpty(t) && NumberUtil.isNumber(t.toString())).collect(Collectors.toList());
            double[] rowData = filterList.stream()
                    .mapToDouble(v -> Double.parseDouble(v.toString())).toArray();
            if (rowData==null || rowData.length==0){
                fillMap.putIfAbsent(index, 0d);
            }else {
                fillMap.putIfAbsent(index, func.doCalculate(rowData));
            }
        };
        return fillMap;
    }

    /**
     * 列均值
     *
     * @param dataList 原数据
     * @return 所有列的平均值
     */
    private Map<String, Double> calculateColumn(List<Map<String, Object>> dataList, CalculateFunction func) {
        // key 列名 value 填充值
        Map<String, Double> fillMap = new HashMap<>();
        Map<String, List<Double>> arrayMap = new HashMap<>();
        // 遍历字段
        dataList.forEach(data -> {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                arrayMap.putIfAbsent(entry.getKey(), new ArrayList<Double>());
                if (ObjectUtils.isNotEmpty(entry.getValue()) && NumberUtil.isNumber(entry.getValue().toString())){
                    arrayMap.get(entry.getKey()).add((Double.parseDouble(entry.getValue().toString())));
                }
            }
        });
        // 获取每个列的均值
        for (String colName : arrayMap.keySet()) {
            double[] colData = arrayMap.get(colName).stream().mapToDouble(Double::doubleValue).toArray();
            if (colData==null || colData.length==0){
                fillMap.putIfAbsent(colName, 0d);
            }else {
                fillMap.putIfAbsent(colName, func.doCalculate(colData));
            }
        }
        return fillMap;
    }

    /**
     * 全局均值
     *
     * @param dataList 原数据
     * @return 所有列的平均值
     */
    private Double calculateGlobal(List<Map<String, Object>> dataList, CalculateFunction func) {
        List<Double> allDataList = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (ObjectUtils.isNotEmpty(entry.getValue()) && NumberUtil.isNumber(entry.getValue().toString())){
                    allDataList.add(Double.parseDouble(entry.getValue().toString()));
                }else {
                    allDataList.add(0d);
                }
            }
        }
        double[] allData = allDataList.stream().mapToDouble(Double::doubleValue).toArray();
        // 遍历字段
        return func.doCalculate(allData);
    }

    /**
     * 缺失项定值填充
     *
     * @param missingMap     每行数据缺失字段记录
     * @param dataList       原始表数据
     * @param constantValue  填充数值
     * @param missingFillDTO
     */
    private void constantValueFill(Map<Integer, Set<String>> missingMap, List<Map<String, Object>> dataList, double constantValue, MissingFillDTO missingFillDTO, String roundFieldData) {
        Optional<DatasrcEntity> optional = datasrcService.findById(missingFillDTO.getDataSrcId());
        DatasrcEntity entity = null;
        if (!optional.isPresent()) {
            return;
        }
        entity = optional.get();
        try {
            for (int i = 0; i < dataList.size(); i++) {
                Map<String, Object> list = dataList.get(i);
                Set<String> strings = missingMap.get(i);
                if (CollectionUtil.isEmpty(strings)) {
                    continue;
                }
                Map<String, String> dataMap = new HashMap<>();
                list.forEach((k, v) -> {
                    dataMap.put(k, String.valueOf(v));
                });
                for (String key : strings) {
                    if ("id".equalsIgnoreCase(key)) {
                        continue;
                    }
                    dataMap.put(key, String.valueOf(constantValue));
                    dataBaseHandler.editTableData(missingFillDTO.getTableName(), "ID", Collections.singletonList(dataMap), entity.createDblinkDto().createDbLinkBo());

                }
            }

            redisMap.remove(missingFillDTO.getDataSrcId() + missingFillDTO.getTableName() + roundFieldData);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }

    private List<Map<String, Object>> getListMapFromEntity(int customId) {
        Gson gson = new Gson();
        CustomDataEntity customDataEntity = customDataService.getOne(new LambdaQueryWrapper<CustomDataEntity>().eq(CustomDataEntity::getCustomName, customId));

        String customResult = customDataEntity.getCustomResult();
        if (StringUtils.isEmpty(customDataEntity)) {
            customResult = customDataEntity.getPendingResult();
        }
        List<Map<String, Object>> listMap = gson.fromJson(customResult, new TypeToken<List<Map<String, Object>>>() {
        }.getType());
        return listMap;
    }

    public static void main(String[] args) {
        StringBuilder stringBuilder = new StringBuilder("UPDATE ");
        stringBuilder.append("dbName" + "." + "dto.getTableName()" + " ");
        stringBuilder.append(" SET " + "key" + " = " + "'value'");
        stringBuilder.append(" where ID =  " + "'id'");
        System.out.println(stringBuilder);
    }
}

