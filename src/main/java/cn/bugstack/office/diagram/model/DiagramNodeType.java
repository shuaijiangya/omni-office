package cn.bugstack.office.diagram.model;

/**
 * 图节点的展示类型。
 */
public enum DiagramNodeType {

    /** 外部参与者。 */
    ACTOR,

    /** 用例。 */
    USE_CASE,

    /** 流程开始节点。 */
    START,

    /** 普通流程处理节点。 */
    PROCESS,

    /** 流程判断节点。 */
    DECISION,

    /** 并行流程分支节点。 */
    PARALLEL_SPLIT,

    /** 并行流程汇合节点。 */
    PARALLEL_JOIN,

    /** 流程结束节点。 */
    END,

    /** ER 图实体节点。 */
    ENTITY,

    /** 系统 E-R 图中的关系菱形节点。 */
    RELATIONSHIP,

    /** 系统 E-R 图中的属性椭圆节点。 */
    ATTRIBUTE,

    /** UML 类图中的类节点。 */
    CLASS,

    /** CSCI 等软件部件节点。 */
    COMPONENT,

    /** 总体功能逻辑图中的系统根节点。 */
    FUNCTION_SYSTEM,

    /** 总体功能逻辑图中的一级功能模块节点。 */
    FUNCTION_MODULE,

    /** 总体功能逻辑图中的末级功能项节点。 */
    FUNCTION_ITEM
}
