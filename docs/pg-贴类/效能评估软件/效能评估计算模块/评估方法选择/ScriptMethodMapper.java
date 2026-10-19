package com.yw.module.calculation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.calculation.domain.entity.ScriptMethodEntity;
import com.yw.module.calculation.contributionRate.domain.vo.ScriptMethodInfoVO;
import com.yw.module.calculation.contributionRate.domain.vo.ScriptMethodVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author lj
 * @description 算法脚本dao层
 * @date 2021-10-25 16:05
 */
@Mapper
public interface ScriptMethodMapper extends BaseMapper<ScriptMethodEntity> {

    /**
     * 将脚本算法基础信息写入数据库
     *
     * @param entity
     */
    void save(ScriptMethodEntity entity);

    /**
     * 修改脚本算法基础信息
     *
     * @param entity
     */
    void update(ScriptMethodEntity entity);

    /**
     * 根据id查询脚本算法信息
     *
     * @param id
     * @return
     */
    ScriptMethodEntity queryById(@Param("id") String id);

    /**
     * 删除脚本算法信息
     *
     * @param id
     */
    void delete(@Param("id") String id);

    /**
     * 去重脚本算法名称
     *
     * @param scriptName
     * @return
     */
    int queryByName(String scriptName,String entityId);

    /**
     * 脚本算法信息可视化
     *
     * @return
     */
    List<ScriptMethodVO> findList();

    /**
     * 根据算法名称获取算法信息
     * @param scriptName
     * @return
     */
    List<ScriptMethodVO> findByName(String scriptName);

    /**
     * 综合pg算法id 以及 名称展示
     * @return
     */
    List<ScriptMethodInfoVO> scriptList();

}
