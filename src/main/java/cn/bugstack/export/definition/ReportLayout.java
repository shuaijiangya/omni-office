package cn.bugstack.export.definition;

/**
 * 报告的版式和前置页配置。
 */
public final class ReportLayout {

    /** 报告原有默认页码页脚模板。 */
    public static final String DEFAULT_PAGE_NUMBER_FOOTER = "第 PAGE 页";
    /** 仅显示页码的页脚模板。 */
    public static final String PAGE_NUMBER_ONLY_FOOTER = "PAGE";
    /** 带中文修饰文字的可选页码页脚模板。 */
    public static final String CHINESE_PAGE_NUMBER_FOOTER = DEFAULT_PAGE_NUMBER_FOOTER;

    /** 文档使用的样式画像。 */
    private final ReportStyleProfile styleProfile;
    /** 是否启用标题自动编号。 */
    private final boolean headingNumberingEnabled;
    /** 是否在模块正文开始位置输出报告主标题。 */
    private final boolean bodyTitleEnabled;
    /** 目录收录的最大标题层级。 */
    private final Integer tableOfContentsDepth;
    /** 页眉文本。 */
    private final String headerText;
    /** 页脚文本。 */
    private final String footerText;
    /** 是否在页脚中自动显示页码。 */
    private final boolean pageNumberFooterEnabled;
    /** 目录独立 Section 使用的页脚文本。 */
    private final String tableOfContentsFooterText;
    /** 模块正文 Section 的起始页码。 */
    private final int modulePageNumberStart;
    /** 封面文档名称。 */
    private final String coverDocumentName;
    /** 封面项目名称。 */
    private final String coverProjectName;
    /** 封面版本号。 */
    private final String coverVersion;
    /** 可动态组合内容的封面模板。 */
    private final ReportCoverTemplate coverTemplate;

    private ReportLayout(Builder builder) {
        this.styleProfile = builder.styleProfile;
        this.headingNumberingEnabled = builder.headingNumberingEnabled;
        this.bodyTitleEnabled = builder.bodyTitleEnabled;
        this.tableOfContentsDepth = builder.tableOfContentsDepth;
        this.headerText = builder.headerText;
        this.footerText = builder.footerText;
        this.pageNumberFooterEnabled = builder.pageNumberFooterEnabled;
        this.tableOfContentsFooterText = builder.tableOfContentsFooterText;
        this.modulePageNumberStart = builder.modulePageNumberStart;
        this.coverDocumentName = builder.coverDocumentName;
        this.coverProjectName = builder.coverProjectName;
        this.coverVersion = builder.coverVersion;
        this.coverTemplate = builder.coverTemplate;
    }

    /**
     * 创建报告版式构建器。
     *
     * @return 报告版式构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取样式画像。
     *
     * @return 样式画像
     */
    public ReportStyleProfile getStyleProfile() {
        return styleProfile;
    }

    /**
     * 判断是否启用标题自动编号。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isHeadingNumberingEnabled() {
        return headingNumberingEnabled;
    }

    /**
     * 判断是否在模块正文开始位置输出报告主标题。
     *
     * @return 输出时返回 {@code true}
     */
    public boolean isBodyTitleEnabled() {
        return bodyTitleEnabled;
    }

    /**
     * 获取目录收录标题最大层级。
     *
     * @return 标题层级；未启用目录时为 {@code null}
     */
    public Integer getTableOfContentsDepth() {
        return tableOfContentsDepth;
    }

    /**
     * 获取页眉文本。
     *
     * @return 页眉文本；未设置时为 {@code null}
     */
    public String getHeaderText() {
        return headerText;
    }

    /**
     * 获取自定义页脚文本。
     *
     * @return 页脚文本；未设置时由自动页码配置决定
     */
    public String getFooterText() {
        return footerText;
    }

    /**
     * 判断未指定自定义页脚时是否自动插入当前页码。
     *
     * @return 自动插入页码页脚时返回 {@code true}
     */
    public boolean isPageNumberFooterEnabled() {
        return pageNumberFooterEnabled;
    }

    /**
     * 获取目录独立 Section 使用的页脚文本。
     *
     * @return 目录页脚；未设置时为 {@code null}
     */
    public String getTableOfContentsFooterText() {
        return tableOfContentsFooterText;
    }

    /**
     * 获取模块正文 Section 的起始页码。
     *
     * @return 起始页码
     */
    public int getModulePageNumberStart() {
        return modulePageNumberStart;
    }

    /**
     * 获取封面文档名称。
     *
     * @return 封面文档名称；未配置封面时为 {@code null}
     */
    public String getCoverDocumentName() {
        return coverDocumentName;
    }

    /**
     * 获取封面项目名称。
     *
     * @return 封面项目名称；未配置封面时为 {@code null}
     */
    public String getCoverProjectName() {
        return coverProjectName;
    }

    /**
     * 获取封面版本号。
     *
     * @return 封面版本号；未配置封面时为 {@code null}
     */
    public String getCoverVersion() {
        return coverVersion;
    }

    /**
     * 获取动态封面模板。
     *
     * @return 封面模板；使用标准固定封面或未配置封面时为 {@code null}
     */
    public ReportCoverTemplate getCoverTemplate() {
        return coverTemplate;
    }

    /** 报告版式构建器。 */
    public static final class Builder {

        /** 待构建布局的样式画像。 */
        private ReportStyleProfile styleProfile = ReportStyleProfile.DEFAULT;
        /** 待构建布局是否启用标题编号。 */
        private boolean headingNumberingEnabled = true;
        /** 待构建布局是否输出正文主标题。 */
        private boolean bodyTitleEnabled = true;
        /** 待构建布局的目录层级。 */
        private Integer tableOfContentsDepth;
        /** 待构建布局的页眉文本。 */
        private String headerText;
        /** 待构建布局的页脚文本。 */
        private String footerText;
        /** 待构建布局是否显示页码。 */
        private boolean pageNumberFooterEnabled = true;
        /** 待构建目录独立 Section 的页脚文本。 */
        private String tableOfContentsFooterText = DEFAULT_PAGE_NUMBER_FOOTER;
        /** 待构建模块正文 Section 的起始页码。 */
        private int modulePageNumberStart = 1;
        /** 待构建封面的文档名称。 */
        private String coverDocumentName;
        /** 待构建封面的项目名称。 */
        private String coverProjectName;
        /** 待构建封面的版本号。 */
        private String coverVersion;
        /** 待构建布局的动态封面模板。 */
        private ReportCoverTemplate coverTemplate;

        /**
         * 设置报告样式画像。
         *
         * @param styleProfile 样式画像
         * @return 当前构建器
         */
        public Builder styleProfile(ReportStyleProfile styleProfile) {
            this.styleProfile = styleProfile;
            return this;
        }

        /**
         * 设置是否启用标题自动编号。
         *
         * @param headingNumberingEnabled 是否启用标题自动编号
         * @return 当前构建器
         */
        public Builder headingNumberingEnabled(boolean headingNumberingEnabled) {
            this.headingNumberingEnabled = headingNumberingEnabled;
            return this;
        }

        /**
         * 设置是否在模块正文开始位置输出报告主标题，默认输出。
         *
         * @param enabled 是否输出正文主标题
         * @return 当前构建器
         */
        public Builder bodyTitle(boolean enabled) {
            this.bodyTitleEnabled = enabled;
            return this;
        }

        /**
         * 设置目录及其收录标题深度。
         *
         * @param depth 收录层级，范围为 1 到 9
         * @return 当前构建器
         */
        public Builder tableOfContents(int depth) {
            if (depth < 1 || depth > 9) {
                throw new IllegalArgumentException("table of contents depth must be between 1 and 9");
            }
            this.tableOfContentsDepth = depth;
            return this;
        }

        /**
         * 设置页眉文本。
         *
         * @param headerText 页眉文本
         * @return 当前构建器
         */
        public Builder header(String headerText) {
            this.headerText = headerText;
            return this;
        }

        /**
         * 设置自定义页脚文本。
         *
         * <p>文本中包含 {@code PAGE} 时会渲染为 Word 当前页码域；未设置该属性时，
         * 默认使用 {@link #DEFAULT_PAGE_NUMBER_FOOTER}。</p>
         *
         * @param footerText 自定义页脚文本
         * @return 当前构建器
         */
        public Builder footer(String footerText) {
            this.footerText = footerText;
            return this;
        }

        /**
         * 设置是否自动生成当前页码页脚。
         *
         * <p>仅当未配置 {@link #footer(String)} 时生效，默认启用。</p>
         *
         * @param enabled 是否自动生成页码页脚
         * @return 当前构建器
         */
        public Builder pageNumberFooter(boolean enabled) {
            this.pageNumberFooterEnabled = enabled;
            return this;
        }

        /**
         * 设置目录独立 Section 使用的页脚文本。
         *
         * <p>文本中包含 {@code PAGE} 时会渲染为 Word 当前页码域。</p>
         *
         * @param footerText 目录页脚文本；传入 {@code null} 表示目录不显示页脚
         * @return 当前构建器
         */
        public Builder tableOfContentsFooter(String footerText) {
            this.tableOfContentsFooterText = footerText;
            return this;
        }

        /**
         * 设置模块正文 Section 的起始页码，默认从 1 开始。
         *
         * @param pageNumber 起始页码，必须大于 0
         * @return 当前构建器
         */
        public Builder modulePageNumberStart(int pageNumber) {
            if (pageNumber < 1) {
                throw new IllegalArgumentException("module page number start must be greater than 0");
            }
            this.modulePageNumberStart = pageNumber;
            return this;
        }

        /**
         * 设置报告封面信息。
         *
         * @param documentName 文档名称
         * @param projectName 项目名称
         * @param version 文档版本
         * @return 当前构建器
         */
        public Builder cover(String documentName, String projectName, String version) {
            this.coverDocumentName = documentName;
            this.coverProjectName = projectName;
            this.coverVersion = version;
            return this;
        }

        /**
         * 设置可动态组合内容的封面模板。
         *
         * @param coverTemplate 封面模板
         * @return 当前构建器
         */
        public Builder coverTemplate(ReportCoverTemplate coverTemplate) {
            if (coverTemplate == null) {
                throw new IllegalArgumentException("report cover template must not be null");
            }
            this.coverTemplate = coverTemplate;
            return this;
        }

        /**
         * 校验并创建不可变报告版式。
         *
         * @return 报告版式
         */
        public ReportLayout build() {
            if (styleProfile == null) {
                throw new IllegalArgumentException("report style profile must not be null");
            }
            boolean hasCoverValue = hasText(coverDocumentName) || hasText(coverProjectName) || hasText(coverVersion);
            if (hasCoverValue && (!hasText(coverDocumentName) || !hasText(coverProjectName) || !hasText(coverVersion))) {
                throw new IllegalArgumentException("cover document name, project name and version must be provided together");
            }
            if (hasCoverValue && coverTemplate != null) {
                throw new IllegalArgumentException("standard cover and template cover cannot be configured together");
            }
            return new ReportLayout(this);
        }

        /**
         * 判断布局文本配置是否有效。
         *
         * @param value 待判断文本
         * @return 包含非空白内容时返回 {@code true}
         */
        private boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }
}
