package com.yw.module.indicator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yw.module.system.domain.dto.LogIndicatorSystemDto;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorStandardEntity;
import com.yw.module.indicator.domain.entity.IndicatorTreeEntity;
import com.yw.common.defines.enums.BusinessType;

import com.yw.framework.common.base.support.BaseServiceImpl;
import com.yw.framework.common.module.login.mapper.UserMapper;
import com.yw.module.indicator.mapper.IIndicatorTreeMapper;
import com.yw.module.indicator.mapper.IndicatorMapper;
import com.yw.module.indicator.mapper.IndicatorStandardMapper;
import com.yw.module.indicator.service.ITIndicatorStandardService;
import com.yw.module.indicator.service.IStandardLabelsService;
import com.yw.common.utils.ArrayUtils;
import com.yw.common.utils.UserUtil;
import com.yw.module.system.mapper.PGLogMapper;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * @Author admin
 * @Description: 指标标准服务层实现
 * @Date: 2021/2/4 10:38
 */
@Service
public class IndicatorStandardServiceImpl extends BaseServiceImpl<IndicatorStandardMapper,IndicatorStandardEntity> implements ITIndicatorStandardService {

    private final IndicatorStandardMapper standardMapper;
//    private final ITIndicatorStandardRepository standardRepository;
    private final IStandardLabelsService standardLabelsService;

    @Resource
    private PGLogMapper pgLogMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private IndicatorMapper indicatorMapper;
    @Resource
    private IIndicatorTreeMapper indicatorTreeMapper;

    public IndicatorStandardServiceImpl(IndicatorStandardMapper standardMapper, IStandardLabelsService standardLabelsService) {
        this.standardMapper = standardMapper;
        this.standardLabelsService = standardLabelsService;
    }

    /**
     * 查询指标
     *
     * @param id 指标ID
     * @return
     */
    @Override
    public Optional<IndicatorStandardEntity> findById(String id) {
//        return standardRepository.findById(id);
        return Optional.ofNullable(this.getById(id));
    }

    /**
     * 查询
     *
     * @param entity
     * @return
     */
    @Override
    public List<IndicatorStandardEntity> find(IndicatorStandardEntity entity) {
        Example<IndicatorStandardEntity> example = Example.of(entity);
        return this.list(new QueryWrapper<IndicatorStandardEntity>().setEntity(entity));
    }

    /**
     * 新增编辑
     *
     * @param data
     * @return
     */
    @Override
    public void edit(IndicatorStandardEntity data) {
        this.saveOrUpdate(data);
    }

    /**
     * 删除
     *
     * @param id
     * @return
     */
    @Override
    public void delete(String id) {
        List<IndicatorStandardEntity> optional = find(IndicatorStandardEntity.builder().indicatorId(id).build());
        if (optional != null && optional.size() > 0) {
            standardMapper.delete(new LambdaQueryWrapper<IndicatorStandardEntity>().eq(IndicatorStandardEntity::getIndicatorId,id));
            optional.stream().forEach(s -> {
                standardLabelsService.delete(s.getLabelId());
            });
        }
    }

    @Override
    public void saveAll(List<IndicatorStandardEntity> entityList) {
        this.saveBatch(entityList);
    }

    @Override
    public void deleteBySid(String sid) {
//        standardMapper.deleteByCompeteSceneIdAndIndicatorIds(sid);
//        standardRepository.deleteBySid(sid);
        this.remove(new QueryWrapper<IndicatorStandardEntity>().eq("SID",sid));
    }

    @Override
    public void deleteByIndicatorIds(List<String> indicatorIdList) {
        standardMapper.deleteByIndicatorId(indicatorIdList);
    }

    /**
     * 删除多个通过id
     *
     * @param ids
     * @return
     */
    @Override
    public void deleteByIds(String ids) {
        try{
            //记录操作日志
            String userId = UserUtil.getUserId();
//            List<IndicatorStandardEntity> list = standardRepository.findAllById(ArrayUtils.stringToList(ids));
            List<IndicatorStandardEntity> list = this.listByIds(ArrayUtils.stringToList(ids));
            list.forEach(
                    i -> {
                        //查询指标树类型
                        IndicatorEntity indicatorEntity = indicatorMapper.selectById(i.getIndicatorId());
                        IndicatorTreeEntity indicatorTreeEntity = indicatorTreeMapper.selectById(indicatorEntity.getTreeId());
                        pgLogMapper.insert(
                                LogIndicatorSystemDto.getLog(
                                        i.getIndicatorStanName(),
                                        userId,
                                        userMapper.selectById(userId).getUsername(),
                                        BusinessType.DELETE.name(),
                                        indicatorTreeEntity.getIndicatorTreeType(),
                                        "删除准则"
                                )
                        );
                    }
            );
            //记录操作日志
        } catch (Exception e){
            e.printStackTrace();
        }
//        standardRepository.deleteByIds(ArrayUtils.stringToList(ids));
        this.removeByIds(ArrayUtils.stringToList(ids));
    }
}
