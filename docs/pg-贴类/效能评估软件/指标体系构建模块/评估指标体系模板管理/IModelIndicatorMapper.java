package com.yw.module.indicator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.indicator.domain.entity.ModelIndicatorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


import java.util.List;

/**
 * @Author caoyu
 * @Date 2023/10/9
 * @Description 指标模型表操作dao
 */

@Mapper
public interface IModelIndicatorMapper extends BaseMapper<ModelIndicatorEntity> {

    List<ModelIndicatorEntity> showIndicatorTreeId(@Param("treeId") String treeId);

    void saveBatch(@Param("list") List<ModelIndicatorEntity> data);
}
