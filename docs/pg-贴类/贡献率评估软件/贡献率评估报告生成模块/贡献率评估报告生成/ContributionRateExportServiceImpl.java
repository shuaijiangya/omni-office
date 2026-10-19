package com.yw.module.export.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.spire.doc.Document;
import com.spire.doc.Section;
import com.spire.doc.documents.Paragraph;
import com.yw.common.defines.constants.SysConstant;
import com.yw.common.defines.enums.CalModuleType;
import com.yw.common.defines.enums.SchemaEvaluateType;
import com.yw.common.utils.BuildTree;
import com.yw.common.utils.Node;
import com.yw.common.utils.UUIDGenerator;
import com.yw.common.utils.export.word.DocxHelper;
import com.yw.common.utils.export.word.TextStyleBean;
import com.yw.framework.backMgr.dict.service.DictService;
import com.yw.framework.common.defines.exception.custom.DataNotFoundException;
import com.yw.framework.common.util.spring.SpringUtils;
import com.yw.module.calculation.contributionRate.domain.enums.AttributePropertyEnums;
import com.yw.module.calculation.contributionRate.service.ContributionRateCalculatedValueService;
import com.yw.module.calculation.contributionRate.service.ContributionRateResultService;
import com.yw.module.calculation.contributionRate.service.EffectAndAbilityResultService;
import com.yw.module.calculation.domain.entity.CalcModuleEntity;
import com.yw.module.calculation.indicatorCalculation.service.ICalcModuleService;
import com.yw.module.export.domain.dto.IndicatorExportNode;
import com.yw.module.export.domain.vo.EquipmentVo;
import com.yw.module.export.handle.ComplexExportTemplate;
import com.yw.module.export.handle.SimpleExportTemplate;
import com.yw.module.export.service.IContributionRateExportService;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorTreeEntity;
import com.yw.module.indicator.service.IIndicatorService;
import com.yw.module.indicator.service.IIndicatorTreeService;
import com.yw.module.missionScene.domain.dto.CompeteSceneDTO;
import com.yw.module.missionScene.domain.entity.*;
import com.yw.module.missionScene.domain.vo.FighterEntityVO;
import com.yw.module.missionScene.service.CompeteSceneService;
import com.yw.module.missionScene.service.ContributionRateAlgorithmService;
import com.yw.module.missionScene.service.IMissionTaskService;
import com.yw.module.missionScene.service.SceneScenarioService;
import com.yw.module.plan.domain.dto.ContributionRateExportDTO;
import com.yw.module.plan.domain.entity.SchemaEntity;
import com.yw.module.plan.service.ISchemaService;
import com.yw.sys.api.Pair;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/*
 *@ClassName ContributionRateExportServiceImpl
 *@Description TODO 体系贡献率导出实现类
 *@Author admin
 *@Dete 2023/5/25  14:26
 *@version 1.0
 */
@Service
public class ContributionRateExportServiceImpl implements IContributionRateExportService {

    private final static String DEFAULT_STR = "暂无";

    /**
     * 使命任务字典值
     */
    private static final String TREE_DICT_CODE = "dataTree";
    private static final String TREE_DICT_KEY = "tree";

    protected final static Logger log = LoggerFactory.getLogger(ContributionRateExportServiceImpl.class);

    @Autowired
    private IMissionTaskService missionTaskService;

    @Autowired
    private EvaluationReportServiceImpl evaluationReportService;

    @Autowired
    private SceneScenarioService sceneScenarioService;

    @Autowired
    private CompeteSceneService competeSceneService;

    @Autowired
    private ICalcModuleService iCalcModuleService;

    @Autowired
    private ISchemaService schemaService;

    @Autowired
    private ContributionRateCalculatedValueService contributionRateCalculatedValueService;

    @Autowired
    private EffectAndAbilityResultService effectAndAbilityResultService;


    @Autowired
    private ContributionRateAlgorithmService contributionRateAlgorithmService;

    @Autowired
    private ContributionRateResultService contributionRateResultService;

    @Autowired
    private IIndicatorTreeService indicatorTreeService;
    @Autowired
    private IIndicatorService indicatorService;
    @Autowired
    private DictService dictService;

    /**
     * 导出报告
     *
     * @param response http response
     * @param sid      schemaId
     */
    @Override
    public void export(HttpServletResponse response, String sid, ContributionRateExportDTO dto) {
        XWPFDocument word = new XWPFDocument();
        Optional<SchemaEntity> schemaEntityOptional = schemaService.findById(sid);
        if (!schemaEntityOptional.isPresent()) {
            return;
        }
        //无使命任务场景时导出走另一个分支
        try {
            //1为无使命任务场景值，0为默认值有使命任务场景
            if (SysConstant.NO_SCENE_VALUE.equals(dictService.getValue(TREE_DICT_CODE, TREE_DICT_KEY))) {
                SimpleExportTemplate template = SpringUtils.getBean(SimpleExportTemplate.class);
                template.doExport(response, word, schemaEntityOptional.get(), dto);
                return;
            }
        } catch (DataNotFoundException ignored) {
        }
        // 默认单场景 也只能单场景
        //方案下作战场景数据  场景 任务名称
        List<CompeteSceneEntity> competeSceneEntityList = competeSceneService.findAll(Example.of(CompeteSceneEntity.builder().sid(sid).build()));
        if (CollUtil.isEmpty(competeSceneEntityList)) {
            evaluationReportService.doExport(response, schemaEntityOptional.get(), word, dto.getExportType(), "", dto);
            return;
        }
        //想定数据
        String competeSceneId = competeSceneEntityList.get(0).getId();
        List<SceneScenarioEntity> scenarioList = sceneScenarioService.findAll(Example.of(SceneScenarioEntity.builder().sid(sid).competeSceneId(competeSceneId).build()));
        if (CollUtil.isEmpty(scenarioList)) {
            log.error("当前方案未构建场景内容，方案名称：{}", schemaEntityOptional.get().getName());
            return;
        }
        ComplexExportTemplate complexExportTemplate = SpringUtils.getBean(ComplexExportTemplate.class);
        complexExportTemplate.doExport(response, word, schemaEntityOptional.get(), dto, scenarioList, competeSceneEntityList.get(0));
    }
    public void exportBySpireDoc(HttpServletResponse response, String sid, ContributionRateExportDTO dto) {
        Document document = new Document();
        Optional<SchemaEntity> schemaEntityOptional = schemaService.findById(sid);
        if (!schemaEntityOptional.isPresent()) {
            return;
        }
        //无使命任务场景时导出走另一个分支
        try {
            //1为无使命任务场景值，0为默认值有使命任务场景
            if (SysConstant.NO_SCENE_VALUE.equals(dictService.getValue(TREE_DICT_CODE, TREE_DICT_KEY))) {
                SimpleExportTemplate template = SpringUtils.getBean(SimpleExportTemplate.class);
                template.doExportV2(response, document, schemaEntityOptional.get(), dto);
//                buildAssessmentReport(word,sid,dto);
//                evaluationReportService.doExport(response, schemaEntityOptional.get(), word, dto.getExportType(), "", dto);
                return;
            }
        } catch (DataNotFoundException ignored) {
        }
        // 默认单场景 也只能单场景
        //方案下作战场景数据  场景 任务名称
        List<CompeteSceneEntity> competeSceneEntityList = competeSceneService.findAll(Example.of(CompeteSceneEntity.builder().sid(sid).build()));
//        if (CollUtil.isEmpty(competeSceneEntityList)) {
//            evaluationReportService.doExport(response, schemaEntityOptional.get(), word, dto.getExportType(), "", dto);
//            return;
//        }
        //想定数据
        String competeSceneId = competeSceneEntityList.get(0).getId();
        List<SceneScenarioEntity> scenarioList = sceneScenarioService.list(new LambdaQueryWrapper<SceneScenarioEntity>().eq(SceneScenarioEntity::getSid,sid).eq(SceneScenarioEntity::getCompeteSceneId,competeSceneId));
        if (CollUtil.isEmpty(scenarioList)) {
            log.error("当前方案未构建场景内容，方案名称：{}", schemaEntityOptional.get().getName());
            return;
        }
        ComplexExportTemplate complexExportTemplate = SpringUtils.getBean(ComplexExportTemplate.class);
        complexExportTemplate.doExportV2(response, document, schemaEntityOptional.get(), dto, scenarioList, competeSceneEntityList.get(0));
    }

    /**tiant
     * 无使命任务场景时，只需要导出评估报告内容
     *
     * @param word document对象
     * @param sid  评估id
     * @param dto  导出对象
     */
    private void buildAssessmentReport(XWPFDocument word, String sid, ContributionRateExportDTO dto) {
        Optional<SchemaEntity> schemaEntity =
                schemaService.findById(sid);
        if (!schemaEntity.isPresent()) {
            return;
        }
        SchemaEntity schema = schemaEntity.get();

        DocxHelper.createHeadingTitles(word);
        TextStyleBean title = new TextStyleBean();
        title.setContent("1 评估报告");
        title.setColor("000000");
        DocxHelper.createTitle(word, null, 1, title);
        setText(word, "          评估报告主要包括4个章节，分别是评估方案描述、评估指标体系、评估样本结构、评估结果与结论。");

        //2.1 评估方案描述
        if (dto.getPgSchemeIsShow() == 1) {
            //2.1 评估方案描述
            DocxHelper.createHeadingTitles(word);
            TextStyleBean faTitle = new TextStyleBean();
            faTitle.setContent("1.1 评估方案描述");
            faTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, faTitle);
            setText(word, "         主要包括评估方案背景、创建时间、评估对象、评估目的与背景，以文本形式展示。例如：");

            evaluationReportService.setSchemaDescs(word, schema, faTitle);
        }

        //2.2 评估指标体系
        List<IndicatorEntity> abilityIndicatorLists = Lists.newArrayList();
        List<IndicatorEntity> efficacyIndicatorLists = Lists.newArrayList();
        if (dto.getPgIndicatorIsShow() == 1) {
            DocxHelper.createHeadingTitles(word);
            TextStyleBean zbTitle = new TextStyleBean();
            zbTitle.setContent("1.2 评估指标体系");
            zbTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, zbTitle);
            setText(word, "         通过表格和树形图展示能力评估指标体系及其映射关系。");
            // 设置指标
            List<IndicatorExportNode> abilityIndicatorExportNode = getIndicatorExportNode(sid, abilityIndicatorLists, SchemaEvaluateType.ABILITY_EVALUATE);
            if (CollUtil.isEmpty(abilityIndicatorExportNode)) {
                return;
            }
            evaluationReportService.setSchemaIndicatorTableAndImageInfo(word, abilityIndicatorExportNode, "         体系作战能力评估指标体系树形图如下所示：", sid + "1", zbTitle);
            setText(word, "         ");
            List<IndicatorExportNode> efficacyIndicatorExportNode = getIndicatorExportNode(sid, efficacyIndicatorLists, SchemaEvaluateType.EFFICACY_EVALUATE);
            if (CollUtil.isEmpty(abilityIndicatorExportNode)) {
                return;
            }
            setText(word, "         通过表格和树形图展示效能评估指标体系及其映射关系。");
            evaluationReportService.setSchemaIndicatorTableAndImageInfo(word, efficacyIndicatorExportNode, "        体系作战效能评估指标体系树形图如下所示：", sid + "2", zbTitle);
            setText(word, "         ");
        } else {
            getIndicatorExportNode(sid, abilityIndicatorLists, SchemaEvaluateType.ABILITY_EVALUATE);
            getIndicatorExportNode(sid, efficacyIndicatorLists, SchemaEvaluateType.EFFICACY_EVALUATE);
        }

        //2.3 评估样本结构
        if (dto.getPgSampleStructureIsShow() == 1) {
            //2.3 评估样本结构
            DocxHelper.createHeadingTitles(word);
            TextStyleBean ybTitle = new TextStyleBean();
            ybTitle.setContent("1.3 评估样本结构");
            ybTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, ybTitle);
            setText(word, "         通过表格展示评估指标计算需要的所有数据字段名称。");
            List<IndicatorExportNode> abilityIndicatorCalcorExportNodes = Lists.newArrayList();

            List<CalcModuleEntity> calcModuleEntities = iCalcModuleService.find(CalcModuleEntity.builder().sid(sid).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build());

            List<String> calModeAlgIds = Lists.newArrayList();
            if (CollUtil.isNotEmpty(calcModuleEntities)) {
                calModeAlgIds.addAll(calcModuleEntities.stream().map(CalcModuleEntity::getId).collect(Collectors.toList()));
            }

            int number = evaluationReportService.setIndicatorCalcorValueAndNumber(abilityIndicatorLists, calModeAlgIds, sid, abilityIndicatorCalcorExportNodes, 0);
            setText(word, "         体系作战能力评估指标计算需要采集" + number + "项数据，具体如下表所示: ");
            // 添加表格
            evaluationReportService.createDocumentTable(abilityIndicatorCalcorExportNodes, Lists.newArrayList("指标名称", "指标计算需要的数据字段（采集项）"), word);
            setText(word, "\n\n");

            List<IndicatorExportNode> efficacyIndicatorCalcorExportNodes = Lists.newArrayList();
            number = evaluationReportService.setIndicatorCalcorValueAndNumber(efficacyIndicatorLists, calModeAlgIds, sid, efficacyIndicatorCalcorExportNodes, 0);
            setText(word, "         体系作战效能评估指标计算需要采集" + number + "项数据，具体如下表所示: ");
            // 添加表格
            evaluationReportService.createDocumentTable(efficacyIndicatorCalcorExportNodes, Lists.newArrayList("指标名称", "指标计算需要的数据字段（采集项）"), word);
            setText(word, "\n\n");
        }
        //2.4 评估结果与结论
        if (dto.getPgResultIsShow() == 1) {
            //2.4 评估结果与结论
            DocxHelper.createHeadingTitles(word);
            TextStyleBean jgTitle = new TextStyleBean();
            jgTitle.setContent("1.4 评估结果与结论");
            jgTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, jgTitle);
            setText(word, "         " + (StringUtils.isEmpty(schema.getSummary()) ? "未设置评估结论" : schema.getSummary()));
        }
    }

    /**
     * 作战场景构建
     *
     * @param word
     * @param sid
     */
    private void buildOperationalScenarios(XWPFDocument word, String sid, ContributionRateExportDTO dto, List<SceneScenarioEntity> list) {
        //1 作战场景构建
        DocxHelper.createHeadingTitles(word);
        TextStyleBean cjTitle = new TextStyleBean();
        cjTitle.setContent("1 作战场景构建");
        cjTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 1, cjTitle);

        // 是否显示武器系使命任务
        if (dto.getWeaponsSystemIsShow() == 1) {
            weaponsSystemShow(word, sid);
        }

        // 是否显示对抗场景设置
        if (dto.getCompeteSceneIsShow() == 1) {
            CompeteSceneShow(word, sid);
        }


        //  对抗场景多种情况分析是是否显示
        if (dto.getManyCompeteSceneIsShow() == 1) {
            manyCompeteSceneShow(word, list);
        }


    }

    private void manyCompeteSceneShow(XWPFDocument word, List<SceneScenarioEntity> list) {
        //1.3 对抗场景多种情况分析
        DocxHelper.createHeadingTitles(word);
        TextStyleBean dkcjTitle = new TextStyleBean();
        dkcjTitle.setContent("1.3 对抗场景多种情况分析");
        dkcjTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, dkcjTitle);
        //想定数据
        int count = 1;
        for (SceneScenarioEntity sceneScenarioEntity : list) {
            //1.3.1 想定1
            DocxHelper.createHeadingTitles(word);
            TextStyleBean xdTitle = new TextStyleBean();
            xdTitle.setContent("1.3." + count + " " + sceneScenarioEntity.getScenarioName());
            xdTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 3, xdTitle);

            //1.3.1.1 原型基础信息
            DocxHelper.createHeadingTitles(word);
            TextStyleBean jcTitle = new TextStyleBean();
            jcTitle.setContent("1.3." + count + ".1 原型基础信息");
            jcTitle.setColor("000000");
            jcTitle.setFontsize(12);
            DocxHelper.createTitle(word, null, 4, jcTitle);

            XWPFTable table2 = word.createTable(1, 1);
            CTTblPr tblPr2 = table2.getCTTbl().getTblPr();
            tblPr2.getTblW().setType(STTblWidth.DXA);
            tblPr2.getTblW().setW(new BigInteger("8200"));
            XWPFTableRow row1 = table2.getRow(0);
            TextStyleBean text1 = new TextStyleBean();
            // todo 补充
            XWPFTableCell cell1 = row1.getCell(0);
            XWPFParagraph xwpfParagraph1 = cell1.addParagraph();
            XWPFParagraph xwpfParagraph2 = cell1.addParagraph();
            XWPFParagraph xwpfParagraph3 = cell1.addParagraph();
            XWPFParagraph xwpfParagraph4 = cell1.addParagraph();
            evaluationReportService.createRunV2(xwpfParagraph1, text1).setText("情况名称：" + StringUtils.defaultIfEmpty(sceneScenarioEntity.getScenarioName(), DEFAULT_STR));
            //数据转换
            Integer attributeProperty = sceneScenarioEntity.getAttributeProperty();
            if (attributeProperty == null) {
                attributeProperty = 1;
            }
            AttributePropertyEnums attributePropertyEnums = AttributePropertyEnums.getAttributePropertyEnums(attributeProperty);
            String name = attributePropertyEnums.getName();
            evaluationReportService.createRunV2(xwpfParagraph2, text1).setText("情况属性：" + StringUtils.defaultIfEmpty(name, DEFAULT_STR));
            //装备
            evaluationReportService.createRunV2(xwpfParagraph3, text1).setText("装备名称:" + StringUtils.defaultIfEmpty(sceneScenarioEntity.getEquipmentName(), DEFAULT_STR));
            //数量
            int equipmentNum = sceneScenarioEntity.getEquipmentNum() == null ? 0 : sceneScenarioEntity.getEquipmentNum();
            evaluationReportService.createRunV2(xwpfParagraph4, text1).setText("数量:" + equipmentNum);
            //段落

            //1.3.1.2 我方兵力编成与部署
            DocxHelper.createHeadingTitles(word);
            TextStyleBean wfTtitle = new TextStyleBean();
            wfTtitle.setContent("1.3." + count + ".2 我方兵力编成与部署");
            wfTtitle.setColor("000000");
            wfTtitle.setFontsize(12);
            DocxHelper.createTitle(word, null, 4, wfTtitle);

            XWPFParagraph paragraph = word.createParagraph();
            String ourTroopDeployment = StringUtils.isEmpty(sceneScenarioEntity.getOtherTroopDeployment()) ? " " : sceneScenarioEntity.getOurTroopDeployment();
            evaluationReportService.createRunV2(paragraph, wfTtitle).setText("          " + ourTroopDeployment);

            //1.3.1.3 敌方兵力编成与部署
            DocxHelper.createHeadingTitles(word);
            TextStyleBean dfTitle = new TextStyleBean();
            dfTitle.setContent("1.3." + count + ".3 敌方兵力编成与部署");
            dfTitle.setColor("000000");
            dfTitle.setFontsize(12);
            DocxHelper.createTitle(word, null, 4, dfTitle);
            XWPFParagraph paragraph1 = word.createParagraph();
            //敌方兵力部署
            String otherTroopDeployment = StringUtils.isEmpty(sceneScenarioEntity.getOtherTroopDeployment()) ? " " : sceneScenarioEntity.getOtherTroopDeployment();
            evaluationReportService.createRunV2(paragraph1, wfTtitle).setText("          " + otherTroopDeployment);
            count++;
        }
    }

    private void CompeteSceneShow(XWPFDocument word, String sid) {
        //1.2 对抗场景设置
        DocxHelper.createHeadingTitles(word);
        TextStyleBean dkTitle = new TextStyleBean();
        dkTitle.setContent("1.2 对抗场景设置");
        dkTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, dkTitle);
        //场景表格构建
        setCompeteSceneTable(word, sid);
    }

    private void weaponsSystemShow(XWPFDocument word, String sid) {
        //1.1 武器系统使命任务
        DocxHelper.createHeadingTitles(word);
        TextStyleBean wqTitle = new TextStyleBean();
        wqTitle.setContent("1.1 武器系统使命任务");
        wqTitle.setColor("000000");
        DocxHelper.createTitle(word, null, 2, wqTitle);
        // 获取所有任务
        setMissionTasksTableInfo(word, sid);
    }


    /**
     * 对战场景表格构建
     */
    private void setCompeteSceneTable(XWPFDocument word, String sid) {
        PageInfo<CompeteSceneDTO> showCompeteScene = competeSceneService.showCompeteScene(null, sid, 0, 10, null);
        List<CompeteSceneDTO> showCompeteSceneList = showCompeteScene.getList();
        if (CollUtil.isEmpty(showCompeteSceneList)) {
            return;
        }
        //创建表格
        int rowSize = showCompeteSceneList.size();//表行
        XWPFTable table = word.createTable(rowSize + 1, 4);
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(new BigInteger("9000"));
        CTString ctString = tblPr.addNewTblStyle();
        ctString.setVal("StyledTable");
        CTTblLayoutType t = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        t.setType(STTblLayoutType.FIXED);

        //表头
        XWPFTableRow tableRow = table.getRow(0);
        XWPFTableCell cell1 = tableRow.getCell(0);
        XWPFParagraph p = cell1.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setText("场景名称");

        XWPFTableCell cell2 = tableRow.getCell(1);
        XWPFParagraph p2 = cell2.addParagraph();
        p2.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run2 = p2.createRun();
        run2.setBold(true);
        run2.setText("军事背景");


        XWPFTableCell cell3 = tableRow.getCell(2);
        XWPFParagraph p3 = cell3.addParagraph();
        p3.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run3 = p3.createRun();
        run3.setBold(true);
        run3.setText("装备体系");


        XWPFTableCell cell4 = tableRow.getCell(3);
        XWPFParagraph p4 = cell4.addParagraph();
        p4.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run4 = p4.createRun();
        run4.setBold(true);
        run4.setText("关联任务");

        //表数据填充
        for (int i = 0; i < showCompeteSceneList.size(); i++) {
            CompeteSceneDTO sceneDTO = showCompeteSceneList.get(i);

            XWPFTableRow tableData = table.getRow(i + 1);
            XWPFTableCell data1 = tableData.getCell(0);
            XWPFTableCell data2 = tableData.getCell(1);
            XWPFTableCell data3 = tableData.getCell(2);
            XWPFTableCell data4 = tableData.getCell(3);

            //名称
            XWPFParagraph xwpfParagraph = data1.addParagraph();
            xwpfParagraph.setAlignment(ParagraphAlignment.CENTER);
            xwpfParagraph.createRun().setText(sceneDTO.getName());

            //军事背景
            FighterEntityVO fighterEntityVO = sceneDTO.getVoList();
            XWPFParagraph xwpfParagraph1 = data2.addParagraph();
            XWPFParagraph xwpfParagraph2 = data2.addParagraph();
            XWPFParagraph xwpfParagraph3 = data2.addParagraph();
            XWPFParagraph xwpfParagraph4 = data2.addParagraph();
            xwpfParagraph1.createRun().setText("作战时间：" + (Objects.isNull(fighterEntityVO.getFighterTime()) ? "" : fighterEntityVO.getFighterTime()));
            xwpfParagraph2.createRun().setText("作战对手：" + (Objects.isNull(fighterEntityVO.getEnemy()) ? "" : fighterEntityVO.getEnemy()));
            xwpfParagraph3.createRun().setText("作战区域：" + (Objects.isNull(fighterEntityVO.getFighterArea()) ? "" : fighterEntityVO.getFighterArea()));
            xwpfParagraph4.createRun().setText("作战样式：" + (Objects.isNull(fighterEntityVO.getTaskModel()) ? "" : fighterEntityVO.getTaskModel()));

            //装备体系
            List<EquipmentVo> equipmentVoList = sceneDTO.getEquipmentVoList();
            if (!CollUtil.isEmpty(equipmentVoList)) {
                for (EquipmentVo equipmentVo : equipmentVoList) {
                    XWPFParagraph xwpfParagraph5 = data3.addParagraph();
                    xwpfParagraph5.createRun().setText("装备种类：" + equipmentVo.getEquipmentName() + "   " + "数量：1");
                }
            }


            //关联任务
            List<Pair<String, String>> missionTasks = sceneDTO.getMissionTasks();
            if (CollUtil.isNotEmpty(missionTasks)) {
                for (Pair<String, String> missionTask : missionTasks) {
                    String value = missionTask.getValue();
                    XWPFParagraph xwpfParagraph6 = data4.addParagraph();
                    xwpfParagraph6.setAlignment(ParagraphAlignment.CENTER);
                    xwpfParagraph6.createRun().setText(value);
                }
            }
        }
    }


    private void setMissionTasksTableInfo(XWPFDocument word, String sid) {
        List<MissionTaskEntity> missionTaskEntities = missionTaskService.find(MissionTaskEntity.builder().sid(sid).build());
        if (CollUtil.isEmpty(missionTaskEntities)) {
            return;
        }
        List<IndicatorExportNode> missionTasksTableInfo = Lists.newArrayList();
        for (MissionTaskEntity missionTaskEntity : missionTaskEntities) {
            if (missionTaskEntity.getFlag() == 2) {
                String pid = UUIDGenerator.getUUID();
                missionTasksTableInfo.add(new IndicatorExportNode(pid, missionTaskEntity.getName(), "0", -1.0));
                String descPid = UUIDGenerator.getUUID();
                missionTasksTableInfo.add(new IndicatorExportNode(descPid, missionTaskEntity.getDescs(), pid, -1.0));
                missionTasksTableInfo.add(new IndicatorExportNode(UUIDGenerator.getUUID(), missionTaskEntity.getBackGround(), descPid, -1.0));
            }
        }
        if (missionTasksTableInfo.size() > 0) {
            evaluationReportService.createDocumentTable(missionTasksTableInfo, Lists.newArrayList("使命任务", "描述信息", "使命任务背景"), word);
        }
    }


    /**
     * 构建评估报告
     *
     * @param word
     * @param sid
     */
    private void buildAssessmentReport(XWPFDocument word, String sid, List<CompeteSceneEntity> competeSceneEntities,
                                       List<SceneScenarioEntity> sceneScenarioEntities, ContributionRateExportDTO dto) {
        Optional<SchemaEntity> schemaEntity =
                schemaService.findById(sid);
        if (!schemaEntity.isPresent()) {
            return;
        }
        SchemaEntity schema = schemaEntity.get();
        CompeteSceneEntity competeSceneEntity = competeSceneEntities.get(0);

        DocxHelper.createHeadingTitles(word);
        TextStyleBean title = new TextStyleBean();
        title.setContent("2 评估报告");
        title.setColor("000000");
        DocxHelper.createTitle(word, null, 1, title);
        setText(word, "          评估报告主要包括4个章节，分别是评估方案描述、评估指标体系、评估样本结构、评估结果与结论。");

        //2.1 评估方案描述
        if (dto.getPgSchemeIsShow() == 1) {
            //2.1 评估方案描述
            DocxHelper.createHeadingTitles(word);
            TextStyleBean faTitle = new TextStyleBean();
            faTitle.setContent("2.1 评估方案描述");
            faTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, faTitle);
            setText(word, "         主要包括评估方案背景、创建时间、评估对象、评估目的与背景，以文本形式展示。例如：");

            evaluationReportService.setSchemaDescs(word, schema, faTitle);
        }

        //2.2 评估指标体系
        List<IndicatorEntity> abilityIndicatorLists = Lists.newArrayList();
        List<IndicatorEntity> efficacyIndicatorLists = Lists.newArrayList();
        if (dto.getPgIndicatorIsShow() == 1) {
            DocxHelper.createHeadingTitles(word);
            TextStyleBean zbTitle = new TextStyleBean();
            zbTitle.setContent("2.2 评估指标体系");
            zbTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, zbTitle);
            setText(word, "         通过表格和树形图展示能力评估指标体系及其映射关系。");
            // 设置指标
            List<IndicatorExportNode> abilityIndicatorExportNode = getIndicatorExportNode(sid, abilityIndicatorLists, SchemaEvaluateType.ABILITY_EVALUATE);
            if (CollUtil.isEmpty(abilityIndicatorExportNode)) {
                return;
            }
            evaluationReportService.setSchemaIndicatorTableAndImageInfo(word, abilityIndicatorExportNode, "         体系作战能力评估指标体系树形图如下所示：", sid + "1", zbTitle);
            setText(word, "         ");
            List<IndicatorExportNode> efficacyIndicatorExportNode = getIndicatorExportNode(sid, efficacyIndicatorLists, SchemaEvaluateType.EFFICACY_EVALUATE);
            if (CollUtil.isEmpty(abilityIndicatorExportNode)) {
                return;
            }
            setText(word, "         通过表格和树形图展示效能评估指标体系及其映射关系。");
            evaluationReportService.setSchemaIndicatorTableAndImageInfo(word, efficacyIndicatorExportNode, "        体系作战效能评估指标体系树形图如下所示：", sid + "2", zbTitle);
            setText(word, "         ");
        } else {
            getIndicatorExportNode(sid, abilityIndicatorLists, SchemaEvaluateType.ABILITY_EVALUATE);
            getIndicatorExportNode(sid, efficacyIndicatorLists, SchemaEvaluateType.EFFICACY_EVALUATE);
        }

        //2.3 评估样本结构
        if (dto.getPgSampleStructureIsShow() == 1) {
            //2.3 评估样本结构
            DocxHelper.createHeadingTitles(word);
            TextStyleBean ybTitle = new TextStyleBean();
            ybTitle.setContent("2.3 评估样本结构");
            ybTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, ybTitle);
            setText(word, "         通过表格展示评估指标计算需要的所有数据字段名称。");
            List<IndicatorExportNode> abilityIndicatorCalcorExportNodes = Lists.newArrayList();

            List<CalcModuleEntity> calcModuleEntities = iCalcModuleService.find(CalcModuleEntity.builder().sid(sid).defaultCalModule(CalModuleType.DEFAULT_MODEL.id).build());

            List<String> calModeAlgIds = Lists.newArrayList();
            if (CollUtil.isNotEmpty(calcModuleEntities)) {
                calModeAlgIds.addAll(calcModuleEntities.stream().map(CalcModuleEntity::getId).collect(Collectors.toList()));
            }

            int number = evaluationReportService.setIndicatorCalcorValueAndNumber(abilityIndicatorLists, calModeAlgIds, sid, abilityIndicatorCalcorExportNodes, 0);
            setText(word, "         体系作战能力评估指标计算需要采集" + number + "项数据，具体如下表所示: ");
            // 添加表格
            evaluationReportService.createDocumentTable(abilityIndicatorCalcorExportNodes, Lists.newArrayList("指标名称", "指标计算需要的数据字段（采集项）"), word);
            setText(word, "\n\n");

            List<IndicatorExportNode> efficacyIndicatorCalcorExportNodes = Lists.newArrayList();
            number = evaluationReportService.setIndicatorCalcorValueAndNumber(efficacyIndicatorLists, calModeAlgIds, sid, efficacyIndicatorCalcorExportNodes, 0);
            setText(word, "         体系作战效能评估指标计算需要采集" + number + "项数据，具体如下表所示: ");
            // 添加表格
            evaluationReportService.createDocumentTable(efficacyIndicatorCalcorExportNodes, Lists.newArrayList("指标名称", "指标计算需要的数据字段（采集项）"), word);
            setText(word, "\n\n");
        }
        //2.4 评估结果与结论
        if (dto.getPgResultIsShow() == 1) {
            //2.4 评估结果与结论
            DocxHelper.createHeadingTitles(word);
            TextStyleBean jgTitle = new TextStyleBean();
            jgTitle.setContent("2.4 评估结果与结论");
            jgTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 2, jgTitle);
            setText(word, "         以下展示的体系贡献率评估结果值，其重点包括当前场景下的实际情况效能值以及能力值。最终存在体系贡献率的效能值和能力值。");

            //2.4.1 体系贡献率评估结果
            DocxHelper.createHeadingTitles(word);
            TextStyleBean gxdTitle = new TextStyleBean();
            gxdTitle.setContent("2.4.1 体系贡献率评估结果");
            gxdTitle.setColor("000000");
            DocxHelper.createTitle(word, null, 3, gxdTitle);

            //2.4.1.1 评估结果摘要
            DocxHelper.createHeadingTitles(word);
            TextStyleBean jgzyTitle = new TextStyleBean();
            jgzyTitle.setContent("2.4.1.1 评估结果摘要");
            jgzyTitle.setColor("000000");
            jgzyTitle.setFontsize(11);
            DocxHelper.createTitle(word, null, 4, jgzyTitle);
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
            evaluationReportService.createRunV2(xwpfParagraph1, jgzyTitle).setText("评估方案名称：" + schema.getName());
            evaluationReportService.createRunV2(xwpfParagraph2, jgzyTitle).setText("评估场景：" + competeSceneEntity.getName());
            String nameList = "";
            for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntities) {
                String scenarioName = sceneScenarioEntity.getScenarioName();
                nameList += scenarioName + "、";
            }
            int i = nameList.lastIndexOf("、");
            String substring = nameList.substring(0, i);
            evaluationReportService.createRunV2(xwpfParagraph3, jgzyTitle).setText("评估想定：" + substring);
            String Summary = StringUtils.isEmpty(schema.getSummary()) ? "未设置评估结论" : schema.getSummary();
            evaluationReportService.createRunV2(xwpfParagraph4, jgzyTitle).setText("评估结论：" + Summary);

            //2.4.1.2 评估结果详情
            DocxHelper.createHeadingTitles(word);
            TextStyleBean jgxqTitle = new TextStyleBean();
            jgxqTitle.setContent("2.4.1.2 评估结果详情");
            jgxqTitle.setColor("000000");
            jgxqTitle.setFontsize(11);
            DocxHelper.createTitle(word, null, 4, jgxqTitle);
            List<String> taskIdLists = competeSceneEntity.getTaskIdLists();
            Map<String, String> taskIdAndNameMap = competeSceneEntity.getTaskIdAndNameMap();


            for (SceneScenarioEntity sceneScenarioEntity : sceneScenarioEntities) {
                List<ContributionRateResultEntity> contributionRateResult = contributionRateResultService.getContributionRateResult(competeSceneEntity.getId(), sceneScenarioEntity.getTaskId(), sceneScenarioEntity.getId());

                Map<String /*indicatorId*/ , List<ContributionRateResultEntity> /*value*/> obtainContributionIndicatorResult =
                        contributionRateResult.stream().collect(Collectors.groupingBy(ContributionRateResultEntity::getIndicatorId));

                if (CollUtil.isEmpty(obtainContributionIndicatorResult)) {
                    continue;
                }
                setText(word, sceneScenarioEntity.getScenarioName() + "效能评估计算结果值");
                Map<String, Map<String, String>> indicatorValueMaps = Maps.newHashMap();
                obtainContributionIndicatorResult.forEach((k, v) -> {
                    Map<String, String> paramMap = new HashMap<String, String>();
                    for (ContributionRateResultEntity entity : v) {
                        String label = entity.getStandardLabelName();
                        paramMap.put(entity.getMissionTaskId(), entity.getIndicatorResult() + "(" + label + ")");
                    }
                    indicatorValueMaps.put(k, paramMap);
                });
                createContributionRateResultTable(word, indicatorValueMaps, taskIdAndNameMap, efficacyIndicatorLists, Lists.newArrayList(sceneScenarioEntity.getTaskId()));
                setText(word, "\n\n");
                setText(word, sceneScenarioEntity.getScenarioName() + "能力评估计算结果值");
                createContributionRateResultTable(word, indicatorValueMaps, taskIdAndNameMap, abilityIndicatorLists, Lists.newArrayList(sceneScenarioEntity.getTaskId()));
                setText(word, "\n\n");
            }
            List<TaskResultEntity> abilityResultServiceList = effectAndAbilityResultService.getList(sid);
            Map<String, Map<String, String>> indicatorContributionRateValueMaps = Maps.newHashMap();
            for (TaskResultEntity entity : abilityResultServiceList) {
                Map<String, String> stringStringMap = indicatorContributionRateValueMaps.get(entity.getIndicatorId());
                if (CollUtil.isEmpty(stringStringMap)) {
                    stringStringMap = Maps.newHashMap();
                    indicatorContributionRateValueMaps.put(entity.getIndicatorId(), stringStringMap);
                }
                stringStringMap.put(entity.getTaskId(), entity.getValue() + "");
            }
            setText(word, "\n\n");
            setText(word, "         ");
            setText(word, "效能贡献率结果值");
            createContributionRateResultTable(word, indicatorContributionRateValueMaps, taskIdAndNameMap, efficacyIndicatorLists, taskIdLists);

            setText(word, "能力贡献率结果值");
            createContributionRateResultTable(word, indicatorContributionRateValueMaps, taskIdAndNameMap, abilityIndicatorLists, taskIdLists);
        }


    }

    private void createContributionRateResultTable(XWPFDocument word, Map<String, Map<String, String>> indicatorValueMaps,
                                                   Map<String, String> missionTaskNameMap, List<IndicatorEntity> indicatorEntities, List<String> taskIdLists) {
        List<String> tableHead = Lists.newArrayList();

        Map<String, List<IndicatorEntity>> map = indicatorEntities.stream().collect(Collectors.groupingBy(IndicatorEntity::getPid));

        List<IndicatorExportNode> efficacyIndicatorValueList = Lists.newArrayList();
        List<IndicatorExportNode> mideValue = Lists.newArrayList();
        for (IndicatorEntity eff : indicatorEntities) {
            IndicatorExportNode indicatorExportNode = new IndicatorExportNode(eff.getId(), eff.getName(), eff.getPid(), -1.0);
            efficacyIndicatorValueList.add(indicatorExportNode);
            mideValue.add(new IndicatorExportNode(eff.getId(), eff.getName(), eff.getPid(), -1.0));
        }
        List<Node> nodes = BuildTree.buildTree(mideValue);
        BuildTree.setLevel(nodes);
        //表的行列数
        int columnSize = BuildTree.maxLevel(nodes, 0);
        for (int i = 0; i < columnSize; i++) {
            tableHead.add((i + 1) + "级指标");
        }
        for (String taskId : taskIdLists) {
            String s = missionTaskNameMap.get(taskId);
            tableHead.add(s);
        }
        for (IndicatorEntity eff : indicatorEntities) {
            if (!map.containsKey(eff.getId()) && indicatorValueMaps.containsKey(eff.getId())) {
                Map<String, String> value = indicatorValueMaps.get(eff.getId());
                String preId = null;
                for (String task : taskIdLists) {
                    if (StringUtils.isEmpty(preId)) {
                        preId = UUIDGenerator.getUUID();
                        IndicatorExportNode indicatorExportNode = new IndicatorExportNode(preId, StringUtils.isEmpty(value.get(task)) ? "" : value.get(task), eff.getId(), -1.0);
                        efficacyIndicatorValueList.add(indicatorExportNode);
                        continue;
                    }
                    String currentId = UUIDGenerator.getUUID();
                    IndicatorExportNode indicatorExportNode = new IndicatorExportNode(currentId, StringUtils.isEmpty(value.get(task)) ? "" : value.get(task), preId, -1.0);
                    preId = currentId;
                    efficacyIndicatorValueList.add(indicatorExportNode);
                }
            }
        }
        evaluationReportService.createDocumentTable(efficacyIndicatorValueList, tableHead, word);
    }


    /**
     * 段落内容
     *
     * @param word
     */
    private void setText(XWPFDocument word, String text) {
        XWPFParagraph paragraph1 = word.createParagraph();
        XWPFRun paragraphRun1 = paragraph1.createRun();
        paragraphRun1.setText("\r");
        paragraphRun1.setFontSize(12);
        paragraphRun1.setColor("000000");
        paragraphRun1.setText(text);
    }

    public List<IndicatorExportNode> getIndicatorExportNode(String sid, List<IndicatorEntity> indicatorEntityList, SchemaEvaluateType type) {
//        List<IndicatorTreeEntity> indicatorTreeEntities = evaluationReportService.getIndicatorTreeRepository().findAll(Example.of(IndicatorTreeEntity.builder().sid(sid).build()));
        List<IndicatorTreeEntity> indicatorTreeEntities = indicatorTreeService.list(new QueryWrapper<IndicatorTreeEntity>().setEntity(IndicatorTreeEntity.builder().sid(sid).build()));
        for (IndicatorTreeEntity indicatorTreeEntity : indicatorTreeEntities) {
            if (indicatorTreeEntity.getIndicatorTreeType() == type.id) {
                String treeEntityId = indicatorTreeEntity.getId();
                List<IndicatorEntity> indicatorEntities = indicatorService.findByTreeId(treeEntityId);
                indicatorEntityList.addAll(indicatorEntities);
                List<IndicatorExportNode> result = Lists.newArrayList();
                indicatorEntities.forEach(e -> {
                    result.add(new IndicatorExportNode(e.getId(), e.getName(), e.getPid(), -1.0));
                });
                return result;
            }
        }
        return null;
    }
}
