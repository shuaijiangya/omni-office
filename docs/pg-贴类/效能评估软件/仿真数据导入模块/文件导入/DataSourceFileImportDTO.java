package com.yw.module.indicator.domain.dto;

import com.yw.framework.common.module.upload.defines.FileInfo;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataSourceFileImportDTO {

    @ApiModelProperty(value = "文件信息")
    private FileInfo fileInfo;

    private List<indicatorMsg> list;

    private String schemaName;
    @Data
    public static class indicatorMsg{
        @ApiModelProperty(value = "数据源名称", name = "dataBasesName")
        private String dataBasesName;

        @ApiModelProperty(value = "表名称", name = "tableName")
        private String tableName;

        @ApiModelProperty(value = "数据源ID", name = "dataSrcId")
        private String dataSrcId;


        @ApiModelProperty(value = "轮次标识", name = "roundKeys")
        private String roundKeys;

        @ApiModelProperty("界面列名")
        private List<String> indicatorColumnNames;
    }

}
