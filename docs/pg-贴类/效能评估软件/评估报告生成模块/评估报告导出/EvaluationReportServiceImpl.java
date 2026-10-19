package com.yw.module.export.service.impl;

import cn.afterturn.easypoi.word.WordExportUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.XML;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.yw.common.defines.config.RestTemplateBean;
import com.yw.common.defines.enums.CalModuleType;
import com.yw.common.defines.enums.DataAnalysisType;
import com.yw.common.defines.enums.ModuleType;
import com.yw.common.defines.enums.SchemaEvaluateType;
import com.yw.common.encryptor.FileEncryptor;
import com.yw.common.util.XmlJsonConvertUtil;
import com.yw.common.utils.BeanUtils;
import com.yw.common.utils.FileUtil;
import com.yw.common.utils.Node;
import com.yw.common.utils.export.ExportSaveToDBUtil;
import com.yw.common.utils.export.word.DocxHelper;
import com.yw.common.utils.export.word.ExcelUtils;
import com.yw.common.utils.export.word.NodeStyle;
import com.yw.common.utils.export.word.TextStyleBean;
import com.yw.framework.backMgr.dbTableGenerator.domain.dto.DbLinkDTO;
import com.yw.framework.backMgr.dbTableGenerator.domain.dto.ExerciseInfoDTO;
import com.yw.framework.backMgr.dbTableGenerator.service.impl.DataBaseHandler;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.framework.common.module.login.mapper.UserMapper;
import com.yw.framework.common.module.table.response.BaseResponse;
import com.yw.framework.common.module.table.response.BaseTableHeaderBody;
import com.yw.framework.common.util.UUIDGenerator;
import com.yw.gather.dbinfomgr.DataSrcManagerService;
import com.yw.gather.dbinfomgr.IDatasrcService;
import com.yw.gather.dbinfomgr.dto.ExportXmlDTO;
import com.yw.gather.dbinfomgr.entity.DatasrcEntity;
import com.yw.gather.gatherscheme.schemeMgr.entity.GatherRoundRecord;
import com.yw.gather.gatherscheme.schemeMgr.service.IGatherRoundRecordService;
import com.yw.gather.gathertask.defines.DataSourceParam;
import com.yw.gather.gathertask.taskmgr.domain.vo.TableInfoVO;
import com.yw.module.calculation.domain.entity.*;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.indicatorCalculation.domain.dto.DictionaryOrderParaDTO;
import com.yw.module.calculation.indicatorCalculation.mapper.IAlgorithmParamMapper;
import com.yw.module.calculation.indicatorCalculation.mapper.IWeightModelMapper;
import com.yw.module.calculation.indicatorCalculation.service.ICalcModuleService;
import com.yw.module.calculation.indicatorCalculation.service.ICalcorService;
import com.yw.module.calculation.indicatorCalculation.service.ITIndicatorWeightService;
import com.yw.module.calculation.indicatorCalculation.service.IWeightModelService;
import com.yw.module.calculation.mapper.AlgorithmConfigureMapper;
import com.yw.module.calculation.mapper.CompStatusRelationMapper;
import com.yw.module.calculation.mapper.IComponentInfoMapper;
import com.yw.module.calculation.mapper.IPartsStateMapper;
import com.yw.module.export.domain.dto.IndicatorExportNode;
import com.yw.module.export.domain.dto.SchemaAllDataDTO;
import com.yw.module.export.mapper.IExportMapper;
import com.yw.module.export.service.IEvaluationReportService;
import com.yw.module.indicator.domain.dto.*;
import com.yw.module.indicator.domain.entity.*;
import com.yw.module.indicator.domain.vo.IndicatorStandardVO;
import com.yw.module.indicator.service.*;
import com.yw.module.missionScene.domain.entity.*;
import com.yw.module.missionScene.service.*;
import com.yw.module.plan.domain.dto.*;
import com.yw.module.plan.domain.entity.*;
import com.yw.module.plan.service.*;
import com.yw.module.system.domain.dto.EquCompositionDTO;
import com.yw.module.system.domain.dto.LogEvaluationPlanDto;
import com.yw.module.system.domain.entity.EquParamEntity;
import com.yw.module.system.domain.entity.EquSysParamEntity;
import com.yw.module.system.domain.entity.EquipmentEntity;
import com.yw.module.system.domain.entity.EquipmentSystemEntity;
import com.yw.module.system.domain.enums.BusinessType;
import com.yw.module.system.mapper.EquParamMapper;
import com.yw.module.system.mapper.EquSysParamMapper;
import com.yw.module.system.mapper.PGLogMapper;
import com.yw.module.system.service.*;
import com.yw.source.dataManagement.handler.DataBasesOperationHandler;
import com.yw.source.importDataDirectory.entity.DataDirectory;
import com.yw.source.importDataDirectory.entity.ImportTableRecord;
import com.yw.source.importDataDirectory.service.IDataDirectoryService;
import com.yw.source.importDataDirectory.service.IImportTableRecordService;
import com.yw.source.preprocessing.entity.CustomDataEntity;
import com.yw.source.preprocessing.service.CustomDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Example;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * pg报告导出服务实现
 *
 * @Author admin
 * @Description: pg报告导出服务实现
 * @Date: 2021/3/30 16:59
 */
@Service
@Slf4j
public class EvaluationReportServiceImpl implements IEvaluationReportService {

    @Value("${pg-general.module.indicator-calculation.basic-file-path}")
    private String basicFilePath;

    /**
     * 文件内容加解密密码
     */
    private static final String PASSWORD = "3923c189671d4c4692c3b1848e2fc4cc707e977686aa43319bf33baf061e3525";
    private final ISceneService sceneService;
    private final IIndicatorService indicatorService;
    private final ITIssuedTaskService issuedTaskService;
    private final ITIndicatorValueService indicatorValueService;
    private final ISchemaService schemaService;
    private final IExportMapper exportMapper;
    private final ITaskService taskService;
    @Autowired
    private SchemaCataService schemaCataService;
    @Autowired
    private ExperimentPreceptService experimentPreceptService;
    private final ISceneTableService iSceneTableService;
    @Autowired
    private IIndicatorTreeService indicatorTreeService;

    @Autowired
    private IGatherRoundRecordService iGatherRoundRecordService;
    @Autowired
    private DataBasesOperationHandler dataBasesOperationHandler;

    @Resource
    private EquParamMapper equParamMapper;
    @Resource
    private IEquParamService equParamService;
    @Resource
    private EquSysParamMapper equSysParamMapper;
    @Resource
    private IEquSysParamService equSysParamService;

    @Autowired
    private DataSrcManagerService dataSrcManagerService;

    @Autowired
    private ITIndicatorWeightService indicatorWeightService;

    @Autowired
    private IWeightModelMapper weightModelMapper;

    @Autowired
    private IWeightModelService weightModelService;

    @Autowired
    private ICalcorService calcorService;

    @Autowired
    private ICalcModuleService iCalcModuleService;

    @Autowired
    private IStandardLabelsService standardLabelsService;

    @Autowired
    private CustomDataService customDataService;

    @Autowired
    private IAlgorithmParamMapper iAlgorithmParamMapper;

    @Autowired
    private AlgorithmConfigureMapper algorithmConfigureMapper;
    @Autowired
    private IComponentInfoMapper componentInfoMapper;
    @Autowired
    private IPartsStateMapper partsStateMapper;
    @Autowired
    private CompStatusRelationMapper compStatusRelationMapper;
    @Autowired
    private CompeteSceneService competeSceneService;
    @Autowired
    private SceneScenarioService sceneScenarioService;
    @Autowired
    private IMissionTaskService missionTaskService;
    @Autowired
    private ContributionRateAlgorithmService algorithmService;
    @Autowired
    private IIndicatorCriteriaService indicatorCriteriaService;
    @Autowired
    private ExportSaveToDBUtil exportSaveToDBUtil;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PGLogMapper pgLogMapper;
    @Resource
    private IEquipmentService equipmentService;
    @Resource
    private IEquipmentSystemService equipmentSystemService;
    @Autowired
    private IDatasrcService dataSrcService;
    @Resource
    private IImportTableRecordService importTableRecordService;
    @Resource
    private IDataDirectoryService dataDirectoryService;
    @Resource
    private IEquSysService equSysService;
    @Resource
    private IndicatorCalculationParameterService indicatorCalculationParameterService;

    @Autowired
    private SidesDirecService sidesDirecService;
    @Autowired
    private SidesParamService sidesParamService;
    @Autowired
    private IMissionTaskDetailsService missionTaskDetailsService;
    /**
     * 场景详情信息service接口
     */
    @Autowired
    private ICompeteSceneEntityDetailsService competeSceneEntityDetailsService;

    public EvaluationReportServiceImpl(ISceneService sceneService, IIndicatorService indicatorService, ITIssuedTaskService issuedTaskService,
                                       ITIndicatorValueService indicatorValueService, ISchemaService schemaService, IExportMapper exportMapper,
                                       ITaskService taskService, ISceneTableService iSceneTableService) {
        this.sceneService = sceneService;
        this.indicatorService = indicatorService;
        this.issuedTaskService = issuedTaskService;
        this.indicatorValueService = indicatorValueService;
        this.schemaService = schemaService;
        this.exportMapper = exportMapper;
        this.taskService = taskService;
        this.iSceneTableService = iSceneTableService;
    }

    /**
     * 上传导出单场景world
     *
     * @param exportDTO 导出配置dto
     */
    @Override
    public void exportWordByIssuedId(ExportDTO exportDTO) {
        List<String> scenes = com.yw.common.utils.ArrayUtils.stringToList(exportDTO.getSceneIds());
        if (CollectionUtils.isEmpty(scenes)) {
            throw new BusinessException("未选择场景");
        }
        // 获取选择场景关联的下达任务
        List<String> issuedIds = exportMapper.findIssuedByScene(com.yw.common.utils.ArrayUtils.stringToList(exportDTO.getSceneIds()));

        // 导出文件
        exportWordCustom(exportDTO);
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
        String ret = df.format(new Date());

        issuedIds.stream().forEach(issuedId -> {
            //下达任务信息
            Optional<IssuedTaskEntity> issuedTaskEntityOp = issuedTaskService.findById(issuedId);
            if (!issuedTaskEntityOp.isPresent()) {
                throw new BusinessException("下达的实验任务不存在");
            }
            IssuedTaskEntity issuedTaskEntity = issuedTaskEntityOp.get();
            uploadFile(new File(basicFilePath + com.yw.common.utils.FileUtil.UPLOAD_MANAGE + exportDTO.getSid() + ret + ".docx"), issuedTaskEntity);
        });

        /*//下达任务信息
        Optional<IssuedTaskEntity> issuedTaskEntityOp = issuedTaskService.findById(issuedId);
        if (!issuedTaskEntityOp.isPresent()) throw new BusinessException("下达的实验任务不存在");
        IssuedTaskEntity issuedTaskEntity = issuedTaskEntityOp.get();
        //场景信息
        String sid;
        List<SceneEntity> sceneEntityList = sceneService.find(SceneEntity.builder().experimentId(issuedTaskEntity.getExperimentId()).build());
        if (CollectionUtils.isEmpty(sceneEntityList)) throw new BusinessException("下达的实验任务没有关联场景");
        sid = sceneEntityList.get(0).getSid();
        Map<String, SceneEntity> sceneEntityMap = new HashMap<>();
        List<String> sceneIds = new ArrayList<>();
        sceneEntityList.forEach(f -> {
            sceneIds.add(f.getId());
            sceneEntityMap.put(f.getId(), f);
        });
        //指标值
        List<IndicatorValuesWithNameBO> indicatorValuesBoList = indicatorValueService.
                findWithNameBySidIndicatorIdSceneId(sceneEntityList.get(0).getSid(), null, sceneIds);
        Map<String, List<Map<String, Object>>> glist = new HashMap<>();
        //构造导出表格数据
        Map<String, Integer> indexMap = new HashMap<>();
        indicatorValuesBoList.forEach(f -> {
            List<Map<String, Object>> valueList;
            if (glist.containsKey(f.getSceneId())) {
                valueList = glist.get(f.getSceneId());
            } else {
                valueList = new ArrayList<>();
                glist.put(f.getSceneId(), valueList);
            }

            Map<String, Object> data = new HashMap<>();
            valueList.add(data);
            Integer sortNum;
            if (indexMap.containsKey(f.getLevel().toString() + f.getSceneId())) {
                sortNum = indexMap.get(f.getLevel().toString() + f.getSceneId());
                sortNum++;
            } else {
                sortNum = 0;
                indexMap.put(f.getLevel().toString() + f.getSceneId(), sortNum);
            }
            data.put("name", f.getName());
            data.put("val", f.getNormalizedValue());
            data.put("num", f.getLevel() + "." + sortNum);
        });

        exportWord(issuedTaskEntity, glist, sceneEntityMap, sid);

        uploadFile(new File(basicFilePath + FileUtil.UPLOAD_MANAGE + issuedId + ".docx"), issuedTaskEntity);*/
    }


    private void exportWord(IssuedTaskEntity issuedTaskEntity, Map<String, List<Map<String, Object>>> glist, Map<String, SceneEntity> sceneEntityMap, String sid) {

        try {
            XWPFDocument word = new XWPFDocument();
            //标题
            DocxHelper.createHeadingTitles(word);
            TextStyleBean style = new TextStyleBean();
            style.setColor("000000");
            style.setContent("pg报告");
            style.setAlign(ParagraphAlignment.CENTER);
            DocxHelper.createTitle(word, null, 1, style);
            //二级标题
            TextStyleBean subTitle = new TextStyleBean();
            subTitle.setContent("1. 实验描述");
            subTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, subTitle);
            //段落内容
            TextStyleBean text = new TextStyleBean();
            text.setFontsize(12);
            text.setColor("000000");
            text.setContent("  任务名称：" + issuedTaskEntity.getName());
            DocxHelper.addText(null, word, text);
            text.setContent("  装备型号：" + issuedTaskEntity.getModel());
            DocxHelper.addText(null, word, text);
            text.setContent("  创建时间：" + issuedTaskEntity.getCreateDate());
            DocxHelper.addText(null, word, text);
            //二级标题
            subTitle.setContent("2. 指标体系");
            DocxHelper.createTitle(word, null, 2, subTitle);
            //图片
            NodeStyle st = new NodeStyle();
            st.align = "center";
            st.height = 300;
            st.width = 500;
            DocxHelper.addImage2Word(word, null, st
                    , new File(basicFilePath + com.yw.common.utils.FileUtil.INDICATOR_IMAGE + sid + ".jpg"), 500);
            //二级标题
            subTitle.setContent("3. 指标计算结果");
            DocxHelper.createTitle(word, null, 2, subTitle);
            //表格
            createTable(word, glist, sceneEntityMap);

            File fileMkdir = new File(basicFilePath + com.yw.common.utils.FileUtil.UPLOAD_MANAGE);// 判断文件目录是否存在
            if (!fileMkdir.exists()) {
                fileMkdir.mkdirs();
            }

            FileOutputStream out = new FileOutputStream(basicFilePath + com.yw.common.utils.FileUtil.UPLOAD_MANAGE + issuedTaskEntity.getId() + ".docx");

            word.write(out);
            out.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new BusinessException(e.getMessage());
        }
    }

    private void createTable(XWPFDocument word, Map<String, List<Map<String, Object>>> glist, Map<String, SceneEntity> sceneEntityMap) {
        //遍历每个场景
        Set<Object> sceneNum = new HashSet<>();

        for (Map.Entry<String, SceneEntity> entry : sceneEntityMap.entrySet()) {
            String sceneId = entry.getKey();
            SceneEntity sceneEntity = entry.getValue();
            List<Map<String, Object>> valueList = glist.getOrDefault(sceneId, new ArrayList<>());
            //三级标题
            TextStyleBean subTitle = new TextStyleBean();
            sceneNum.add(sceneId);
            subTitle.setContent("  3." + sceneNum.size() + "" + sceneEntity.getName());
            DocxHelper.createTitle(word, null, 3, subTitle);

            int rowNum = valueList.size() + 1;
            XWPFTable xwpfTable = word.createTable(rowNum, 3);

            CTTblPr tblPr = xwpfTable.getCTTbl().getTblPr();
            tblPr.getTblW().setType(STTblWidth.DXA);
            tblPr.getTblW().setW(new BigInteger("9000"));
            CTString styleStr = tblPr.addNewTblStyle();
            styleStr.setVal("StyledTable");
            CTTblLayoutType t = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
            t.setType(STTblLayoutType.FIXED);

            List<XWPFTableRow> rows = xwpfTable.getRows();

            for (int i = 0; i < rowNum; i++) {
                XWPFTableRow row = rows.get(i);
                CTTrPr trPr = row.getCtRow().addNewTrPr();
                //表格标题
                if (i == 0) {
                    CTHeight ht = trPr.addNewTrHeight();
                    ht.setVal(BigInteger.valueOf(400));

                    XWPFTableCell cell1 = row.getCell(0);
                    setXWPFRun(cell1, "序号");

                    XWPFTableCell cell2 = row.getCell(1);
                    setXWPFRun(cell2, "名称");

                    XWPFTableCell cell3 = row.getCell(2);
                    setXWPFRun(cell3, "指标值(秒)");
                } else {
                    int index = i - 1;
                    CTHeight ht = trPr.addNewTrHeight();
                    ht.setVal(BigInteger.valueOf(360));
                    Map<String, Object> rowData = valueList.get(index);
                    row.getCell(0).setText(rowData.getOrDefault("num", "").toString());
                    row.getCell(1).setText(rowData.getOrDefault("name", "").toString());
                    row.getCell(2).setText(rowData.getOrDefault("val", "").toString());
                }

            }
        }

    }

    private void setXWPFRun(XWPFTableCell cell, String text) {
        XWPFParagraph p = cell.addParagraph();
        XWPFRun pRun = p.createRun();
        pRun.setBold(true);
        pRun.setText(text);
    }

    private void uploadFile(File file, IssuedTaskEntity issuedTaskEntity) {
        try {
            FileSystemResource fileSystemResource = new FileSystemResource(file);
            MultiValueMap<String, Object> param = new LinkedMultiValueMap<>();
            param.add("file", fileSystemResource);
            param.add("resultId", issuedTaskEntity.getExperimentId());
            param.add("reportName", issuedTaskEntity.getName() + "-报告");
            param.add("taskId", issuedTaskEntity.getId());
            param.add("taskName", issuedTaskEntity.getName());

            HttpHeaders headers = new HttpHeaders();
            headers.add("Accept", MediaType.APPLICATION_JSON.toString());
            headers.setContentType(MediaType.parseMediaType("multipart/form-data; charset=UTF-8"));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(param, headers);

            String managerPlatformBaseUrl = "";
            ResponseEntity<Map> result = RestTemplateBean.template.postForEntity(managerPlatformBaseUrl + "/api/assess/save", requestEntity, Map.class);
//            ResponseEntity<Map> result = RestTemplateBean.template.postForEntity(managerPlatformBaseUrl + "/pgrj-Service/evaluationReport/test", requestEntity, Map.class);
            Map<String, Object> body = result.getBody();
            String code = body.get("code").toString();
            if (!"0".equals(code)) {
                String msg = body.get("msg").toString();
                throw new BusinessException(msg);
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            throw new BusinessException("上传文件失败 " + e.getMessage());
        }
    }

    /**
     * @param exportDTO
     * @description: 生成自定义
     * @return: void
     * @author: 肖强虎
     * @time: 2021/8/26 0026 10:41
     */
    private void exportWordCustom(ExportDTO exportDTO) {
        try {
            String sid = exportDTO.getSid();
            SchemaEntity schemaEntity = schemaService.findById(sid).orElse(null);
            if (schemaEntity == null) {
                throw new BusinessException("方案不存在");
            }
            List<String> sceneIds = com.yw.common.utils.ArrayUtils.stringToList(exportDTO.getSceneIds());
            if (CollectionUtils.isEmpty(sceneIds)) {
                throw new BusinessException("至少选择一个场景");
            }


            //二级标题序号
            int titleNum = 1;
            XWPFDocument word = new XWPFDocument();
            setCover(word, schemaEntity, null);
            //标题
            word.createParagraph();
            DocxHelper.createHeadingTitles(word);
            TextStyleBean style = new TextStyleBean();
            style.setColor("000000");
            style.setContent("pg报告");
            style.setAlign(ParagraphAlignment.CENTER);
            DocxHelper.createTitle(word, null, 1, style);
            if ("1".equals(exportDTO.getModel())) {
                if (1 == exportDTO.getShowArm()) {
                    setArm(word, schemaEntity, titleNum);
                    titleNum++;
                }
                if (1 == exportDTO.getShowIndicator()) {
                    setIndicator(word, sid, titleNum);
                    titleNum++;
                }
                if (1 == exportDTO.getShowMethod()) {
                    setMethod(word, sid, titleNum);
                    titleNum++;
                }
                if (1 == exportDTO.getShowBackground()) {
                    // 暂不需要
//                setBackGround(word, schemaEntity, titleNum);
//                titleNum++;
                }
                if (1 == exportDTO.getShowResult()) {
                    setResult(word, sceneIds, titleNum, exportDTO.getTransValue());
                    titleNum++;
                }
                if (1 == exportDTO.getShowAdvice()) {
                    setAdvice(word, titleNum, sceneIds);
                }
            } else {
                setCustom(word, exportDTO.getSection(), titleNum);
            }
            File fileMkdir = new File(basicFilePath + com.yw.common.utils.FileUtil.UPLOAD_MANAGE);// 判断文件目录是否存在
            if (!fileMkdir.exists()) {
                fileMkdir.mkdirs();
            }

            SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
            String ret = df.format(new Date());
            FileOutputStream out = new FileOutputStream(basicFilePath + com.yw.common.utils.FileUtil.UPLOAD_MANAGE + exportDTO.getSid() + ret + ".docx");

            word.write(out);
            out.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new BusinessException(e.getMessage());
        }
    }

    private void exportXmlV2(HttpServletResponse response, SchemaEntity schemaEntity, List<String> sceneIds, ContributionRateExportDTO exportDTO) {
        String sid = schemaEntity.getId();
        //导出json
        String taskName = schemaEntity.getName();
        Map<String, Object> retsult = Maps.newHashMap();
        try {
            List<ExportXmlDTO> list = Lists.newArrayList();
            ExportXmlDTO exportXmlDTO = new ExportXmlDTO();
            List<Map<String, Object>> dataList = Lists.newArrayList();

            Map<String, Object> resultMap = Maps.newLinkedHashMap();
            resultMap.put("名称", schemaEntity.getName());
            resultMap.put("描述", schemaEntity.getDescribes());
            resultMap.put("背景", StringUtils.isBlank(schemaEntity.getBackground()) ? "" : schemaEntity.getBackground());
            dataList.add(resultMap);
            exportXmlDTO.setTableName("方案");
            exportXmlDTO.setData(dataList);
            list.add(exportXmlDTO);
            List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
            Map<String, String> indicatorNameMap = indicatorEntities.stream().collect(Collectors.toMap(IndicatorEntity::getId, IndicatorEntity::getName));
            if (1 == exportDTO.getPgIndicatorIsShow()) {

                ExportXmlDTO indicatorDTO = new ExportXmlDTO();
                List<Map<String, Object>> indicatorDataList = Lists.newArrayList();
                for (IndicatorEntity indicatorEntity : indicatorEntities) {
                    Map<String, Object> indicatorMap = Maps.newLinkedHashMap();
                    indicatorMap.put("指标名称", indicatorEntity.getName());
                    indicatorMap.put("指标层级", indicatorEntity.getLevel());
                    indicatorMap.put("量纲", indicatorEntity.getDimension());
                    indicatorDataList.add(indicatorMap);
                }
                indicatorDTO.setTableName("指标");
                indicatorDTO.setData(indicatorDataList);
                list.add(indicatorDTO);
            }
            if (1 == exportDTO.getPgResultIsShow()) {
                //指标值
                List<IndicatorValuesWithNameDTO> indicatorValuesBoList = indicatorValueService.
                        findWithNameBySidIndicatorIdSceneId(sid, null, sceneIds);
                Map<String, List<IndicatorValuesWithNameDTO>> collect = indicatorValuesBoList.stream().collect(Collectors.groupingBy(IndicatorValuesWithNameDTO::getSceneId));
                for (Map.Entry<String, List<IndicatorValuesWithNameDTO>> stringListEntry : collect.entrySet()) {
                    String sceneId = stringListEntry.getKey();
                    List<IndicatorValuesWithNameDTO> value = stringListEntry.getValue();
                    SceneEntity sceneEntity = sceneService.getById(sceneId);
                    ExportXmlDTO indicatorDTO = new ExportXmlDTO();
                    List<Map<String, Object>> indicatorDataList = Lists.newArrayList();
                    for (IndicatorValuesWithNameDTO indicatorEntity : value) {
                        Map<String, Object> indicatorMap = Maps.newLinkedHashMap();
                        indicatorMap.put("指标名称", indicatorNameMap.get(indicatorEntity.getIndicatorId()));
                        indicatorMap.put("指标值", indicatorEntity.getNormalizedValue());
                        indicatorMap.put("指标标准值", indicatorEntity.getStandardLabelValue());
                        indicatorMap.put("指标标准名称", indicatorEntity.getStandardLabelName());
                        indicatorDataList.add(indicatorMap);
                    }
                    indicatorDTO.setTableName("样本");
                    indicatorDTO.setData(indicatorDataList);
                    list.add(indicatorDTO);
                }
            }
            String xmlContent = XmlJsonConvertUtil.listToXml(list);
            try {
                com.yw.common.utils.FileUtil.downloadFileByte(response, JSON.toJSONBytes(xmlContent, SerializerFeature.NotWriteDefaultValue), taskName + ".xml");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception var8) {
            throw new BusinessException("导出xml异常！");
        }

        try {
            String xml = XML.toXml(retsult);
            com.yw.common.utils
                    .FileUtil.downloadFileByte(response, JSON.toJSONBytes(xml, SerializerFeature.NotWriteDefaultValue), taskName + ".xml");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void exportJsonV2(HttpServletResponse response, SchemaEntity schemaEntity, List<String> sceneIds, ContributionRateExportDTO exportDTO) {
        String sid = schemaEntity.getId();
        //导出json
        String taskName = schemaEntity.getName();
        Map<String, Object> retsult = Maps.newHashMap();
        if (1 == exportDTO.getPgSchemeIsShow()) {
            List<String> methods = exportMapper.findEvaMethodV2(sid);
            String method = CollectionUtils.isEmpty(methods) ? "" : StringUtils.join(methods, "、");
            retsult.put("method", method);
        }
        if (1 == exportDTO.getPgSchemeIsShow()) {
            String background = StringUtils.isBlank(schemaEntity.getBackground()) ? "" : schemaEntity.getBackground();
            retsult.put("background", background);
        }
        if (1 == exportDTO.getPgIndicatorIsShow()) {
            List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
            retsult.put("indicatorEntities", indicatorEntities);
        }
        if (1 == exportDTO.getPgResultIsShow()) {
            //指标值
            List<IndicatorValuesWithNameDTO> indicatorValuesBoList = indicatorValueService.
                    findWithNameBySidIndicatorIdSceneId(sid, null, sceneIds);
            retsult.put("indicatorValuesBoList", indicatorValuesBoList);
        }
        if (1 == exportDTO.getPgSampleStructureIsShow()) {
//            List<CalcorEntity> calcorEntities = calcorRepository.findAll(Example.of(CalcorEntity.builder().sid(sid).build()));
            List<CalcorEntity> calcorEntities = calcorService.list(new QueryWrapper<CalcorEntity>().setEntity(CalcorEntity.builder().sid(sid).build()));
            retsult.put("conclusion", calcorEntities);
        }
        try {
            com.yw.common.utils.FileUtil.downloadFileByte(response, JSON.toJSONBytes(retsult, SerializerFeature.NotWriteDefaultValue), taskName + ".json");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void exportJson(HttpServletResponse response, SchemaEntity schemaEntity, List<String> sceneIds, ExportDTO exportDTO) {
        String sid = schemaEntity.getId();
        //导出json
        String taskName = schemaEntity.getName();
        Map<String, Object> retsult = Maps.newHashMap();
        if (1 == exportDTO.getShowMethod()) {
            List<String> methods = exportMapper.findEvaMethodV2(sid);
            String method = CollectionUtils.isEmpty(methods) ? "" : StringUtils.join(methods, "、");
            retsult.put("method", method);
        }
        if (1 == exportDTO.getShowBackground()) {
            String background = StringUtils.isBlank(schemaEntity.getBackground()) ? "" : schemaEntity.getBackground();
            retsult.put("background", background);
        }
        if (1 == exportDTO.getShowArm()) {

        }
        if (1 == exportDTO.getShowIndicator()) {
            List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
            retsult.put("indicatorEntities", indicatorEntities);
        }
        if (1 == exportDTO.getShowResult()) {
            //指标值
            List<IndicatorValuesWithNameDTO> indicatorValuesBoList = indicatorValueService.
                    findWithNameBySidIndicatorIdSceneId(sid, null, sceneIds);
            retsult.put("indicatorValuesBoList", indicatorValuesBoList);
        }
        if (1 == exportDTO.getShowAdvice()) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(schemaEntity.getName()).append(":").append(schemaEntity.getSummary() == null ? "" : schemaEntity.getSummary()).append("\n");

//            List<SceneEntity> sceneEntityList = sceneService.findByIdIn(sceneIds);
//            sceneEntityList.forEach(e -> {
//                stringBuilder.append(e.getName() + ":" + (e.getSummary() == null ? "" : e.getSummary()) + "\n");
//            });
            String conclusion = stringBuilder.toString();
            retsult.put("conclusion", conclusion);
        }
        try {
            com.yw.common.utils.FileUtil.downloadFileByte(response, JSON.toJSONBytes(retsult, SerializerFeature.NotWriteDefaultValue), taskName + ".json");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void exportOneSchema(HttpServletResponse response, ExportDTO exportDTO) {
        String sid = exportDTO.getSid();
        SchemaEntity schemaEntity = schemaService.findById(sid).orElse(null);
        if (schemaEntity == null) throw new BusinessException("方案不存在");
        List<String> sceneIds = com.yw.common.utils.ArrayUtils.stringToList(exportDTO.getSceneIds());
        if (CollectionUtils.isEmpty(sceneIds)) throw new BusinessException("至少选择一个场景");

        String docType = exportDTO.getDocType();
        if ("json".equals(docType)) {
            exportJson(response, schemaEntity, sceneIds, exportDTO);
        }

        //二级标题序号
        int titleNum = 1;
        XWPFDocument word = new XWPFDocument();
        // todo 2022 - 03 -01 在封面展示方案描述（没有则不展示）
        setCover(word, schemaEntity, null);
//        String schemaCata = schemaEntity.getSchemaCata();
//        String schemaCataName = "默认分类";
//        if (StringUtils.isNotEmpty(schemaCata)) {
//            SchemaCataEntity schemaCataEntity = iSchemaCataRepository.getOne(schemaCata);
//            if (schemaCataEntity != null) {
//                schemaCataName = schemaCataEntity.getCataName();
//            }
//        }
        //标题
//        XWPFParagraph paragraph = word.createParagraph();
//        // 换行分页 增加分隔符
//        paragraph.setPageBreak(true);
//        DocxHelper.createHeadingTitles(word);
//        TextStyleBean style = new TextStyleBean();
//        style.setColor("000000");
//        style.setContent(schemaCataName);
//        style.setAlign(ParagraphAlignment.CENTER);
//        DocxHelper.createTitle(word, null, 1, style);
        //设置二级标题
        TextStyleBean towStyle = new TextStyleBean();
        towStyle.setColor("000000");
        towStyle.setContent(schemaEntity.getName());
        towStyle.setAlign(ParagraphAlignment.CENTER);
        DocxHelper.createTitle(word, null, 1, towStyle);

        if ("1".equals(exportDTO.getModel())) {
            if (1 == exportDTO.getShowArm()) {
                setArm(word, schemaEntity, titleNum);
                titleNum++;
            }
            if (1 == exportDTO.getShowIndicator()) {
                setIndicator(word, sid, titleNum);
                titleNum++;
            }
            if (1 == exportDTO.getShowMethod()) {
                setMethod(word, sid, titleNum);
                titleNum++;
            }
            if (1 == exportDTO.getShowBackground()) {
                // 暂不需要
                setBackGround(word, schemaEntity, titleNum);
                titleNum++;
            }
            if (1 == exportDTO.getShowResult()) {
                setResult(word, sceneIds, titleNum, exportDTO.getTransValue());
                titleNum++;
            }
            if (1 == exportDTO.getShowAdvice()) {
                setAdvice(word, titleNum, sceneIds);
            }
        } else {
            setCustom(word, exportDTO.getSection(), titleNum);
        }
        //设置检验科目及pg指标对照表
        setIndicatorTable(word, ++titleNum, sceneIds, Lists.newArrayList(schemaEntity));
        doExport(response, schemaEntity, word, exportDTO.getDocType(), null, null);

    }

    private void exportOneSchemaV3(HttpServletResponse response, ExportDTO exportDTO) {
        String sid = exportDTO.getSid();
        SchemaEntity schemaEntity = schemaService.findById(sid).orElse(null);
        if (schemaEntity == null) throw new BusinessException("方案不存在");
        List<String> sceneIds = com.yw.common.utils.ArrayUtils.stringToList(exportDTO.getSceneIds());
        if (CollectionUtils.isEmpty(sceneIds)) throw new BusinessException("至少选择一个场景");

        String docType = exportDTO.getDocType();
        if ("json".equals(docType)) {
            exportJson(response, schemaEntity, sceneIds, exportDTO);
        }

        //二级标题序号
        Map<String, Object> map = Maps.newHashMap();
        map.put("schemaName", "评估方案名称：" + schemaEntity.getName());
        map.put("schemaDate", "评估方案创建时间：" + schemaEntity.getCreateDate());
        map.put("schemaDesc", "评估任务描述：" + (StringUtils.isEmpty(schemaEntity.getDescribes()) ? "" : schemaEntity.getDescribes()));
        map.put("schemaTaget", "评估目的：" + (StringUtils.isEmpty(schemaEntity.getAssessTarget()) ? "" : schemaEntity.getAssessTarget()));
        map.put("schemaBackground", "评估背景：" + (StringUtils.isEmpty(schemaEntity.getBackground()) ? "" : schemaEntity.getBackground()));

        // 设置指标
        List<IndicatorExportNode> indicatorExportNodes = Lists.newArrayList();
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
        indicatorEntities.forEach(e -> {
            indicatorExportNodes.add(new IndicatorExportNode(e.getId(), e.getName(), e.getPid(), -1.0));
        });

        Map<String, String> indicatorNameMaps = Maps.newHashMap();
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            indicatorNameMaps.put(indicatorEntity.getId(), indicatorEntity.getName());
        }
        Set<String> overConstructData = Sets.newHashSet();
        List<IndicatorExportNode> indicatorExportNodesV2 = Lists.newArrayList();
        // 表格构造
        //表的行列数
//        List<CalcorEntity> calcorEntities = calcorRepository.findAll(Example.of(CalcorEntity.builder().sid(sid).build()));
        List<CalcorEntity> calcorEntities = calcorService.list(new QueryWrapper<CalcorEntity>().setEntity(CalcorEntity.builder().sid(sid).build()));
        int number = 0;
        for (CalcorEntity calcorEntity : calcorEntities) {
            String colName = calcorEntity.getColName();
            if (StringUtils.isNotEmpty(colName)) {
                String indicatorId = calcorEntity.getIndicatorId();
                if (!overConstructData.contains(indicatorId)) {
                    indicatorExportNodesV2.add(new IndicatorExportNode(indicatorId, indicatorNameMaps.get(indicatorId), "0", -1.0));
                    overConstructData.add(indicatorId);
                }
                indicatorExportNodesV2.add(new IndicatorExportNode(calcorEntity.getId(), colName, indicatorId, -1.0));
                number++;
            }
        }
        map.put("sceneDesc", "  体系作战能力评估指标计算需要采集" + number + "项数据，具体如下表所示: ");

        map.put("schemaMethod", "评估计算方法模型：" + EvaluationType.getTypeByName(schemaEntity.getEvaluationMethod()).getChName());

        List<IndicatorValuesEntity> indicatorValuesEntities = indicatorValueService.findBySidSceneId(schemaEntity.getId(), sceneIds.get(0));
        Map<String, IndicatorValuesEntity> indicatorValuesEntityMap = Maps.newHashMap();
        for (IndicatorValuesEntity entity : indicatorValuesEntities) {
            indicatorValuesEntityMap.put(entity.getIndicatorId(), entity);
        }
        IndicatorEntity rootEntity = null;
        for (IndicatorEntity entity : indicatorEntities) {
            if (entity.getPid().equals("0")) {
                rootEntity = entity;
                break;
            }
        }
        IndicatorValuesEntity entity = indicatorValuesEntityMap.get(rootEntity.getId());
        String result = "";
        String var = "";
        if (!Objects.isNull(entity)) {
            // 效能指标  -》 效能指标名称（getNormalizedValue）、效能指标名称（getNormalizedValue）
            // 计算结果评价值 （效能）-》 第一个的评价结果、第二个的评价结果、
            result = StringUtils.isEmpty(entity.getNormalizedValue()) ? result : entity.getNormalizedValue();
            var = StringUtils.isEmpty(entity.getStandardLabelName()) ? result : entity.getStandardLabelName();
        }
        map.put("schemaResult", "评估计算结果：" + result);
        map.put("result", "计算结果评价结果：" + var);
        map.put("summary", "评估结论：" + (StringUtils.isEmpty(schemaEntity.getSummary()) ? "" : schemaEntity.getBackground()));
        try {
            XWPFDocument xwpfDocument = WordExportUtil.exportWord07("E:\\能力评估模板.docx", map);
            List<XWPFParagraph> paragraphs = xwpfDocument.getParagraphs();
            for (XWPFParagraph para : paragraphs) {
                if (StringUtils.isNotEmpty(para.getText()) && "indicatorImage".contains(para.getText())) {
                    para.removeRun(0);
                    //图片
                    NodeStyle st = new NodeStyle();
                    st.align = "center";
                    st.height = 300;
                    st.width = 500;
                    DocxHelper.addImage2Word(xwpfDocument, para, st
                            , new File(basicFilePath + com.yw.common.utils.FileUtil.INDICATOR_IMAGE + sid + ".jpg"), 500);
                    continue;
                }
                if (StringUtils.isNotEmpty(para.getText()) && "oneTable".equals(para.getText())) {
                    // 设置第一个表格
                    para.removeRun(0);
                    XmlCursor xmlCursor = para.getCTP().newCursor();
                    XWPFTable xwpfTable = xwpfDocument.insertNewTbl(xmlCursor);
                    setMergeTableV2(xwpfTable, indicatorExportNodes);
                    continue;
                }
                if (StringUtils.isNotEmpty(para.getText()) && "twoTable".equals(para.getText())) {
                    // 设置第一个表格
                    para.removeRun(0);
                    XmlCursor xmlCursor = para.getCTP().newCursor();
                    XWPFTable xwpfTable = xwpfDocument.insertNewTbl(xmlCursor);
                    setMergeTableV2(xwpfTable, indicatorExportNodesV2);
                    continue;
                }
                if (StringUtils.isNotEmpty(para.getText()) && "threeTable".equals(para.getText())) {
                    para.removeRun(0);
                    List<IndicatorExportNode> indicatorExportNodesV3 = Lists.newArrayList();
                    // 记录已经添加的效能父节点
                    indicatorEntities.forEach(e -> {
                        // 效能指标去掉跟指标 保留叶子指标的父节点
                        // 取出所有叶子节点 根据叶子节点pid 去构造indicatorExportNode
                        // 构造叶子指标父节点信息的时候 需要将他的pid设置”0“

                        indicatorExportNodes.add(new IndicatorExportNode(e.getId(), e.getName(), e.getPid(), -1.0));
                    });
                    for (IndicatorExportNode node : indicatorExportNodes) {
                        String id = node.getId();
                        IndicatorValuesEntity valuesEntity = indicatorValuesEntityMap.get(id);
                        if (!Objects.isNull(valuesEntity) && StringUtils.isNotEmpty(valuesEntity.getNormalizedValue())) {
                            node.setResult(Double.parseDouble(valuesEntity.getNormalizedValue()));
                        }
                    }
                    XmlCursor xmlCursor = para.getCTP().newCursor();
                    XWPFTable xwpfTable = xwpfDocument.insertNewTbl(xmlCursor);
                    setMergeTableV2(xwpfTable, indicatorExportNodesV3);
                    continue;
                }
            }
            // 表格填充
            List<XWPFTable> tables = xwpfDocument.getTables();
            int i = 0;
            for (XWPFTable xwpfTable : tables) {
                switch (i) {
                    case 1:
                        setMergeTableV2(xwpfTable, indicatorExportNodes);
                        break;
                    case 2:
                        setMergeTableV2(xwpfTable, indicatorExportNodesV2);
                        break;
                    case 4:
                        List<IndicatorExportNode> indicatorExportNodesV3 = Lists.newArrayList();
                        // 记录已经添加的效能父节点
                        indicatorEntities.forEach(e -> {
                            // 效能指标去掉跟指标 保留叶子指标的父节点
                            // 取出所有叶子节点 根据叶子节点pid 去构造indicatorExportNode
                            // 构造叶子指标父节点信息的时候 需要将他的pid设置”0“

                            indicatorExportNodes.add(new IndicatorExportNode(e.getId(), e.getName(), e.getPid(), -1.0));
                        });
                        for (IndicatorExportNode node : indicatorExportNodes) {
                            String id = node.getId();
                            IndicatorValuesEntity valuesEntity = indicatorValuesEntityMap.get(id);
                            if (!Objects.isNull(valuesEntity) && StringUtils.isNotEmpty(valuesEntity.getNormalizedValue())) {
                                node.setResult(Double.parseDouble(valuesEntity.getNormalizedValue()));
                            }
                        }
                        setMergeTableV2(xwpfTable, indicatorExportNodesV3);
                        //写标题和添加图片 图片只有中间指标才有 柱状图 折线图 饼图  雷达图
                        TextStyleBean text = new TextStyleBean();
                        XWPFRun runSix = createRun(xwpfDocument, text);
                        runSix.setText("（2）评估计算结果图");
                        String id = rootEntity.getId();
                        int j = 0;
                        for (IndicatorEntity en : indicatorEntities) {
                            if (j < en.getLevel()) {
                                j = en.getLevel();
                            }
                        }
                        // 能力 根指标pid =0 二级指标的pid就是根指标的id
                        for (int k = 1; k <= j; k++) {
                            XWPFRun runSeven = createRun(xwpfDocument, text);
                            String level = "二级";
                            if (k == 2) {
                                level = "三级";
                            }
                            if (k == 3) {
                                level = "四级";
                            }
                            runSeven.setText(k + "）" + level + "评估指标计算结果");
                            runSeven.addBreak();//软回车
                            ReportImage(xwpfDocument, schemaEntity, level, text);
                        }
                        break;

                }
                i++;
            }

            FileOutputStream outputStream = new FileOutputStream("E:\\" + schemaEntity.getName() + ".docx");
            xwpfDocument.write(outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void exportOneSchemaV2(HttpServletResponse response, ContributionRateExportDTO exportDTO) {
        String sid = exportDTO.getSid();
        SchemaEntity schemaEntity = schemaService.findById(sid).orElse(null);
        if (schemaEntity == null) throw new BusinessException("方案不存在");
        List<String> sceneIds = com.yw.common.utils.ArrayUtils.stringToList(exportDTO.getSceneId());
        if (CollectionUtils.isEmpty(sceneIds)) throw new BusinessException("至少选择一个场景");

        String docType = exportDTO.getExportType();
        if ("json".equals(docType)) {
            exportJsonV2(response, schemaEntity, sceneIds, exportDTO);
        }

        //二级标题序号
        int titleNum = 1;
        XWPFDocument word = new XWPFDocument();
        // todo 2022 - 03 -01 在封面展示方案描述（没有则不展示）
        setCover(word, schemaEntity, null);
        //设置一级标题
        TextStyleBean towStyle = new TextStyleBean();
        towStyle.setColor("000000");
        towStyle.setContent("1、评估报告");
        towStyle.setAlign(ParagraphAlignment.LEFT);
        DocxHelper.createTitle(word, null, 1, towStyle);
        // 段落内容
        TextStyleBean text = new TextStyleBean();
        text.setFontsize(12);
        text.setColor("000000");
        text.setAlign(ParagraphAlignment.LEFT);
        String arm = "评估报告主要包括4个章节,分别是评估方案背景、评估指标体系、评估样本结构、评估结果与结论";
        text.setContent("  " + arm);
        DocxHelper.addText(null, word, text);
        if (exportDTO.getPgSchemeIsShow() != null && exportDTO.getPgSchemeIsShow() == 1) {
            // 设置评估背景
            setEstimateBackGround(word, schemaEntity, titleNum);
            titleNum++;
        }
        // 设置指标
        List<IndicatorExportNode> indicatorExportNodes = Lists.newArrayList();
        List<IndicatorEntity> indicatorEntities = getIndicatorEntities(sid, indicatorExportNodes);
        if (exportDTO.getPgIndicatorIsShow() != null && exportDTO.getPgIndicatorIsShow() == 1) {
            setEstimateIndicator(word, schemaEntity, titleNum, indicatorExportNodes);
            titleNum++;
        }
        if (exportDTO.getPgSampleStructureIsShow() != null && exportDTO.getPgSampleStructureIsShow() == 1) {
            // 设置样本数据结构数据
            setEstimateSceneTable(word, schemaEntity, titleNum, sceneIds, indicatorEntities);
            titleNum++;
        }
        if (exportDTO.getPgResultIsShow() != null && exportDTO.getPgResultIsShow() == 1) {
            // 设置评估结果与结论
            setEstimateResult(word, schemaEntity, titleNum, sceneIds, indicatorEntities, indicatorExportNodes, exportDTO.getType());
        }
        doExport(response, schemaEntity, word, docType, null, exportDTO);

    }


    private List<IndicatorEntity> getIndicatorEntities(String sid, List<IndicatorExportNode> indicatorExportNodes) {
        IndicatorEntity build = IndicatorEntity.builder().sid(sid).build();
        List<IndicatorEntity> indicatorEntities = indicatorService.find(build);
        indicatorEntities.forEach(e -> {
            indicatorExportNodes.add(new IndicatorExportNode(e.getId(), e.getName(), e.getPid(), -1.0));
        });
        return indicatorEntities;
    }

    private void setEstimateResult(XWPFDocument word, SchemaEntity schemaEntity, int titleNum, List<String> sceneIds, List<IndicatorEntity> indicatorEntities,
                                   List<IndicatorExportNode> indicatorExportNodes, String type) {  //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent("1." + titleNum + " 评估结果与结论");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);
        String scencId = sceneIds.get(0);
        List<IndicatorValuesEntity> indicatorValuesEntities = indicatorValueService.findBySidSceneId(schemaEntity.getId(), scencId);
        Map<String, IndicatorValuesEntity> indicatorValuesEntityMap = Maps.newHashMap();
        for (IndicatorValuesEntity entity : indicatorValuesEntities) {
            indicatorValuesEntityMap.put(entity.getIndicatorId(), entity);
        }
        IndicatorEntity rootEntity = null;
        for (IndicatorEntity entity : indicatorEntities) {
            if (entity.getPid().equals("0")) {
                rootEntity = entity;
                break;
            }
        }
        if (schemaEntity.getSchemaEvaluationType() == SchemaEvaluateType.ABILITY_EVALUATE.id) {
            abilityEvaluateWord(word, schemaEntity, titleNum, indicatorValuesEntityMap, rootEntity, indicatorEntities);

        } else if (schemaEntity.getSchemaEvaluationType() == SchemaEvaluateType.EFFICACY_EVALUATE.id) {
            // 效能评估
            effectEvaluateWord(word, schemaEntity, titleNum, indicatorValuesEntityMap, rootEntity, indicatorEntities);
        }


    }


    /**
     * 效能评估结果
     */
    private void effectEvaluateWord(XWPFDocument word, SchemaEntity schemaEntity, int titleNum,
                                    Map<String, IndicatorValuesEntity> indicatorValuesEntityMap,
                                    IndicatorEntity rootEntity, List<IndicatorEntity> indicatorEntities) {
        List<IndicatorExportNode> indicatorExportNodes = Lists.newArrayList();

        String tableName;
        // 效能评估
        tableName = "效能评估结果";
        //三级标题
        TextStyleBean subTitle2 = new TextStyleBean();
        subTitle2.setContent("1." + titleNum + ".1 " + tableName);
        subTitle2.setColor("000000");
        DocxHelper.createTitle(word, null, 3, subTitle2);

        //段落内容
        TextStyleBean subTitle3 = new TextStyleBean();
        subTitle3.setContent("1." + titleNum + ".1.1 " + "评估结果摘要");
        subTitle3.setColor("000000");
        DocxHelper.createTitle(word, null, 3, subTitle3);
        TextStyleBean text = new TextStyleBean();
        XWPFTable table = word.createTable(1, 1);
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("8200"));
        XWPFTableRow row = table.getRow(0);
        XWPFTableCell cell = row.getCell(0);
        XWPFParagraph xwpfParagraph1 = cell.addParagraph();
        XWPFParagraph xwpfParagraph2 = cell.addParagraph();
        XWPFParagraph xwpfParagraph3 = cell.addParagraph();
        XWPFParagraph xwpfParagraph4 = cell.addParagraph();
        XWPFRun runOne = createRunV2(xwpfParagraph1, text);
        runOne.setText("评估方案名称：" + schemaEntity.getName());
        XWPFRun runThree = createRunV2(xwpfParagraph2, text);
        String result = "";
        String var = "";
        // key pid value indicatorEntity
        Map<String, String> indicatorInfoMap = Maps.newHashMap();

        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            indicatorInfoMap.put(indicatorEntity.getId(), indicatorEntity.getName());
        }
        Map<String, List<IndicatorEntity>> collect = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        List<String> list = Lists.newArrayList();
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            // 效能指标  -》 效能指标名称（getNormalizedValue）、效能指标名称（getNormalizedValue）
            // 计算结果评价值 （效能）-》 第一个的评价结果、第二个的评价结果、
            String id = indicatorEntity.getId();
            if (collect.containsKey(id)) {
                continue;
            }
            // 叶子节点
            IndicatorValuesEntity valuesEntity = indicatorValuesEntityMap.get(id);
            if (Objects.isNull(valuesEntity)) {
                continue;
            }
            String pid = indicatorEntity.getPid();
            if (!list.contains(pid)) {
                indicatorExportNodes.add(new IndicatorExportNode(pid, indicatorInfoMap.get(pid), "0", -1.0));
                list.add(indicatorEntity.getPid());
            }
            //叶子节点数据构造完成
            indicatorExportNodes.add(new IndicatorExportNode(indicatorEntity.getId(), indicatorEntity.getName(), indicatorEntity.getPid(), Double.parseDouble(valuesEntity.getNormalizedValue())));
            result = StringUtils.isEmpty(valuesEntity.getNormalizedValue()) ? result : result + "、" + valuesEntity.getNormalizedValue();
            var = StringUtils.isEmpty(valuesEntity.getStandardLabelName()) ? var : var + "、" + valuesEntity.getStandardLabelName();
        }
        runThree.setText("评估计算结果：" + result);
        XWPFRun runFour = createRunV2(xwpfParagraph3, text);
        runFour.setText("计算结果评价结果：" + var);
        XWPFRun runFive = createRunV2(xwpfParagraph4, text);
        runFive.setText("评估结论：" + (StringUtils.isEmpty(schemaEntity.getSummary()) ? "" : schemaEntity.getSummary()));

        //段落内容
        TextStyleBean subTitle4 = new TextStyleBean();
        subTitle4.setContent("1." + titleNum + ".1.2 " + "评估结果详情");
        subTitle4.setColor("000000");
        DocxHelper.createTitle(word, null, 3, subTitle4);

        for (IndicatorExportNode node : indicatorExportNodes) {
            String id = node.getId();
            IndicatorValuesEntity valuesEntity = indicatorValuesEntityMap.get(id);
            if (!Objects.isNull(valuesEntity) && StringUtils.isNotEmpty(valuesEntity.getNormalizedValue())) {
                node.setResult(Double.parseDouble(valuesEntity.getNormalizedValue()));
            }
        }
        XWPFRun var1 = createRun(word, text);
        var1.setText("（1）综合评估计算结果");
        setMergeTable(word, indicatorExportNodes);
        // todo 设置每个中间指标的截图

        //写标题和添加图片 图片只有中间指标才有 柱状图 折线图 饼图  雷达图
        XWPFRun runSix = createRun(word, text);
        runSix.setText("（2）评估计算结果图");

        int j = 0;
        for (IndicatorEntity en : indicatorEntities) {
            if (j < en.getLevel()) {
                j = en.getLevel();
            }
        }

        // 能力 根指标pid =0 二级指标的pid就是根指标的id
//        List<IndicatorEntity> indicatorEntities1 = collect.get(id);
//        Map<String, List<IndicatorEntity>> indicate = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        for (int k = 1; k <= j; k++) {
            String level = "二级";
            if (k == 2) {
                level = "三级";
            }
            if (k == 3) {
                level = "四级";
            }
            XWPFRun runSeven = createRun(word, text);
            runSeven.setText(k + "）" + level + "指标评估结果");
            runSeven.addBreak();//软回车
            ReportImageEffective(word, schemaEntity, level, text);
        }

    }

    private void abilityEvaluateWord(XWPFDocument word, SchemaEntity schemaEntity, int titleNum,
                                     Map<String, IndicatorValuesEntity> indicatorValuesEntityMap,
                                     IndicatorEntity rootEntity, List<IndicatorEntity> indicatorEntities) {
        List<IndicatorExportNode> indicatorExportNodes = Lists.newArrayList();
        // 记录已经添加的效能父节点
        indicatorEntities.forEach(e -> {
            // 效能指标去掉跟指标 保留叶子指标的父节点
            // 取出所有叶子节点 根据叶子节点pid 去构造indicatorExportNode
            // 构造叶子指标父节点信息的时候 需要将他的pid设置”0“

            indicatorExportNodes.add(new IndicatorExportNode(e.getId(), e.getName(), e.getPid(), -1.0));
        });
        String tableName;
        // 能力评估
        tableName = "能力评估结果";
        //三级标题
        TextStyleBean subTitle2 = new TextStyleBean();
        subTitle2.setContent("1." + titleNum + ".1 " + tableName);
        subTitle2.setColor("000000");
        DocxHelper.createTitle(word, null, 3, subTitle2);

        //段落内容
        TextStyleBean subTitle3 = new TextStyleBean();
        subTitle3.setContent("1." + titleNum + ".1.1 " + "评估结果摘要");
        subTitle3.setColor("000000");
        DocxHelper.createTitle(word, null, 3, subTitle3);
        XWPFTable table = word.createTable(1, 1);
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("8200"));
        XWPFTableRow row = table.getRow(0);
        TextStyleBean text = new TextStyleBean();
        XWPFTableCell cell = row.getCell(0);
        XWPFParagraph xwpfParagraph1 = cell.addParagraph();
        XWPFParagraph xwpfParagraph2 = cell.addParagraph();
        XWPFParagraph xwpfParagraph3 = cell.addParagraph();
        XWPFParagraph xwpfParagraph4 = cell.addParagraph();
        XWPFParagraph xwpfParagraph5 = cell.addParagraph();
        XWPFRun runOne = createRunV2(xwpfParagraph1, text);
        runOne.setText("评估方案名称：" + schemaEntity.getName());
        XWPFRun runTwo = createRunV2(xwpfParagraph2, text);
        runTwo.setText("评估计算方法模型：" + EvaluationType.getTypeByName(schemaEntity.getEvaluationMethod()).getChName());
        XWPFRun runThree = createRunV2(xwpfParagraph3, text);
        IndicatorValuesEntity entity = indicatorValuesEntityMap.get(rootEntity.getId());
        String result = "";
        String var = "";
        if (!Objects.isNull(entity)) {
            // 效能指标  -》 效能指标名称（getNormalizedValue）、效能指标名称（getNormalizedValue）
            // 计算结果评价值 （效能）-》 第一个的评价结果、第二个的评价结果、
            result = StringUtils.isEmpty(entity.getNormalizedValue()) ? result : entity.getNormalizedValue();
            var = StringUtils.isEmpty(entity.getStandardLabelName()) ? result : entity.getStandardLabelName();
        }
        runThree.setText("评估计算结果：" + result);
        XWPFRun runFour = createRunV2(xwpfParagraph4, text);
        runFour.setText("计算结果评价结果：" + var);
        XWPFRun runFive = createRunV2(xwpfParagraph5, text);
        runFive.setText("评估结论：" + (StringUtils.isEmpty(schemaEntity.getSummary()) ? "" : schemaEntity.getSummary()));

        //段落内容
        TextStyleBean subTitle4 = new TextStyleBean();
        subTitle4.setContent("1." + titleNum + ".1.2 " + "评估结果详情");
        subTitle4.setColor("000000");
        DocxHelper.createTitle(word, null, 3, subTitle4);

        for (IndicatorExportNode node : indicatorExportNodes) {
            String id = node.getId();
            IndicatorValuesEntity valuesEntity = indicatorValuesEntityMap.get(id);
            if (!Objects.isNull(valuesEntity) && StringUtils.isNotEmpty(valuesEntity.getNormalizedValue())) {
                node.setResult(Double.parseDouble(valuesEntity.getNormalizedValue()));
            }
        }
        XWPFRun var1 = createRun(word, text);
        var1.setText("（1）综合评估计算结果");
        setMergeTable(word, indicatorExportNodes);
        // todo 设置每个中间指标的截图

        //写标题和添加图片 图片只有中间指标才有 柱状图 折线图 饼图  雷达图
        XWPFRun runSix = createRun(word, text);
        runSix.setText("（2）评估计算结果图");

        int j = 0;
        for (IndicatorEntity en : indicatorEntities) {
            if (j < en.getLevel()) {
                j = en.getLevel();
            }
        }

        // 能力 根指标pid =0 二级指标的pid就是根指标的id
//        List<IndicatorEntity> indicatorEntities1 = collect.get(id);
//        Map<String, List<IndicatorEntity>> indicate = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        for (int k = 1; k <= j; k++) {
            String level = "二级";
            if (k == 2) {
                level = "三级";
            }
            if (k == 3) {
                level = "四级";
            }
            XWPFRun runSeven = createRun(word, text);
            runSeven.setText(k + "）" + level + "指标评估结果");
            runSeven.addBreak();//软回车
            ReportImage(word, schemaEntity, level, text);
        }

    }

    /**
     * 获取图片对象st
     *
     * @param name 图片名称
     * @param word
     * @param text
     * @return
     */
    public NodeStyle getSt(String name, XWPFDocument word, TextStyleBean text) {
        //柱状图
        XWPFRun run = createRun(word, text);
        run.setText(name);
        NodeStyle st = new NodeStyle();
        st.align = "center";
        st.height = 300;
        st.width = 500;
        run.addBreak();
        return st;
    }

    /**
     * @param word         XWPFDocument 对象
     * @param st           NodeStyle 图片对象
     * @param schemaEntity 方案实体
     * @param name         图片的名称
     */
    public void addImage(XWPFDocument word, NodeStyle st, SchemaEntity schemaEntity, String indicatorName, String name) {
        try {
            //basicFilePath + FileUtil.INDICATOR_IMAGE + sid + indicatorName + ".jpg"
            DocxHelper.addImage2Word(word, null, st
                    , new File(basicFilePath + com.yw.common.utils.FileUtil.INDICATOR_IMAGE + schemaEntity.getId() + schemaEntity.getId() + "-" + indicatorName + "指标评估结果" + "-" + name + ".jpg"), 500);
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 导出图片方法 柱状图  折线图 饼图 雷达图   柱状图的保存方式和其他的图的格式不一样
     *
     * @param word            XWPFDocument
     * @param schemaEntity    方案实体
     * @param indicatorEntity 指标实体
     * @param text            文本对象
     */
    public void ReportImage(XWPFDocument word, SchemaEntity schemaEntity,
                            String indicatorEntity, TextStyleBean text) {
        //柱状图
        NodeStyle st1 = getSt("柱状图:", word, text);
        addImage(word, st1, schemaEntity, indicatorEntity, "柱状图");
        //折线图
        NodeStyle st2 = getSt("折线图:", word, text);
        addImage(word, st2, schemaEntity, indicatorEntity, "线性图");
//        //饼图
//        NodeStyle st3 = getSt("饼图:", word, text);
//        addImage(word, st3, schemaEntity, indicatorEntity, "饼状图");
        //雷达图
        NodeStyle st4 = getSt("雷达图:", word, text);
        addImage(word, st4, schemaEntity, indicatorEntity, "雷达图");

    }

    /**
     * 效能评估没有饼图
     *
     * @param word
     * @param schemaEntity
     * @param indicatorEntity
     * @param text
     */
    public void ReportImageEffective(XWPFDocument word, SchemaEntity schemaEntity,
                                     String indicatorEntity, TextStyleBean text) {
        //柱状图
        NodeStyle st1 = getSt("柱状图:", word, text);
        addImage(word, st1, schemaEntity, indicatorEntity, "柱状图");
        //折线图
        NodeStyle st2 = getSt("折线图:", word, text);
        addImage(word, st2, schemaEntity, indicatorEntity, "线性图");
        //饼图
//        NodeStyle st3 = getSt("饼图:", word, text);
//        addImage(word, st3, schemaEntity, indicatorEntity, "饼状图");
        //雷达图
        NodeStyle st4 = getSt("雷达图:", word, text);
        addImage(word, st4, schemaEntity, indicatorEntity, "雷达图");

    }

    /**
     * 样本数据结构数据设置
     *
     * @param word         document
     * @param schemaEntity 方案信息
     * @param titleNum     标志
     * @param sceneIds     样本集合
     */
    private void setEstimateSceneTable(XWPFDocument word, SchemaEntity schemaEntity, int titleNum, List<String> sceneIds, List<IndicatorEntity> indicatorEntities) {
        String sid = schemaEntity.getId();
        //段落内容
        TextStyleBean text = new TextStyleBean();
        // 缩进
        text.setFontsize(12);
        text.setColor("000000");
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent("1." + titleNum + " 评估样本结构");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);
        List<IndicatorExportNode> indicatorExportNodes = Lists.newArrayList();
        int number = 0;

        number = setIndicatorCalcorValueAndNumber(indicatorEntities, null, sid, indicatorExportNodes, number);

        XWPFRun runOne = createRun(word, text);
        runOne.setText("  通过表格展示评估指标计算需要的所有数据字段名称。");
        XWPFRun runTwo = createRun(word, text);

        runTwo.setText("  体系作战能力评估指标计算需要采集" + number + "项数据，具体如下表所示: ");
        // 添加表格
        createDocumentTable(indicatorExportNodes, Lists.newArrayList("指标名称", "指标计算需要的数据字段（采集项）"), word);
    }

    public int setIndicatorCalcorValueAndNumber(List<IndicatorEntity> indicatorEntities, List<String> calModeIds, String sid, List<IndicatorExportNode> indicatorExportNodes, int number) {
        Map<String, String> indicatorNameMaps = Maps.newHashMap();
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            indicatorNameMaps.put(indicatorEntity.getId(), indicatorEntity.getName());
        }
        Set<String> overConstructData = Sets.newHashSet();
        // 表格构造
        //表的行列数
//        List<CalcorEntity> calcorEntities = calcorRepository.findAll(Example.of(CalcorEntity.builder().sid(sid).build()));
        List<CalcorEntity> calcorEntities = calcorService.list(new QueryWrapper<CalcorEntity>().setEntity(CalcorEntity.builder().sid(sid).build()));
        //获取当前场景关联的方法模型
        for (CalcorEntity calcorEntity : calcorEntities) {
            String colName = calcorEntity.getColName();
            if (StringUtils.isNotEmpty(colName) && indicatorNameMaps.containsKey(calcorEntity.getIndicatorId())) {
                if (CollUtil.isNotEmpty(calModeIds) && !calModeIds.contains(calcorEntity.getCalcModuleId())) {
                    continue;
                }
                String indicatorId = calcorEntity.getIndicatorId();
                if (!overConstructData.contains(indicatorId)) {
                    indicatorExportNodes.add(new IndicatorExportNode(indicatorId, indicatorNameMaps.get(indicatorId), "0", -1.0));
                    overConstructData.add(indicatorId);
                }
                indicatorExportNodes.add(new IndicatorExportNode(calcorEntity.getId(), colName, indicatorId, -1.0));
                number++;
            }
        }
        return number;
    }

    public void createDocumentTable(List<IndicatorExportNode> indicatorExportNodes, List<String> tableHead, XWPFDocument word) {
        List<com.yw.common.utils.Node> nodes = com.yw.common.utils.BuildTree.buildTree(indicatorExportNodes);
        com.yw.common.utils.BuildTree.setLevel(nodes);
        List<com.yw.common.utils.Node> leafNode = com.yw.common.utils.BuildTree.getLeafNode(nodes, new ArrayList<>());
        //表的行列数
        int rowSize = leafNode.size();
        int columnSize = tableHead.size();

        XWPFTable table = word.createTable(rowSize + 1, columnSize);
        table.setTableAlignment(TableRowAlign.CENTER);
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("9000"));
        CTString ctString = tblPr.addNewTblStyle();
        ctString.setVal("StyledTable");
        CTTblLayoutType t = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        t.setType(STTblLayoutType.FIXED);

        List<XWPFTableRow> rows = table.getRows();

        //表头
        XWPFTableRow header = rows.get(0);
        for (int i = 0; i < columnSize; i++) {
            XWPFTableCell cell = header.getCell(i);
            XWPFParagraph p = cell.addParagraph();
            XWPFRun run = p.createRun();
            run.setBold(true);
            run.setText(tableHead.get(i));
        }

        // 表数据
        setTableRowNum(indicatorExportNodes, nodes);

        dfsSetTableCellText(nodes, rows, table);
    }

    /**
     * 设置评估指标
     *
     * @param word         document
     * @param schemaEntity 方案对象
     * @param titleNum     等级
     */
    private void setEstimateIndicator(XWPFDocument word, SchemaEntity schemaEntity, int titleNum, List<IndicatorExportNode> indicatorExportNodes) {
        String sid = schemaEntity.getId();
        //段落内容
        TextStyleBean text = new TextStyleBean();
        // 缩进
        text.setFontsize(12);
        text.setColor("000000");
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent("1." + titleNum + " 评估指标体系");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);
        // 添加表格
        XWPFRun runOne = createRun(word, text);
        runOne.setText("  通过表格和树形图展示评估指标体系及其映射关系。");
        XWPFRun runTwo = createRun(word, text);
        runTwo.setText("  体系作战能力评估指标体系表如下所示: ");

        setSchemaIndicatorTableAndImageInfo(word, indicatorExportNodes, "  体系作战能力评估指标体系树形图如下所示: ", sid, text);
    }

    public void setSchemaIndicatorTableAndImageInfo(XWPFDocument word, List<IndicatorExportNode> indicatorExportNodes, String content, String sid, TextStyleBean text) {
        // 表格数据
        setMergeTable(word, indicatorExportNodes);

        XWPFRun run = createRun(word, text);
        run.setText(content);
//        run.addBreak();
        //图片
        NodeStyle st = new NodeStyle();
        st.align = "center";
        st.height = 400;
        st.width = 500;
        try {
            DocxHelper.addImage2Word(word, null, st
                    , new File(basicFilePath + com.yw.common.utils.FileUtil.INDICATOR_IMAGE + sid + ".jpg"), 500);
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 创建段落
     *
     * @param document document
     * @param text     文本
     * @return
     */
    public XWPFRun createRun(XWPFDocument document, TextStyleBean text) {
        XWPFParagraph paragraph = createParagraph(document, text);
        XWPFRun run = paragraph.createRun();
        run.setColor(text.getColor());
        run.setFontSize(12);
        run.setBold(text.isBold());
        run.setItalic(text.isItalic());
        run.setFontFamily(text.getFontFamily());
        if (text.getFontFamily() != null)
            run.setFontFamily(text.getFontFamily());
        if (text.isUnderline()) {
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
        return run;

    }

    /**
     * 创建段落
     *
     * @param paragraph document
     * @param text      文本
     * @return
     */
    public XWPFRun createRunV2(XWPFParagraph paragraph, TextStyleBean text) {
        XWPFRun run = paragraph.createRun();
        run.setColor(text.getColor());
        run.setFontSize(12);
        run.setBold(text.isBold());
        run.setItalic(text.isItalic());
        run.setFontFamily(text.getFontFamily());
        if (text.getFontFamily() != null)
            run.setFontFamily(text.getFontFamily());
        if (text.isUnderline()) {
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
        return run;

    }


    private XWPFParagraph createParagraph(XWPFDocument word, TextStyleBean text) {
        XWPFParagraph paragraph = word.createParagraph();
        paragraph.setAlignment(text.getAlign());
        // 首行缩进
        paragraph.setFirstLineIndent(400);

        paragraph.setAlignment(text.getAlign());
        return paragraph;
    }

    /**
     * 设置评估背景
     *
     * @param word         document对象
     * @param schemaEntity 方案信息
     * @param titleNum     titleNum
     */
    private void setEstimateBackGround(XWPFDocument word, SchemaEntity schemaEntity, int titleNum) {
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent("1." + titleNum + " 评估方案描述");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);

        //段落内容
        TextStyleBean text = new TextStyleBean();
        // 缩进
        text.setFontsize(12);
        text.setColor("000000");
        String background = "  主要包括评估方案创建时间、评估对象、评估目的与背景，以文本形式展示: ";
        text.setContent(background);
        DocxHelper.addText(null, word, text);
        setSchemaDescs(word, schemaEntity, text);
    }

    public void setSchemaDescs(XWPFDocument word, SchemaEntity schemaEntity, TextStyleBean text) {
        XWPFTable table = word.createTable(1, 1);
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("8200"));
        XWPFTableRow row = table.getRow(0);
        XWPFTableCell cell = row.getCell(0);
        XWPFParagraph xwpfParagraph1 = cell.addParagraph();
        XWPFParagraph xwpfParagraph2 = cell.addParagraph();
        XWPFParagraph xwpfParagraph3 = cell.addParagraph();
        XWPFParagraph xwpfParagraph4 = cell.addParagraph();
        XWPFParagraph xwpfParagraph5 = cell.addParagraph();
        XWPFRun runOne = createRunV2(xwpfParagraph1, text);
        runOne.setText("评估方案名称：" + schemaEntity.getName());
        XWPFRun runTwo = createRunV2(xwpfParagraph2, text);
        runTwo.setText("评估方案创建时间：" + schemaEntity.getCreateDate());
        XWPFRun runThree = createRunV2(xwpfParagraph3, text);
        runThree.setText("评估任务描述：" + (StringUtils.isEmpty(schemaEntity.getDescribes()) ? "" : schemaEntity.getDescribes()));
        XWPFRun runFour = createRunV2(xwpfParagraph4, text);
        runFour.setText("评估目的：" + (StringUtils.isEmpty(schemaEntity.getAssessTarget()) ? "" : schemaEntity.getAssessTarget()));
        XWPFRun runFive = createRunV2(xwpfParagraph5, text);
        runFive.setText("评估背景：" + (StringUtils.isEmpty(schemaEntity.getBackground()) ? "" : schemaEntity.getBackground()));
    }

    @Override
    public void exportV2(HttpServletResponse response, ContributionRateExportDTO dto) {
        String sids = dto.getSid();
        if (StringUtils.isEmpty(sids)) {
            throw new BusinessException("方案不存在");
        }
        exportOneSchemaV2(response, dto);
    }

    @Override
    public void exportV3(HttpServletResponse response, ExportDTO dto) {
        String sids = dto.getSid();
        if (StringUtils.isEmpty(sids)) {
            throw new BusinessException("方案不存在");
        }
        exportOneSchemaV3(response, dto);
    }

    /**
     * 导出报告
     *
     * @param dto      导出配置dto
     * @param response http response
     */
    @Override
    public void export(HttpServletResponse response, ExportDTO dto) {
        String experimentId = dto.getExperimentId();
        String sids = dto.getSid();
        if (StringUtils.isEmpty(sids)) {
            throw new BusinessException("方案不存在");
        }
        List<String> schemaIds = com.yw.common.utils.ArrayUtils.stringToList(sids);
        if (StringUtils.isEmpty(experimentId) && CollUtil.isNotEmpty(schemaIds)) {
            exportOneSchema(response, dto);
            return;
        }
//        Optional<ExperimentPreceptEntity> repositoryById = experimentPreceptRepository.findById(experimentId);
        Optional<ExperimentPreceptEntity> repositoryById = Optional.ofNullable(experimentPreceptService.getById(experimentId));
        if (!repositoryById.isPresent()) {
            throw new BusinessException("方案消息不存在");
        }
        // 获取当前任务分类 1.获取所有任务id

        // 配置导出信息
        Map<String, ExportDTO> exportDTOMap = Maps.newHashMap();
        // key 方案id value 样本id
        Map<String, List<String>> scenceIdMap = Maps.newHashMap();
        schemaIds.stream().forEach(var1 -> {
            exportDTOMap.put(var1, dto);
            scenceIdMap.put(var1, sceneService.findIdsBySid(var1));
        });

        List<SchemaEntity> schemaEntities = schemaService.findListByIds(schemaIds);
        if (schemaIds.size() != schemaEntities.size() || schemaEntities.size() == 0) {
            throw new BusinessException("方案不存在");
        }
        //2.查询对应分类信息 3.分类分组
//        List<SchemaCataEntity> cataEntities = iSchemaCataRepository.findAll();
        List<SchemaCataEntity> cataEntities = schemaCataService.list();
        // key 任务分类id  value 任务分类名称
        Map<String, String> cataNameMap = Maps.newHashMap();
        if (CollUtil.isEmpty(cataEntities)) {
            cataNameMap.put("0", "默认分类");
        } else {
            for (SchemaCataEntity entity : cataEntities) {
                cataNameMap.put(entity.getId(), entity.getCataName());
            }
        }
        //获取默认分类id
        Map<String, List<SchemaEntity>> groupSchemaCataMap = Maps.newLinkedHashMap();
        schemaEntities.stream().forEach(var2 -> {
            String schemaCata = var2.getSchemaCata();
            if (StringUtils.isEmpty(schemaCata)) {
                schemaCata = "0";
            }
            List<SchemaEntity> entityList = groupSchemaCataMap.get(schemaCata);
            if (CollUtil.isEmpty(entityList)) {
                entityList = Lists.newArrayList();
            }
            entityList.add(var2);
            groupSchemaCataMap.put(schemaCata, entityList);
        });
        //todo 多个任务导出 编写word文档
        XWPFDocument word = new XWPFDocument();
        // todo 2022 - 03 -01 在封面展示方案描述（没有则不展示）
        setCover(word, null, experimentId);
        for (Map.Entry<String, List<SchemaEntity>> groupMap : groupSchemaCataMap.entrySet()) {
            //设置任务类别一级标题标题
            String key = groupMap.getKey();
            //标题
//            word.createParagraph();
//            DocxHelper.createHeadingTitles(word);
//            TextStyleBean style = new TextStyleBean();
//            style.setColor("000000");
//            style.setContent(cataNameMap.get(key));
//            style.setAlign(ParagraphAlignment.CENTER);
//            DocxHelper.createTitle(word, null, 1, style);
            List<SchemaEntity> value = groupMap.getValue();
            for (SchemaEntity schemaEntity : value) {
                //三级标题序号
                int titleNum = 1;
                String sid = schemaEntity.getId();
                // 设置二级标题
                ExportDTO exportDTO = exportDTOMap.get(sid);
                TextStyleBean subTitle = new TextStyleBean();
                subTitle.setContent(schemaEntity.getName());
                subTitle.setColor("000000");
                DocxHelper.createTitle(word, null, 1, subTitle);
                if ("1".equals(exportDTO.getModel())) {
                    if (1 == exportDTO.getShowMethod()) {
                        setArm(word, schemaEntity, titleNum);
                        titleNum++;
                    }
                    if (1 == exportDTO.getShowBackground()) {
                        setIndicator(word, sid, titleNum);
                        titleNum++;
                    }
                    if (1 == exportDTO.getShowArm()) {
                        setMethod(word, sid, titleNum);
                        titleNum++;
                    }
                    if (1 == exportDTO.getShowIndicator()) {
                        // todo 暂不需要
//                        setBackGround(word, schemaEntity, titleNum);
//                        titleNum++;
                    }
                    if (1 == exportDTO.getShowResult()) {
                        setResult(word, scenceIdMap.get(sid), titleNum, exportDTO.getTransValue());
                        titleNum++;
                    }
                    if (1 == exportDTO.getShowAdvice()) {
                        setAdvice(word, titleNum, scenceIdMap.get(sid));
                    }
                } else {
                    setCustom(word, exportDTO.getSection(), titleNum);
                }
            }
        }
        //设置检验科目及pg指标对照表
        setIndicatorTable(word, 1, Lists.newArrayList(), schemaEntities);
        doExport(response, schemaEntities.get(0), word, dto.getDocType(), repositoryById.get().getName(), null);
    }

    /**
     * word添加合并表格
     *
     * @param table
     * @param indicatorExportNodes
     */
    private void setMergeTableV2(XWPFTable table, List<IndicatorExportNode> indicatorExportNodes) {
        List<com.yw.common.utils.Node> nodes = com.yw.common.utils.BuildTree.buildTree(indicatorExportNodes);
        com.yw.common.utils.BuildTree.setLevel(nodes);
        List<com.yw.common.utils.Node> leafNode = com.yw.common.utils.BuildTree.getLeafNode(nodes, new ArrayList<>());
        //表的行列数
        int rowSize = leafNode.size();
        int columnSize = com.yw.common.utils.BuildTree.maxLevel(nodes, 0);
        for (int i = 0; i < rowSize + 1; i++) {
            XWPFTableRow tabRow = (table.getRow(i) == null) ? table.createRow() : table.getRow(i);
            for (int k = 0; k < columnSize; k++) {
                if (tabRow.getCell(k) == null) {
                    tabRow.createCell();
                }
            }
        }
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("9000"));
        CTString ctString = tblPr.addNewTblStyle();
        ctString.setVal("StyledTable");
        CTTblLayoutType t = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        t.setType(STTblLayoutType.FIXED);

        List<XWPFTableRow> rows = table.getRows();

        //表头
        XWPFTableRow header = rows.get(0);
        for (int i = 0; i < columnSize; i++) {
            XWPFTableCell cell = header.getCell(i);
            XWPFParagraph p = cell.addParagraph();
            XWPFRun run = p.createRun();
            run.setBold(true);
            run.setText((i + 1) + "级指标");
        }

        setTableRowNum(indicatorExportNodes, nodes);

        dfsSetTableCellText(nodes, rows, table);
    }

    /**
     * word添加合并表格
     *
     * @param word
     * @param indicatorExportNodes
     */
    private void setMergeTable(XWPFDocument word, List<IndicatorExportNode> indicatorExportNodes) {
        List<com.yw.common.utils.Node> nodes = com.yw.common.utils.BuildTree.buildTree(indicatorExportNodes);
        com.yw.common.utils.BuildTree.setLevel(nodes);
        List<com.yw.common.utils.Node> leafNode = com.yw.common.utils.BuildTree.getLeafNode(nodes, new ArrayList<>());
        //表的行列数
        int rowSize = leafNode.size();
        int columnSize = com.yw.common.utils.BuildTree.maxLevel(nodes, 0);

        XWPFTable table = word.createTable(rowSize + 1, columnSize);

        CTTblPr tblPr = table.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("9000"));
        CTString ctString = tblPr.addNewTblStyle();
        ctString.setVal("StyledTable");
        CTTblLayoutType t = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        t.setType(STTblLayoutType.FIXED);

        List<XWPFTableRow> rows = table.getRows();

        //表头
        XWPFTableRow header = rows.get(0);
        for (int i = 0; i < columnSize; i++) {
            XWPFTableCell cell = header.getCell(i);
            XWPFParagraph p = cell.addParagraph();
            XWPFRun run = p.createRun();
            run.setBold(true);
            run.setText((i + 1) + "级指标");
        }

        setTableRowNum(indicatorExportNodes, nodes);

        dfsSetTableCellText(nodes, rows, table);
    }

    private void dfsSetTableCellText(List<? extends com.yw.common.utils.Node> nodes, List<XWPFTableRow> rows, XWPFTable table) {
        for (int i = 0; i < nodes.size(); i++) {
            com.yw.common.utils.Node node = nodes.get(i);
            IndicatorExportNode indicatorExportNode = (IndicatorExportNode) node;
            XWPFTableRow row = rows.get(indicatorExportNode.getTableRowNumStart());
            XWPFTableCell cell = row.getCell(indicatorExportNode.getRank() - 1);
            //单元格没有值时不做处理，避免空指针
            if (null == cell) {
                continue;
            }
            //垂直居中
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            CTTc ctTc = cell.getCTTc();
            CTTcPr ctTcPr = ctTc.addNewTcPr();
            ctTcPr.addNewVAlign().setVal(STVerticalJc.CENTER);
            ctTc.getPList().get(0).addNewPPr().addNewJc().setVal(STJc.CENTER);

            XWPFParagraph p = cell.addParagraph();
            XWPFRun run = p.createRun();
            p.setAlignment(ParagraphAlignment.CENTER);
            run.setText((indicatorExportNode.getName()));
            if (indicatorExportNode.getResult() != null) {
                XWPFParagraph p2 = cell.addParagraph();
                XWPFRun run2 = p2.createRun();
                p2.setAlignment(ParagraphAlignment.CENTER);
                if (indicatorExportNode.getResult() != -1) {
                    run2.setText("(" + (indicatorExportNode.getResult()) + ")");
                }
            }

            mergeCellsVertically(table, indicatorExportNode.getRank() - 1, indicatorExportNode.getTableRowNumStart(), indicatorExportNode.getTableRowNumEnd() + 1);

            if (node.getChildren() != null && node.getChildren().size() > 0) {
                dfsSetTableCellText(node.getChildren(), rows, table);
            }
        }
    }

    /**
     * 给树节点设置word文档table行数标识
     *
     * @param nodes
     * @Author zl
     */

    private void setTableRowNum(List<IndicatorExportNode> allNodes, List<com.yw.common.utils.Node> nodes) {
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                int size = 0;
                com.yw.common.utils.Node node = nodes.get(i);
                //找到父节点
                Optional<? extends Node> first = allNodes.stream().filter(
                        e -> Objects.equals(e.getId(), node.getParentId())
                ).findFirst();
                if (first.isPresent()) {
                    IndicatorExportNode indicatorExportNode = (IndicatorExportNode) first.get();
                    size = indicatorExportNode.getTableRowNumStart() - 1;
                }

                IndicatorExportNode indicatorExportNode = (IndicatorExportNode) node;
                int leafNodeSize = com.yw.common.utils.BuildTree.getLeafNodeSize(Collections.singletonList(node), 0);
                if (i != 0) {
                    IndicatorExportNode before = (IndicatorExportNode) nodes.get(i - 1);
                    indicatorExportNode.setTableRowNumStart(before.getTableRowNumEnd() + 1);
                    indicatorExportNode.setTableRowNumEnd(before.getTableRowNumEnd() + leafNodeSize);
                } else {
                    indicatorExportNode.setTableRowNumStart(1 + size);
                    indicatorExportNode.setTableRowNumEnd(leafNodeSize + size);
                }
                if (node.getChildren() != null && node.getChildren().size() > 0) {
                    setTableRowNum(allNodes, node.getChildren());
                }
            }
        }
    }

    /**
     * table合并单元格--列合并
     *
     * @param table
     * @param col
     * @param from
     * @param to
     */
    private void mergeCellsVertically(XWPFTable table, int col, int from, int to) {
        for (int rowIndex = from; rowIndex < to; rowIndex++) {
            XWPFTableCell cell = table.getRow(rowIndex).getCell(col);
            if (rowIndex == from) {
                cell.getCTTc().addNewTcPr().addNewVMerge().setVal(STMerge.RESTART);
            } else {
                cell.getCTTc().addNewTcPr().addNewVMerge().setVal(STMerge.CONTINUE);
            }
        }
    }

    public static void main(String[] args) {
        try {
            XWPFDocument document = new XWPFDocument(new FileInputStream(new File("D:\\评估报告模板(2).docx")));
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                List<XWPFRun> runs = paragraph.getRuns();
                for (XWPFRun xwpfRun : runs) {
                    XWPFTable tableArray = document.getTableArray(0);
                    System.err.println(xwpfRun.getText(xwpfRun.getTextPosition()));
                    if (StringUtils.isNotEmpty(xwpfRun.getText(xwpfRun.getTextPosition())) && xwpfRun.getText(xwpfRun.getTextPosition()).equals("使命任务")) {
                        IndicatorExportNode node1 = new IndicatorExportNode("1", "1-1指标", "0", 1.0);
                        IndicatorExportNode node2 = new IndicatorExportNode("2", "1-2指标", "0", 1.0);
                        IndicatorExportNode node3 = new IndicatorExportNode("3", "2-1指标", "1", 1.0);
                        IndicatorExportNode node4 = new IndicatorExportNode("4", "2-2指标", "1", 1.0);
                        IndicatorExportNode node5 = new IndicatorExportNode("5", "3-1指标", "3", 1.0);
                        IndicatorExportNode node6 = new IndicatorExportNode("6", "3-2指标", "3", 1.0);
                        IndicatorExportNode node7 = new IndicatorExportNode("7", "3-3指标", "4", 1.0);
                        IndicatorExportNode node8 = new IndicatorExportNode("8", "3-4指标", "4", 1.0);
                        IndicatorExportNode node9 = new IndicatorExportNode("9", "3-5指标", "4", 1.0);
                        List<IndicatorExportNode> indicatorExportNodes = new ArrayList<>();
                        indicatorExportNodes.add(node1);
                        indicatorExportNodes.add(node2);
                        indicatorExportNodes.add(node3);
                        indicatorExportNodes.add(node4);
                        indicatorExportNodes.add(node5);
                        indicatorExportNodes.add(node6);
                        indicatorExportNodes.add(node7);
                        indicatorExportNodes.add(node8);
                        indicatorExportNodes.add(node9);

                        EvaluationReportServiceImpl evaluationReportService = new EvaluationReportServiceImpl(null, null, null, null, null, null, null, null);
                        evaluationReportService.setMergeTableV2(tableArray, indicatorExportNodes);
                    }
                }
            }
            // 本地导出
            FileOutputStream fos;
            try {
                fos = new FileOutputStream(new File("D:/bbb.docx"));
                ByteArrayOutputStream ops = new ByteArrayOutputStream();
                document.write(ops);
                fos.write(ops.toByteArray());
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.err.println(document);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * //todo
     * 此种导出后续需要重构
     * part1:导出world，直接导出
     * part2:导出pdf/html,需要先在缓存文件夹导出world文件，然后将world文件转换成相应的类型后，导出给前端
     * 导出html时可能会有图片，需压缩html文件夹 → html.zip 导出zip给前端
     * 缓存文件目录结构：cache:
     * demo1.docx
     * html.zip
     * pdf:
     * demo1.pdf
     * html:
     * demo1.html
     * (demo1.001.png)
     *
     * @param response
     * @param schemaEntity
     * @param word
     * @param docType
     * @return
     * @Author 刘乐法
     */
    public void doExport(HttpServletResponse response, SchemaEntity schemaEntity, XWPFDocument word, String docType, String name, ContributionRateExportDTO dto) {
        String sid = schemaEntity.getId();
        if ("world".equals(docType)) {
            //直接导出docx
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                word.write(bos);
                com.yw.common.utils.FileUtil.downloadFileByte(response, bos.toByteArray(), (StringUtils.isEmpty(name) ? schemaEntity.getName() : name) + ".docx");
            } catch (Exception e) {
                throw new BusinessException(e.getMessage());
            }
        } else {
            final String cachePathWorld = basicFilePath + com.yw.common.utils.FileUtil.CACHE;
            final String cachePath = basicFilePath + com.yw.common.utils.FileUtil.CACHE;

            //判断world缓存路径是否存在
            File file = new File(cachePathWorld);
            if (!file.exists()) file.mkdirs();

            try (FileOutputStream fos = new FileOutputStream(cachePathWorld + sid + ".docx")) {
                word.write(fos);//写入文件
                if ("pdf".equals(docType)) {
                    final String cachePathPdf = basicFilePath + com.yw.common.utils.FileUtil.CACHE_PDF;
                    //pdf绝对路径
                    String transPdf = cachePathPdf + sid + ".pdf";
                    //world绝对路径
                    String transWorld = cachePathWorld + sid + ".docx";
                    //pdf缓存路径是否存在
                    File pdf = new File(cachePathPdf);
                    if (!pdf.exists()) pdf.mkdirs();
//                    AsposeUtil.wordToPdf(transWorld, transPdf, 1);
                    com.yw.common.utils.AsposeUtil.wordToPdf(transWorld, transPdf);
                    com.yw.common.utils.FileUtil.downloadFileRes(response, transPdf, (StringUtils.isEmpty(name) ? schemaEntity.getName() : name) + ".pdf");
                } else if ("html".equals(docType)) {
                    final String cachePathHtml = basicFilePath + com.yw.common.utils.FileUtil.CACHE_HTML;
                    File html = new File(cachePathHtml);
                    if (!html.exists()) html.mkdirs();
                    com.yw.common.utils.AsposeUtil.parseWord2Html(cachePathWorld + sid + ".docx", cachePathHtml + schemaEntity.getName() + ".html");
                    //压缩文件夹
                    FileOutputStream fos1 = new FileOutputStream(cachePath + schemaEntity.getName() + ".zip");
                    com.yw.common.utils.ZipUtils.toZip(cachePathHtml, fos1, true);
                    com.yw.common.utils.FileUtil.downloadFileRes(response, cachePath + (StringUtils.isEmpty(name) ? schemaEntity.getName() : name) + ".zip", (StringUtils.isEmpty(name) ? schemaEntity.getName() : name) + ".zip");
                } else if ("excel".equals(docType)) {
                    final String cachePathExcel = basicFilePath + FileUtil.CACHE_EXCEL;
                    //pdf绝对路径
                    String transExcel = cachePathExcel + sid + ".xlsx";
                    //world绝对路径
                    String transWorld = cachePathWorld + sid + ".docx";
                    File pdf = new File(cachePathExcel);
                    if (!pdf.exists()) pdf.mkdirs();
                    ExcelUtils.wordToExcel(transWorld, transExcel, name);
                    com.yw.common.utils.FileUtil.downloadFileRes(response, transExcel, (StringUtils.isEmpty(name) ? schemaEntity.getName() : name) + ".xlsx");
                } else if ("json".equals(docType)) {
                    if (null == dto) {
                        log.info("参数异常");
                        throw new BusinessException("参数异常");
                    }

                    List<String> sceneIds = com.yw.common.utils.ArrayUtils.stringToList(dto.getSceneId());
                    exportJsonV2(response, schemaEntity, sceneIds, dto);
                } else if ("xml".equals(docType)) {
                    if (null == dto) {
                        log.info("参数异常");
                        throw new BusinessException("参数异常");
                    }
                    List<String> sceneIds = com.yw.common.utils.ArrayUtils.stringToList(dto.getSceneId());
                    exportXmlV2(response, schemaEntity, sceneIds, dto);
                } else {
                    throw new BusinessException("导出文件格式不支持");
                }
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                throw new BusinessException(e.getMessage());
            } finally {
                try {
                    com.yw.common.utils.FileUtil.deletefile(cachePath);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * 设置改进结论
     */
    private void setAdvice(XWPFDocument word, int titleNum, List<String> sceneIds) {
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent(titleNum + ". 评估结论");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);
        StringBuilder stringBuilder = new StringBuilder();
        List<SceneEntity> sceneEntityList = sceneService.findByIdIn(sceneIds);

        Optional<SchemaEntity> schemaEntity = schemaService.findById(sceneEntityList.get(0).getSid());
        SchemaEntity entity = schemaEntity.get();
        stringBuilder.append(entity.getName()).append(":").append(entity.getSummary() == null ? "" : entity.getSummary()).append("\n");
        //段落内容
        TextStyleBean text = new TextStyleBean();
        text.setFontsize(12);
        text.setColor("000000");
        text.setContent(stringBuilder.toString());
        DocxHelper.addText(null, word, text);
    }

    /**
     * 设置封面
     */
    private void setCover(XWPFDocument word, SchemaEntity schemaEntity, String experimentId) {
        //增加密级
        XWPFParagraph paragraph0 = word.createParagraph();
        paragraph0.setAlignment(ParagraphAlignment.RIGHT);
        paragraph0.setSpacingBetween(1);
        //创建操作对象run,设置字体为黑体，字号24，加粗
        XWPFRun run0 = paragraph0.createRun();
        run0.setFontFamily("黑体");
        run0.setFontSize(24);
        run0.setBold(true);
//        run0.setText("密级:  ");
        run0.setText(" ");
        //创建paragraph对象,设置对其方式为中间对齐，行间距为1
        XWPFParagraph paragraph = word.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBetween(1);
        //创建操作对象run,设置字体为黑体，字号24，加粗
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("黑体");
        run.setFontSize(24);
        run.setBold(true);
        for (int i = 0; i < 4; i++) {
            run.addBreak();
        }
        run.setText("体系作战能力评估报告");
        run.addBreak();
        int len = 14;
        // 追加描述
        if (StringUtils.isNotEmpty(experimentId)) {
//            Optional<ExperimentPreceptEntity> preceptEntity = experimentPreceptRepository.findById(experimentId);
            Optional<ExperimentPreceptEntity> preceptEntity = Optional.ofNullable(experimentPreceptService.getById(experimentId));
            if (preceptEntity.isPresent() && StringUtils.isNotEmpty(preceptEntity.get().getDescs())) {
                run.addBreak();
                run = paragraph.createRun();
                run.setFontFamily("黑体");
                run.setFontSize(18);
                run.setBold(true);
                String descs = preceptEntity.get().getDescs();
                run.setText(descs);
                len = len - 1;
            }
        }
        //空出行
        for (int i = 0; i < len; i++) {
            run.addBreak();
        }

        run = paragraph.createRun();
        run.setBold(false);
        run.setFontSize(12);
        run.setFontFamily("宋体");
        run.setText("评估机构：");
        run.addBreak();
        LocalDateTime localDateTime = LocalDateTime.now();
        run.setText("评估时间：" + localDateTime.getYear() + "年" + localDateTime.getMonth().getValue() + "月" + localDateTime.getDayOfMonth() + "日");
        run.addBreak();
        run.addBreak();

    }

    /**
     * 添加pg方法
     */
    private void setMethod(XWPFDocument word, String sid, int titleNum) {
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent(titleNum + ". 评估方法");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);

        List<String> methods = exportMapper.findEvaMethodV2(sid);
        //段落内容
        TextStyleBean text = new TextStyleBean();
        text.setFontsize(12);
        text.setColor("000000");
        String m = CollectionUtils.isEmpty(methods) ? "" : StringUtils.join(methods, "、");
        text.setContent("  " + m);
        DocxHelper.addText(null, word, text);
    }

    /**
     * 添加pg目意义
     */
    private void setArm(XWPFDocument word, SchemaEntity schemaEntity, int titleNum) {
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent(titleNum + ". 评估目的");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);

        //段落内容
        TextStyleBean text = new TextStyleBean();
        text.setFontsize(12);
        text.setColor("000000");
        text.setAlign(ParagraphAlignment.LEFT);

        String arm = StringUtils.isBlank(schemaEntity.getAssessTarget()) ? "" : schemaEntity.getAssessTarget();
        text.setContent("  " + arm);

        DocxHelper.addText(null, word, text);
    }

    /**
     * 添加pg目意义
     */
    private void setBackGround(XWPFDocument word, SchemaEntity schemaEntity, int titleNum) {
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent(titleNum + ". 任务背景");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);

        //段落内容
        TextStyleBean text = new TextStyleBean();
        text.setFontsize(12);
        text.setColor("000000");
        String background = StringUtils.isBlank(schemaEntity.getBackground()) ? "" : schemaEntity.getBackground();
        text.setContent("  " + background);
        DocxHelper.addText(null, word, text);
    }

    /**
     * 添加指标体系截图
     */
    private void setIndicator(XWPFDocument word, String sid, int titleNum) {
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent(titleNum + ". 指标体系");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);

        //图片
        NodeStyle st = new NodeStyle();
        st.align = "center";
        st.height = 300;
        st.width = 500;
        try {
            DocxHelper.addImage2Word(word, null, st
                    , new File(basicFilePath + com.yw.common.utils.FileUtil.INDICATOR_IMAGE + sid + ".jpg"), 500);
        } catch (Exception e) {
//            e.printStackTrace();
            throw new BusinessException(e.getMessage());
        }
        // 添加表格
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
        Map<String, IndicatorEntity> idMap = Maps.newHashMap();
        indicatorEntities.forEach(e -> {
            idMap.put(e.getId(), e);
            if (e.getLevel() == null) {
                e.setLevel(0);
            }
        });
        Map<String, List<IndicatorEntity>> collect = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));
        int colNum = indicatorEntities.stream().mapToInt(IndicatorEntity::getLevel).max().getAsInt() + 1;
        List<IndicatorEntity> leftIndicatorList = indicatorEntities.stream().filter(e -> !collect.containsKey(e.getId())).collect(Collectors.toList());
        Collections.sort(leftIndicatorList, (o1, o2) -> {
            if (o1.getPid().hashCode() > o2.getPid().hashCode()) {
                return -1;
            }
            return 1;
        });
        int rowNum = leftIndicatorList.size() + 1;
        XWPFTable xwpfTable = word.createTable(rowNum, colNum);
        CTTblPr tblPr = xwpfTable.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("9000"));
        CTString styleStr = tblPr.addNewTblStyle();
        styleStr.setVal("StyledTable");
        CTTblLayoutType t = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        t.setType(STTblLayoutType.FIXED);
        List<XWPFTableRow> rows = xwpfTable.getRows();
        //合并行单元格的第一个单元格
        CTVMerge startMerge = CTVMerge.Factory.newInstance();
        startMerge.setVal(STMerge.RESTART);
        //合并行单元格的第一个单元格之后的单元格
        CTVMerge endMerge = CTVMerge.Factory.newInstance();
        endMerge.setVal(STMerge.CONTINUE);
        for (int i = 0; i < rowNum; i++) {
            XWPFTableRow row = rows.get(i);
            CTTrPr trPr = row.getCtRow().addNewTrPr();
            //表格标题
            if (i == 0) {
                CTHeight ht = trPr.addNewTrHeight();
                ht.setVal(BigInteger.valueOf(400));
                for (int j = 0; j < colNum; j++) {
                    String title;
                    if (0 == j) {
                        title = "根指标";
                    } else if (j == colNum - 1) {
                        title = "叶子指标";
                    } else {
                        title = "中间指标";
                    }
                    XWPFTableCell cell = row.getCell(j);
                    setXWPFRun(cell, title);
                }
            } else {
                IndicatorEntity entity = leftIndicatorList.get(i - 1);
                int currCol = colNum;
                String content;
                do {
                    --currCol;
                    if (entity == null || !row.getCell(entity.getLevel()).getText().isEmpty()) {
                        continue;
                    }
                    String result = StringUtils.isEmpty(entity.getConnotation()) ? "" : "(" + entity.getConnotation() + ")";
                    content = entity.getName() + result;
                    row.getCell(entity.getLevel()).addParagraph().createRun().setText(content);
                    entity = idMap.get(entity.getPid());
                } while (currCol > 0);
            }
        }
        for (int a = 0; a < colNum; a++) {
            String content = "";
            for (int i = 1; i <= leftIndicatorList.size(); i++) {
                XWPFTableCell xwpfTableCell = xwpfTable.getRow(i).getCell(a);
                CTTc ctTc = xwpfTable.getRow(i).getCell(a).getCTTc();
                if (ctTc.getTcPr() == null) {
                    ctTc.addNewTcPr();
                }
                if (ctTc.getTcPr().getGridSpan() == null) {
                    ctTc.getTcPr().addNewGridSpan();
                }
                if (i == 1) {
                    ctTc.getTcPr().setVMerge(startMerge);
                    content = xwpfTableCell.getText();
                    continue;
                }
                if (xwpfTableCell.getText().equals(content)) {
                    ctTc.getTcPr().setVMerge(endMerge);
                } else {
                    ctTc.getTcPr().setVMerge(startMerge);
                    content = xwpfTableCell.getText();
                }
            }
        }
    }

    private void setCustom(XWPFDocument word, String custom, Integer titleNum) {
        List<Map> customs = JSONArray.parseArray(custom, Map.class);
        for (Map<String, Object> item : customs) {
            //二级标题
            TextStyleBean subTitle = new TextStyleBean();
            subTitle.setContent(titleNum + ". " + item.get("title"));
            subTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, subTitle);

            //段落内容
            TextStyleBean text = new TextStyleBean();
            text.setFontsize(12);
            text.setColor("000000");
            text.setContent("  " + item.get("content"));
            DocxHelper.addText(null, word, text);
            titleNum++;
        }
    }

    /**
     * @param word
     * @param titleNum
     * @param sceneIds
     * @param schemaEntity
     */
    public void setIndicatorTable(XWPFDocument word, int titleNum, List<String> sceneIds, List<SchemaEntity> schemaEntity) {
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent("检验科目及评估指标对照表");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 1, subTitle);

//        List<SceneEntity> sceneEntityList = sceneService.findByIdIn(sceneIds);
////        if (CollectionUtils.isEmpty(sceneEntityList)) throw new BusinessException("下达的实验任务没有关联场景");
//        Map<String, SceneEntity> sceneEntityMap = new HashMap<>();
//        sceneEntityList.forEach(f -> {
//            sceneEntityMap.put(f.getId(), f);
//        });
        List<Map<String, Object>> resultLists = Lists.newArrayList();
        for (SchemaEntity schema : schemaEntity) {
            //获取pg对象名称
            String evaluationObject = schema.getEvaluationObject();
            //方案id
            String sid = schema.getId();
            //装备类别
            if (evaluationObject.contains("equ")) {
                //pg对象：equ
                String equId = StringUtils.substringAfterLast(evaluationObject, "u");
                Optional<EquipmentEntity> byId = equipmentService.findById(Integer.parseInt(equId));
                if (byId.isPresent()) {
                    //pg对象
                    String name = byId.get().getEquName();
                    //装备类别
                    String category = byId.get().getCategory();
                    resultLists.add(createEvaluateIndicatorTable(word, name, category, schema, sid));
                } else {
                    throw new BusinessException("获取装备名称异常！");
                }
            } else {
                //pg对象：装备体系名称 eSys
                String equSystemId = StringUtils.substringAfterLast(evaluationObject, "s");
                Optional<EquipmentSystemEntity> byId = equipmentSystemService.findById(Integer.parseInt(equSystemId));
                if (byId.isPresent()) {
                    //pg对象
                    String name = byId.get().getEquSysName();
                    //装备类别
                    LambdaQueryWrapper<EquSysParamEntity> lqw = new LambdaQueryWrapper<>();
                    lqw.eq(EquSysParamEntity::getEquSysId, equSystemId);
                    List<EquSysParamEntity> all = equSysParamService.list(lqw);
                    resultLists.add(createEvaluateIndicatorTable(word, name, stringBuilderEquSys(all), schema, sid));
                } else {
                    throw new BusinessException("获取装备体系名称异常！");
                }
            }
        }
        createEvaluateIndicatorTable(word, resultLists);
    }

    private Map<String, Object> createEvaluateIndicatorTable(XWPFDocument word, String name, String category, SchemaEntity schemaEntity, String sid) {
        List<IndicatorEntity> indicatorNode = Lists.newArrayList();
        List<IndicatorEntity> evaluateIndicator = Lists.newArrayList();
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
        if (CollUtil.isNotEmpty(indicatorEntities)) {
            //pg要点：顶层指标
            List<IndicatorEntity> indicatorEntityList = indicatorEntities.stream().filter(i -> "0".equals(i.getPid())).collect(Collectors.toList());
            indicatorNode.addAll(indicatorEntityList);
            //pg指标：除顶层指标之外的所有的子指标
            if (CollUtil.isNotEmpty(indicatorEntityList)) {
                List<IndicatorEntity> rootNodeToNextNode = indicatorEntities.stream().filter(i -> !i.getPid().equals("0")).collect(Collectors.toList());
                evaluateIndicator.addAll(rootNodeToNextNode);
                log.info("根节点的下一级节点:{}", JSON.toJSONString(rootNodeToNextNode));
            }
        }
        //pg对象：综合pg方法
        String evaluationMethod = schemaEntity.getEvaluationMethod();
        EvaluationType typeByName = EvaluationType.getTypeByName(evaluationMethod);
        String evaluationTypeName = typeByName.getChName();
        //数据来源：指数据从哪获取
        List<SceneTableEntity> sceneTableEntities = iSceneTableService.find(SceneTableEntity.builder().sid(sid).build());
        List<DatasrcEntity> dataResources = Lists.newArrayList();
        if (CollUtil.isNotEmpty(sceneTableEntities)) {
            List<String> dataResourceIds = sceneTableEntities.stream().map(SceneTableEntity::getDsid).collect(Collectors.toList());
            dataResources.addAll(dataSrcService.listByIds(dataResourceIds));
        }
        //组装表格数据
        Map<String, Object> ret = Maps.newHashMap();
        if (StringUtils.isNotEmpty(category)) {
            ret.put("equipType", category);
        } else {
            ret.put("equipType", "暂无");
        }
        ret.put("checkObject", name);
        ret.put("evaluateGist", stringBuilderIndicator(indicatorNode));
        ret.put("evaluateIndicator", stringBuilderIndicator(evaluateIndicator));
        ret.put("evaluateMethod", evaluationTypeName);
        ret.put("dataSource", stringBuilderDataSource(dataResources));
        return ret;

    }


    public String stringBuilderEquSys(List<EquSysParamEntity> equSysParamEntities) {
        StringBuilder sb = new StringBuilder();
        if (CollUtil.isNotEmpty(equSysParamEntities)) {
            for (EquSysParamEntity equSysParamEntity : equSysParamEntities) {
                sb.append(equSysParamEntity.getChParam()).append(",");
            }
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public String stringBuilderDataSource(List<DatasrcEntity> datasrcEntities) {
        StringBuilder sb = new StringBuilder();
        if (CollUtil.isNotEmpty(datasrcEntities)) {
            for (DatasrcEntity datasrcEntity : datasrcEntities) {
                sb.append("地址：").append(datasrcEntity.getIp()).append(",")
                        .append("数据库：").append(datasrcEntity.getName()).append(";");
            }
        }
        return sb.toString();
    }


    private String stringBuilderIndicator(List<IndicatorEntity> indicatorNode) {
        StringBuilder sb = new StringBuilder();
        for (IndicatorEntity indicator : indicatorNode) {
            sb.append(indicator.getName()).append(",");
        }
        if (sb.length() > 0 && sb.lastIndexOf(",") == sb.length() - 1) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }


    /**
     * 添加pg结果
     */
    private void setResult(XWPFDocument word, List<String> sceneIds, int titleNum, int transValue) {
        //二级标题
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent(titleNum + ". 评估结果");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, subTitle);

        List<SceneEntity> sceneEntityList = sceneService.findByIdIn(sceneIds);
        if (CollUtil.isEmpty(sceneEntityList)) return;
//        if (CollectionUtils.isEmpty(sceneEntityList)) throw new BusinessException("下达的实验任务没有关联场景");
        Map<String, SceneEntity> sceneEntityMap = new HashMap<>();
        sceneEntityList.forEach(f -> {
            sceneEntityMap.put(f.getId(), f);
        });
        //指标值
        List<IndicatorValuesWithNameDTO> indicatorValuesBoList = indicatorValueService.
                findWithNameBySidIndicatorIdSceneId(sceneEntityList.get(0).getSid(), null, sceneIds);
        Map<String, List<Map<String, Object>>> glist = new HashMap<>();
        //构造导出表格数据
        Map<String, Integer> indexMap = new HashMap<>();
        indicatorValuesBoList.forEach(f -> {
            List<Map<String, Object>> valueList;
            if (glist.containsKey(f.getSceneId())) {
                valueList = glist.get(f.getSceneId());
            } else {
                valueList = new ArrayList<>();
                glist.put(f.getSceneId(), valueList);
            }

            Map<String, Object> data = new HashMap<>();
            valueList.add(data);
            Integer sortNum;
            int level = 1;
            if (f.getLevel() != 0) {
                level = f.getLevel();
            }
            if (indexMap.containsKey(f.getLevel() + f.getSceneId())) {
                sortNum = indexMap.get(f.getLevel().toString() + f.getSceneId());
                sortNum++;
                indexMap.put(f.getLevel().toString() + f.getSceneId(), sortNum);
            } else {
                sortNum = 0;
                if (f.getLevel() != 0) {
                    sortNum = 1;
                }
                indexMap.put(f.getLevel().toString() + f.getSceneId(), sortNum);
            }
            data.put("name", f.getName());
            data.put("val", f.getNormalizedValue());
            data.put("num", level + "." + sortNum);
            if (transValue == 1) {
                String standardLabelName = f.getStandardLabelName();
                if (StringUtils.isEmpty(standardLabelName)) {
                    standardLabelName = "";
                }
                data.put("transName", standardLabelName);
            }
        });
        createTable(word, glist, sceneEntityMap, titleNum, transValue);
        // write image
//        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
//
//        for (IndicatorEntity entity : indicatorEntities) {
//            if (entity.getPid().equals("0")) {
//                continue;
//            }
//            createImage(word, sid, entity.getName(), titleNum,1);
//        }
    }

    private void createImage(XWPFDocument word, String sid, String indicatorName, int titleNum, int size) {
        File file = new File(basicFilePath + com.yw.common.utils.FileUtil.INDICATOR_IMAGE + sid + indicatorName + ".jpg");
        if (!file.exists()) {
            return;
        }
        if (file.length() == 0) {
            return;
        }
        TextStyleBean subTitle = new TextStyleBean();
        subTitle.setContent("  " + titleNum + "." + size + "  " + indicatorName + "柱状图");
        subTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 3, subTitle);


        //图片
        NodeStyle st = new NodeStyle();
        st.align = "center";
        st.height = 300;
        st.width = 500;
        try {
            DocxHelper.addImage2Word(word, null, st
                    , file, 500);
        } catch (Exception e) {
//            e.printStackTrace();
            throw new BusinessException(e.getMessage());
        }
    }

    public void createEvaluateIndicatorTable(XWPFDocument word, List<Map<String, Object>> textVals) {
        int rowNum = textVals.size() + 1;
        XWPFTable xwpfTable = word.createTable(rowNum, 6);

        CTTblPr tblPr = xwpfTable.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("9000"));
        CTString styleStr = tblPr.addNewTblStyle();
        styleStr.setVal("StyledTable");
        CTTblLayoutType t = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        t.setType(STTblLayoutType.FIXED);

        List<XWPFTableRow> rows = xwpfTable.getRows();
        for (int i = 0; i < rowNum; i++) {
            XWPFTableRow row = rows.get(i);
            CTTrPr trPr = row.getCtRow().addNewTrPr();
            //表格标题
            if (i == 0) {
                CTHeight ht = trPr.addNewTrHeight();
                ht.setVal(BigInteger.valueOf(400));

                XWPFTableCell cell1 = row.getCell(0);
                setXWPFRun(cell1, "装备类别");

                XWPFTableCell cell2 = row.getCell(1);
                setXWPFRun(cell2, "检验对象");

                XWPFTableCell cell3 = row.getCell(2);
                setXWPFRun(cell3, "pg要点");

                XWPFTableCell cell4 = row.getCell(3);
                setXWPFRun(cell4, "pg指标");

                XWPFTableCell cell5 = row.getCell(4);
                setXWPFRun(cell5, "pg方法");

                XWPFTableCell cell6 = row.getCell(5);
                setXWPFRun(cell6, "数据来源");
            } else {
                Map<String, Object> textVal = textVals.get(i - 1);
                CTHeight ht = trPr.addNewTrHeight();
                ht.setVal(BigInteger.valueOf(100));
                XWPFParagraph paragraph0 = row.getCell(0).addParagraph();
                for (String str : textVal.get("equipType").toString().split(",")) {
                    XWPFRun run = paragraph0.createRun();
                    run.setText(str);
                    run.addBreak();
                }
                row.getCell(1).addParagraph().createRun().setText(textVal.get("checkObject").toString());
                row.getCell(2).addParagraph().createRun().setText(textVal.get("evaluateGist").toString());
                XWPFParagraph paragraph3 = row.getCell(3).addParagraph();
                for (String str : textVal.get("evaluateIndicator").toString().split(",")) {
                    paragraph3.setAlignment(ParagraphAlignment.LEFT);
                    XWPFRun run = paragraph3.createRun();
                    run.setText(str + ";");
                    run.addBreak();
                }
                row.getCell(4).addParagraph().createRun().setText(textVal.get("evaluateMethod").toString());
                row.getCell(5).addParagraph().createRun().setText(textVal.get("dataSource").toString());
            }
        }
    }


    private void createTable(XWPFDocument word, Map<String, List<Map<String, Object>>> glist, Map<String, SceneEntity> sceneEntityMap, int titleNum, int transValue) {
        //遍历每个场景
        Set<Object> sceneNum = new HashSet<>();
        for (Map.Entry<String, SceneEntity> entry : sceneEntityMap.entrySet()) {
            String sceneId = entry.getKey();
            SceneEntity sceneEntity = entry.getValue();
            List<Map<String, Object>> valueList = glist.getOrDefault(sceneId, new ArrayList<>());
            //三级标题
            TextStyleBean subTitle = new TextStyleBean();
            sceneNum.add(sceneId);
            subTitle.setContent("  " + titleNum + "." + sceneNum.size() + "  " + sceneEntity.getName());
            DocxHelper.createTitle(word, null, 3, subTitle);

            int rowNum = valueList.size() + 1;
            XWPFTable xwpfTable = word.createTable(rowNum, 4);

            CTTblPr tblPr = xwpfTable.getCTTbl().getTblPr();
            tblPr.getTblW().setType(STTblWidth.DXA);
            tblPr.getTblW().setW(new BigInteger("9000"));
            CTString styleStr = tblPr.addNewTblStyle();
            styleStr.setVal("StyledTable");
            CTTblLayoutType t = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
            t.setType(STTblLayoutType.FIXED);

            List<XWPFTableRow> rows = xwpfTable.getRows();

            for (int i = 0; i < rowNum; i++) {
                XWPFTableRow row = rows.get(i);
                CTTrPr trPr = row.getCtRow().addNewTrPr();
                //表格标题
                if (i == 0) {
                    CTHeight ht = trPr.addNewTrHeight();
                    ht.setVal(BigInteger.valueOf(400));

                    XWPFTableCell cell1 = row.getCell(0);
                    setXWPFRun(cell1, "序号");

                    XWPFTableCell cell2 = row.getCell(1);
                    setXWPFRun(cell2, "名称");

                    XWPFTableCell cell3 = row.getCell(2);
                    setXWPFRun(cell3, "指标值");

                    XWPFTableCell cell4 = row.getCell(3);
                    setXWPFRun(cell4, "指标pg值");
                } else {
                    int index = i - 1;
                    CTHeight ht = trPr.addNewTrHeight();
                    ht.setVal(BigInteger.valueOf(360));
                    Map<String, Object> rowData = valueList.get(index);
                    row.getCell(0).setText(rowData.getOrDefault("num", "").toString());
                    row.getCell(1).setText(rowData.getOrDefault("name", "").toString());
                    row.getCell(2).setText(rowData.getOrDefault("val", "").toString());
                    if (transValue == 1) {
                        row.getCell(3).setText(rowData.getOrDefault("transName", "").toString());
                    }
                }

            }
        }

    }


    @Override
    public void exportAll(String sid, HttpServletResponse response) {
        //方案信息
        SchemaEntity schemaEntity = getSchemaEntityInfo(sid);

        String evaluationObject = schemaEntity.getEvaluationObject();
                // 方案均取消评估对象，判空处理
//        String evaluationObject = StringUtils.EMPTY;
//        if (StringUtils.isNotEmpty(schemaEntity.getEvaluationObject())) {
//            //评估对象
//            evaluationObject = getEvaluationObject(schemaEntity);
//        }

        //指标树信息 兼容体系贡献率，有两个指标树
        List<IndicatorTreeEntity> indicatorTreeEntityList = getIndicatorTreeInfo(sid);
        // 指标计算参数
        List<IndicatorCalculationParameter> indicatorCalculationParameters = indicatorCalculationParameterService.listBySchemaId(sid);
        //获取指标信息
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().sid(sid).build());
        //评估准则信息 兼容体系贡献率，有多个指标准则
        List<IndicatorStandardGroupDTO> indicatorStandardGroupDTOList = new ArrayList<>();
        indicatorTreeEntityList.forEach(entity -> {
            IndicatorStandardGroupDTO indicatorStandardGroupDTO = standardLabelsService.findByTreeId(entity.getId());
            indicatorStandardGroupDTOList.add(indicatorStandardGroupDTO);
        });
        //样本数据结构
        List<SceneTableEntity> sceneTableEntities = iSceneTableService.find(SceneTableEntity.builder().sid(sid).build());

        List<TableInfoVO> tableDataInfos = Lists.newArrayList();
        List<DatasrcEntity> datasrcEntities = Lists.newArrayList();
        Map<String, List<GatherRoundRecord>> gatherRoundRecords = Maps.newHashMap();
        List<CustomDataEntity> customDataEntities = Lists.newArrayList();
        List<String> dataSrcOnList = Lists.newArrayList();
        List<ImportTableRecord> importTableRecords = Lists.newArrayList();
        for (SceneTableEntity entity : sceneTableEntities) {
            //数据源信息
            String tableName = entity.getTableName();
            //样本数据结构所包含的数据表：（有任务数据还需要包含任务数据表）
            String dsid = entity.getDsid();

            // 轮次数据
            LambdaQueryWrapper<GatherRoundRecord> gatherRoundRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
            gatherRoundRecordLambdaQueryWrapper.eq(GatherRoundRecord::getDataSrcId, dsid);
            List<GatherRoundRecord> list = iGatherRoundRecordService.list(gatherRoundRecordLambdaQueryWrapper);
            if (CollUtil.isNotEmpty(list)) {
                if (!gatherRoundRecords.containsKey(dsid)) {
                    gatherRoundRecords.put(dsid, list);
                }
            }

            // 数据源 & 自定义数据
            Optional<DatasrcEntity> datasrcEntity = dataSrcService.findById(dsid);
            DatasrcEntity datasrcInfo = datasrcEntity.get();
            if (!dataSrcOnList.contains(dsid)) {
                datasrcEntities.add(datasrcInfo);
                dataSrcOnList.add(dsid);
            }
            if (datasrcInfo.getTaskType() == DataAnalysisType.CUSTOM_DATA.getId()) {
                // 获取自定义数据信息
                CustomDataEntity customDataEntity = customDataService.getOne(new LambdaQueryWrapper<CustomDataEntity>().eq(CustomDataEntity::getCustomName, tableName));
                if (customDataEntity == null) {
                    throw new BusinessException("自定义数据获取异常,请检查样本数据结构: " + tableName);
                }
                customDataEntities.add(customDataEntity);
                String dataSrc = customDataEntity.getDataSrc();
                Optional<DatasrcEntity> customDataSrcInfo = dataSrcService.findById(dataSrc);
                DatasrcEntity customDataSrc = customDataSrcInfo.get();
                if (!dataSrcOnList.contains(dataSrc)) {
                    datasrcEntities.add(customDataSrc);
                    dataSrcOnList.add(dataSrc);
                }
                List<String> resourceData = customDataService.customResourceData(customDataEntity.getId());
                for (String customTableName : resourceData) {
                    getTableData(tableDataInfos, customTableName, dataSrc, customDataSrc);
                }
                continue;
            }
            getTableData(tableDataInfos, tableName, dsid, datasrcInfo);
            // 数据目录下的表记录
            List<ImportTableRecord> importTableRecordList = importTableRecordService.getRecordByFromIdAndTableName(dsid, tableName);
            importTableRecords.addAll(importTableRecordList);
        }
        //样本
        List<SceneEntity> sceneEntities = sceneService.find(SceneEntity.builder().sid(sid).build());

        // 体系贡献率还需获取场景、任务、情况、样本关联关系
        List<CompeteSceneEntity> competeSceneEntityList = new ArrayList<>();
        List<SceneScenarioEntity> sceneScenarioEntityList = new ArrayList<>();
        List<MissionTaskEntity> missionTaskEntityList = new ArrayList<>();
        List<ContributionRateAlgorithmEntity> algorithmEntityList = new ArrayList<>();
        List<IndicatorCriteria> indicatorCriteriaList = new ArrayList<>();
//        if (3 == schemaEntity.getSchemaEvaluationType()) {
        // 获取场景信息
        competeSceneEntityList = competeSceneService.findAll(Example.of(CompeteSceneEntity.builder().sid(sid).build()));
        //获取场景详情
        List<String> competeSceneIdList = competeSceneEntityList.stream().map(CompeteSceneEntity::getId).collect(Collectors.toList());
        List<CompeteSceneEntityDetailsEntity> competeSceneEntityDetailsEntities = competeSceneEntityDetailsService.getByCompeteSceneIdList(competeSceneIdList);

        // 获取想定信息
        sceneScenarioEntityList = sceneScenarioService.list(new LambdaQueryWrapper<SceneScenarioEntity>().eq(SceneScenarioEntity::getSid, sid));
        for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntityList) {
            List<SidesDirecEntity> list = sidesDirecService.list(new LambdaQueryWrapper<SidesDirecEntity>()
                    .eq(SidesDirecEntity::getSceneScenarioId, sceneScenarioEntity.getId()));
            sceneScenarioEntity.setSidesDirecEntityList(list);

            List<SidesParamEntity> sidesParamEntityList = sidesParamService.list(new LambdaQueryWrapper<SidesParamEntity>()
                    .eq(SidesParamEntity::getSceneScenarioId, sceneScenarioEntity.getId()));
            sceneScenarioEntity.setSidesParamEntityList(sidesParamEntityList);
        }

        // 获取任务信息
        missionTaskEntityList = missionTaskService.find(MissionTaskEntity.builder().sid(sid).build());
        List<String> missionTaskIdList = missionTaskEntityList.stream().map(MissionTaskEntity::getId).collect(Collectors.toList());
        //获取任务详情信息
        List<MissionTaskDetailsEntity> missionTaskDetailsEntityList = missionTaskDetailsService.findByMissionBatchTaskId(missionTaskIdList);

        // 获取样本关联关系
        if (!CollectionUtils.isEmpty(sceneEntities)) {
            List<String> sceneIdList = sceneEntities.stream().map(SceneEntity::getId).collect(Collectors.toList());
            algorithmEntityList = algorithmService.findBySceneIds(sceneIdList);
        }
        // 获取映射矩阵信息
        List<String> indicatorIdList = indicatorEntities.stream().map(IndicatorEntity::getId).collect(Collectors.toList());
        indicatorCriteriaList = indicatorCriteriaService.findAllByIndicatorIds(indicatorIdList);
//        }

        //默认指标算法||方法模型
        List<String> calcModuleIds = Lists.newArrayList();
        List<CalcModuleEntity> calcModuleEntities = iCalcModuleService.find(CalcModuleEntity.builder().sid(sid).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build());
        List<String> adcCalcModel = Lists.newArrayList();
        for (CalcModuleEntity calcModuleEntity : calcModuleEntities) {
            if (StringUtils.isNotEmpty(calcModuleEntity.getMethodId()) && calcModuleEntity.getMethodId().equals(EvaluationType.ADC_MEANS.getName())) {
                adcCalcModel.add(calcModuleEntity.getId());
            }
            calcModuleIds.add(calcModuleEntity.getId());
        }
        //adc算法参数信息
        Map<String, AdcCalcModelParamDTO> adcCalcModelParamDTOMap = Maps.newHashMap();
        if (CollUtil.isNotEmpty(adcCalcModel)) {
            for (String adcCalcModelId : adcCalcModel) {
                AdcCalcModelParamDTO adcCalcModelParamDTO = new AdcCalcModelParamDTO();
//                AlgorithmConfigureEntity configureEntity = iAlgorithmConfigureRepository.findBySid(adcCalcModelId);
                AlgorithmConfigureEntity configureEntity = algorithmConfigureMapper.selectOne(new QueryWrapper<AlgorithmConfigureEntity>().eq("SID", adcCalcModelId));
                if (configureEntity == null) {
                    continue;
                }
                adcCalcModelParamDTO.configureEntity = configureEntity;
                adcCalcModelParamDTOMap.put(adcCalcModelId, adcCalcModelParamDTO);
//                List<ComponentInfoEntity> componentInfoEntities = iComponentInfoRepository
//                        .findBySidOrderByCreateTimeDesc(adcCalcModelId);
                List<ComponentInfoEntity> componentInfoEntities = componentInfoMapper.selectList(
                        new QueryWrapper<ComponentInfoEntity>()
                                .eq("SID", adcCalcModelId).orderByDesc("CREATE_TIME"));
                if (CollUtil.isEmpty(componentInfoEntities)) {
                    continue;
                }
                adcCalcModelParamDTO.componentInfoEntities = componentInfoEntities;
//                List<PartsStateEntity> partsStateEntities = iPartsStateRepository.findBySidOrderByCreateTimeDesc(adcCalcModelId);
                List<PartsStateEntity> partsStateEntities = partsStateMapper.selectList(new QueryWrapper<PartsStateEntity>().eq("SID", adcCalcModelId).orderByDesc("CREATE_TIME"));
                if (CollUtil.isEmpty(partsStateEntities)) {
                    continue;
                }
                adcCalcModelParamDTO.partsStateEntities = partsStateEntities;
//                List<CompStatusRelationEntity> bySidOrderByCreateTimeDesc = iCompStatusRelationRepository.findBySidOrderByCreateTimeDesc(adcCalcModelId);
                List<CompStatusRelationEntity> bySidOrderByCreateTimeDesc = compStatusRelationMapper.selectList(
                        new QueryWrapper<CompStatusRelationEntity>()
                                .eq("SID", adcCalcModelId).orderByDesc("CREATE_TIME"));
                if (CollUtil.isEmpty(bySidOrderByCreateTimeDesc)) {
                    continue;
                }
                adcCalcModelParamDTO.bySidOrderByCreateTimeDesc = bySidOrderByCreateTimeDesc;
            }
        }
        //获取算法的配置参数
//        List<AlgorithmParamEntity> algorithmParamEntities = algorithmParamRepository.findByCalcModuleIdIn(calcModuleIds);
        List<AlgorithmParamEntity> algorithmParamEntities = iAlgorithmParamMapper.selectList(new QueryWrapper<AlgorithmParamEntity>().in(!calcModuleIds.isEmpty(), "CALC_MODULE_ID", calcModuleIds));
        //指标算子信息
//        List<CalcorEntity> calcorEntityList = calcorRepository.findByCalcModuleIdIn(calcModuleIds);
        List<CalcorEntity> calcorEntityList = calcorService.list(new QueryWrapper<CalcorEntity>().in(!calcModuleIds.isEmpty(), "calc_module_id", calcModuleIds));
        //权重信息
        List<String> weightModelIds = Lists.newArrayList();
        List<WeightModelEntity> weightModelEntities = weightModelMapper.findBySid(sid);
        for (WeightModelEntity modelEntity : weightModelEntities) {
            weightModelIds.add(modelEntity.getId());
        }
        //获取权重值
//        List<IndicatorWeightEntity> byWeightModelIdIn = itIndicatorWeightRepository.findByWeightModelIdIn(weightModelIds);
        List<IndicatorWeightEntity> byWeightModelIdIn = indicatorWeightService.findByWeightModelIdIn(weightModelIds);
        String schemaAllStr = JSON.toJSONString(
                SchemaAllDataDTO.valueOf(schemaEntity, evaluationObject, indicatorTreeEntityList,
                        indicatorEntities, indicatorStandardGroupDTOList, sceneTableEntities,
                        tableDataInfos, sceneEntities, calcorEntityList,
                        byWeightModelIdIn, datasrcEntities, customDataEntities, calcModuleEntities, weightModelEntities,
                        algorithmParamEntities, adcCalcModelParamDTOMap, competeSceneEntityList, competeSceneEntityDetailsEntities, sceneScenarioEntityList,
                        missionTaskEntityList, missionTaskDetailsEntityList, algorithmEntityList, indicatorCriteriaList, gatherRoundRecords, importTableRecords,
                        indicatorCalculationParameters));
        String name = schemaEntity.getName();
        String targetFile = basicFilePath + com.yw.common.utils.FileUtil.CACHE + schemaEntity.getName() + ".zip";

        try {
            File path = new File(basicFilePath + com.yw.common.utils.FileUtil.CACHE + name + "/");
            if (!path.exists()) path.mkdirs();
            String jsonPath = basicFilePath + com.yw.common.utils.FileUtil.CACHE + name + "/" + name + ".json";
            FileOutputStream outputStream = new FileOutputStream(jsonPath);
            outputStream.write(FileEncryptor.encryptFile(schemaAllStr, PASSWORD).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            FileOutputStream targetOut = new FileOutputStream(targetFile);
            com.yw.common.utils.ZipUtils.toZip(basicFilePath + com.yw.common.utils.FileUtil.CACHE + name + "/", targetOut, true);
            outputStream.close();
            com.yw.common.utils.FileUtil.downloadFileRes(response, jsonPath, (StringUtils.isEmpty(name) ? schemaEntity.getName() : name) + ".json");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            File file = new File(basicFilePath + com.yw.common.utils.FileUtil.CACHE + name + "/" + name + ".json");
            if (file.exists()) {
                file.delete();
            }
//            File targetFileObject = new File(targetFile);
//            if (targetFileObject.exists()) {
//                targetFileObject.delete();
//            }
        }

    }

    private void getTableData(List<TableInfoVO> tableDataInfos, String tableName, String dsid, DatasrcEntity datasrcInfo) {
//        if (datasrcInfo.getIncludeTasks() == DataTableType.INCLUDE_TASK_DATA_SHEET.id) {
//            TableInfoVO tableInfoVO = dataSrcManagerService.tableNameDefineField(ExerciseInfoDTO.valueOf(datasrcInfo.getTaskName(), dsid, 0, 0));
//            tableInfoVO.setDataSrcName(datasrcInfo.getTaskName());
//            tableInfoVO.setDataSrcId(dsid);
//            tableDataInfos.add(tableInfoVO);
//        }
        try {
            TableInfoVO tableInfoVO = dataSrcManagerService.tableNameDefineField(ExerciseInfoDTO.valueOf(tableName, dsid, 0, 0));
            tableInfoVO.setDataSrcName(tableName);
            tableInfoVO.setDataSrcId(dsid);
            tableDataInfos.add(tableInfoVO);
        } catch (Exception e) {
            log.error("export schema data exception, exception as follows: {}", e.getMessage());
            throw new BusinessException("方案数据丢失,丢失数据表: " + tableName);
        }
    }

    /**
     * 获取指标树信息
     *
     * @param sid
     * @return
     */
    private List<IndicatorTreeEntity> getIndicatorTreeInfo(String sid) {
//        List<IndicatorTreeEntity> entityList = indicatorTreeRepository.findAll(Example.of(IndicatorTreeEntity.builder().sid(sid).build()));
        List<IndicatorTreeEntity> entityList = indicatorTreeService.list(new QueryWrapper<IndicatorTreeEntity>().setEntity(IndicatorTreeEntity.builder().sid(sid).build()));
        return entityList;
    }

    private String getEvaluationObject(SchemaEntity schemaEntity) {
        String evaluationObject = schemaEntity.getEvaluationObject();
        EquAndEquSysDTO equAndEquSysDTO = new EquAndEquSysDTO();
        if (evaluationObject.contains("equ")) {
            String equId = evaluationObject.replace("equ", "").trim();
            Optional<EquipmentEntity> equipmentEntity = equipmentService.findById(Integer.parseInt(equId));
            if (equipmentEntity.isPresent()) {
                equAndEquSysDTO.equipmentEntities = Lists.newArrayList(equipmentEntity.get());
                return JSON.toJSONString(equAndEquSysDTO);
            }
        } else {
            String eSysId = evaluationObject.replace("eSys", "").trim();
            Optional<EquipmentSystemEntity> equipmentSystemEntity = equipmentSystemService.findById(Integer.parseInt(eSysId));
            if (equipmentSystemEntity.isPresent()) {
                EquipmentSystemEntity equipmentSystem = equipmentSystemEntity.get();
                String composition = equipmentSystem.getComposition();
                List<Integer> parseArray = JSON.parseArray(composition, Integer.class);
                List<EquipmentEntity> equipmentEntities = equipmentService.listByIds(parseArray);
                equAndEquSysDTO.equipmentEntities = equipmentEntities;
                equAndEquSysDTO.equipmentSystemEntity = equipmentSystem;
                return JSON.toJSONString(equAndEquSysDTO);
            }
        }
        return "";
    }

    /**
     * 获取方案信息
     *
     * @param sid
     * @return
     */
    private SchemaEntity getSchemaEntityInfo(String sid) {
        Optional<SchemaEntity> schemaEntity = schemaService.findById(sid);
        if (schemaEntity.isPresent()) {
            return schemaEntity.get();
        }
        throw new BusinessException("对象不存在");
    }


    private static final Pattern pattern = Pattern.compile("^能力指标树[0-9]*[0-9]$");
    private static final Pattern pattern_1 = Pattern.compile("^效能指标树[0-9]*[0-9]$");


    @Transactional(rollbackFor = Exception.class)
    @Override
    public String importAll(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isEmpty(originalFilename) || !originalFilename.contains(".json")) {
            throw new BusinessException("文件导入格式错误,正确后缀为: .json");
        }
        //方案id
        String sid = com.yw.common.utils.UUIDGenerator.getUUID();
        Map<String/*old data src id*/, DatasrcEntity/*new data src info*/> dataSrcInfoMaps = Maps.newHashMap();
        Map<String/*old data src id*/, String/*old data dbName*/> oldDataDbNameMaps = Maps.newHashMap();
        Map<String/*data src id */, List<String>/*db table*/> saveDbTable = Maps.newHashMap();
        try {
            String str = IoUtil.read(file.getInputStream(), StandardCharsets.UTF_8);
            String schemaAllStr = FileEncryptor.decryptFile(str, PASSWORD);
            SchemaAllDataDTO schemaAllDataDTO = JSON.parseObject(schemaAllStr, SchemaAllDataDTO.class);
            log.info("数据解析成功。。。。。。。。。。。。。");
            //校验方案字段长度
            lengthCheck(schemaAllDataDTO.getSchemaEntity());
            // 方案
            SchemaEntity newSchemaEntity = setSchemaEntity(schemaAllDataDTO, sid);
            // 方案均取消评估对象，判空处理
//            Integer qeuId;
//            if (StringUtils.isNotEmpty(newSchemaEntity.getEvaluationObject())) {
//                //评估对象
//                qeuId = saveEvaluationObject(schemaAllDataDTO, newSchemaEntity);
//            } else {
//                qeuId = 0;
//            }
            String userId = com.yw.common.utils.UserUtil.getUserId();
            newSchemaEntity.setUserId(userId);
            schemaService.edit(newSchemaEntity);
            try {
                //记录操作日志
                LogEvaluationPlanDto logEntity = LogEvaluationPlanDto.getLog(newSchemaEntity.getName(), userId, userMapper.getUserNameById(userId),
                        BusinessType.EXPORT.name(), newSchemaEntity.getSchemaEvaluationType(), "导入评估方案",
                        userId, userMapper.getUserNameById(userId), LocalDateTime.now());
                pgLogMapper.insert(logEntity);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //指标树
            List<IndicatorTreeEntity> indicatorTreeEntityList = saveIndicatorTreeEntity(schemaAllDataDTO, sid);

            Map<String/*old indicatorId*/, String/*new indicatorId*/> indicatorIdsMap = Maps.newHashMap();
            Map<String/*old indicator pid */, List<String>/*old indicatorId*/> indicatorPidMap = Maps.newHashMap();
            //指标
            saveIndicatorEntity(schemaAllDataDTO, sid, indicatorTreeEntityList, indicatorIdsMap, indicatorPidMap);
            //指标准则
            saveIndicatorStandard(schemaAllDataDTO, sid, indicatorIdsMap);
            //数据源
            Map<String/*old data src id*/, String/*new data src id*/> dataSrcIdMaps = Maps.newHashMap();
            saveDataSrcInfo(schemaAllDataDTO, dataSrcIdMaps, dataSrcInfoMaps, oldDataDbNameMaps);
            //数据表
            Map<String, String> customDataNames = Maps.newHashMap();
            saveDbTAble(schemaAllDataDTO, dataSrcIdMaps, dataSrcInfoMaps, customDataNames, saveDbTable, oldDataDbNameMaps);

            //数据目录表记录（V0.17版本数据源统一放到数据源管理，多评估工程共用，不再处理数据源相关）
//            saveImportTableRecords(dataSrcInfoMaps, schemaAllDataDTO);
            //样本数据结构
            Map<String/*ole sceneTable id*/, String/*new sceneTable id*/> sceneTableIdMap = Maps.newHashMap();
            Map<String/*ole aliasId id*/, String/*new aliasId id*/> aliasIdMap = Maps.newHashMap();
            saveSceneTableInfo(dataSrcInfoMaps, schemaAllDataDTO, sid, dataSrcIdMaps, customDataNames, sceneTableIdMap, aliasIdMap, indicatorIdsMap);
            //样本
            Map<String/*old sceneId*/, String/*new sceneId*/> oldKeyNewValueMap = Maps.newHashMap();
            List<SceneEntity> sceneEntities = schemaAllDataDTO.getSceneEntities();
            saveSceneInfo(sid, dataSrcIdMaps, sceneEntities, schemaAllDataDTO, oldKeyNewValueMap);
            // 轮次数据（V0.17版本数据源统一放到数据源管理，多评估工程共用，不再处理数据源相关）
//            saveRoundRecord(dataSrcIdMaps, schemaAllDataDTO.getGatherRoundRecordMaps());

            //指标算法
            saveCalcModule(null, schemaAllDataDTO, sid, indicatorIdsMap, aliasIdMap);
            //指标计算参数
//            saveIndicatorCalculationParameters(sid, indicatorIdsMap, schemaAllDataDTO);
            //权重算法
            saveWeightModule(schemaAllDataDTO, sid, indicatorIdsMap, indicatorPidMap);


            // 方案要绑定目录（V0.17版本数据源统一放到数据源管理，方案不再绑定目录）
//            if (ObjectUtils.isNotEmpty(schemaAllDataDTO.getDataDirectory())) {
//                newSchemaEntity.setDataDirectoryId(schemaAllDataDTO.getDataDirectory().getId());
//                newSchemaEntity.setDataDirectoryName(schemaAllDataDTO.getDataDirectory().getName());
//            }
            schemaService.edit(newSchemaEntity);
            // 体系贡献率额外处理
//            if (3 == newSchemaEntity.getSchemaEvaluationType()) {
            // 先保存任务
            Map<String/*old id*/, String/*new id*/> oldKeyAndNewValueMap = Maps.newHashMap();
            exportSaveToDBUtil.saveMissionTask(schemaAllDataDTO, sid, oldKeyAndNewValueMap);
            // 再保存场景
            exportSaveToDBUtil.saveCompeteScene(schemaAllDataDTO, sid, oldKeyAndNewValueMap);
            // 保存想定
            exportSaveToDBUtil.saveSceneScenario(schemaAllDataDTO, sid, oldKeyAndNewValueMap);
            // 保存样本关联关系
            exportSaveToDBUtil.saveAlgorithm(schemaAllDataDTO, sid, oldKeyNewValueMap);
            // 保存映射矩阵信息
            exportSaveToDBUtil.saveIndicatorCriteria(schemaAllDataDTO);
//            }

        } catch (Exception e) {
            log.error("文件导入异常: {}", e);
            //删除导入的方案
            schemaService.delete(sid);

            DataBaseHandler baseHandler = BeanUtils.getBean(DataBaseHandler.class);
            //删除所有数据并且删除数据表信息
            if (!CollUtil.isEmpty(saveDbTable)) {
                for (Map.Entry<String, List<String>> dbTables : saveDbTable.entrySet()) {
                    String key = dbTables.getKey();
                    DatasrcEntity datasrcEntity = dataSrcInfoMaps.get(key);
                    dataSrcService.removeById(datasrcEntity.getId());
                    DbLinkDTO dblinkDto = datasrcEntity.createDblinkDto();
                    List<String> value = dbTables.getValue();
                    baseHandler.deleteTable(value, dblinkDto.createDbLinkBo());
                    baseHandler.deleteDatabase(datasrcEntity.getDatabaseName(), dblinkDto.createDbLinkBo());
                }
            }
            throw new BusinessException("文件导入异常,异常错误信息: " + e.getMessage());
        }
        return sid;
    }

    /**
     * 校验方案字段长度
     */
    private void lengthCheck(SchemaEntity schemaEntity) {
        if (ObjectUtil.isNull(schemaEntity)) {
            return;
        }
        if (StrUtil.length(schemaEntity.getName()) > 50) {
            throw new BusinessException("评估工程名称长度超限制，最大长度50，当前长度 " + StrUtil.length(schemaEntity.getName()));
        }
        if (StrUtil.length(schemaEntity.getAssessTarget()) > 2000) {
            throw new BusinessException("评估目的长度超限制，最大长度2000，当前长度 " + StrUtil.length(schemaEntity.getAssessTarget()));
        }
        if (StrUtil.length(schemaEntity.getBackground()) > 2000) {
            throw new BusinessException("评估背景长度超限制，最大长度2000，当前长度 " + StrUtil.length(schemaEntity.getBackground()));
        }
        if (StrUtil.length(schemaEntity.getUnit()) > 50) {
            throw new BusinessException("评估单位长度超限制，最大长度50，当前长度 " + StrUtil.length(schemaEntity.getUnit()));
        }
    }

    private void saveRoundRecord(Map<String, String> dataSrcIdMaps, Map<String, List<GatherRoundRecord>> gatherRoundRecordMaps) {
        if (CollUtil.isEmpty(dataSrcIdMaps) || CollUtil.isEmpty(gatherRoundRecordMaps)) {
            return;
        }
        List<GatherRoundRecord> save = Lists.newArrayList();
        for (Map.Entry<String, String> stringStringEntry : dataSrcIdMaps.entrySet()) {
            String oldDsid = stringStringEntry.getKey();
            String newDsid = stringStringEntry.getValue();
            if (!gatherRoundRecordMaps.containsKey(oldDsid)) {
                continue;
            }
            List<GatherRoundRecord> gatherRoundRecords = gatherRoundRecordMaps.get(oldDsid);
            for (GatherRoundRecord gatherRoundRecord : gatherRoundRecords) {
                gatherRoundRecord.setDataSrcId(newDsid);
                gatherRoundRecord.setId(com.yw.common.utils.UUIDGenerator.getUUID());
                save.add(gatherRoundRecord);
            }
        }
        iGatherRoundRecordService.saveBatch(save);

    }

    private void saveWeightModule(SchemaAllDataDTO schemaAllDataDTO, String sid, Map<String, String> indicatorIdsMap, Map<String, List<String>> indicatorPidMap) {
        List<WeightModelEntity> weightModelEntities = schemaAllDataDTO.getWeightModelEntities();
        List<IndicatorWeightEntity> saveIndicatorWeight = Lists.newArrayList();
        if (CollUtil.isNotEmpty(weightModelEntities)) {
            List<IndicatorWeightEntity> byWeightModelIdIn = schemaAllDataDTO.getByWeightModelIdIn();
            Map<String/*indicatorWeight id*/, List<IndicatorWeightEntity>> indicatorWeightMap = Maps.newHashMap();
            if (CollUtil.isNotEmpty(byWeightModelIdIn)) {
                indicatorWeightMap.putAll(byWeightModelIdIn.stream().collect(Collectors.groupingBy(IndicatorWeightEntity::getWeightModelId)));
            }
            for (WeightModelEntity weight : weightModelEntities) {
                String oldWeightId = weight.getId();
                String newWeightId = UUIDGenerator.getUUID();
                String oldIndicatorId = weight.getIndicatorId();
                weight.setIndicatorId(indicatorIdsMap.get(oldIndicatorId));
                weight.setId(newWeightId);
                weight.setSid(sid);
                weight.setName("权重");
                String algorithmJson = weight.getAlgorithmJson();
                List<String> childIndicatorId = indicatorPidMap.get(oldIndicatorId);
                if (CollUtil.isNotEmpty(childIndicatorId)) {
                    for (String childId : childIndicatorId) {
                        algorithmJson = algorithmJson.replaceAll(childId, indicatorIdsMap.get(childId));
                    }
                }
                List<IndicatorWeightEntity> indicatorWeightEntities = indicatorWeightMap.get(oldWeightId);
                //权重值
                if (CollUtil.isNotEmpty(indicatorWeightEntities)) {
                    for (IndicatorWeightEntity weightEntity : indicatorWeightEntities) {
                        weightEntity.setId(UUIDGenerator.getUUID());
                        String indicatorId = weightEntity.getIndicatorId();
                        weightEntity.setIndicatorId(indicatorIdsMap.get(indicatorId));
                        weightEntity.setWeightModelId(newWeightId);
                        weightEntity.setSid(sid);
                        saveIndicatorWeight.add(weightEntity);
                        algorithmJson = algorithmJson.replaceAll(indicatorId, indicatorIdsMap.get(indicatorId));
                    }
                }
                weight.setAlgorithmJson(algorithmJson);
            }
        }
//        weightModelRepository.saveAll(weightModelEntities);
        weightModelService.saveBatch(weightModelEntities);
//        itIndicatorWeightRepository.saveAll(saveIndicatorWeight);
        indicatorWeightService.saveBatch(saveIndicatorWeight);
    }

    private void saveCalcModule(Integer evaluationObject, SchemaAllDataDTO schemaAllDataDTO, String sid, Map<String, String> indicatorIdsMap, Map<String, String> aliasIdMap) {
        List<CalcModuleEntity> calcModuleEntities = schemaAllDataDTO.getCalcModuleEntities();

        if (!CollUtil.isEmpty(calcModuleEntities)) {
            List<CalcorEntity> calcorEntityList = schemaAllDataDTO.getCalcorEntityList();
            Map<String /*calcModuleId*/, List<CalcorEntity>> calcorMaps = Maps.newHashMap();
            if (!CollUtil.isEmpty(calcorEntityList)) {
                calcorMaps.putAll(calcorEntityList.stream().collect(Collectors.groupingBy(CalcorEntity::getCalcModuleId)));
            }
            List<AlgorithmParamEntity> algorithmParamEntities = schemaAllDataDTO.getAlgorithmParamEntities();
            Map<String/*calc module id*/, List<AlgorithmParamEntity>> algorithmParamMap = Maps.newHashMap();
            if (CollUtil.isNotEmpty(algorithmParamEntities)) {
                algorithmParamMap.putAll(algorithmParamEntities.stream().collect(Collectors.groupingBy(AlgorithmParamEntity::getCalcModuleId)));
            }
            for (CalcModuleEntity calcMo : calcModuleEntities) {
                String oldId = calcMo.getId();
                String newId = com.yw.common.utils.UUIDGenerator.getUUID();
                String oldIndicatorId = calcMo.getIndicatorId();
                calcMo.setIndicatorId(indicatorIdsMap.get(oldIndicatorId));
                String oldSid = calcMo.getSid();
                calcMo.setSid(sid);
                calcMo.setId(newId);
                List<CalcorEntity> calcorEntities = calcorMaps.get(oldId);
                if (calcMo.getCalcType() != ModuleType.METHOD_MODEL.id && CollUtil.isNotEmpty(calcorEntities)) {
                    calcMo.setName("指标算法");
                    Map<String/*old cal cor id*/, String/*new cal cor id*/> calcorId = Maps.newHashMap();
                    //算子调整
                    if (StringUtils.isEmpty(calcMo.getJson())) {
                        calcMo.setJson(new String(""));
                    }
                    if (com.yw.framework.common.util.StringUtils.isEmpty(calcMo.getIndicatorId())){
                        continue;
                    }
                    calcMo.setJson(calcMo.getJson().replace(oldId, newId));
                    calcMo.setJson(calcMo.getJson().replace(oldIndicatorId, calcMo.getIndicatorId()));
                    calcMo.setJson(calcMo.getJson().replace(oldSid, sid));
                    for (CalcorEntity calcorEntity : calcorEntities) {
                        String newCalcorId = com.yw.common.utils.UUIDGenerator.getUUID();
                        String oldCalcorid = calcorEntity.getId();
                        calcorId.put(oldCalcorid, newCalcorId);
                        calcorEntity.setCalcModuleId(newId);
                        calcorEntity.setId(newCalcorId);
                        calcorEntity.setSid(sid);
                        calcorEntity.setIndicatorId(calcMo.getIndicatorId());
                        String aliasId = calcorEntity.getAliasId();
                        if (StringUtils.isEmpty(aliasId) || StringUtils.isEmpty(aliasIdMap.get(aliasId))) {
                            continue;
                        }
                        calcorEntity.setAliasId(aliasIdMap.get(aliasId));
                        calcMo.setJson(calcMo.getJson().replace(oldCalcorid, newCalcorId));
                        calcMo.setJson(calcMo.getJson().replace(aliasId, calcorEntity.getAliasId()));
                    }
                    for (CalcorEntity calcorEntity : calcorEntities) {
                        if (!StringUtils.isEmpty(calcorEntity.getPid()) && !calcorEntity.getPid().equals("0")) {
                            calcorEntity.setPid(calcorId.get(calcorEntity.getPid()));
                        }
                        if (!StringUtils.isEmpty(calcorEntity.getIns())) {
                            calcorEntity.setIns(calcorId.get(calcorEntity.getIns()));
                        }
                        if (!StringUtils.isEmpty(calcorEntity.getOus())) {
                            calcorEntity.setOus(calcorId.get(calcorEntity.getOus()));
                        }
                    }
                } else {
                    String methodId = calcMo.getMethodId();
                    EvaluationType typeByName = EvaluationType.getTypeByName(methodId);
                    calcMo.setName(typeByName.getChName());

                    if (typeByName == EvaluationType.GEA_METHOD && StringUtils.isNotEmpty(calcMo.getAlgParam())) {
                        // 处理灰色关联算法的配置信息
                        try {
                            GeaDTO geaDTO = JSONUtil.toBean(calcMo.getAlgParam(), GeaDTO.class);
                            if (ObjectUtil.isNotNull(geaDTO.getResolutionCoefficient())) {
                                List<GeaIdealSequenceDTO> geaIdealSequenceDTOLists = geaDTO.getGeaIdealSequenceDTOLists();
                                for (GeaIdealSequenceDTO geaIdealSequenceDTOList : geaIdealSequenceDTOLists) {
                                    String childIndicatorId = geaIdealSequenceDTOList.getIndicatorId();
                                    geaIdealSequenceDTOList.setIndicatorId(indicatorIdsMap.get(childIndicatorId));
                                }
                            }
                            calcMo.setAlgParam(JSON.toJSONString(geaDTO));
                        } catch (Exception e) {
                            log.error("无需要转换的算法配置数据");
                        }
                    }
                    if (typeByName == EvaluationType.DICTIONARY_ORDER_METHOD && StringUtils.isNotEmpty(calcMo.getAlgParam())) {
                        // 处理灰色关联算法的配置信息
                        try {
                            List<DictionaryOrderParaDTO> dictionaryOrderParaDTOS = JSONUtil.toList(JSONUtil.parseArray(calcMo.getAlgParam()), DictionaryOrderParaDTO.class);
                            if (CollUtil.isNotEmpty(dictionaryOrderParaDTOS)) {
                                for (DictionaryOrderParaDTO dictionaryOrderParaDTO : dictionaryOrderParaDTOS) {
                                    String childIndicatorId = dictionaryOrderParaDTO.getIndicatorId();
                                    dictionaryOrderParaDTO.setIndicatorId(indicatorIdsMap.get(childIndicatorId));
                                }
                            }
                            calcMo.setAlgParam(JSON.toJSONString(dictionaryOrderParaDTOS));
                        } catch (Exception e) {
                            log.error("无需要转换的算法配置数据");
                        }
                    }
                    if (typeByName == EvaluationType.BP_METHOD && StringUtils.isNotEmpty(calcMo.getJson())) {
                        try {
                            // 处理bp算法的配置信息
                            BaseResponse<Map<String, String>> baseResponse = JSON.parseObject(calcMo.getJson(), new TypeReference<BaseResponse<Map<String, String>>>() {
                            });
                            if (!Objects.isNull(baseResponse)) {
                                if (CollUtil.isNotEmpty(baseResponse.getTableHeader())) {
                                    for (BaseTableHeaderBody baseTableHeaderBody : baseResponse.getTableHeader()) {
                                        String childIndicatorId = baseTableHeaderBody.getProp();
                                        baseTableHeaderBody.setProp(indicatorIdsMap.get(childIndicatorId));
                                    }
                                }
                                if (CollUtil.isNotEmpty(baseResponse.getTableData())) {
                                    List<Map<String, String>> result = Lists.newArrayList();
                                    for (Map<String, String> tableDatum : baseResponse.getTableData()) {
                                        Map<String, String> rowMap = Maps.newLinkedHashMap();
                                        for (Map.Entry<String, String> stringStringEntry : tableDatum.entrySet()) {
                                            String key = stringStringEntry.getKey();
                                            if (indicatorIdsMap.containsKey(key)) {
                                                key = indicatorIdsMap.get(key);
                                            }
                                            rowMap.put(key, stringStringEntry.getValue());
                                        }
                                        result.add(rowMap);
                                    }
                                    baseResponse.setTableData(result);
                                }

                                calcMo.setJson(JSON.toJSONString(baseResponse));
                            }
                        } catch (Exception e) {
                            log.error("无需要转换的算法配置数据");
                        }
                    }
                }
                //算法参数存储
                List<AlgorithmParamEntity> algorithmParamEntityList = algorithmParamMap.get(oldId);
                if (CollUtil.isNotEmpty(algorithmParamEntityList)) {
                    for (AlgorithmParamEntity algorithmParamEntity : algorithmParamEntityList) {
                        algorithmParamEntity.setId(com.yw.common.utils.UUIDGenerator.getUUID());
                        algorithmParamEntity.setIndicatorId(indicatorIdsMap.get(algorithmParamEntity.getIndicatorId()));
                        algorithmParamEntity.setCalcModuleId(newId);
                    }
//                    algorithmParamRepository.saveAll(algorithmParamEntityList);
                    iAlgorithmParamMapper.savaBatch(algorithmParamEntityList);
                }
                if (StringUtils.isNotEmpty(calcMo.getMethodId()) && calcMo.getMethodId().equals(EvaluationType.ADC_MEANS.getName()) && CollUtil.isNotEmpty(schemaAllDataDTO.getAdcCalcModelParamDTOMap()) && !Objects.isNull(schemaAllDataDTO.getAdcCalcModelParamDTOMap().get(oldId))) {
                    saveAdcCalcModelParam(schemaAllDataDTO.getAdcCalcModelParamDTOMap().get(oldId), newId, indicatorIdsMap, evaluationObject);
                }
                iCalcModuleService.saveWithoutValid(calcMo);
                if (CollUtil.isNotEmpty(calcorEntities)) {
//                    calcorRepository.saveAll(calcorEntities);
                    calcorService.saveBatch(calcorEntities);
                }

            }
        }
    }

    private void saveAdcCalcModelParam(AdcCalcModelParamDTO adcCalcModelParamDTO, String newId, Map<String, String> indicatorIdsMap, Integer evaluationObject) {
        AlgorithmConfigureEntity configureEntity = adcCalcModelParamDTO.getConfigureEntity();
        if (configureEntity != null) {
            String newConfigId = com.yw.common.utils.UUIDGenerator.generateShortUUID();
            configureEntity.setSid(newId);
            configureEntity.setEquId(evaluationObject);
            configureEntity.setId(newConfigId);
//            iAlgorithmConfigureRepository.saveAndFlush(configureEntity);
            if (algorithmConfigureMapper.selectCount(new QueryWrapper<AlgorithmConfigureEntity>().setEntity(configureEntity)) > 0) {
                algorithmConfigureMapper.updateById(configureEntity);
            } else {
                algorithmConfigureMapper.insert(configureEntity);
            }
            List<ComponentInfoEntity> componentInfoEntities = adcCalcModelParamDTO.componentInfoEntities;
            if (CollUtil.isNotEmpty(componentInfoEntities)) {
                Map<String/*old component id*/, String/*new component id*/> componentIdMap = Maps.newHashMap();
                for (ComponentInfoEntity componentInfoEntity : componentInfoEntities) {
                    String oldComponentId = componentInfoEntity.getId();
                    String newComponentId = com.yw.common.utils.UUIDGenerator.generateShortUUID();
                    componentIdMap.put(oldComponentId, newComponentId);
                    componentInfoEntity.setId(newComponentId);
                    componentInfoEntity.setAlgId(newConfigId);
                    componentInfoEntity.setSid(newId);
                    if (!StringUtils.isEmpty(componentInfoEntity.getIndicatorId())) {
                        componentInfoEntity.setIndicatorId(indicatorIdsMap.get(componentInfoEntity.getIndicatorId()));
                    }
                }
//                iComponentInfoRepository.saveAll(componentInfoEntities);
                componentInfoEntities.forEach(item -> {
                    componentInfoMapper.insert(item);
                });
                List<PartsStateEntity> partsStateEntities = adcCalcModelParamDTO.partsStateEntities;
                if (CollUtil.isNotEmpty(partsStateEntities)) {
                    Map<String/*old partState id*/, String/*new partState id*/> partsStateMap = Maps.newHashMap();

                    for (PartsStateEntity entity : partsStateEntities) {
                        String oldPartsStateId = entity.getId();
                        String newPartsStateId = com.yw.common.utils.UUIDGenerator.generateShortUUID();
                        partsStateMap.put(oldPartsStateId, newPartsStateId);
                        entity.setSid(newId);
                        entity.setId(newPartsStateId);
                    }
//                    iPartsStateRepository.saveAll(partsStateEntities);
                    partsStateEntities.forEach(item -> {
                        partsStateMapper.insert(item);
                    });
                    List<CompStatusRelationEntity> bySidOrderByCreateTimeDesc = adcCalcModelParamDTO.bySidOrderByCreateTimeDesc;
                    if (CollUtil.isNotEmpty(bySidOrderByCreateTimeDesc)) {
                        for (CompStatusRelationEntity entity : bySidOrderByCreateTimeDesc) {
                            entity.setId(com.yw.common.utils.UUIDGenerator.generateShortUUID());
                            entity.setSid(newId);
                            entity.setCompId(componentIdMap.get(entity.getCompId()));
                            entity.setStateId(partsStateMap.get(entity.getStateId()));
                        }
//                        iCompStatusRelationRepository.saveAll(bySidOrderByCreateTimeDesc);
                        bySidOrderByCreateTimeDesc.forEach(item -> {
                            compStatusRelationMapper.insert(item);
                        });
                    }
                }
            }
        }

    }

    private void saveSceneInfo(String sid, Map<String, String> dataSrcIdMaps, List<SceneEntity> sceneEntities, SchemaAllDataDTO schemaAllDataDTO,
                               Map<String/*old sceneId*/, String/*new sceneId*/> oldKeyNewValueMap) {
        if (!CollUtil.isEmpty(sceneEntities)) {
            Map<String, String> idMap = new HashMap<>();
            int count = 1;
            for (SceneEntity sceneEntity : sceneEntities) {
                //sceneEntity.setName("样本-" + count++);
                idMap.put(sceneEntity.getId(), sceneEntity.getName());
                // ID置为空，就能再save之后获取到新的ID
                String newSceneId = UUIDGenerator.getUUID();
                String oldSceneId = sceneEntity.getId();
                oldKeyNewValueMap.put(oldSceneId, newSceneId);
                sceneEntity.setId(newSceneId);
                sceneEntity.setSid(sid);
                Map<String, String> newSampleTaskMap = Maps.newHashMap();
                Map<String, String> sampleTaskMap = sceneEntity.getSampleTaskMap();
                if (!CollUtil.isEmpty(sampleTaskMap)) {
                    sampleTaskMap.forEach((k, v) -> {
                        newSampleTaskMap.put(dataSrcIdMaps.get(k), v);
                    });
                }
                sceneEntity.setSampleTaskMap(newSampleTaskMap);
            }
            sceneService.saveAll(sceneEntities);

            // 处理样本关联关系的样本ID
            if (!CollectionUtils.isEmpty(schemaAllDataDTO.getAlgorithmEntityList())) {
                sceneEntities.stream().forEach(sceneEntity -> {
                    for (String key : idMap.keySet()) {
                        if (StringUtils.equals(idMap.get(key), sceneEntity.getName())) {
                            idMap.put(key, sceneEntity.getId());
                        }
                    }
                });
//
//                schemaAllDataDTO.getAlgorithmEntityList().forEach(algorithmEntity -> {
//                    algorithmEntity.setSceneId(idMap.get(algorithmEntity.getSceneId()));
//                });
            }
        }
    }

    private void saveSceneTableInfo(Map<String, DatasrcEntity> dataSrcInfoMaps, SchemaAllDataDTO schemaAllDataDTO, String sid,
                                    Map<String, String> dataSrcIdMaps, Map<String, String> customDataNames, Map<String, String> sceneTableIdMap,
                                    Map<String/*ole aliasId id*/, String/*new aliasId id*/> aliasIdMap,
                                    Map<String, String> indicatorIdsMap) {
        List<SceneTableEntity> sceneTableEntities = schemaAllDataDTO.getSceneTableEntities();
        List<SchemaAliasEntity> aliasEntities = Lists.newArrayList();
        if (!CollUtil.isEmpty(sceneTableEntities)) {
            for (SceneTableEntity entity : sceneTableEntities) {
                String oleSceneTableId = entity.getId();
                String sceneTableId = com.yw.common.utils.UUIDGenerator.getUUID();
                entity.setSid(sid);
                String oldDsid = entity.getDsid();
                String newDsid = dataSrcIdMaps.get(oldDsid);
                DatasrcEntity datasrcEntity = dataSrcInfoMaps.get(oldDsid);
                String tableName = entity.getTableName();
                if (datasrcEntity.getTaskType() == DataAnalysisType.CUSTOM_DATA.getId()) {
                    tableName = customDataNames.get(tableName);
                }
                entity.setDsid(newDsid);
                sceneTableIdMap.put(oleSceneTableId, sceneTableId);
                entity.setId(sceneTableId);
                entity.setDbName(datasrcEntity.getDatabaseName());
                String aliasId = com.yw.common.utils.UUIDGenerator.getUUID();
                String oldAliasId = entity.getAliasId();
                aliasIdMap.put(oldAliasId, aliasId);
                entity.setAliasId(aliasId);
                entity.setTableName(tableName);
                entity.setIndicatorId(indicatorIdsMap.get(entity.getIndicatorId()));
                // 过滤重复数据
                String finalTableName = tableName;
                List<SchemaAliasEntity> tempList = aliasEntities.stream().filter(item -> StringUtils.equals(item.getSid(), sid)).filter(item ->
                        StringUtils.equals(item.getAlias(), finalTableName)).filter(item -> StringUtils.equals(item.getDsid(), newDsid)).collect(Collectors.toList());
                if (CollectionUtils.isEmpty(tempList)) {
                    aliasEntities.add(SchemaAliasEntity.builder().alias(tableName).sid(sid).id(aliasId).dsid(newDsid).build());
                }
            }
            iSceneTableService.saveAll(sceneTableEntities, aliasEntities);
        }
    }

    private void saveDbTAble(SchemaAllDataDTO schemaAllDataDTO, Map<String, String> dataSrcIdMaps, Map<String, DatasrcEntity> dataSrcInfoMaps,
                             Map<String, String> customDataNames, Map<String/*data src id */, List<String>/*db table*/> saveDbTable,
                             Map<String, String> dataDbNameMaps) {
        // 自定义数据
        List<CustomDataEntity> customDataEntities = schemaAllDataDTO.getCustomDataEntities();
        if (CollUtil.isNotEmpty(customDataEntities)) {
            for (CustomDataEntity customDataEntity : customDataEntities) {
                customDataNames.put(customDataEntity.getCustomName(), customDataEntity.getCustomName());
            }
        }

//        if (!CollUtil.isEmpty(customDataEntities)) {
//            for (CustomDataEntity dataEntity : customDataEntities) {
//                String customName = dataEntity.getCustomName();
//                dataEntity.setCustomName(customName + new Random().nextInt() * 10000 + "-(评估任务导入)");
//                dataEntity.setId(null);
//                String oldDataSrcId = dataEntity.getDataSrc();
//                String oldDataBaseName = dataDbNameMaps.get(oldDataSrcId);
//                dataEntity.setDataSrc(dataSrcIdMaps.get(oldDataSrcId));
//                String executeSql = dataEntity.getExecuteSql();
//                if (StringUtils.isNotEmpty(executeSql)) {
//                    DatasrcEntity datasrcEntity = dataSrcInfoMaps.get(oldDataSrcId);
//                    dataEntity.setExecuteSql(executeSql.replace(oldDataBaseName, datasrcEntity.getDatabaseName()));
//                }
//                dataEntity.setCreateDate(new Date());
//                customDataNames.put(customName, dataEntity.getCustomName());
//            }
//            customDataService.saveOrUpdateBatch(customDataEntities);
//        }
//        System.err.println("开始数据表导入。。。。。");
//        List<TableInfoVO> tableDataInfos = schemaAllDataDTO.getTableDataInfos();
//        if (CollUtil.isNotEmpty(tableDataInfos)) {
//            CountDownLatch downLatch = new CountDownLatch(tableDataInfos.size());
//            for (TableInfoVO vo : tableDataInfos) {
//                CompletableFuture.runAsync(() -> {
//                    String voDataSrcId = vo.getDataSrcId();
//                    String dataSrcId = dataSrcIdMaps.get(voDataSrcId);
//                    DatasrcEntity datasrcEntity = dataSrcInfoMaps.get(voDataSrcId);
//                    String dataSrcName = vo.getDataSrcName();
//                    List<String> saveDbTables = saveDbTable.get(dataSrcId);
//                    if (CollUtil.isEmpty(saveDbTables)) {
//                        saveDbTables = new ArrayList<>();
//                    }
//                    saveDbTables.add(dataSrcName);
//                    saveDbTable.put(voDataSrcId, saveDbTables);
//                    int tableType = 2;
//                    if (datasrcEntity.getIncludeTasks() == DataTableType.INCLUDE_TASK_DATA_SHEET.id && dataSrcName.equals(datasrcEntity.getTaskName())) {
//                        tableType = 1;
//                    }
//                    List<Map<String, Object>> list = vo.getList();
//                    if (list.size() > 500) {
//                        int count = 0;
//                        List<Map<String, Object>> data = new ArrayList<>(500);
//                        while (list.size() - 1 != count) {
//                            int i = 0;
//                            while (i != 500 && count != list.size() - 1) {
//                                data.add(list.get(count));
//                                count++;
//                                i++;
//                            }
//                            dataBasesOperationHandler.saveV2("ID", datasrcEntity.createDblinkDto().createDbLinkBo(), 1, vo.getDataSrcName(), vo.getTableFields(), data, tableType, datasrcEntity.getIncludeTasks());
//                            data = new ArrayList<>(500);
//                        }
//                    } else {
//                        dataBasesOperationHandler.saveV2("ID", datasrcEntity.createDblinkDto().createDbLinkBo(), 1, vo.getDataSrcName(), vo.getTableFields(), list, tableType, datasrcEntity.getIncludeTasks());
//                    }
//                    downLatch.countDown();
//                }).exceptionally((throwable -> {
//                    log.error("exception info: {}", throwable.getMessage());
//                    downLatch.countDown();
//                    return null;
//                }));
//            }
//            try {
//                downLatch.await();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
    }

    private List<DatasrcEntity> saveDataSrcInfo(SchemaAllDataDTO schemaAllDataDTO, Map<String, String> dataSrcIdMaps,
                                                Map<String/*old data src id*/, DatasrcEntity/*new data src info*/> dataSrcInfoMaps,
                                                Map<String/*old data src id*/, String/*old data dbName*/> dataDbNameMaps) {
        List<DatasrcEntity> datasrcEntities = schemaAllDataDTO.getDatasrcEntities();
        if (CollUtil.isEmpty(datasrcEntities)) {
            return datasrcEntities;
        }
        DataSourceParam init = BeanUtils.getBean(DataSourceParam.class);
        if (org.springframework.util.StringUtils.isEmpty(init.getHost())) {
            init.setHost(null);
        }
        List<DatasrcEntity> iDatasrcRepositoryAll = dataSrcService.list();
        int count = CollUtil.isEmpty(iDatasrcRepositoryAll) ? 1 : iDatasrcRepositoryAll.size();
        for (DatasrcEntity datasrcEntity : datasrcEntities) {
            //V0.17版本数据源统一放到数据源管理，多评估工程共用，不再处理数据源相关
            dataSrcIdMaps.put(datasrcEntity.getId(), datasrcEntity.getId());
            dataSrcInfoMaps.put(datasrcEntity.getId(), datasrcEntity);
            dataDbNameMaps.put(datasrcEntity.getId(), datasrcEntity.getDatabaseName());

//            if (datasrcEntity.getTaskType() == DataAnalysisType.CUSTOM_DATA.getId()) {
////                Optional<DatasrcEntity> datasrcEntityOptional = iDatasrcRepository.findOne(Example.of(DatasrcEntity.builder().taskType(DataAnalysisType.CUSTOM_DATA.getId()).build()));
//                Optional<DatasrcEntity> datasrcEntityOptional = Optional.ofNullable(dataSrcService.getOne(new QueryWrapper<DatasrcEntity>().setEntity(DatasrcEntity.builder().taskType(DataAnalysisType.CUSTOM_DATA.getId()).build())));
//                DatasrcEntity var1 = datasrcEntityOptional.get();
//                dataSrcIdMaps.put(datasrcEntity.getId(), var1.getId());
//                datasrcEntity.setIp(var1.getIp());
//                dataSrcInfoMaps.put(datasrcEntity.getId(), var1);
//                continue;
//            }
//            String oldDataSrcId = datasrcEntity.getId();
//            String newDataSrcId = com.yw.common.utils.UUIDGenerator.getUUID();
//            String databaseName = datasrcEntity.getDatabaseName();
//            dataDbNameMaps.put(oldDataSrcId, databaseName);
//            String newDatabaseName = "A" + com.yw.common.utils.UUIDGenerator.getUUID().substring(0, 12).toUpperCase(Locale.ROOT);
//            datasrcEntity.setId(newDataSrcId);
//            datasrcEntity.setIp(init.getHost());
//            datasrcEntity.setDbUser(init.getUserName());
//            datasrcEntity.setPass(init.getUserPassword());
//            if ("mysql".equals(datasrcEntity.getType())) {
//                datasrcEntity.setDatabaseName("mysql");
//            } else {
//                datasrcEntity.setDatabaseName(init.getUserName());
//            }
//            datasrcEntity.setPort(init.getPort() + "");
//            datasrcEntity.setCreateDate(new Date());
//            datasrcEntity.setAnalysisId("");
//            datasrcEntity.setName("数据源-" + ++count + new Random().nextInt() * 10 + "(评估任务导入)");
//            dataSrcIdMaps.put(oldDataSrcId, newDataSrcId);
//            dataSrcInfoMaps.put(oldDataSrcId, datasrcEntity);
//            DataBaseHandler bean = BeanUtils.getBean(DataBaseHandler.class);
//            DbLinkBO dbLinkBo = datasrcEntity.createDblinkDto().createDbLinkBo();
//            //创建数据库
//            try {
//                bean.createDatabase(newDatabaseName, dbLinkBo);
//            } catch (Exception e) {
//                if (!e.getMessage().contains("database exists")) {
//                    throw new BusinessException(e.getMessage());
//                }
//            }
////            iDatasrcRepository.saveAndFlush(datasrcEntity);
//            if ("mysql".equals(datasrcEntity.getType())) {
//                datasrcEntity.setDatabaseName(newDatabaseName);
//            }
//            datasrcEntity.setDatabaseName(newDatabaseName);
//            dataSrcService.saveOrUpdate(datasrcEntity);
        }

        return datasrcEntities;
    }

    private void saveIndicatorStandard(SchemaAllDataDTO schemaAllDataDTO, String sid, Map<String, String> indicatorIdsMap) {
        List<IndicatorStandardGroupDTO> indicatorStandardGroupDTOList = schemaAllDataDTO.getIndicatorStandardGroupDTOList();
        if (!CollectionUtils.isEmpty(indicatorStandardGroupDTOList)) {
            indicatorStandardGroupDTOList.forEach(indicatorStandardGroupDTO -> {
                Map<String, List<IndicatorStandardVO>> standardMap = indicatorStandardGroupDTO.getStandardMap();
                for (Map.Entry<String, List<IndicatorStandardVO>> standard : standardMap.entrySet()) {
                    StandardIndicatorDTO dto = new StandardIndicatorDTO();
                    String key = standard.getKey();//old indicatorId
                    List<IndicatorStandardVO> value = standard.getValue();
                    String newIndicatorId = indicatorIdsMap.get(key);
                    dto.setSid(sid);
                    dto.setIndicatorId(newIndicatorId);
                    List<StandardInsertDTO> dtos = Lists.newArrayList();
                    for (IndicatorStandardVO vo : value) {
                        dtos.add(StandardInsertDTO.valueOf(vo.getLabelId(), com.yw.common.utils.UUIDGenerator.getUUID(), vo.getLableName(), vo.getValue(),
                                vo.getFlag(), vo.getMax(), vo.getMin(), vo.getMinSymbol(), vo.getMaxSymbol(), vo.getScriptPath(),
                                vo.getStatus(), vo.getFunc(), vo.getIndicatorStanName()));
                    }
                    dto.setDtos(dtos);
                    standardLabelsService.save(dto);
                }
            });
        }
    }

    private List<IndicatorEntity> saveIndicatorEntity(SchemaAllDataDTO schemaAllDataDTO, String sid, List<IndicatorTreeEntity> indicatorTreeEntityList,
                                                      Map<String, String> indicatorIdsMap, Map<String/*old indicatorId*/, List<String>/*old indicatorId*/> indicatorPidMap) {
        // 新指标计算参数map key newIndicatorId value params
        Map<String, List<IndicatorCalculationParameter>> paramMap = new HashMap<>();

        List<IndicatorCalculationParameter> oldIndicatorParameter = schemaAllDataDTO.getIndicatorCalculationParameters();
        Map<String, List<IndicatorCalculationParameter>> indicatorParameterMap = Maps.newHashMap();
        if (CollUtil.isNotEmpty(oldIndicatorParameter)) {
            indicatorParameterMap = oldIndicatorParameter.stream().collect(Collectors.groupingBy(IndicatorCalculationParameter::getIndicatorId));
        }
        List<IndicatorEntity> indicatorEntities = schemaAllDataDTO.getIndicatorEntities();
        for (IndicatorEntity entity : indicatorEntities) {
            String newIndicatorId = UUIDGenerator.getUUID();
            String oldIndicatorId = entity.getId();
            if (indicatorParameterMap.containsKey(oldIndicatorId)) {
                List<IndicatorCalculationParameter> indicatorCalculationParameters = indicatorParameterMap.get(oldIndicatorId);
                for (IndicatorCalculationParameter parameter : indicatorCalculationParameters) {
                    parameter.setIndicatorId(newIndicatorId);
                    parameter.setSchemaId(sid);
                    parameter.setId(UUIDGenerator.getUUID());
                    if (parameter.getSource() != null && !"".equals(parameter.getSource())) {
                        String[] split = parameter.getSource().split(",");
                        if (split.length > 1) {
                            parameter.setSourceDisplay(split[1]);
                        }
                    }
                }
                indicatorCalculationParameterService.saveOrUpdateBatch(indicatorCalculationParameters);
                paramMap.put(newIndicatorId, indicatorCalculationParameters);
            }
            indicatorIdsMap.put(oldIndicatorId, newIndicatorId);
            List<String> list = indicatorPidMap.get(newIndicatorId);
            if (CollUtil.isEmpty(list)) {
                list = Lists.newArrayList();
                indicatorPidMap.put(entity.getPid(), list);
            }
            list.add(oldIndicatorId);

            // 同步映射矩阵对象里的指标ID
            if (!CollectionUtils.isEmpty(schemaAllDataDTO.getIndicatorCriteriaList())) {
                schemaAllDataDTO.getIndicatorCriteriaList().forEach(indicatorCriteria -> {
                    if (StringUtils.equals(indicatorCriteria.getIndicatorId(), entity.getId())) {
                        indicatorCriteria.setIndicatorId(newIndicatorId);
                    }
                });
            }

            entity.setId(newIndicatorId);
            entity.setCreateDate(LocalDateTime.now());
            entity.setSid(sid);
        }
        for (IndicatorEntity entity : indicatorEntities) {
            if (entity.getPid().equals("0")) {
                continue;
            }
            entity.setName(new String(entity.getName().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            //替换指标的父级id
            entity.setPid(indicatorIdsMap.get(entity.getPid()));
        }

        if (CollUtil.isEmpty(indicatorTreeEntityList)) {
            indicatorTreeEntityList = Lists.newArrayList();
        }
        indicatorTreeEntityList.forEach(indicatorTreeEntity -> {
            List<LinkDataDTO> linkDataArray = Lists.newArrayList();
            List<NodeDataDTO> nodeDataArray = Lists.newArrayList();
            indicatorEntities.stream().forEach(s -> {
                if (StringUtils.equals(s.getTreeId(), indicatorTreeEntity.getId())) {
                    if (null != s.getLevel() && s.getLevel().intValue() != 0) {
                        linkDataArray.add(LinkDataDTO.valueOf(s.getPid(), s.getId()));
                    }
                    NodeDataDTO nodeDataDTO = NodeDataDTO.valueOf(s, indicatorTreeEntity.getId());
                    nodeDataDTO.getData().setIndicatorCalculationParameter(paramMap.get(s.getId()));
                    nodeDataArray.add(nodeDataDTO);
                }
            });
            JsonDTO jsonDTO = JsonDTO.valueOf(linkDataArray, nodeDataArray);
            String jsonString = JSON.toJSONString(jsonDTO);
            indicatorTreeEntity.setJson(jsonString);
//            indicatorTreeRepository.saveAndFlush(indicatorTreeEntity);
            indicatorTreeService.saveOrUpdate(indicatorTreeEntity);
        });

        indicatorService.saveAll(indicatorEntities);
        return indicatorEntities;
    }

    private List<IndicatorTreeEntity> saveIndicatorTreeEntity(SchemaAllDataDTO schemaAllDataDTO, String sid) {
        List<IndicatorTreeEntity> indicatorTreeEntityList = schemaAllDataDTO.getIndicatorTreeEntityList();
        if (!CollectionUtils.isEmpty(indicatorTreeEntityList)) {
            indicatorTreeEntityList.forEach(indicatorTreeEntity -> {
                indicatorTreeSave(schemaAllDataDTO, sid, indicatorTreeEntity);
            });
        } else {
            IndicatorTreeEntity indicatorTreeEntity = schemaAllDataDTO.getIndicatorTreeEntity();
            indicatorTreeSave(schemaAllDataDTO, sid, indicatorTreeEntity);
            return Lists.newArrayList(indicatorTreeEntity);
        }


        return indicatorTreeEntityList;
    }

    private void indicatorTreeSave(SchemaAllDataDTO schemaAllDataDTO, String sid, IndicatorTreeEntity indicatorTreeEntity) {
        String indicatorTreeId = com.yw.common.utils.UUIDGenerator.getUUID();
        indicatorTreeEntity.setSid(sid);
//        List<IndicatorTreeEntity> indicatorTreeEntityAllList = indicatorTreeRepository.findAll();
        List<IndicatorTreeEntity> indicatorTreeEntityAllList = indicatorTreeService.list();
        Optional<Long> schemaNameNum;
        if (1 == indicatorTreeEntity.getIndicatorTreeType().intValue()) {
            schemaNameNum = indicatorTreeEntityAllList.stream().filter(f -> pattern.matcher(f.getName()).find())
                    .map(m -> Long.parseLong(m.getName().replace("能力指标树", "")))
                    .max(Long::compareTo);
        } else {
            schemaNameNum = indicatorTreeEntityAllList.stream().filter(f -> pattern_1.matcher(f.getName()).find())
                    .map(m -> Long.parseLong(m.getName().replace("效能指标树", "")))
                    .max(Long::compareTo);
        }

        long num = schemaNameNum.map(aLong -> (aLong + 1)).orElse(1L);
        indicatorTreeEntity.setName(1 == indicatorTreeEntity.getIndicatorTreeType().intValue() ? "能力指标树" + num : "效能指标树" + num);
        indicatorTreeEntity.setCreateDate(LocalDateTime.now());

        // 重置指标树ID之前，先将下属的指标的所属指标树同步修改
        if (!CollectionUtils.isEmpty(schemaAllDataDTO.getIndicatorEntities())) {
            schemaAllDataDTO.getIndicatorEntities().forEach(indicatorEntity -> {
                if (StringUtils.equals(indicatorEntity.getTreeId(), indicatorTreeEntity.getId())) {
                    indicatorEntity.setTreeId(indicatorTreeId);
                }
            });
        }

        // 同步修改样本的指标树标识
        if (!CollectionUtils.isEmpty(schemaAllDataDTO.getSceneEntities())) {
            schemaAllDataDTO.getSceneEntities().forEach(sceneEntity -> {
                if (StringUtils.equals(sceneEntity.getTreeId(), indicatorTreeEntity.getId())) {
                    sceneEntity.setTreeId(indicatorTreeId);
                }
            });
        }

        // 同步修改样本数据结构的指标树标识
        if (!CollectionUtils.isEmpty(schemaAllDataDTO.getSceneTableEntities())) {
            schemaAllDataDTO.getSceneTableEntities().forEach(sceneTableEntity -> {
                if (StringUtils.equals(sceneTableEntity.getIndicatorTreeId(), indicatorTreeEntity.getId())) {
                    sceneTableEntity.setIndicatorTreeId(indicatorTreeId);
                }
            });
        }

        indicatorTreeEntity.setId(indicatorTreeId);
    }

    /**
     * 根据装备以及确定参数 修改 装备参数表
     *
     * @param equId
     * @param dto
     * @return
     */
    public List<EquParamEntity> editEquParam(int equId, List<EquCompositionDTO> dto) {
        List<EquParamEntity> resultParam = Lists.newArrayList();
        List<EquParamEntity> paramEntityList = equParamMapper.findByEquId(equId);
        if (paramEntityList != null && !paramEntityList.isEmpty()) {
            //将现有的装备参数进行修改 并且移除不需要信息
            for (Iterator<EquParamEntity> entityIterator = paramEntityList.iterator(); entityIterator.hasNext(); ) {
                EquParamEntity paramEntity = entityIterator.next();
                String enParam = paramEntity.getEnParam();
                for (Iterator<EquCompositionDTO> iterator = dto.iterator(); iterator.hasNext(); ) {
                    EquCompositionDTO next = iterator.next();
                    if (enParam.equals(next.getParamName())) {
                        resultParam.add(EquParamEntity.valueOf(paramEntity.getId(), enParam, equId, next.getNum(), next.getCompany()));
                        iterator.remove();
                        entityIterator.remove();
                    }
                }
            }
            if (!paramEntityList.isEmpty()) {
                //删除不需要的装备参数
                equParamService.removeBatchByIds(paramEntityList.stream().map(EquParamEntity::getId).collect(Collectors.toList()));
            }
        }
        if (dto != null && !dto.isEmpty()) {
            //设置遗留的装备参数信息
            dto.stream().forEach(s -> {
                resultParam.add(EquParamEntity.valueOf(s.getParamName(), equId, s.getNum(), s.getCompany()));
            });
        }
        return resultParam;
    }

    private Integer saveEvaluationObject(SchemaAllDataDTO schemaAllDataDTO, SchemaEntity newSchemaEntity) {
        String evaluationObject = schemaAllDataDTO.getEvaluationObject();
        EquAndEquSysDTO equipment = JSON.parseObject(evaluationObject, EquAndEquSysDTO.class);
        List<EquipmentEntity> equipmentEntitie = equipmentService.list();
        int count = CollUtil.isEmpty(equipmentEntitie) ? 1 : equipmentEntitie.size();
        if (newSchemaEntity.getEvaluationObject().contains("equ")) {
            //装备信息表
            List<EquipmentEntity> equipmentEntities = equipment.getEquipmentEntities();
            EquipmentEntity entity = equipmentEntities.get(0);
            saveEqu(entity, count);
            newSchemaEntity.setEvaluationObject("equ" + entity.getId());
            return entity.getId();
        } else {
            //装备体系信息表
            EquipmentSystemEntity equipmentSystemEntity = equipment.equipmentSystemEntity;
            equipmentSystemEntity.setId(null);
            List<EquipmentSystemEntity> equipmentSystemEntities = equipmentSystemService.list();
            int sysCount = CollUtil.isEmpty(equipmentSystemEntities) ? 1 : equipmentSystemEntities.size();
            equipmentSystemEntity.setEquSysName("装备体系-" + sysCount + "(导入)");
            equipmentSystemService.saveOrUpdate(equipmentSystemEntity);
            newSchemaEntity.setEvaluationObject("eSys" + equipmentSystemEntity.getId());
            List<EquipmentEntity> equipmentEntities = equipment.equipmentEntities;
            List<Integer> equIds = Lists.newArrayList();
            if (!CollUtil.isEmpty(equipmentEntities)) {
                for (EquipmentEntity entity : equipmentEntities) {
                    saveEqu(entity, count++);
                    equIds.add(entity.getId());
                }
                equipmentSystemEntity.setComposition(JSON.toJSONString(equIds));
                equipmentSystemService.saveOrUpdate(equipmentSystemEntity);
            }
            String statisticalInfo = equipmentSystemEntity.getStatisticalInfo();
            Map map = JSON.parseObject(statisticalInfo, Map.class);
            editEquSysParam(equipmentSystemEntity.getId(), map);
            return equipmentSystemEntity.getId();
        }
    }

    /**
     * 装备体系参数修改
     *
     * @param equSysId
     * @param paramMaps
     */
    public void editEquSysParam(Integer equSysId, Map<String, Integer> paramMaps) {
        List<EquSysParamEntity> resultLists = Lists.newArrayList();
        //获取当前装备以及存在的参数信息
        LambdaQueryWrapper<EquSysParamEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(EquSysParamEntity::getEquSysId, equSysId);
        List<EquSysParamEntity> equSysParamLists
                = equSysParamMapper.selectList(lqw);
        for (Iterator<EquSysParamEntity> iterator = equSysParamLists.iterator(); iterator.hasNext(); ) {
            EquSysParamEntity next = iterator.next();
            String enParam = next.getEnParam();
            Integer paramValue = paramMaps.get(enParam);
            if (paramValue != null) {
                next.setParamValue(paramValue + "");
                resultLists.add(next);
                paramMaps.remove(enParam);
                iterator.remove();
            }
        }
        //移除已存在但是现在不需要
        equSysParamService.removeBatchByIds(equSysParamLists.stream().map(EquSysParamEntity::getId).distinct().collect(Collectors.toList()));
        paramMaps.entrySet().stream().forEach(s -> {
            String enParam = s.getKey();
            Integer paramValue = s.getValue();
            resultLists.add(EquSysParamEntity.valueOf(equSysId, enParam, paramValue));
        });
        //存储信息
        equSysParamService.saveOrUpdateBatch(resultLists);
    }

    private void saveEqu(EquipmentEntity entity, int count) {
        entity.setId(null);

        entity.setEquName("装备-" + count + "(导入)");
        String keyParam = entity.getKeyParam();
        equipmentService.saveOrUpdate(entity);
        if (!StringUtils.isEmpty(keyParam)) {
            List<EquCompositionDTO> equCompositionDTOS = JSON.parseArray(keyParam, EquCompositionDTO.class);
            editEquParam(entity.getId(), equCompositionDTOS);
        }
    }

    private void saveImportTableRecords(Map<String, DatasrcEntity> dataSrcInfoMaps, SchemaAllDataDTO schemaAllDataDTO) throws Exception {

        List<ImportTableRecord> records = schemaAllDataDTO.getImportTableRecords();
        Map<String, List<GatherRoundRecord>> gatherRoundRecordMaps = schemaAllDataDTO.getGatherRoundRecordMaps();

        for (Map.Entry<String, DatasrcEntity> entry : dataSrcInfoMaps.entrySet()) {

            String oldDsid = entry.getKey();
            DatasrcEntity newDatasrc = entry.getValue();

            List<GatherRoundRecord> gatherRoundRecords = gatherRoundRecordMaps.get(oldDsid);

            // root
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("dbName", newDatasrc.getDatabaseName());
            jsonObject.put("dataSrcId", newDatasrc.getId());
            DataDirectory d = new DataDirectory();
            d.setId(UUIDGenerator.getUUID());
            d.setName(newDatasrc.getName());
            d.setParentId("0");
            d.setLeafNodeFlag(ObjectUtils.isEmpty(gatherRoundRecords));
            d.setMark(jsonObject.toJSONString());
            d.setMarkType(10);
            String rootDirectoryId = dataDirectoryService.addDirectoryNotDataSrc(d);
            schemaAllDataDTO.setDataDirectory(d);

            if (ObjectUtils.isEmpty(gatherRoundRecords)) {
                // 没有轮次数据的情况
                List<ImportTableRecord> newRecords = records.stream().filter(r -> r.getFromId().equals(oldDsid)).map(r -> {
                    ImportTableRecord record = BeanUtil.toBean(r, ImportTableRecord.class);
                    record.setId(UUIDGenerator.getUUID());
                    record.setFromId(newDatasrc.getId());
                    record.setTableName(r.getTableName());
                    record.setDirectoryId(rootDirectoryId);
                    record.setCreateTime(LocalDateTime.now());
                    return record;
                }).collect(Collectors.toList());
                if (ObjectUtils.isNotEmpty(newRecords)) {
                    importTableRecordService.addRecords(newRecords);
                }
            } else {

                Queue<ImportTableRecord> recordQueue = records.stream()
                        .filter(r -> r.getFromId().equals(oldDsid))
                        .collect(Collectors.toCollection(LinkedList::new));
                // children (轮次数据)
                for (GatherRoundRecord grr : gatherRoundRecords) {
                    // {"roundList":[{"roundData":"2023-10-08 10:48:21","roundField":"TASKID"}],"dbName":"AD659638E579E","dataSrcId":"6e353b5689d44eda81a8eaf430b4c5f3"}
                    JSONObject j = new JSONObject();
                    j.put("dbName", newDatasrc.getDatabaseName());
                    j.put("dataSrcId", newDatasrc.getId());
                    j.put("roundList", new HashMap<String, Object>() {{
                        put("roundData", grr.getTaskId());
                        put("roundField", grr.getRoundField());
                    }});
                    DataDirectory cd = new DataDirectory();
                    cd.setId(UUIDGenerator.getUUID());
                    cd.setName(grr.getRoundField().toUpperCase() + "-" + grr.getTaskId() + "(导入)");
                    cd.setParentId(rootDirectoryId);
                    cd.setLeafNodeFlag(true);
                    cd.setMark(j.toJSONString());
                    cd.setMarkType(6);
                    String childDirectoryId = dataDirectoryService.addDirectory(cd);
                    ImportTableRecord r = null;
                    while ((r = recordQueue.poll()) != null) {
                        r.setId(UUIDGenerator.getUUID());
                        r.setFromId(newDatasrc.getId());
                        r.setTableName(r.getTableName());
                        r.setDirectoryId(childDirectoryId);
                        r.setCreateTime(LocalDateTime.now());
                        try {
                            importTableRecordService.addRecord(r);
                        } catch (Exception e) {
                            log.error(e.getMessage());
                        }
                    }

                }
            }
        }
    }
//
//    private void saveIndicatorCalculationParameters(String sid, Map<String, String> indicatorIdsMap, SchemaAllDataDTO schemaAllDataDTO) {
//        List<IndicatorCalculationParameter> indicatorCalculationParameters = schemaAllDataDTO.getIndicatorCalculationParameters();
//        indicatorCalculationParameters.forEach(i -> {
//            String oldIndicatorId = i.getIndicatorId();
//            String newIndicatorId = indicatorIdsMap.get(oldIndicatorId);
//            i.setId(UUIDGenerator.getUUID());
//            i.setSchemaId(sid);
//            i.setIndicatorId(newIndicatorId);
//        });
//        indicatorCalculationParameterService.saveBatch(indicatorCalculationParameters);
//    }

    private SchemaEntity setSchemaEntity(SchemaAllDataDTO schemaAllDataDTO, String sid) {
        SchemaEntity schemaEntity = schemaAllDataDTO.getSchemaEntity();
        List<SchemaEntity> schemaEntities = schemaService.find(SchemaEntity.builder().build());
        int count = 1;
        String schemaName = "评估任务-" + (CollUtil.isEmpty(schemaEntities) ? 1 : schemaEntities.size() + 1) + "(导入)";
        //根据方案名称查询是否存在
        while (true) {
            List<SchemaEntity> list = schemaService.list(new LambdaUpdateWrapper<SchemaEntity>().eq(SchemaEntity::getName, schemaName));
            if (CollUtil.isEmpty(list)) {
                break;
            }
            schemaName = "评估任务-" + (CollUtil.isEmpty(schemaEntities) ? count++ : schemaEntities.size() + count++) + "(导入)";
        }
        schemaEntity.setName(schemaName);
        schemaEntity.setId(sid);
        schemaEntity.setCreateDate(LocalDateTime.now());
        schemaEntity.setUpdateDate(LocalDateTime.now());
        schemaEntity.setEvaluationDate(new Date());
        if (schemaEntity.getSchemaEvaluationType() == null) {
            schemaEntity.setSchemaEvaluationType(SchemaEvaluateType.ABILITY_EVALUATE.getId());
        }
        return schemaEntity;
    }


    public IIndicatorService getIndicatorService() {
        return indicatorService;
    }


}




