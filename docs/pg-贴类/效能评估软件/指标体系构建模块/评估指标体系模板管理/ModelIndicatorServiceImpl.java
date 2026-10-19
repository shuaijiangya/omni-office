package com.yw.module.indicator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.google.common.collect.Lists;
import com.yw.common.defines.constants.ServiceConstant;
import com.yw.common.utils.TreeToolHelp;
import com.yw.common.utils.TreeToolUtils;
import com.yw.framework.common.base.support.BaseServiceImpl;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorTreeEntity;
import com.yw.module.indicator.domain.entity.ModelIndicatorEntity;
import com.yw.module.indicator.domain.entity.StandardValueEntity;
import com.yw.module.indicator.mapper.IIndicatorTreeMapper;
import com.yw.module.indicator.mapper.IModelIndicatorMapper;
import com.yw.module.indicator.mapper.IndicatorMapper;
import com.yw.module.indicator.service.IModelIndicatorService;
import com.yw.module.indicator.service.IStandardValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @Author admin
 * @Description: 指标模板服务层实现
 * @Date: 2021/1/29 16:35
 */
@Service
public class ModelIndicatorServiceImpl extends BaseServiceImpl<IModelIndicatorMapper, ModelIndicatorEntity> implements IModelIndicatorService {

//    @Autowired
//    private IModelIndicatorRepository modelIndicatorRepository;

    @Autowired
    private IStandardValueService standardValueService;

//    @Autowired
//    private IIndicatorRepository indicatorRepository;

    @Autowired
    private IndicatorMapper indicatorMapper;

//    @Autowired
//    private IIndicatorTreeRepository indicatorTreeRepository;

    @Resource
    private IIndicatorTreeMapper indicatorTreeMapper;

//    public ModelIndicatorServiceImpl(IModelIndicatorRepository modelIndicatorRepository, IStandardValueService standardValueService) {
//        this.modelIndicatorRepository = modelIndicatorRepository;
//        this.standardValueService = standardValueService;
//    }

    @Override
    public Optional<ModelIndicatorEntity> findById(String id) {
//        return modelIndicatorRepository.findById(id);
        ModelIndicatorEntity entity = baseMapper.selectById(id);
        return Optional.ofNullable(entity);
    }

    @Override
    public List<ModelIndicatorEntity> find(ModelIndicatorEntity entity) {
//        Example<ModelIndicatorEntity> example = Example.of(entity);
        QueryWrapper<ModelIndicatorEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(entity.getPid()), "PID", entity.getPid())
                .eq(StringUtils.isNotBlank(entity.getTreeId()), "TREE_ID", entity.getTreeId())
                .eq(StringUtils.isNotBlank(entity.getName()), "NAME", entity.getName())
                .orderByAsc("CREATE_TIME");
//        queryWrapper.allEq()
//        return modelIndicatorRepository.findAll(example, Sort.by(Sort.Order.asc("createDate")));
        List<ModelIndicatorEntity> list = baseMapper.selectList(queryWrapper);
        return list;
    }

    @Override
    public void delete(String id) {
        Optional<ModelIndicatorEntity> data = findById(id);
        if (!data.isPresent()) throw new BusinessException(ServiceConstant.NO_SUCK_OBJECT);

        List<StandardValueEntity> standardValueEntityList = standardValueService.find(StandardValueEntity.builder().indicatorId(id).build());
        if (!CollectionUtils.isEmpty(standardValueEntityList))
            throw new BusinessException("[" + data.get().getName() + "]" + " 关联了准则不能删除");

        baseMapper.deleteById(id);
    }

    @Override
    public void saveAll(List<ModelIndicatorEntity> data) {
        baseMapper.saveBatch(data);
//        modelIndicatorRepository.saveAll(data);
    }

    @Override
    public List<TreeToolHelp> findTreeByTreeId(String treeId) {
//        List<IndicatorEntity> indicatorEntityList = indicatorRepository.findAll();
        List<IndicatorEntity> indicatorEntityList = indicatorMapper.selectList(new QueryWrapper<IndicatorEntity>().orderByDesc("createDate"));
//        List<IndicatorTreeEntity> all = indicatorTreeRepository.findAll();
        List<IndicatorTreeEntity> all = indicatorTreeMapper.selectList(new QueryWrapper<IndicatorTreeEntity>().orderByDesc("createDate"));
        List<TreeToolHelp> treeRootList = Lists.newArrayList();
        all.forEach(tree -> {
            TreeToolHelp toolHelp = new TreeToolHelp();
            toolHelp.setName(tree.getName());
            toolHelp.setNodeId(tree.getId());
            toolHelp.setSort(0);
            treeRootList.add(toolHelp);
        });
        List<TreeToolHelp> rootList = new ArrayList<>();
        List<TreeToolHelp> bodyList = new ArrayList<>();
        indicatorEntityList.forEach(f -> {
            if ("0".equals(f.getPid())) {
                TreeToolHelp toolHelp = new TreeToolHelp();
                toolHelp.setName(f.getName());
                toolHelp.setNodeId(f.getId());
                toolHelp.setSort(0);
                toolHelp.setPid(f.getTreeId());
                toolHelp.setSid(f.getSid());
                rootList.add(toolHelp);
            } else {
                TreeToolHelp toolHelp = new TreeToolHelp();
                toolHelp.setName(f.getName());
                toolHelp.setNodeId(f.getId());
                toolHelp.setSort(0);
                toolHelp.setPid(f.getPid());
                toolHelp.setSid(f.getSid());
                bodyList.add(toolHelp);
            }
        });

        //生成树形结构
        TreeToolUtils treeToolUtils = new TreeToolUtils(rootList, bodyList);
        List<TreeToolHelp> list = treeToolUtils.getTree();
        treeToolUtils = new TreeToolUtils(treeRootList, list);
        return treeToolUtils.getTree();
    }
}
