package com.yw.module.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.plan.domain.entity.SchemaEntity;
import com.yw.module.plan.domain.vo.ExistingResultSchemaVO;
import com.yw.module.plan.domain.vo.SchemaInfoVO;
import com.yw.framework.backMgr.dbTableGenerator.domain.dto.DbLinkDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface ISchemaMapper extends BaseMapper<SchemaEntity> {

    /**
     * 获取场景信息，场景下想定信息，场景下仿真变量信息
     * @param schemaId 方案id
     * @return
     */
    SchemaEntity openSchema(String schemaId);

    /**
     * @description: 查询方案、场景、任务关联关系
     * @return: java.util.List<java.util.Map < java.lang.String, java.lang.Object>>
     * @author: 肖强虎
     * @time: 2021/8/20 0020 15:54
     */
    List<Map<String, Object>> findSSI();

    /**
     * 根据装备id和指标id查询
     *
     * @param ids
     * @param judge
     * @return
     */
    List<Map<String, Object>> findIndicatorByEquipmentId(@Param("ids") List<String> ids, @Param("judge") String judge);


    /**
     * 根据下达任务id获取方案id
     *
     * @param issuedTaskId
     * @return
     */
    List<String> findId(@Param("id") String issuedTaskId);

    /**
     * 查询所有任务信息
     *
     * @return
     */
    List<SchemaInfoVO> findList();

    /**
     * 根据任务名称查看任务基础信息
     *
     * @param name
     * @param type
     * @param ids
     * @return
     */
    List<SchemaInfoVO> findByName(@Param("name") String name, @Param("startTime") String startTime, @Param("endTime") String endTime,
                                  @Param("type")Integer type,@Param("ids")List<String> ids, @Param("userId") String userId,
                                  @Param("sortType")String sortType);

    /**
     * 获取方案个数
     *
     * @param ids
     * @return
     */
    int showcount(@Param("ids") List<String> ids);

    List<String> getIdInfo(@Param("item") List<String> item);

    void adjustSelectedStatus(@Param("ids") List<String> ids, @Param("schemaCheck") int schemaCheck);

    /**
     * @return void
     * @Author lj
     * @Description 修改所有的选择状态
     * @Date 2022/3/29
     * @Param [id]
     **/
    void adjustSelectedStatusAll(@Param("schemaCheck") int schemaCheck);

    /**
     * 查询装备关联的任务样本数据
     *
     * @param evaluationIdList 装备id数组
     */
    @Select("<script>" +
            "SELECT sc.ID, sc.SAMPLE_TASK_ID, st.DSID " +
            "FROM YW_TOSES_SCHEMA s INNER JOIN YW_TOSES_SCENE sc ON s.ID = sc.SID " +
            "INNER JOIN YW_TOSES_SCENE_TABLE st ON st.SID = s.ID " +
            "WHERE s.EVALUATION_OBJECT IN " +
            "<foreach item='item' collection='evaluationIdList' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    List<Map<String, String>> findTaskIdAndDsidByEvaluationObject(@Param("evaluationIdList") Set<String> evaluationIdList);


    List<Map<String, Object>> findIndicatorByEquipmentIdV2(List<String> ids);

    List<ExistingResultSchemaVO> selectExistingResultSchema(@Param("schemaId") String schemaId);

    /**
     * 根据方案id查询对应的数据源信息
     * @param schemaId
     * @return
     */
    DbLinkDTO selectDbLinkInfo(@Param("dataSrcId") String dataSrcId);
}
