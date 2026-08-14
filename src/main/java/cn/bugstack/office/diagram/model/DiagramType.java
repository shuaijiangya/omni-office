package cn.bugstack.office.diagram.model;

/**
 * 可生成的设计图类型。
 */
public enum DiagramType {

    /** 用例图。 */
    USE_CASE,

    /** 流程图。 */
    FLOW,

    /** 数据库实体关系图。 */
    ER,

    /** Chen 表示法的系统概念 E-R 图。 */
    SYSTEM_ER,

    /** UML 类图。 */
    CLASS,

    /** CSCI 等软件部件关系图。 */
    COMPONENT,

    /** 系统总体功能逻辑分解图。 */
    OVERALL_FUNCTION_LOGIC
}
