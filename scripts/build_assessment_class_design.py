#!/usr/bin/env python3
"""Build source-backed chapter 5.1 class tables for the assessment design."""

from __future__ import annotations

import copy
import csv
import re
import shutil
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.table import Table
from docx.text.paragraph import Paragraph


SOURCE = Path('/Users/luojiang/Desktop/评估助手软件设计说明.docx')
TEMPLATE = Path('/Users/luojiang/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_ag86xavr41tr21_8d77/msg/file/2026-10/软件设计文档模板666.docx')
OUTPUT = Path('/Users/luojiang/Desktop/评估助手软件设计说明_5.1类设计参数返回值修订版.docx')
JAVA_ROOT = Path('/Users/luojiang/Desktop/评估助手5.1类设计参数返回值源码')
ZIP_OUTPUT = Path('/Users/luojiang/Desktop/评估助手5.1类设计参数返回值源码.zip')
METHOD_NAMES_PATH = Path(__file__).with_name('assessment_method_names.txt')

CODE_RE = re.compile(r'（(ZNZS-GN-[^)]+)）')
CLASS_RE = re.compile(r'(?P<access>public|protected|private)\s+(?:abstract\s+)?(?:class|interface)\s+(?P<name>\w+)')
FIELD_RE = re.compile(r'(?P<access>private|protected|public)\s+(?:final\s+)?(?P<type>[\w<>?, \[\]]+)\s+(?P<name>\w+)\s*(?:=\s*[^;]+)?;')
METHOD_RE = re.compile(r'(?P<access>public|protected|private)\s+(?P<return>[\w<>?, \[\]]+)\s+(?P<name>\w+)\((?P<args>[^)]*)\)\s*(?:\{|;)')


@dataclass
class Child:
    title: str
    code: str
    purpose: str = ''
    input_text: str = ''
    output_text: str = ''
    error_text: str = ''


@dataclass
class Function:
    title: str
    code: str
    heading_element: object
    group: str
    module: str
    old_service_name: str = ''
    children: list[Child] = field(default_factory=list)


def para_info(doc, element):
    if element.tag != qn('w:p'):
        return None
    p = Paragraph(element, doc)
    return p.style.name, p.text.strip()


def extract(doc):
    body = list(doc.element.body)
    functions = []
    module = group = ''
    for i in range(94, 412):
        info = para_info(doc, body[i])
        if not info:
            continue
        style, title = info
        if style == 'Heading 3':
            module = title
        elif style == 'Heading 4':
            group = title
        elif style == 'Heading 5':
            m = CODE_RE.search(title)
            if m:
                item = Function(title, m.group(1), copy.deepcopy(body[i]), group, module)
                if i + 1 < len(body):
                    next_info = para_info(doc, body[i + 1])
                    if next_info:
                        name_match = re.search(r'([A-Za-z][A-Za-z0-9]*Service)类', next_info[1])
                        if name_match:
                            item.old_service_name = name_match.group(1)
                functions.append(item)

    by_code = {f.code: f for f in functions}
    current = child = None
    for i in range(690, len(body)):
        element = body[i]
        info = para_info(doc, element)
        if info:
            style, title = info
            if style == 'Heading 4':
                m = CODE_RE.search(title)
                current = by_code.get(m.group(1)) if m else None
                child = None
            elif current and style in ('Heading 5', 'Heading 6'):
                m = CODE_RE.search(title)
                desired = 'Heading 5' if '-DXYY-' in current.code else 'Heading 6'
                if style == desired and m and m.group(1).startswith(current.code + '-'):
                    child = Child(title.split('（')[0], m.group(1))
                    current.children.append(child)
            continue
        if current and child and element.tag == qn('w:tbl'):
            table = Table(element, doc)
            if len(table.rows) < 2:
                continue
            head = table.rows[0].cells[0].text.strip()
            cells = table.rows[1].cells
            if head == 'CSU名称' and len(cells) >= 3:
                child.purpose = cells[2].text.strip()
            elif head == '用例及标识尾码' and len(cells) >= 3:
                child.input_text = cells[1].text.strip()
                child.output_text = cells[2].text.strip()
            elif head == '错误名称' and len(cells) >= 3:
                child.error_text = cells[2].text.strip()

    missing = [f.title for f in functions if not f.children]
    if missing:
        raise ValueError('Missing 6.2 child features: ' + ', '.join(missing))
    return functions, body


def base_name(function):
    if function.old_service_name:
        return function.old_service_name.removesuffix('Service')
    segments = function.code.split('-')
    family = {'FAZNT': 'AssessmentPlan', 'CHZNT': 'ImplementationPlan', 'BGZNT': 'AssessmentReport'}
    return family.get(segments[3], 'Agent') + segments[-1]


def package_name(function):
    parts = function.code.lower().split('-')
    return 'design.assessment.' + '.'.join(parts[2:])


def load_method_names():
    groups = {}
    for raw in METHOD_NAMES_PATH.read_text(encoding='utf-8').splitlines():
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        code, names = line.split('|', 1)
        if code in groups:
            raise ValueError(f'Duplicate method-name group: {code}')
        groups[code] = names.split()
    return groups


METHOD_NAMES = load_method_names()

AGENT_STEP_NAMES = {
    'ZNZS-GN-DXYY-FAZNT-CLHZ': 'analyzeAssessmentRequirementMaterials',
    'ZNZS-GN-DXYY-FAZNT-PGMD': 'analyzeAssessmentPurposeAndScope',
    'ZNZS-GN-DXYY-FAZNT-FFSC': 'generateAssessmentQuestionsAndMethods',
    'ZNZS-GN-DXYY-FAZNT-RWHD': 'extractActivitiesAndLinkIndicators',
    'ZNZS-GN-DXYY-FAZNT-ZBTX': 'designIndicatorSystemAndAssessmentModel',
    'ZNZS-GN-DXYY-FAZNT-SJXQ': 'generateDataCollectionRequirements',
    'ZNZS-GN-DXYY-FAZNT-PGFA': 'compileAndApproveAssessmentPlan',
    'ZNZS-GN-DXYY-CHZNT-PGFA': 'validateImplementationScope',
    'ZNZS-GN-DXYY-CHZNT-ZBMX': 'planIndicatorCheckDetails',
    'ZNZS-GN-DXYY-CHZNT-CJLJ': 'matchAndValidateCollectionPaths',
    'ZNZS-GN-DXYY-CHZNT-CJSJ': 'arrangeCollectionScheduleAndStaffing',
    'ZNZS-GN-DXYY-CHZNT-SSWJ': 'generateAndApproveImplementationDocuments',
    'ZNZS-GN-DXYY-BGZNT-BGXQ': 'analyzeReportRequirementsAndMaterials',
    'ZNZS-GN-DXYY-BGZNT-BGJG': 'reviewAssessmentResultsAndEvidence',
    'ZNZS-GN-DXYY-BGZNT-BGMB': 'planReportTemplateAndSections',
    'ZNZS-GN-DXYY-BGZNT-PGTW': 'generateReportChartsAndConclusions',
    'ZNZS-GN-DXYY-BGZNT-BGSJ': 'reviewAndApproveAssessmentReport',
}


def method_name(function, child):
    index = next(i for i, item in enumerate(function.children) if item.code == child.code)
    return METHOD_NAMES[function.code][index]


REQUIREMENT_SIGNATURES = {
    'uploadAssessmentMaterial': ('String taskId, String fileName, String materialType, byte[] content, long expectedRevision', 'String'),
    'selectExistingMaterial': ('String taskId, List<String> materialIds, long expectedRevision', 'List<String>'),
    'extractPurposeAndObject': ('String taskId, List<String> materialIds', '{dto}'),
    'extractTrainingTasksAndActivities': ('String taskId, List<String> materialIds', '{dto}'),
    'extractTaskStandards': ('String taskId, List<String> materialIds', '{dto}'),
    'selectObjectsAndTasks': ('String taskId, List<String> objectIds, List<String> activityIds, long expectedRevision', '{dto}'),
    'setTimeAndSpaceScope': ('String taskId, LocalDateTime startTime, LocalDateTime endTime, String spatialScope, long expectedRevision', '{dto}'),
    'setEnvironmentAndDataLimits': ('String taskId, Map<String, String> limits, long expectedRevision', '{dto}'),
    'generateQuestionDrafts': ('String taskId, List<String> materialIds', 'List<{dto}>'),
    'editQuestionContent': ('String taskId, String questionId, String content, long expectedRevision', '{dto}'),
    'generateQuestionList': ('String taskId, List<String> questionIds', 'List<{dto}>'),
    'queryMissingRequirements': ('String taskId, String requirementDraftId', 'List<String>'),
    'checkTimeAndStandardConflicts': ('String taskId, String requirementDraftId', 'List<String>'),
    'generateRequirementList': ('String taskId, String requirementDraftId', '{dto}'),
}

SIGNATURE_OVERRIDES = {
    'queryAssessmentActivityMaterials': ('String taskId, String activityId', 'List<{dto}>'),
    'queryDuplicateAssessmentActivities': ('String taskId', 'List<{dto}>'),
    'queryDuplicateIndicatorsAndStandardDifferences': ('String taskId', 'List<{dto}>'),
    'queryUncollectableDataItems': ('String taskId', 'List<{dto}>'),
    'queryMissingCalculationResults': ('String taskId', 'List<String>'),
    'queryReportCitedEvidence': ('String taskId, String reportId', 'List<{dto}>'),
    'selectAssessmentChartData': ('String taskId, List<String> dataIds, long expectedRevision', '{dto}'),
    'selectReportSectionMaterials': ('String taskId, String sectionId, List<String> materialIds, long expectedRevision', '{dto}'),
    'selectAssessmentDataPackageContents': ('String taskId, List<String> resultIds, long expectedRevision', '{dto}'),
    'selectCollectionFieldDataSources': ('String taskId, String fieldId, List<String> sourceIds, long expectedRevision', '{dto}'),
    'calculateMultipleIndicatorsForObject': ('String taskId, String objectId, List<String> indicatorIds, {dto} calculationOptions', '{dto}'),
    'calculateOverallAssessmentWithMultipleModels': ('String taskId, String objectId, List<String> modelIds, {dto} calculationOptions', '{dto}'),
    'queryObjectIndicatorProgress': ('String taskId, String objectId, String indicatorId', 'int'),
    'generateReportChartNumbers': ('String taskId, String reportId', 'Map<String, String>'),
    'annotateTaskCapabilityGraphSources': ('String taskId, String chartId, List<String> sourceIds, long expectedRevision', '{dto}'),
}


def identifier_parameter(method):
    for token, identifier in (
        ('ReportSection', 'sectionId'), ('Section', 'sectionId'),
        ('Chart', 'chartId'), ('Indicator', 'indicatorId'),
        ('Material', 'materialId'), ('Evidence', 'evidenceId'),
        ('Question', 'questionId'), ('Model', 'modelId'),
        ('Method', 'methodId'), ('Algorithm', 'algorithmId'),
        ('Template', 'templateId'), ('Job', 'jobId'),
        ('Report', 'reportId'), ('Plan', 'planId'),
        ('Form', 'formId'), ('Object', 'objectId'),
        ('Activity', 'activityId'), ('Result', 'resultId'),
        ('Output', 'outputId'), ('File', 'fileId'),
        ('DataPackage', 'packageId'), ('Calculation', 'calculationId'),
        ('Field', 'fieldId'), ('Data', 'dataId'),
    ):
        if token in method:
            return identifier
    return 'objectId'


def method_signature(function, child):
    """Use operation-specific public signatures, with explicit detail for 6.2.1.1."""
    name = method_name(function, child)
    dto = base_name(function) + 'Dto'
    if function.code.endswith('XQJX'):
        args, result = REQUIREMENT_SIGNATURES[name]
        return args, result.format(dto=dto)
    if name in SIGNATURE_OVERRIDES:
        args, result = SIGNATURE_OVERRIDES[name]
        return args.format(dto=dto), result.format(dto=dto)
    entity_id = identifier_parameter(name)
    entity_arg = f'String {entity_id}'
    if name.startswith('queryAndDownload') or name.startswith('export'):
        return f'String taskId, {entity_arg}', 'byte[]'
    if name.startswith('count'):
        return f'String taskId, {entity_arg}', 'int'
    if name.endswith('Count'):
        return 'String taskId', 'long'
    if 'Progress' in name:
        return f'String taskId, {entity_arg}', 'Map<String, Integer>'
    if name.startswith(('check', 'validate')) or name.startswith('queryMissing'):
        return f'String taskId, {entity_arg}', 'List<String>'
    if name.startswith('determine'):
        return f'String taskId, {entity_arg}', 'boolean'
    if name.startswith('search'):
        return 'String taskId, String keyword', f'List<{dto}>'
    if name.startswith('query'):
        if 'Without' in name:
            return 'String taskId', f'List<{dto}>'
        if 'Candidate' in name or name.endswith(('Templates', 'Methods', 'Models')):
            return 'String taskId, String keyword', f'List<{dto}>'
        if 'Reasons' in name or 'Failures' in name:
            return f'String taskId, {entity_arg}', 'List<String>'
        if 'StandardValues' in name:
            return f'String taskId, {entity_arg}', 'Map<String, Double>'
        return f'String taskId, {entity_arg}', f'List<{dto}>'
    if name.startswith('recommend'):
        return f'String taskId, {dto} criteria', f'List<{dto}>'
    if name.startswith('selectOrDeselect'):
        return f'String taskId, List<String> activityIds, boolean selected, long expectedRevision', dto
    if name.startswith('select'):
        selected = (f'{dto} selection' if 'And' in name else
                    f'List<String> {entity_id.removesuffix("Id")}Ids' if name.endswith('s') else entity_arg)
        return f'String taskId, {selected}, long expectedRevision', dto
    if name.startswith('extract'):
        return 'String taskId, List<String> sourceMaterialIds', dto
    if name.startswith('compare'):
        return 'String taskId, List<String> candidateIds', dto
    if name.startswith(('calculate', 'recalculate')):
        targets = 'List<String> objectIds' if 'MultipleObjects' in name or 'ObjectGroups' in name else entity_arg
        result = 'Map<String, Double>' if 'Differences' in name or 'Deviations' in name else dto
        return f'String taskId, {targets}, {dto} calculationOptions', result
    if name.startswith('aggregate'):
        return 'String taskId, List<String> resultIds', dto
    if name.startswith(('submit', 'start')):
        return f'String taskId, {dto} request', 'String'
    if name.startswith('generate'):
        if 'Chart' in name or 'Graph' in name:
            return f'String taskId, {dto} chartSpec', 'String'
        if 'List' in name:
            return f'String taskId, {dto} criteria', f'List<{dto}>'
        return f'String taskId, {dto} generationInput', dto
    if name.startswith('produce'):
        return 'String taskId, String inputVersionId', 'String'
    if name.startswith('plan'):
        return f'String taskId, {dto} planningInput', dto
    if name.startswith('retry'):
        return 'String taskId, String jobId', 'boolean'
    if name.startswith('switch'):
        return 'String taskId, String jobId, String fallbackServiceId', 'boolean'
    if name.startswith('resume'):
        return 'String taskId, String jobId, String checkpointId', 'String'
    if name.startswith('run'):
        return 'String taskId, String jobId', 'String'
    if name.startswith('delete'):
        return f'String taskId, {entity_arg}, long expectedRevision', 'boolean'
    if name.startswith('addOrRemove'):
        return f'String taskId, List<String> itemIds, boolean add, long expectedRevision', dto
    if name.startswith(('add', 'create', 'merge', 'link', 'insert')):
        return f'String taskId, {dto} item, long expectedRevision', dto
    if name.startswith(('set', 'configure', 'revise', 'update', 'write', 'annotate', 'move',
                        'reorder', 'rename', 'change', 'define', 'record', 'arrange',
                        'classify', 'split', 'match', 'allocate', 'request', 'layout')):
        return f'String taskId, {entity_arg}, {dto} changes, long expectedRevision', dto
    raise ValueError(f'No parameter/return design for {function.code}: {name}')


def make_java(function, kind, class_name):
    title = function.title.split('（')[0]
    dto = base_name(function) + 'Dto'
    service = base_name(function) + 'Service'
    mapper = base_name(function) + 'Mapper'
    # Name representative operations from each business group; the method rows
    # below retain the full operation list and its detailed responsibilities.
    chosen = []
    seen_groups = set()
    for child in function.children:
        suffix = child.code.removeprefix(function.code + '-')
        group = suffix.split('-')[0]
        if len(function.children) <= 5 or group not in seen_groups:
            chosen.append(child.title)
            seen_groups.add(group)
    for child in function.children:
        if len(chosen) >= min(5, len(function.children)):
            break
        if child.title not in chosen:
            chosen.append(child.title)
    chosen = chosen[:5]
    operation_list = '、'.join(chosen) + ('等' if len(chosen) < len(function.children) else '')
    dto_detail = ('任务标识、材料标识与类型、评估目的、对象与范围、关键问题、来源引用及校验问题'
                  if function.code.endswith('XQJX') else
                  '任务标识、对象标识、来源材料、依据引用、期望版本、处理状态及校验问题')
    descriptions = {
        'Controller': f'提供{operation_list}请求入口，校验任务归属与访问权限，调用{service}并返回处理结果。',
        'Service': f'处理{operation_list}业务，核对输入、来源引用与期望版本，并通过{mapper}保存草稿或结果。',
        'Dto': f'封装{title}请求与结果的{dto_detail}，供控制类与服务类传递。',
        'Mapper': f'负责{title}草稿、版本及来源关系的持久化访问，按任务范围查询并以期望版本控制更新。',
        'Support': '负责评估材料上传前的文件格式、大小、可读性与摘要校验，向服务类提供可复用的文件检查能力。',
    }
    header = [f'package {package_name(function)};', '', 'import java.time.LocalDateTime;',
              'import java.util.List;', 'import java.util.Map;', '',
              '/**', f' * {descriptions[kind]}', ' */']
    body = []
    if kind == 'Controller':
        body += [f'public class {class_name} {{',
                 f'    /** 调用{title}业务服务，统一校验任务上下文及访问权限。 */',
                 f'    private final {service} service = null;', '']
        for c in function.children:
            n = method_name(function, c)
            args, result = method_signature(function, c)
            detail = c.purpose or f'完成{c.title}。'
            body += [f'    /** {c.title}：接收请求、校验任务归属并调用服务；{detail} */',
                     f'    public {result} {n}({args}) {{',
                     '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '']
        if function.code.endswith('XQJX'):
            body += [f'    /** 检查上传请求中的文件名、类型、大小与当前任务，失败时返回可定位的原因。 */',
                     '    public List<String> validateUploadRequest(String taskId, String fileName, String materialType, long fileSize) {',
                     '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '',
                     f'    /** 查询上传登记结果，供当前任务材料清单展示并打开原文件。 */',
                     f'    public {dto} queryUploadResult(String taskId, String materialId) {{',
                     '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '']
    elif kind == 'Service':
        body += [f'public class {class_name} {{',
                 f'    /** 保存{title}草稿、版本与来源引用。 */',
                 f'    private final {mapper} mapper = null;', '']
        if function.code.endswith('XQJX'):
            body += [f'    /** 检查文件格式、大小、完整性和材料类型，不合格文件不进入可用清单。 */',
                     '    private List<String> validateUploadFile(String fileName, String materialType, byte[] content) {',
                     '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '',
                     f'    /** 将原始文件写入受控文件区，并计算摘要供后续完整性核对。 */',
                     '    private String storeOriginalFile(String taskId, byte[] content) {',
                     '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '',
                     f'    /** 保存文件名、材料类型、文件标识及来源版本。 */',
                     '    private String registerMaterialMetadata(String taskId, String fileId, String fileName, String materialType) {',
                     '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '',
                     f'    /** 以幂等方式将材料挂接到当前评估任务，避免重复条目。 */',
                     '    private boolean attachMaterialToTask(String taskId, String materialId, long expectedRevision) {',
                     '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '']
        for c in function.children:
            n = method_name(function, c)
            args, result = method_signature(function, c)
            detail = c.purpose or f'完成{c.title}。'
            caution = ('；异常处理：' + c.error_text) if c.error_text else ''
            body += [f'    /** {c.title}：{detail.rstrip("。")}{caution}。 */',
                     f'    public {result} {n}({args}) {{',
                     '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '']
    elif kind == 'Dto':
        body += [f'public class {class_name} {{']
        fields = [
            ('String', 'taskId', '所属评估任务标识，所有读写按此隔离。'),
            ('String', 'objectId', '当前业务对象或草稿标识。'),
            ('long', 'expectedRevision', '提交时的期望版本，用于乐观并发控制。'),
            ('List<String>', 'sourceMaterialIds', '输入材料或上游成果的版本引用。'),
            ('List<String>', 'evidenceRefs', '可定位到材料片段的来源依据。'),
            ('Map<String, String>', 'values', '业务请求与结果的键值字段，字段名由对应处理方法约定。'),
            ('List<String>', 'issueMessages', '缺项、冲突和校验失败的明细。'),
            ('String', 'status', '草稿、待确认、已确认等处理状态。'),
        ]
        if function.code.endswith('XQJX'):
            fields += [
                ('String', 'fileName', '上传材料原始文件名。'),
                ('String', 'materialType', '训练方案、想定、脚本或说明材料类型。'),
                ('String', 'materialId', '受控文件及材料登记标识。'),
                ('String', 'purpose', '提取或修订后的评估目的。'),
                ('String', 'assessmentObjectId', '被评估对象标识。'),
                ('LocalDateTime', 'startTime', '评估起始时间。'),
                ('LocalDateTime', 'endTime', '评估结束时间。'),
                ('String', 'spatialScope', '评估空间范围。'),
                ('List<String>', 'questionIds', '关键评估问题标识清单。'),
            ]
        for typ, name, desc in fields:
            body += [f'    /** {desc} */', f'    private {typ} {name};', '']
        body += [f'    /** 检查{title}必填字段、任务归属和来源引用，返回问题明细。 */',
                 '    public List<String> validate() {',
                 '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '']
    elif kind == 'Mapper':
        body += [f'public interface {class_name} {{',
                 f'    /** 按任务和对象查询{title}当前版本。 */',
                 f'    public {dto} findByTaskAndObject(String taskId, String objectId);', '',
                 f'    /** 保存{title}新草稿及来源关系。 */',
                 f'    public int insertDraft({dto} draft);', '',
                 f'    /** 匹配期望版本后更新，冲突时返回零。 */',
                 f'    public int updateIfRevisionMatches({dto} draft, long expectedRevision);', '',
                 f'    /** 查询当前任务下{title}清单，不跨任务返回数据。 */',
                 f'    public List<{dto}> listByTaskId(String taskId);', '']
    elif kind == 'Support':
        body += [f'public class {class_name} {{',
                 '    /** 允许的材料格式与大小约束由配置提供。 */',
                 '    private final Map<String, Long> uploadLimits = null;', '',
                 '    /** 校验扩展名与实际文件类型是否一致。 */',
                 '    public boolean isSupportedFormat(String fileName, byte[] content) {',
                 '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '',
                 '    /** 检查文件大小是否超过该材料类型的配置上限。 */',
                 '    public boolean isWithinSizeLimit(String materialType, long fileSize) {',
                 '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '',
                 '    /** 计算文件摘要，供上传、打开与下载时校验内容一致。 */',
                 '    public String calculateDigest(byte[] content) {',
                 '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '',
                 '    /** 检查文件结构可读性，损坏文件不得写入可用清单。 */',
                 '    public boolean isReadable(byte[] content) {',
                 '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '']
    body.append('}')
    return '\n'.join(header + body) + '\n'


def parse_java(path):
    lines = path.read_text(encoding='utf-8').splitlines()
    info = {'name': path.stem, 'description': '', 'fields': [], 'methods': [], 'kind': ''}
    pending = []
    in_comment = False
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('/**'):
            pending = []
            in_comment = True
            text = stripped[3:].removesuffix('*/').strip(' *')
            if text:
                pending.append(text)
            if '*/' in stripped:
                in_comment = False
            continue
        if in_comment:
            if '*/' in stripped:
                text = stripped.split('*/')[0].strip(' *')
                if text:
                    pending.append(text)
                in_comment = False
            else:
                text = stripped.strip(' *')
                if text:
                    pending.append(text)
            continue
        desc = ' '.join(pending).strip()
        cm = CLASS_RE.search(stripped)
        if cm:
            info['name'] = cm.group('name')
            info['kind'] = '接口' if ' interface ' in stripped else '类'
            info['description'] = desc
            pending = []
            continue
        fm = FIELD_RE.search(stripped)
        if fm:
            info['fields'].append((fm.group('name'), fm.group('type').strip(), fm.group('access'), desc, '-'))
            pending = []
            continue
        mm = METHOD_RE.search(stripped)
        if mm:
            info['methods'].append((mm.group('name'), mm.group('args') or '无', mm.group('access'), mm.group('return').strip(), desc))
            pending = []
    return info


def make_agent_java(group_code, group_title, class_name, steps):
    package = 'design.assessment.' + '.'.join(group_code.lower().split('-')[2:])
    lines = [f'package {package};', '', 'import java.util.Map;', '', '/**',
             f' * 负责{group_title}的有状态步骤编排，按固定输入版本调用各功能服务，记录输出引用，并在人工确认后继续处理。', ' */',
             f'public class {class_name} {{',
             '    /** 智能体运行实例标识，用于恢复与审计。 */',
             '    private String agentRunId;', '',
             '    /** 每个步骤的输出引用及版本。 */',
             '    private Map<String, String> stepResults;', '']
    for function in steps:
        title = function.title.split('（')[0]
        method = AGENT_STEP_NAMES[function.code]
        lines += [f'    /** 执行{title}，调用对应业务服务并记录来源与输出版本。 */',
                  f'    public String {method}(String taskId, String inputVersionId) {{',
                  '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '']
    lines += ['    /** 确认事项完成后，从已保存的步骤和版本继续运行。 */',
              '    public String resumeAfterConfirmation(String taskId, String runId) {',
              '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '',
              '    /** 校验业务审定记录后登记正式成果引用。 */',
              '    public String publishApprovedArtifact(String taskId, String approvalId) {',
              '        throw new UnsupportedOperationException("设计骨架，待实现");', '    }', '}']
    return '\n'.join(lines) + '\n'


def import_template_table_styles(doc, template_doc, template_table):
    """Bring the template's actual paragraph styles into this document without
    changing identically named styles used elsewhere in the design document.
    """
    source_styles = template_doc.styles.element
    target_styles = doc.styles.element
    ids = {p.pPr.pStyle.val for p in template_table._tbl.iter(qn('w:p'))
           if p.pPr is not None and p.pPr.pStyle is not None}
    ids.update((template_doc.styles['正文1'].style_id,
                template_doc.styles['表注'].style_id))
    needed = set()

    def include(style_id):
        if style_id in needed:
            return
        source = source_styles.find(f".//w:style[@w:styleId='{style_id}']",
                                    {'w': qn('w:style').split('}')[0][1:]})
        if source is None:
            raise ValueError(f'Template style not found: {style_id}')
        needed.add(style_id)
        for tag in ('basedOn', 'link', 'next'):
            ref = source.find(qn('w:' + tag))
            if ref is not None:
                include(ref.get(qn('w:val')))

    for style_id in ids:
        include(style_id)
    mapping = {style_id: 'T666' + style_id for style_id in needed}
    for style_id in sorted(needed):
        original = source_styles.find(f".//w:style[@w:styleId='{style_id}']",
                                      {'w': qn('w:style').split('}')[0][1:]})
        cloned = copy.deepcopy(original)
        cloned.set(qn('w:styleId'), mapping[style_id])
        cloned.attrib.pop(qn('w:default'), None)
        name = cloned.find(qn('w:name'))
        if name is not None:
            name.set(qn('w:val'), '模板666_' + name.get(qn('w:val')))
        for tag in ('basedOn', 'link', 'next'):
            ref = cloned.find(qn('w:' + tag))
            if ref is not None:
                ref.set(qn('w:val'), mapping[ref.get(qn('w:val'))])
        target_styles.append(cloned)
    return mapping


def replace_cell_text(cell, value):
    """Replace text while retaining the template cell's paragraph/run format."""
    paragraph = cell.paragraphs[0]._p
    first_run = next((node for node in paragraph if node.tag == qn('w:r')), None)
    run = copy.deepcopy(first_run) if first_run is not None else OxmlElement('w:r')
    for node in list(run):
        if node.tag != qn('w:rPr'):
            run.remove(node)
    text_node = OxmlElement('w:t')
    text_node.text = str(value)
    run.append(text_node)
    for node in list(paragraph):
        if node.tag != qn('w:pPr'):
            paragraph.remove(node)
    paragraph.append(run)
    for extra in cell._tc.findall(qn('w:p'))[1:]:
        cell._tc.remove(extra)


def make_table(doc, template_table, parsed, style_mapping):

    element = copy.deepcopy(template_table._tbl)
    table = Table(element, doc)
    field_row = table.rows[4]._tr
    for _ in range(max(0, len(parsed['fields']) - 1)):
        field_row.addnext(copy.deepcopy(field_row))
    table = Table(element, doc)
    method_row = table.rows[-1]._tr
    for _ in range(max(0, len(parsed['methods']) - 1)):
        method_row.addnext(copy.deepcopy(method_row))
    table = Table(element, doc)
    for paragraph in element.iter(qn('w:p')):
        ppr = paragraph.find(qn('w:pPr'))
        pstyle = ppr.find(qn('w:pStyle')) if ppr is not None else None
        if pstyle is not None:
            pstyle.set(qn('w:val'), style_mapping[pstyle.get(qn('w:val'))])
    rows = table.rows
    replace_cell_text(rows[0].cells[0], '类名/标识')
    replace_cell_text(rows[0].cells[1], parsed['name'])
    replace_cell_text(rows[0].cells[3], '父类/标识')
    replace_cell_text(rows[0].cells[4], '-')
    replace_cell_text(rows[1].cells[0], '说明')
    replace_cell_text(rows[1].cells[1], parsed['description'])
    replace_cell_text(rows[2].cells[0], '属性')
    for cell, text in zip(rows[3].cells, ['序号', '属性名称', '属性类型', '访问范围', '属性说明', '属性备注']):
        replace_cell_text(cell, text)
    fields = parsed['fields'] or [('-', '-', '-', '无状态字段', '-')]
    for i, data in enumerate(fields):
        name, typ, access, description, remark = data
        shown = [str(i+1), name, typ, access, description, remark]
        for cell, text in zip(rows[4+i].cells, shown):
            replace_cell_text(cell, text)
    pos = 4 + len(fields)
    replace_cell_text(rows[pos].cells[0], '方法')
    for cell, text in zip(rows[pos+1].cells, ['序号', '函数名称', '函数参数', '访问范围', '函数返回值', '功能说明']):
        replace_cell_text(cell, text)
    methods = parsed['methods'] or [('-', '无', '-', '无', '仅作为数据承载对象')]
    for i, data in enumerate(methods):
        name, args, access, result, description = data
        shown = [str(i+1), name, args, access, result, description]
        for cell, text in zip(rows[pos+2+i].cells, shown):
            replace_cell_text(cell, text)
    return element


def add_class_heading(doc, anchor, parent_element, text, level):
    heading = doc.add_paragraph(style=f'Heading {level}')
    heading.add_run(text)
    parent_num = parent_element.find(f'./{qn("w:pPr")}/{qn("w:numPr")}')
    if parent_num is not None:
        source_id = parent_num.find(qn('w:numId'))
        if source_id is not None:
            number = OxmlElement('w:numPr')
            ilvl = OxmlElement('w:ilvl')
            ilvl.set(qn('w:val'), str(level - 1))
            num_id = OxmlElement('w:numId')
            num_id.set(qn('w:val'), source_id.get(qn('w:val')))
            number.extend((ilvl, num_id))
            heading._p.get_or_add_pPr().append(number)
    anchor.addprevious(heading._p)


def build():
    if JAVA_ROOT.exists():
        shutil.rmtree(JAVA_ROOT)
    JAVA_ROOT.mkdir(parents=True)
    doc = Document(SOURCE)
    template_doc = Document(TEMPLATE)
    template_table = template_doc.tables[0]
    style_mapping = import_template_table_styles(doc, template_doc, template_table)
    functions, body = extract(doc)
    function_codes = {function.code for function in functions}
    if set(METHOD_NAMES) != function_codes:
        raise ValueError('Method-name groups do not match 5.1 functions: '
                         f'missing={function_codes - set(METHOD_NAMES)}, '
                         f'extra={set(METHOD_NAMES) - function_codes}')
    for function in functions:
        names = METHOD_NAMES[function.code]
        if len(names) != len(function.children) or len(set(names)) != len(names):
            raise ValueError(f'Incomplete or duplicate method names: {function.code}')
        if any(not re.fullmatch(r'[a-z][A-Za-z0-9]*', name) or
               re.fullmatch(r'processF\d+', name) for name in names):
            raise ValueError(f'Invalid or numbered method name: {function.code}')
    all_method_names = {name for names in METHOD_NAMES.values() for name in names}
    if set(SIGNATURE_OVERRIDES) - all_method_names:
        raise ValueError('Unknown signature override names: '
                         + ', '.join(sorted(set(SIGNATURE_OVERRIDES) - all_method_names)))
    agent_codes = {code for code in function_codes if '-DXYY-' in code}
    if set(AGENT_STEP_NAMES) != agent_codes:
        raise ValueError('Agent step names do not match agent functions')
    original_nodes = list(doc.element.body)
    outline_nodes = []
    for i in range(94, 412):
        info = para_info(doc, original_nodes[i])
        if info and info[0] in ('Heading 3', 'Heading 4', 'Heading 5'):
            outline_nodes.append((info[0], info[1], copy.deepcopy(original_nodes[i])))
    for element in original_nodes[94:412]:
        doc.element.body.remove(element)
    anchor = original_nodes[412]
    funcs_by_title = {f.title: f for f in functions}
    class_count = table_count = 0
    for style, title, heading_element in outline_nodes:
        anchor.addprevious(heading_element)
        if style == 'Heading 4' and '-DXYY-' in title:
            group_code_match = CODE_RE.search(title)
            if group_code_match:
                group_code = group_code_match.group(1)
                agent_names = {'FAZNT': 'AssessmentPlanAgent',
                               'CHZNT': 'ImplementationPlanningAgent',
                               'BGZNT': 'ReportGenerationAgent'}
                agent_name = agent_names.get(group_code.split('-')[-1])
                if agent_name:
                    group_steps = [f for f in functions if f.code.startswith(group_code + '-')]
                    package = 'design.assessment.' + '.'.join(group_code.lower().split('-')[2:])
                    package_dir = JAVA_ROOT.joinpath(*package.split('.'))
                    package_dir.mkdir(parents=True, exist_ok=True)
                    java_path = package_dir / (agent_name + '.java')
                    java_path.write_text(make_agent_java(group_code, title.split('（')[0], agent_name, group_steps), encoding='utf-8')
                    parsed = parse_java(java_path)
                    add_class_heading(doc, anchor, heading_element,
                                      title.split('（')[0] + '智能体编排类设计', 5)
                    lead = doc.add_paragraph(style='模板666_正文1')
                    lead.add_run('智能体编排类详细设计如下表所示。')
                    anchor.addprevious(lead._p)
                    cap = doc.add_paragraph(style='模板666_表注')
                    cap.add_run(f'表 0  {agent_name}智能体编排类详细设计表')
                    cap.paragraph_format.keep_with_next = True
                    anchor.addprevious(cap._p)
                    anchor.addprevious(make_table(doc, template_table, parsed, style_mapping))
                    class_count += 1
                    table_count += 1
        if style != 'Heading 5':
            continue
        function = funcs_by_title[title]
        base = base_name(function)
        classes = [(base+'Controller', 'Controller', '控制器类'),
                   (base+'Service', 'Service', '服务实现类'),
                   (base+'Dto', 'Dto', '数据传输类'),
                   (base+'Mapper', 'Mapper', '数据映射类')]
        if function.code.endswith('XQJX'):
            classes.append(('MaterialUploadSupport', 'Support', '材料上传支持类'))
        package_dir = JAVA_ROOT.joinpath(*package_name(function).split('.'))
        package_dir.mkdir(parents=True, exist_ok=True)
        for name, kind, label in classes:
            java_path = package_dir / (name + '.java')
            java_path.write_text(make_java(function, kind, name), encoding='utf-8')
            parsed = parse_java(java_path)
            if not parsed['description'] or (kind != 'Dto' and not parsed['methods']):
                raise ValueError('Java parse failed: ' + str(java_path))
            heading_label = {'Controller': '控制类', 'Service': '服务类',
                             'Dto': '数据传输类', 'Mapper': '数据映射类',
                             'Support': '材料上传支持类'}[kind]
            add_class_heading(doc, anchor, heading_element,
                              function.title.split('（')[0] + heading_label + '设计', 6)
            lead = doc.add_paragraph(style='模板666_正文1')
            lead.add_run(f'{label}详细设计如下表所示。')
            anchor.addprevious(lead._p)
            cap = doc.add_paragraph(style='模板666_表注')
            cap.add_run(f'表 0  {name}{label}详细设计表')
            cap.paragraph_format.keep_with_next = True
            anchor.addprevious(cap._p)
            anchor.addprevious(make_table(doc, template_table, parsed, style_mapping))
            class_count += 1
            table_count += 1
    # Existing captions are static text; renumber all table captions in document order.
    table_number = 0
    for element in doc.element.body:
        info = para_info(doc, element)
        if not info:
            continue
        style, text = info
        if re.match(r'^表\s*\d+', text):
            table_number += 1
            p = Paragraph(element, doc)
            new_text = re.sub(r'^表\s*\d+', f'表 {table_number}', text, count=1)
            p.text = new_text
    doc.save(OUTPUT)
    with (JAVA_ROOT / '子功能覆盖关系.csv').open('w', encoding='utf-8-sig', newline='') as out:
        writer = csv.writer(out)
        writer.writerow(['5.1功能标识', '5.1功能', '6.2子功能标识', '6.2子功能',
                         '控制器方法', '服务方法', '函数参数', '函数返回值'])
        for function in functions:
            for child in function.children:
                args, result = method_signature(function, child)
                writer.writerow([function.code, function.title.split('（')[0], child.code,
                                 child.title, method_name(function, child), method_name(function, child),
                                 args, result])
    (JAVA_ROOT / 'README.md').write_text(
        '# 评估助手 5.1 类设计源码\n\n'
        '这些 Java 文件是软件设计说明的设计骨架，用于解析生成 5.1 的类设计表；'
        '方法体保留待实现标记，不代表功能已经开发完成。'
        '控制器和服务类的方法与需求的对应关系见 `子功能覆盖关系.csv`。\n\n'
        '执行 `mvn -Dmaven.repo.local=/Users/luojiang/maven/repository clean test` '
        '可检查 Java 11 语法和类型引用。\n', encoding='utf-8')
    (JAVA_ROOT / 'pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>design.assessment</groupId><artifactId>assessment-class-design-skeleton</artifactId><version>1.0-SNAPSHOT</version>
  <properties><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
  <build><sourceDirectory>${project.basedir}</sourceDirectory><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version><configuration><release>11</release><includes><include>design/**/*.java</include></includes></configuration></plugin></plugins></build>
</project>\n''', encoding='utf-8')
    with zipfile.ZipFile(ZIP_OUTPUT, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(JAVA_ROOT.rglob('*')):
            if not path.is_file():
                continue
            archive.write(path, path.relative_to(JAVA_ROOT))
        archive.write(Path(__file__), 'build_assessment_class_design.py')
        archive.write(METHOD_NAMES_PATH, METHOD_NAMES_PATH.name)
    print(f'functions={len(functions)} classes={class_count} added_tables={table_count} total_table_captions={table_number}')
    print(f'docx={OUTPUT} zip={ZIP_OUTPUT}')


if __name__ == '__main__':
    build()
