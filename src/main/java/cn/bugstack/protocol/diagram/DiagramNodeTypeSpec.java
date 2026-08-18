package cn.bugstack.protocol.diagram;

/** 对外稳定的图节点类型。 */
public enum DiagramNodeTypeSpec {
    ACTOR,
    USE_CASE,
    START,
    PROCESS,
    DECISION,
    PARALLEL_SPLIT,
    PARALLEL_JOIN,
    END,
    ENTITY,
    RELATIONSHIP,
    ATTRIBUTE,
    CLASS,
    COMPONENT,
    FUNCTION_SYSTEM,
    FUNCTION_MODULE,
    FUNCTION_ITEM
}
