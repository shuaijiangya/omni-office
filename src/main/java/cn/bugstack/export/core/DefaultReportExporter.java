package cn.bugstack.export.core;

import cn.bugstack.export.api.ReportExportException;
import cn.bugstack.export.api.ReportExportStage;
import cn.bugstack.export.api.ReportExporter;
import cn.bugstack.export.api.ReportRequest;
import cn.bugstack.export.api.ReportResult;
import cn.bugstack.export.definition.ModuleSlot;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.export.document.ReportSection;
import cn.bugstack.export.module.ReportDataContext;
import cn.bugstack.export.module.ReportModule;
import cn.bugstack.export.module.ReportModuleContext;
import cn.bugstack.export.module.ReportModuleRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 固定报告导出生命周期的默认门面实现。
 *
 * <p>该类通过模板方法顺序执行蓝图解析、模块计划、语义文档组装、语义校验和目标格式渲染。
 * 它不依赖 Spring，应用框架仅需负责构造模块注册表和注入业务模块。</p>
 */
public final class DefaultReportExporter implements ReportExporter {

    /** 报告模块策略注册表。 */
    private final ReportModuleRegistry moduleRegistry;
    /** 模块计划与依赖校验器。 */
    private final ReportPlanner planner;
    /** 报告语义树校验器。 */
    private final ReportDocumentValidator validator;
    /** 具体格式渲染器。 */
    private final ReportDocumentRenderer renderer;

    /**
     * 创建报告导出门面。
     *
     * <p>传入的 {@code planner} 必须使用同一个 {@code moduleRegistry}，以确保计划和
     * 组装阶段解析到相同的模块实现。</p>
     *
     * @param moduleRegistry 报告模块注册表
     * @param planner 报告执行计划器
     * @param validator 报告语义文档校验器
     * @param renderer 报告目标格式渲染器
     */
    public DefaultReportExporter(ReportModuleRegistry moduleRegistry, ReportPlanner planner,
                                 ReportDocumentValidator validator, ReportDocumentRenderer renderer) {
        if (moduleRegistry == null || planner == null || validator == null || renderer == null) {
            throw new IllegalArgumentException("report exporter dependencies must not be null");
        }
        if (!planner.usesRegistry(moduleRegistry)) {
            throw new IllegalArgumentException("report planner must use the same module registry as exporter");
        }
        this.moduleRegistry = moduleRegistry;
        this.planner = planner;
        this.validator = validator;
        this.renderer = renderer;
    }

    /**
     * 按固定生命周期导出报告。
     *
     * <p>依次完成蓝图与计划解析、模块内容组装、语义树校验和目标格式渲染。任一阶段失败时
     * 都会转换为携带阶段与模块信息的 {@link ReportExportException}。</p>
     *
     * @param request 本次导出请求
     * @param outputPath 最终输出文件路径
     * @param <T> 报告入口业务数据类型
     * @return 导出结果和审计摘要
     */
    @Override
    public <T> ReportResult export(ReportRequest<T> request, Path outputPath) {
        if (outputPath == null) {
            throw new IllegalArgumentException("report output path must not be null");
        }
        PreparedReport prepared = prepare(request);
        long started = System.nanoTime();
        try {
            renderer.render(prepared.document, prepared.blueprint, request.getOutputFormat(), outputPath);
        } catch (Exception e) {
            throw failure(ReportExportStage.RENDER, prepared.blueprint.getCode(), null, e);
        } finally {
            prepared.durations.put(ReportExportStage.RENDER.name(), elapsedMillis(started));
        }
        return new ReportResult(prepared.blueprint.getCode(), prepared.blueprint.getVersion(), request.getOutputFormat(),
                outputPath, prepared.warnings, prepared.durations);
    }

    /**
     * 按固定生命周期将报告导出为内存字节。
     *
     * <p>计划、模块组装和语义校验逻辑与 {@link #export(ReportRequest, Path)} 完全一致，
     * 唯一差异是渲染结果保留在内存中。</p>
     *
     * @param request 本次导出请求
     * @param <T> 报告入口业务数据类型
     * @return 与请求格式一致的完整报告字节
     */
    @Override
    public <T> byte[] exportToBytes(ReportRequest<T> request) {
        PreparedReport prepared = prepare(request);
        long started = System.nanoTime();
        try {
            byte[] content = renderer.renderToBytes(prepared.document, prepared.blueprint, request.getOutputFormat());
            if (content == null) {
                throw new IllegalStateException("report renderer returned null byte content");
            }
            return content;
        } catch (Exception e) {
            throw failure(ReportExportStage.RENDER, prepared.blueprint.getCode(), null, e);
        } finally {
            prepared.durations.put(ReportExportStage.RENDER.name(), elapsedMillis(started));
        }
    }

    /**
     * 校验请求、构建蓝图并组装待渲染的报告语义文档。
     *
     * @param request 导出请求
     * @param <T> 输入数据类型
     * @return 已完成计划和内容组装的报告
     */
    private <T> PreparedReport prepare(ReportRequest<T> request) {
        if (request == null) {
            throw new IllegalArgumentException("report request must not be null");
        }
        Map<String, Long> durations = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        ReportBlueprint blueprint;
        ReportDataContext dataContext = new ReportDataContext();
        ReportPlan plan;
        ReportDocument document;

        long started = System.nanoTime();
        try {
            blueprint = request.getDefinition().blueprint(request.getInput());
            if (blueprint == null) {
                throw new IllegalArgumentException("report definition returned null blueprint");
            }
            request.getDefinition().contributeData(dataContext, request.getInput());
            plan = planner.plan(blueprint, dataContext);
            warnings.addAll(plan.getWarnings());
        } catch (Exception e) {
            throw failure(ReportExportStage.PLAN, null, null, e);
        } finally {
            durations.put(ReportExportStage.PLAN.name(), elapsedMillis(started));
        }

        started = System.nanoTime();
        try {
            document = new ReportDocument();
            document.setTitle(blueprint.getTitle());
            document.setBasicInfo(blueprint.getBasicInfo());
            for (ModuleSlot slot : plan.getModuleSlots()) {
                // 组装模块内容
                ReportSection section = composeModule(moduleRegistry.require(slot.getModuleCode()), blueprint, slot, dataContext);
                document.getSections().add(section);
            }
        } catch (ReportExportException e) {
            throw e;
        } catch (Exception e) {
            throw failure(ReportExportStage.COMPOSE, blueprint.getCode(), null, e);
        } finally {
            durations.put(ReportExportStage.COMPOSE.name(), elapsedMillis(started));
        }

        started = System.nanoTime();
        try {
            List<String> errors = validator.validate(document);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(String.join("; ", errors));
            }
        } catch (Exception e) {
            throw failure(ReportExportStage.VALIDATE, blueprint.getCode(), null, e);
        } finally {
            durations.put(ReportExportStage.VALIDATE.name(), elapsedMillis(started));
        }
        return new PreparedReport(blueprint, document, warnings, durations);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    /**
     * 调用模块并将异常转换为包含模块上下文的导出异常。
     *
     * @param module 当前模块
     * @param blueprint 报告蓝图
     * @param slot 模块槽位
     * @param dataContext 报告数据上下文
     * @return 模块生成的章节
     */
    private ReportSection composeModule(ReportModule module, ReportBlueprint blueprint, ModuleSlot slot,
                                        ReportDataContext dataContext) {
        try {
            Object data = dataContext.require(module.descriptor().getDataKey());
            ReportSection section = module.compose(new ReportModuleContext(blueprint, slot, dataContext), data);
            if (section == null) {
                throw new IllegalStateException("report module returned null section");
            }
            return section;
        } catch (Exception e) {
            throw failure(ReportExportStage.COMPOSE, blueprint.getCode(), slot.getModuleCode(), e);
        }
    }

    /**
     * 创建携带导出阶段和报告上下文的统一异常。
     *
     * @param stage 失败阶段
     * @param reportCode 报告编码
     * @param moduleCode 模块编码
     * @param cause 原始异常
     * @return 导出异常
     */
    private ReportExportException failure(ReportExportStage stage, String reportCode, String moduleCode,
                                          Exception cause) {
        String message = "report export failed at " + stage
                + (reportCode == null ? "" : ", report=" + reportCode)
                + (moduleCode == null ? "" : ", module=" + moduleCode)
                + ": " + cause.getMessage();
        return new ReportExportException(stage, reportCode, moduleCode, message, cause);
    }

    /**
     * 计算自指定纳秒时间点开始至今的耗时毫秒数。
     *
     * @param started 起始纳秒时间
     * @return 已耗时毫秒数
     */
    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    /**
     * 已完成计划、组装和校验的报告中间结果。
     */
    private static final class PreparedReport {

        /** 已解析的报告蓝图。 */
        private final ReportBlueprint blueprint;
        /** 已组装并通过校验的语义文档。 */
        private final ReportDocument document;
        /** 计划和组装阶段产生的告警。 */
        private final List<String> warnings;
        /** 已完成阶段的耗时记录。 */
        private final Map<String, Long> durations;

        private PreparedReport(ReportBlueprint blueprint, ReportDocument document, List<String> warnings,
                               Map<String, Long> durations) {
            this.blueprint = blueprint;
            this.document = document;
            this.warnings = warnings;
            this.durations = durations;
        }
    }
}
