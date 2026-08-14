package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 可组合评估报告的模块页模型。
 *
 * <p>模型保存目录之后需要写入的有序模块，并可选设置模块页页眉。
 * 未调用 {@link Builder#header(String)} 时模块页不生成页眉。</p>
 */
public final class ComposableReportModuleModel {

    /** 模块页可选页眉。 */
    private final String headerText;
    /** 目录和模块页使用的页码页脚外观。 */
    private final ComposablePageNumberFooterFormat pageNumberFooterFormat;
    /** 按调用方顺序保存的模块数据。 */
    private final List<ComposableModuleData> moduleData;
    /** 按模块类型索引的模块数据。 */
    private final Map<ComposableReportModule, ComposableModuleData> moduleDataByType;

    private ComposableReportModuleModel(Builder builder) {
        this.headerText = builder.headerText;
        this.pageNumberFooterFormat = builder.pageNumberFooterFormat;
        this.moduleData = Collections.unmodifiableList(new ArrayList<>(builder.moduleData));
        this.moduleDataByType = Collections.unmodifiableMap(new EnumMap<>(builder.moduleDataByType));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getHeaderText() {
        return headerText;
    }

    /**
     * 获取目录和模块页使用的页码页脚外观。
     *
     * @return 页码页脚格式
     */
    public ComposablePageNumberFooterFormat getPageNumberFooterFormat() {
        return pageNumberFooterFormat;
    }

    public List<ComposableModuleData> getModuleData() {
        return moduleData;
    }

    public List<ComposableReportModule> getSelectedModules() {
        List<ComposableReportModule> modules = new ArrayList<>();
        for (ComposableModuleData data : moduleData) {
            modules.add(data.getModule());
        }
        return Collections.unmodifiableList(modules);
    }

    public <T extends ComposableModuleData> T requireModuleData(ComposableReportModule module, Class<T> dataType) {
        ComposableModuleData data = moduleDataByType.get(module);
        if (data == null) {
            throw new IllegalArgumentException("report module is not selected: " + module);
        }
        if (!dataType.isInstance(data)) {
            throw new IllegalStateException("report module data type mismatch: " + module.getCode());
        }
        return dataType.cast(data);
    }

    /** 模块页模型构建器。 */
    public static final class Builder {

        private String headerText;
        private ComposablePageNumberFooterFormat pageNumberFooterFormat =
                ComposablePageNumberFooterFormat.PAGE_ONLY;
        private final List<ComposableModuleData> moduleData = new ArrayList<>();
        private final Map<ComposableReportModule, ComposableModuleData> moduleDataByType =
                new EnumMap<>(ComposableReportModule.class);

        /** 设置模块页页眉；不调用时不生成页眉。 */
        public Builder header(String headerText) {
            if (headerText == null || headerText.trim().isEmpty()) {
                throw new IllegalArgumentException("module header must not be blank");
            }
            this.headerText = headerText.trim();
            return this;
        }

        /**
         * 设置页码页脚外观，默认仅显示页码。
         *
         * @param format 页码页脚格式
         * @return 当前构建器
         */
        public Builder pageNumberFooterFormat(ComposablePageNumberFooterFormat format) {
            if (format == null) {
                throw new IllegalArgumentException("page number footer format must not be null");
            }
            this.pageNumberFooterFormat = format;
            return this;
        }

        /** 按最终导出顺序增加一个强类型模块数据对象。 */
        public Builder module(ComposableModuleData data) {
            if (data == null || data.getModule() == null) {
                throw new IllegalArgumentException("report module data and module type must not be null");
            }
            ComposableReportModule module = data.getModule();
            if (moduleDataByType.containsKey(module)) {
                throw new IllegalArgumentException("duplicate report module: " + module.getCode());
            }
            moduleData.add(data);
            moduleDataByType.put(module, data);
            return this;
        }

        public ComposableReportModuleModel build() {
            if (moduleData.isEmpty()) {
                throw new IllegalArgumentException("at least one report module must be selected");
            }
            return new ComposableReportModuleModel(this);
        }
    }
}
