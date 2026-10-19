package com.yw.module.missionScene.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yw.module.missionScene.domain.dto.CompeteSceneDTO;
import com.yw.module.missionScene.domain.entity.CompeteSceneEntity;
import com.yw.module.missionScene.domain.vo.SceneScenarioTreeVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author x l
 * @description 对抗场景Mapper
 * @date 2023-4-6 19:09
 */
@Mapper
public interface CompeteSceneMapper extends BaseMapper<CompeteSceneEntity> {

    /**
     * 获取对抗场景信息
     *
     *
     * @param s
     * @param name 名称
     * @param taskModel
     * @return
     */
//    @Select("<script>" +
//            "SELECT ID as id,NAME,TASK_COUNT,EQUIPMENT_COUNT,DESCS as descs,CREATE_TIME as createTime,SID,TASK_IDS as taskIds," +
//            "FIGHTER_TIME as fighterTime , ENEMY as enemy , FIGHTERAREA as fighterArea , TASK_MODEL as taskModel" +
//            "FROM YW_TOSES_COMPETE_SCENE " +
//            "<where>" +
//            "<if test=\"name != null and name != ''\"> and NAME like concat('%',#{name},'%')</if>" +
//            "<if test=\"sid != null and sid != ''\"> and SID = #{sid}</if>" +
//            "</where> order by CREATE_TIME desc" +
//            "</script>")
    List<CompeteSceneDTO> showCompeteScene(@Param("name") String name, @Param("sid")String sid, @Param("taskModel")Integer taskModel);

    /**
     * 修改场景装备个数
     *
     * @param competeSceneId 场景ID
     * @param size           装备个数
     */
    @Update("<script>" +
            "update YW_TOSES_COMPETE_SCENE set EQUIPMENT_COUNT =  #{size} " +
            "where ID = #{competeSceneId} " +
            "</script>")
    void updateEquipmentCount(@Param("competeSceneId") String competeSceneId, @Param("size") int size);

    /**
     * 通过场景id，查询所有样本
     * @param competeSceneId 场景ID
     * @return
     */
    @Select("<script>" +
            "SELECT s.id, s.name " +
            "FROM yw_toses_scene s " +
            "left JOIN yw_toses_contribution_rate_algorithm cra ON cra.SCENE_ID = s.id " +
            "where cra.COMPETE_SCENE_ID = #{competeSceneId} and cra.SCENE_SCENARIO_ID=#{sceneScenarioId} " +
            "</script>")
    List<SceneScenarioTreeVo> selectChildScene(@Param("competeSceneId")String competeSceneId, String sceneScenarioId);
}
