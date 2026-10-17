package cn.bugstack.application.csci;

import java.util.ArrayList;
import java.util.List;

/**
 * 从上传的 Java 源文件和 DDL 提取出的 CSCI 详细设计中间模型。
 *
 * <p>所有列表按 ZIP 条目路径及源码声明顺序构建，以保证重复生成结果稳定。</p>
 */
public final class CsciDesign {

    /** 软件设计及数据库设计共用的软件名称。 */
    public String software;
    /** 软件包含的功能模块，按目录顺序排列。 */
    public final List<Module> modules = new ArrayList<>();
    /** 从 DDL 提取出的数据库表。 */
    public final List<DbTable> databaseTables = new ArrayList<>();

    /** 软件下的一个功能模块。 */
    public static final class Module {
        /** 功能模块名称，对应 ZIP 中的模块目录名。 */
        public String name;
        /** 模块包含的功能。 */
        public final List<Feature> features = new ArrayList<>();
    }

    /** 功能及其源码、界面设计信息。 */
    public static final class Feature {
        /** 功能名称，对应 ZIP 中的功能目录名。 */
        public String name;
        /** 从类 Javadoc 中提取的界面设计说明。 */
        public String uiDescription;
        /** 可选的界面设计 PNG 原始字节。 */
        public byte[] uiImagePng;
        /** 属于该功能的 Java 类，包括内部类。 */
        public final List<JavaClass> classes = new ArrayList<>();
    }

    /** Java 类、接口或枚举的设计信息。 */
    public static final class JavaClass {
        /** 类名；内部类以点号连接外部类名。 */
        public String name;
        /** 声明类型，如“类”“接口”或“枚举”。 */
        public String kind;
        /** 类 Javadoc 的主体描述。 */
        public String description;
        /** 类中声明的字段。 */
        public final List<Field> fields = new ArrayList<>();
        /** 类中声明的非构造方法。 */
        public final List<Method> methods = new ArrayList<>();
    }

    /** 类字段；同时用于数据元素表。 */
    public static final class Field {
        /** 字段名称。 */
        public String name;
        /** 字段在源码中声明的类型。 */
        public String type;
        /** 字段访问级别。 */
        public String visibility;
        /** 字段 Javadoc 的主体描述。 */
        public String description;
    }

    /** 类操作及函数设计。 */
    public static final class Method {
        /** 方法名称。 */
        public String name;
        /** 方法访问级别。 */
        public String visibility;
        /** 方法返回类型。 */
        public String returnType;
        /** 方法 Javadoc 的主体描述。 */
        public String description;
        /** Javadoc {@code @return} 中的返回值说明。 */
        public String returnDescription;
        /** 算法与逻辑说明，优先取显式 Javadoc。 */
        public String algorithm;
        /** 编程注意事项，优先取显式 Javadoc。 */
        public String caution;
        /** 原始方法体，仅作为可选 AI 补全的证据，不直接写入文档。 */
        public String sourceBody;
        /** 从 Javadoc 流程标签提取的有序步骤。 */
        public final List<String> flowSteps = new ArrayList<>();
        /** 方法形参及其说明。 */
        public final List<Parameter> parameters = new ArrayList<>();
    }

    /** 方法参数。 */
    public static final class Parameter {
        /** 形参名称。 */
        public String name;
        /** 形参在源码中声明的类型。 */
        public String type;
        /** Javadoc {@code @param} 中的形参说明。 */
        public String description;
    }

    /** 数据库物理表。 */
    public static final class DbTable {
        /** DDL 中的物理表名。 */
        public String physicalName;
        /** 表注释对应的逻辑名称；无注释时为物理表名。 */
        public String logicalName;
        /** DDL 中的表注释。 */
        public String description;
        /** 表中按 DDL 声明顺序排列的字段。 */
        public final List<Column> columns = new ArrayList<>();
    }

    /** 数据库字段。 */
    public static final class Column {
        /** 字段名称。 */
        public String name;
        /** DDL 中声明的数据类型。 */
        public String type;
        /** 主键、空值及默认值等约束文字。 */
        public String constraint;
        /** 字段注释。 */
        public String comment;
    }
}
