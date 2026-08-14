package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.definition.ReportStyleProfile;
import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.office.docx.style.StyleProfile;

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
    /** 整份报告使用的内置或业务自定义样式画像。 */
    private final StyleProfile styleProfile;
    /** 按调用方顺序保存的模块数据。 */
    private final List<ComposableModuleData> moduleData;
    /** 按模块类型索引的模块数据。 */
    private final Map<ComposableReportModule, ComposableModuleData> moduleDataByType;

    private ComposableReportModuleModel(Builder builder) {
        this.headerText = builder.headerText;
        this.pageNumberFooterFormat = builder.pageNumberFooterFormat;
        this.styleProfile = builder.styleProfile;
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

    /**
     * 获取整份报告使用的样式画像。
     *
     * @return 内置或业务自定义样式画像
     */
    public StyleProfile getStyleProfile() {
        return styleProfile;
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
        T data = findModuleData(module, dataType);
        if (data == null) {
            throw new IllegalArgumentException("report module is not selected: " + module);
        }
        return data;
    }

    /**
     * 查询当前组合中某个模块的强类型数据，未选择时返回 {@code null}。
     *
     * @param module 模块类型
     * @param dataType 模块数据类型
     * @param <T> 模块数据类型
     * @return 模块数据；未选择时为 {@code null}
     */
    public <T extends ComposableModuleData> T findModuleData(ComposableReportModule module, Class<T> dataType) {
        if (module == null || dataType == null) {
            throw new IllegalArgumentException("report module and data type must not be null");
        }
        ComposableModuleData data = moduleDataByType.get(module);
        if (data == null) {
            return null;
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
        private StyleProfile styleProfile = ReportStyleProfile.GJB_438C;
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

        /**
         * 设置整份报告的样式画像，默认使用 {@link ReportStyleProfile#GJB_438C}。
         *
         * <p>业务侧可以直接传入自行实现的 {@link StyleProfile}，无需修改报告定义或
         * 框架内置样式枚举。</p>
         *
         * @param styleProfile 内置或业务自定义样式画像
         * @return 当前构建器
         */
        public Builder styleProfile(StyleProfile styleProfile) {
            if (styleProfile == null) {
                throw new IllegalArgumentException("report style profile must not be null");
            }
            this.styleProfile = styleProfile;
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
