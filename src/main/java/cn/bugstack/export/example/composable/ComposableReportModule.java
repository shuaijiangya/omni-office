package cn.bugstack.export.example.composable;

/**
 * 可组合文本报告支持的八个业务模块。
 *
 * <p>枚举只表达模块的稳定标识和默认标题，不再统一定义 {@code String} 数据键。
 * 每个模块拥有自己独立的数据对象和类型化数据键。</p>
 */
public enum ComposableReportModule {

    /** 评估场景构设。 */
    ASSESSMENT_SCENARIO_CONSTRUCTION("assessment-scenario-construction", "评估场景构设"),
    /** 评估计算分析。 */
    ASSESSMENT_CALCULATION_ANALYSIS("assessment-calculation-analysis", "评估计算分析"),
    /** 贡献率分析。 */
    CONTRIBUTION_RATE_ANALYSIS("contribution-rate-analysis", "贡献率分析"),
    /** 影响分析。 */
    IMPACT_ANALYSIS("impact-analysis", "影响分析"),
    /** 对比分析。 */
    COMPARISON_ANALYSIS("comparison-analysis", "对比分析"),
    /** 作战流程分析。 */
    COMBAT_PROCESS_ANALYSIS("combat-process-analysis", "作战流程分析"),
    /** 脆弱性分析。 */
    VULNERABILITY_ANALYSIS("vulnerability-analysis", "脆弱性分析"),
    /** 功能优化分析。 */
    FUNCTIONAL_OPTIMIZATION_ANALYSIS("functional-optimization-analysis", "功能优化分析");

    /** 注册到报告框架的模块编码。 */
    private final String code;
    /** 模块默认章节标题。 */
    private final String title;
    ComposableReportModule(String code, String title) {
        this.code = code;
        this.title = title;
    }

    /**
     * 获取模块编码。
     *
     * @return 模块编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取默认章节标题。
     *
     * @return 默认章节标题
     */
    public String getTitle() {
        return title;
    }

}
