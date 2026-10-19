package com.yw.module.export.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Author admin
 * @Description: 导出mapper
 * @Date: 2021/5/10 16:25
 */
@Mapper
public interface IExportMapper {

    /**
     * @param sid 方案id
     * @Author 刘乐法
     * @Description 查询该方案下使用了什么算法（pg算法，权重算法）
     * @Return
     * @Date 2021/5/10 16:29
     */
    List<String> findEvaMethod(@Param("sid") String sid);

    /**
     * @param sid 方案id
     * @Author 刘乐法
     * @Description 查询该方案下使用了什么算法（评估算法，权重算法）
     * @Return
     * @Date 2021/5/10 16:29
     */
    List<String> findEvaMethodV2(@Param("sid") String sid);
    /**
     * 根据样本查询
     *
     * @param scenes
     * @return
     */
    List<String> findIssuedByScene(@Param("scenes") List<String> scenes);
}
