package com.yw.module.indicator.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yw.framework.common.base.support.BaseServiceImpl;
import com.yw.module.analysis.domain.vo.IndicatorValueWeightVo;
import com.yw.module.indicator.domain.dto.IndicatorValuesWithNameDTO;
import com.yw.module.indicator.domain.entity.IndicatorValuesEntity;
import com.yw.module.indicator.mapper.IndicatorValuesMapper;
import com.yw.module.indicator.service.ITIndicatorValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @Author admin
 * @Description: 指标值业务
 * @Date: 2020/12/23 16:09
 */
@Service
public class IndicatorValueServiceImpl extends BaseServiceImpl<IndicatorValuesMapper, IndicatorValuesEntity> implements ITIndicatorValueService {

    @Autowired
    private IndicatorValuesMapper indicatorValuesMapper;

    @Override
    public List<IndicatorValuesEntity> find(IndicatorValuesEntity entity) {
        return indicatorValuesMapper.selectList(new QueryWrapper<IndicatorValuesEntity>().setEntity(entity));
    }

    @Override
    public List<IndicatorValuesEntity> findBySid(String sid) {
        return indicatorValuesMapper.selectList(new QueryWrapper<IndicatorValuesEntity>().eq("SID", sid));
    }

    @Transactional
    @Override
    public void saveAll(Collection<IndicatorValuesEntity> valuesEntities) {
//        for (IndicatorValuesEntity valuesEntity : valuesEntities) {
//            valuesEntity.setId(UUIDGenerator.getUUID());
//        }
        this.saveBatch(valuesEntities);
    }

    @Override
    public void delete(IndicatorValuesEntity entity) {
        indicatorValuesMapper.deleteById(entity);
    }

    @Override
    public List<IndicatorValuesWithNameDTO> findWithNameBySidIndicatorIdSceneId(String sid, List<String> indicatorIds, List<String> sceneIdList) {
        return indicatorValuesMapper.findWithNameBySidIndicatorIdSceneId(sid, indicatorIds, sceneIdList);
    }

    @Override
    public List<IndicatorValuesWithNameDTO> findWithNameBySidIndicatorTreeId(String sid, String indicatorTreeId) {
        return indicatorValuesMapper.findWithNameBySidIndicatorTreeId(sid, indicatorTreeId);
    }

    @Override
    public List<IndicatorValuesWithNameDTO> findWithNameBySidIndicatorTreeIdSceneId(String sid, String indicatorTreeId, String sceneId) {
        return indicatorValuesMapper.findWithNameBySidIndicatorTreeIdSceneId(sid, indicatorTreeId, sceneId);
    }

    @Override
    public List<IndicatorValuesWithNameDTO> findWithNameBySidIndicatorIdSSLId(String sid, List<String> indicatorIds, List<String> SSLIds) {
        return indicatorValuesMapper.findWithNameBySidIndicatorIdSSLId(sid, indicatorIds, SSLIds);
    }

    @Override
    public List<IndicatorValuesEntity> findBySidSceneId(String sid, String sceneId) {
        return indicatorValuesMapper.findBySidSceneId(sid, sceneId);
    }

    @Override
    public List<IndicatorValuesWithNameDTO> findBySceneIdPid(String sceneId, String pid) {
        return indicatorValuesMapper.findBySceneIdPid(sceneId, pid);
    }

    @Override
    public List<IndicatorValueWeightVo> findIndicatorValueWeightVoBySceneId(String sceneId) {
        return indicatorValuesMapper.findIndicatorValueWeightVoBySceneId(sceneId);
    }

    @Override
    public IndicatorValueWeightVo findIndicatorValueWeightVoByIndicatorId(String indicatorId) {
        return indicatorValuesMapper.findIndicatorValueWeightVoByIndicator(indicatorId);
    }

    @Override
    public void deleteBySceneIdIndicatorIdSid(String sid, String sceneId, String indicatorId) {
        indicatorValuesMapper.delete(new LambdaQueryWrapper<IndicatorValuesEntity>().eq(IndicatorValuesEntity::getSid, sid).eq(IndicatorValuesEntity::getIndicatorId, indicatorId).eq(IndicatorValuesEntity::getSceneId, sceneId));
    }

    /**
     * 删除指标结果根据方案ID、样本ID
     *
     * @param sid             方案ID
     * @param sceneId         样本ID
     * @param removeIndicator 指标ID
     */
    @Override
    public void deleteBySceneIdSid(String sid, String sceneId, List<String> removeIndicator) {
        if (CollUtil.isEmpty(removeIndicator)) {
            return;
        }
        indicatorValuesMapper.delete(new LambdaQueryWrapper<IndicatorValuesEntity>()
                //.eq(IndicatorValuesEntity::getSid, sid)
                .eq(IndicatorValuesEntity::getSceneId, sceneId)
                .in(IndicatorValuesEntity::getIndicatorId, removeIndicator));
    }

    @Override
    public List<IndicatorValuesEntity> findByIndicatorIdIn(List<String> indicator) {
        return this.list(new QueryWrapper<IndicatorValuesEntity>().in("INDICATOR_ID", indicator));
    }

    @Override
    public List<IndicatorValuesEntity> findBySidAndSceneIdIn(String sid, List<String> sceneIds) {
        return indicatorValuesMapper.selectList(new QueryWrapper<IndicatorValuesEntity>().eq("SID", sid).in(!sceneIds.isEmpty(), "SCENE_ID", sceneIds));
    }

    /**
     * 删除方案下的样本数据集
     *
     * @param sid          方案id
     * @param sceneList    样本id集合
     * @param indicatorIds 指标集合id
     */
    @Override
    public void deleteBySceneIdAndSid(String sid, List<String> sceneList, Set<String> indicatorIds) {
        indicatorValuesMapper.delete(new LambdaQueryWrapper<IndicatorValuesEntity>()
                .eq(IndicatorValuesEntity::getSid, sid)
                .in(CollUtil.isNotEmpty(sceneList), IndicatorValuesEntity::getSceneId, sceneList)
                .in(CollUtil.isNotEmpty(indicatorIds), IndicatorValuesEntity::getIndicatorId, indicatorIds));
    }
}
