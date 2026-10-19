package com.yw.module.calculation.indicatorCalculation.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yw.common.utils.UUIDGenerator;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.indicatorCalculation.domain.dto.EvaluateCalcorDTO;
import com.yw.module.calculation.indicatorCalculation.domain.entity.AuthorInformationEntity;
import com.yw.module.calculation.indicatorCalculation.domain.entity.EvaluateCalcorEntity;
import com.yw.module.calculation.indicatorCalculation.domain.entity.InputParamEntity;
import com.yw.module.calculation.indicatorCalculation.domain.entity.UnitInformationEntity;
import com.yw.module.calculation.indicatorCalculation.domain.enums.RequestTypeEnums;
import com.yw.module.calculation.indicatorCalculation.mapper.EvaluateCalcorMapper;
import com.yw.module.calculation.indicatorCalculation.service.AuthorInformationService;
import com.yw.module.calculation.indicatorCalculation.service.EvaluateCalcorService;
import com.yw.module.calculation.indicatorCalculation.service.InputParamService;
import com.yw.module.calculation.indicatorCalculation.service.UnitInformationService;
import com.yw.module.datasource.domain.dto.EvaluateCalcorAndParamDto;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 算子管理业务实现层
 * <p>
 * yc
 */
@Service
public class EvaluateCalcorServiceImpl implements EvaluateCalcorService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(EvaluateCalcorServiceImpl.class);

    @Autowired
    private EvaluateCalcorMapper evaluateCalcorMapper;

    private final TypeAliasRegistry typeAliasRegistry = new TypeAliasRegistry();

    @Autowired
    private InputParamService inputParamService;

    @Autowired
    private UnitInformationService unitInformationService;

    @Autowired
    private AuthorInformationService authorInformationService;

    @Override
    public PageInfo<EvaluateCalcorEntity> list(Integer offSet, Integer limit, EvaluateCalcorDTO dto) {
        //校验参数 开启分页
        if (offSet != 0) {
            PageHelper.startPage(offSet, limit);
        }
        //查询所有信息
        QueryWrapper<EvaluateCalcorEntity> queryWrapper = new QueryWrapper<>();
        if (ObjectUtils.isNotEmpty(dto.getEntityType())) {
            queryWrapper.eq("ENTITY_TYPE", dto.getEntityType());
        }
        if (StringUtils.isNotEmpty(dto.getName())) {
            queryWrapper.like("NAME", dto.getName());
        }
        if (StringUtils.isNotEmpty(dto.getUnitName())) {
            queryWrapper.like("UNIT_NAME", dto.getUnitName());
        }
        if (StringUtils.isNotEmpty(dto.getAuthor())) {
            queryWrapper.like("AUTHOR", dto.getAuthor());
        }
        if (StringUtils.isNotEmpty(dto.getMethod())) {
            queryWrapper.like("METHOD", dto.getMethod());
        }
        queryWrapper.orderByDesc("CREATE_TIME");
        List<EvaluateCalcorEntity> list = evaluateCalcorMapper.selectList(queryWrapper);
        if (CollectionUtil.isNotEmpty(list)) {
            List<String> idList = list.stream().map(EvaluateCalcorEntity::getId).collect(Collectors.toList());
            List<InputParamEntity> inputParamEntityList = inputParamService.findByEvaluateIds(idList);
            if (CollectionUtil.isNotEmpty(inputParamEntityList)) {
                //按外部算子ID分组
                Map<String, List<InputParamEntity>> groupByEvaluateId = inputParamEntityList.stream().collect(Collectors.groupingBy(InputParamEntity::getEvaluateId));
                //设置算子参数
                list.forEach(e -> {
                    List<InputParamEntity> findParamList = groupByEvaluateId.get(e.getId());
                    if (CollectionUtil.isNotEmpty(findParamList)) {
                        e.setParamList(findParamList);
                    }
                });
            }
        }
        return new PageInfo<>(list);
    }

    @Override
    public EvaluateCalcorEntity getById(String id) {
        EvaluateCalcorEntity evaluateCalcorEntity = evaluateCalcorMapper.selectById(id);
        if (evaluateCalcorEntity != null) {
            List<InputParamEntity> list = inputParamService.findByEvaluateId(id);
            evaluateCalcorEntity.setParamList(list);
            return evaluateCalcorEntity;
        }
        return null;
    }

    @Transactional
    @Override
    public void deleteById(String id) {
        if (StringUtils.isEmpty(id)) {
            throw new BusinessException("id is null");
        }
        inputParamService.deleteByEvaluateId(id);
        try {
            evaluateCalcorMapper.deleteById(id);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    @Transactional
    @Override
    public String add(EvaluateCalcorEntity evaluateCalcorEntity) {
        //添加判断,重名校验
        List<EvaluateCalcorEntity> evaluateCalcorEntities = evaluateCalcorMapper.selectList(new QueryWrapper<EvaluateCalcorEntity>().likeRight("NAME", evaluateCalcorEntity.getName()));
        List<String> calcorIds = evaluateCalcorEntities.stream().map(EvaluateCalcorEntity::getId).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(calcorIds) && calcorIds.size() > 1) {
            throw new BusinessException("名称重复，不能添加");
        }
        if (CollUtil.isNotEmpty(calcorIds) && calcorIds.size() == 1 && Objects.isNull(evaluateCalcorEntity.getId())) {
            throw new BusinessException("名称重复，不能添加");
        }
        if (CollUtil.isNotEmpty(calcorIds) && calcorIds.size() == 1 && !evaluateCalcorEntity.getId().equals(calcorIds.get(0))) {
            throw new BusinessException("名称重复，不能添加");
        }
        //校验请求方式
        RequestTypeEnums typeEnums = RequestTypeEnums.getTypeEnums(evaluateCalcorEntity.getType());
        if (typeEnums == RequestTypeEnums.NONE) {
            throw new BusinessException("未指定对应调用类型");
        }
        if (StringUtils.isBlank(evaluateCalcorEntity.getId())) {
            evaluateCalcorEntity.setId(UUIDGenerator.getUUID());
        }
        EvaluateCalcorEntity save = saveEvaluateCalcorEntity(evaluateCalcorEntity);
        String id = save.getId();
        List<InputParamEntity> paramList = doSetInputParamEntities(evaluateCalcorEntity, id);
        inputParamService.add(paramList);
        return id;

    }


    private List<InputParamEntity> doSetInputParamEntities(EvaluateCalcorEntity evaluateCalcorEntity, String id) {
        List<InputParamEntity> paramList = evaluateCalcorEntity.getParamList();
        for (InputParamEntity inputParamEntity : paramList) {
            String paramEntityId = inputParamEntity.getId();
            if (StringUtils.isEmpty(paramEntityId)) {
                inputParamEntity.setId(UUIDGenerator.getUUID());
            }
            String type = inputParamEntity.getType();
            if (!typeAliasRegistry.getTypeAliases().containsKey(type)) {
                throw new BusinessException("无法解析当前设置的数据类型,数据类型为: " + type);
            }
            inputParamEntity.setEvaluateId(id);
        }
        return paramList;
    }

    /**
     * 1、命名 满足驼峰命名
     * 2、需求考虑全面一点在开始做 不要急于动手 写代码先写伪代码 以文字的方式描述当前方法需要做什么事情
     * 3、先完成一个功能点再往下做 不要急着做 时间会给的充足
     * 4、多写点注释以及给出日志
     * 5、考虑将公共的代码抽取成一个方法 ctrl+alt+m
     *
     * @param evaluateCalcorEntity
     * @param ParamIds
     */
    @Override
    public void edit(EvaluateCalcorEntity evaluateCalcorEntity, List<String> ParamIds) {
        if (Objects.isNull(evaluateCalcorEntity)) {
            throw new BusinessException("对象为空");
        }
        EvaluateCalcorEntity save = updateEvaluateCalcorEntity(evaluateCalcorEntity);
        String evaluateId = save.getId();
        if (StringUtils.isEmpty(evaluateId)) {
            throw new BusinessException("EvaluateId为空");
        }
        List<InputParamEntity> paramList = setParamInputLists(evaluateCalcorEntity, evaluateId);
        inputParamService.deleteByEvaluateId(evaluateId);
        inputParamService.add(paramList);
    }


    private List<InputParamEntity> setParamInputLists(EvaluateCalcorEntity evaluateCalcorEntity, String evaluateId) {
        return doSetInputParamEntities(evaluateCalcorEntity, evaluateId);
    }


    private EvaluateCalcorEntity saveEvaluateCalcorEntity(EvaluateCalcorEntity evaluateCalcorEntity) {
        String authorID = evaluateCalcorEntity.getAuthorId();
        AuthorInformationEntity authorInformationEntity = authorInformationService.selectById(authorID);
        String unitId = evaluateCalcorEntity.getUnitId();
        UnitInformationEntity unitInformationEntity = unitInformationService.selectById(unitId);
        evaluateCalcorEntity.setAuthor(authorInformationEntity.getAuthorName());
        evaluateCalcorEntity.setUnitName(unitInformationEntity.getUnitName());
        evaluateCalcorMapper.insert(evaluateCalcorEntity);
        return evaluateCalcorEntity;
    }


    private EvaluateCalcorEntity updateEvaluateCalcorEntity(EvaluateCalcorEntity evaluateCalcorEntity) {
        String authorID = evaluateCalcorEntity.getAuthorId();
        AuthorInformationEntity authorInformationEntity = authorInformationService.selectById(authorID);
        String unitId = evaluateCalcorEntity.getUnitId();
        UnitInformationEntity unitInformationEntity = unitInformationService.selectById(unitId);
        evaluateCalcorEntity.setAuthor(authorInformationEntity.getAuthorName());
        evaluateCalcorEntity.setUnitName(unitInformationEntity.getUnitName());
        evaluateCalcorMapper.updateById(evaluateCalcorEntity);
        return evaluateCalcorEntity;
    }

    @Override
    public List<EvaluateCalcorEntity> getBatch(List<String> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        List<EvaluateCalcorEntity> AllList = evaluateCalcorMapper.selectBatchIds(ids);
        return AllList;
    }

    @Override
    public List<EvaluateCalcorEntity> listDefault() {
        return evaluateCalcorMapper.selectList(new QueryWrapper<EvaluateCalcorEntity>().eq("ENTITY_TYPE", 0));
    }

    @Override
    public void importCalcorAndParam(List<EvaluateCalcorAndParamDto> data) {

        for (EvaluateCalcorAndParamDto e : data) {

//            Date date = new Date();

            AuthorInformationEntity authorInformationEntity = authorInformationService.selectById(e.getAuthorId());
            UnitInformationEntity unitInformationEntity = unitInformationService.selectById(e.getUnitId());

            EvaluateCalcorEntity evaluateCalcorEntity = BeanUtil.toBean(e, EvaluateCalcorEntity.class);
            // 图片需要编码
            evaluateCalcorEntity.setImage(Arrays.toString(Base64.getEncoder().encode(e.getImage().getBytes())));
            // 默认导入均为外部算法
            evaluateCalcorEntity.setEntityType(1);
            evaluateCalcorEntity.setCreateTime(LocalDateTime.now());
            evaluateCalcorEntity.setAuthor(authorInformationEntity.getAuthorName());
            evaluateCalcorEntity.setUnitName(unitInformationEntity.getUnitName());

            for (EvaluateCalcorAndParamDto.InputParam inputParam : e.getParams()) {
                InputParamEntity inputParamEntity = BeanUtil.toBean(inputParam, InputParamEntity.class);
                evaluateCalcorEntity.getParamList().add(inputParamEntity);
            }
            add(evaluateCalcorEntity);
        }
    }
}
