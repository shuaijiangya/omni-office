package com.yw.module.datasource.domain.req;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author admin
 * @since 2024/2/23
 */
@Data
public class DbImportRequest implements Serializable {
    /**
     * 数据源连接id
     */
    private String analysisId;
    /**
     * 关联计算参数
     */
    private List<DbDataToCalcParam> dbDataToCalcParamList;
    /**
     * 轮次字段
     */
    private String roundRecordKey;

    @Data
    public static class DbDataToCalcParam implements Serializable {
        /**
         * 表名称
         */
        private String tableName;
        /**
         * 字段名称
         */
        private String columnName;
        /**
         * 计算参数id
         */
        private String calcParamId;
    }
}
