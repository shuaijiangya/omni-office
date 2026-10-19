package com.yw.module.indicator.service.impl;


import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.common.defines.constants.ServiceConstant;
import com.yw.common.utils.FileUtils;
import com.yw.common.utils.UUIDGenerator;
import com.yw.common.utils.ValidValue;
import com.yw.framework.common.base.api.R;
import com.yw.framework.common.base.support.BaseServiceImpl;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.framework.common.module.login.mapper.UserMapper;
import com.yw.module.indicator.domain.dto.IndicatorStandardGroupDTO;
import com.yw.module.indicator.domain.dto.StandardIndicatorDTO;
import com.yw.module.indicator.domain.dto.StandardInsertDTO;
import com.yw.module.indicator.domain.entity.IndicatorEntity;
import com.yw.module.indicator.domain.entity.IndicatorStandardEntity;
import com.yw.module.indicator.domain.entity.StandardLabelsEntity;
import com.yw.module.indicator.domain.vo.IndicatorStandardVO;
import com.yw.module.indicator.mapper.IIndicatorTreeMapper;
import com.yw.module.indicator.mapper.IndicatorMapper;
import com.yw.module.indicator.mapper.IndicatorStandardMapper;
import com.yw.module.indicator.mapper.StandardLabelsMapper;
import com.yw.module.indicator.service.IIndicatorService;
import com.yw.module.indicator.service.IStandardLabelsService;
import com.yw.module.indicator.service.IStandardValueService;
import com.yw.module.indicator.service.ITIndicatorStandardValuesService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Author: 刘灿莲
 * @Description: 等级实现类
 * @Date: 2021/01/26 11:37
 */
@Service
public class StandardLabelsServiceImpl extends BaseServiceImpl<StandardLabelsMapper, StandardLabelsEntity> implements IStandardLabelsService {

    @Autowired
    private StandardLabelsMapper standardLabelsMapper;
    @Autowired
    private IndicatorStandardMapper indicatorStandardMapper;
    protected final static Logger LOGGER = LoggerFactory.getLogger(StandardLabelsServiceImpl.class);
    //    private final IStandardLabelsRepository standardLabelsRepository;
    private final IStandardValueService standardValueService;
    //    private final ITIndicatorStandardRepository indicatorStandardRepository;
    private static final Pattern pattern = Pattern.compile("^等级[0-9]*[0-9]$");

    @Autowired
    @Lazy
    private IIndicatorService indicatorService;

    @Autowired
    private ITIndicatorStandardValuesService indicatorStandardValuesService;

    @Resource
    private com.yw.module.system.mapper.PGLogMapper PGLogMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private IIndicatorTreeMapper indicatorTreeMapper;
    @Resource
    private IndicatorMapper indicatorMapper;

    public StandardLabelsServiceImpl(IStandardValueService standardValueService, IndicatorStandardMapper standardMapper) {
        this.standardValueService = standardValueService;
        this.indicatorStandardMapper = standardMapper;
    }

    /**
     * @param data
     * @Author 刘灿莲
     * @Description 新增编辑单个pg等级
     * @Return com.yw.common.response.ApiResult
     * @Date 2021/01/26 11:28
     */
    @Override
    public String edit(StandardLabelsEntity data) {
        if (StringUtils.isNotBlank(data.getId()) && StringUtils.isBlank(data.getName())) {
            throw new BusinessException("等级名称不能为空");
        }
        //给标签设置默认名字
        if (StringUtils.isBlank(data.getId()) && StringUtils.isBlank(data.getName())) {
//            List<StandardLabelsEntity> standardLabelsEntityList = standardLabelsRepository.findAll();
            List<StandardLabelsEntity> standardLabelsEntityList = this.list();
            //在所有等级标准实体里筛选出最后一个加1作为name
            Optional<Long> standardLabelsNameNum = standardLabelsEntityList.stream().filter(f -> pattern.matcher(f.getName()).find())
                    .map(m -> Long.parseLong(m.getName().replace("等级", "")))
                    .max(Long::compareTo);
            long num = !standardLabelsNameNum.isPresent() ? 1 : (standardLabelsNameNum.get() + 1);
            data.setName("等级" + num);
        }
        String uuid = StringUtils.isBlank(data.getId()) ? UUIDGenerator.getUUID() : data.getId();

        data.setId(uuid);
        System.out.println("standardLabelsRepository" + data);
//        standardLabelsRepository.saveAndFlush(data);
        this.saveOrUpdate(data);
//        sortData();
        return uuid;
    }

    /**
     * @param id 主键
     * @Author 刘灿莲
     * @Description 通过主键查询单个pg等级
     * @Return java.util.Optional<com.yw.module.indicator.domain.entity.StandardLabelsEntity>
     * @Date 2021/01/26 11:28
     */
    @Override
    public Optional<StandardLabelsEntity> findById(String id) {

//        return standardLabelsRepository.findById(id);
        return Optional.ofNullable(this.getById(id));
    }

    /**
     * @param entity 等级对象
     * @Author 刘灿莲
     * @Description 通过实例查询pg等级
     * @Return java.util.List<com.yw.module.indicator.domain.entity.StandardLabelsEntity>
     * @Date 2021/01/26 11:29
     */
    @Override
    public List<StandardLabelsEntity> find(StandardLabelsEntity entity) {
        if (entity == null) {
            entity.setFlag(0);
        }
        Example<StandardLabelsEntity> example = Example.of(entity);
//        return standardLabelsRepository.findAll(example, Sort.by(Sort.Order.asc("sortno")));
        return this.list(new QueryWrapper<StandardLabelsEntity>().setEntity(entity).orderByAsc("sortno"));
    }

//    public List<StandardLabelsEntity> findByStandardId(String standardId) {
//        return standardLabelsRepository.findByStandardId(standardId);
//    }

    /**
     * @param entity
     * @Author 刘灿莲
     * @Description 校验参数
     * @Return void
     * @Date 2021/01/26 11:31
     */
    @Override
    public void validValue(StandardLabelsEntity entity) {
        StandardLabelsEntity query = new StandardLabelsEntity();
        String name = entity.getName();
        if (name != null && name.length() != 0) {
            query.setName(name);
            List<StandardLabelsEntity> data = find(query);

            try {
                ValidValue.valid(entity.getId(), "等级名称", data);
            } catch (BusinessException businessException) {
                throw businessException;
            } catch (Exception e) {
                throw new BusinessException(e.getMessage());
            }
        }
    }

    /**
     * @param id 主键
     * @Author 刘灿莲
     * @Description 通过主键删除标准
     * @Return void
     * @Date 2021/01/26 11:31
     */
    @Override
    @Transactional
    public void delete(String id) {
        Optional<StandardLabelsEntity> data = findById(id);
        if (!data.isPresent()) throw new BusinessException(ServiceConstant.NO_SUCK_OBJECT);
        StandardLabelsEntity entity = data.get();
        if (entity.getFlag() == 0) {
            return;
        }
        if (entity.getFlag() == 2) {
            String scriptPath = entity.getScriptPath();
            if (!StringUtils.isEmpty(scriptPath)) {
                // 删除文件
                FileUtils.deleteFileV2(scriptPath);
            }
        }
        indicatorStandardValuesService.deleteByLabelsId(id);
//        standardLabelsRepository.deleteById(id);
        this.removeById(id);
    }

    public void sortData() {
        StandardLabelsEntity query = new StandardLabelsEntity();
        List<StandardLabelsEntity> standardLabelsEntities = find(query);
        int num = 1;
        for (StandardLabelsEntity entity : standardLabelsEntities) {
            System.out.println(entity.getSortno());
        }
//        standardLabelsRepository.saveAndFlush(data);
    }

//    @Override
//    public List<StandardLabelsEntity> findAll() {
//        return standardLabelsRepository.findAll();
//    }

//    public void deleteByStandardId(String standardId) {
//        List<StandardLabelsEntity> standardLabelsEntityList = findByStandardId(standardId);
//        if (standardLabelsEntityList != null) {
//            standardLabelsEntityList.forEach(f -> {
//                delete(f.getId());
//            });
//        }
//    }

    /**
     * 新增编辑
     *
     * @param dto
     * @return
     */
    @Transactional
    @Override
    public R save(StandardIndicatorDTO dto) {
        String indicatorId = dto.getIndicatorId();
        if (StringUtils.isEmpty(indicatorId)) {
            LOGGER.error("Indicator or scheme ID cannot be null");
            throw new BusinessException("指标Id不能为null");
        }
        // 支持多个，新增前不再删除
        // standardMapper.deleteIndicatorId(dto.getIndicatorId());
        // 支持多个，做准则名称的重复性校验
        if (StringUtils.isNotEmpty(dto.getIndicatorStanName())) {
//            List<IndicatorStandardEntity> indicatorStandardEntityList = indicatorStandardRepository.findAll(Example.
//                    of(IndicatorStandardEntity.builder().indicatorStanName(dto.getIndicatorStanName()).indicatorId(dto.getIndicatorId()).sid(dto.getSid()).build()));
            List<IndicatorStandardEntity> indicatorStandardEntityList = indicatorStandardMapper.selectList(
                    new QueryWrapper<IndicatorStandardEntity>().setEntity(IndicatorStandardEntity.builder()
                            .indicatorStanName(dto.getIndicatorStanName()).indicatorId(dto.getIndicatorId())
                            .sid(dto.getSid()).build()));
            if (!CollectionUtils.isEmpty(indicatorStandardEntityList)) {
                List<String> collect = indicatorStandardEntityList.stream().map(IndicatorStandardEntity::getId).collect(Collectors.toList());
                for (StandardInsertDTO dtoDto : dto.getDtos()) {
                    if (!collect.contains(dtoDto.getIndicatorStanId())) {
                        return R.data("此指标准则已被导入,如还需导入请先删除：" + dto.getIndicatorStanName());
                    }
                }
            }
        }
        String sid = dto.getSid();
        if (StringUtils.isEmpty(sid)) {
            Optional<IndicatorEntity> optional = indicatorService.findById(indicatorId);
            if (!optional.isPresent()) {
                throw new BusinessException("指标不存在!");
            }
            sid = optional.get().getSid();
        }
        //获取存储的pg指标信息
        List<StandardInsertDTO> standardInsertDTOS = dto.getDtos();
        for (StandardInsertDTO standardInsertDTO : standardInsertDTOS) {
            if (StringUtils.isEmpty(standardInsertDTO.getMax()) || StringUtils.isEmpty(standardInsertDTO.getMin())) {
                continue;
            }
            if (StringUtils.isNotEmpty(standardInsertDTO.getMax()) && standardInsertDTO.getMax().equals("+")) {
                continue;
            }
            if (StringUtils.isNotEmpty(standardInsertDTO.getMin()) && standardInsertDTO.getMin().equals("-")) {
                continue;
            }
            try {
                double max = Double.parseDouble(standardInsertDTO.getMax());
                double min = Double.parseDouble(standardInsertDTO.getMin());
                if (min > max) {
                    throw new BusinessException("指标准则最小值不能大于最大值");
                }
                for (StandardInsertDTO standardInsertDTO2 : standardInsertDTOS) {
                    if (StringUtils.isEmpty(standardInsertDTO2.getMax()) || StringUtils.isEmpty(standardInsertDTO2.getMin())) {
                        continue;
                    }
                    if (StringUtils.isNotEmpty(standardInsertDTO2.getMax()) && standardInsertDTO2.getMax().equals("+")) {
                        continue;
                    }
                    if (StringUtils.isNotEmpty(standardInsertDTO2.getMin()) && standardInsertDTO2.getMin().equals("-")) {
                        continue;
                    }
                    double max2 = Double.parseDouble(standardInsertDTO2.getMax());
                    double min2 = Double.parseDouble(standardInsertDTO2.getMin());
                    if (!standardInsertDTO.getName().equals(standardInsertDTO2.getName()) && !(Double.compare(max2, min) <= 0 || Double.compare(max, min2) <= 0)) {
//                        throw new BusinessException("指标准则名称:"+standardInsertDTO.getName()+"-"+standardInsertDTO2.getName()+" 最大最小值区间不能重叠");
                    }
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
                throw new BusinessException("指标准则最大最小值必须为数值");
            }
        }
        if (standardInsertDTOS != null && !standardInsertDTOS.isEmpty()) {
            AtomicInteger sortNo = new AtomicInteger(1);
            List<StandardInsertDTO> standardInsertDTOList = standardInsertDTOS.stream().sorted(new Comparator<StandardInsertDTO>() {
                @Override
                public int compare(StandardInsertDTO o1, StandardInsertDTO o2) {
                    if ((StringUtils.isEmpty(o1.getMax()) || "+".equals(o1.getMax())) && (StringUtils.isEmpty(o1.getMin()) || "-".equals(o1.getMin()))) {
                        return 1;
                    }
                    if ((StringUtils.isEmpty(o2.getMax()) || "+".equals(o2.getMax())) && (StringUtils.isEmpty(o2.getMin()) || "-".equals(o2.getMin()))) {
                        return -1;
                    }
                    if (StringUtils.isEmpty(o1.getMax()) || "+".equals(o1.getMax())) {
                        return 1;
                    }
                    if (StringUtils.isEmpty(o2.getMax()) || "+".equals(o2.getMax())) {
                        return -1;
                    }
                    double max = Double.parseDouble(o1.getMax());
                    double max1 = Double.parseDouble(o2.getMax());
                    if (max > max1) {
                        return 1;
                    } else if (max == max1) {
                        return 0;
                    } else {
                        return -1;
                    }
                }
            }).collect(Collectors.toList());
            for (StandardInsertDTO standardInsertDTO : standardInsertDTOList) {
                int flag = standardInsertDTO.getFlag();
                if (flag != 2) {
                    if (StringUtils.isEmpty(standardInsertDTO.getMax()) || "+".equals(standardInsertDTO.getMax())) {
                        standardInsertDTO.setMax("+");
                    }
                    if (StringUtils.isEmpty(standardInsertDTO.getMin()) || "-".equals(standardInsertDTO.getMin())) {
                        standardInsertDTO.setMin("-");
                    }
                    standardInsertDTO.setSortno(sortNo.get());
                    sortNo.incrementAndGet();
                    //做存储操作
                    String standardId = standardInsertDTO.getLabelId();
                    standardInsertDTO.setIndicatorStanName(dto.getIndicatorStanName());
                    if (flag != 0) {
                        if (StringUtils.isEmpty(standardId)) {
                            standardId = UUID.randomUUID().toString().replace("-", "");
                        }
                        StandardLabelsEntity entity = StandardLabelsEntity.valueOf(standardId, standardInsertDTO);
                        //保存pg准则等级实体信息
//                        standardLabelsRepository.saveAndFlush(entity);
                        this.saveOrUpdate(entity);
                    } else {
                        String name = standardInsertDTO.getName();
                        //根据名称获取pg准则id
//                        Optional<StandardLabelsEntity> one = standardLabelsRepository.findOne(Example.of(StandardLabelsEntity.builder().name(name).build()));
                        Optional<StandardLabelsEntity> one = Optional.ofNullable(this.getOne(new QueryWrapper<StandardLabelsEntity>().setEntity(StandardLabelsEntity.builder().name(name).build())));
                        if (!one.isPresent()) {
                            LOGGER.error("The storage evaluation criteria type is abnormal. Please check the corresponding type information");
                            throw new BusinessException("存储pg准则类型异常,请检查对应类型信息");
                        }
                        standardId = one.get().getId();
                        if (StringUtils.isEmpty(standardInsertDTO.getFunc())) {
                            standardInsertDTO.setFunc(one.get().getValue());
                        }
                    }

                    IndicatorStandardEntity indicatorStandardEntity = IndicatorStandardEntity.valueOf(indicatorId, standardId, sid, standardInsertDTO);
                    indicatorStandardEntity.setOpenFlag(dto.getOpenFlag());
                    //保存指标标准信息
//                    indicatorStandardRepository.saveAndFlush(indicatorStandardEntity);
                    if (indicatorStandardMapper.selectCount(new QueryWrapper<IndicatorStandardEntity>().eq("ID", indicatorStandardEntity.getId())) > 0) {
                        indicatorStandardMapper.updateById(indicatorStandardEntity);
                    } else {
                        indicatorStandardMapper.insert(indicatorStandardEntity);
                    }
                } else {
                    //创建pg准则：脚本方式
                    String standardId = standardInsertDTO.getLabelId();
                    File classPath = null;
                    try {
                        classPath = new File(URLDecoder.decode(ResourceUtils.getURL("classpath:").getFile(),
                                "utf-8")).getParentFile();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    String classPathStr = classPath.getPath();
                    if (classPathStr.contains("file:")) {
                        classPath = classPath.getParentFile().getParentFile();
                        classPathStr = classPath.getPath().replace("file:", "");
                    }
                    String scriptPath = standardInsertDTO.getScriptPath();
                    classPathStr = classPathStr + "\\" + scriptPath;
                    if (FileUtils.exist(classPathStr)) {
                        if (StringUtils.isEmpty(standardId)) {
                            standardId = UUID.randomUUID().toString().replace("-", "");
                        }
                        StandardLabelsEntity entity = StandardLabelsEntity.valueOf(standardId, standardInsertDTO);
//                        standardLabelsRepository.saveAndFlush(entity);
                        this.saveOrUpdate(entity);
                    } else {
                        throw new BusinessException("脚本文件不存在!");
                    }
                    IndicatorStandardEntity indicatorStandardEntity = IndicatorStandardEntity.valueOf(indicatorId, standardId, sid, standardInsertDTO);
                    indicatorStandardEntity.setIndicatorStanName(dto.getIndicatorStanName());
                    // set is open flag
                    indicatorStandardEntity.setOpenFlag(dto.getOpenFlag());
                    //保存指标标准信息
//                    indicatorStandardRepository.saveAndFlush(indicatorStandardEntity);
                    if (indicatorStandardMapper.selectById(indicatorStandardEntity.getId()) != null) {
                        indicatorStandardMapper.updateById(indicatorStandardEntity);
                    } else {
                        indicatorStandardMapper.insert(indicatorStandardEntity);
                    }
                }
                LOGGER.info("save success");
            }
        }
//        try {
//            //记录操作日志
//            String userId = UserUtil.getUserId();
//            //查询指标树类型
//            IndicatorEntity indicatorEntity = indicatorMapper.selectById(dto.getIndicatorId());
//            IndicatorTreeEntity indicatorTreeEntity = indicatorTreeMapper.selectById(indicatorEntity.getTreeId());
//            LogIndicatorSystemDto logEntity =
//                    LogIndicatorSystemDto.getStandardIndicatorDTO(userId, userMapper.getUserNameById(userId), dto, indicatorTreeEntity.getIndicatorTreeType());
//            PGLogMapper.insert(logEntity);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return R.success();
    }

    /**
     * @return void
     * @Author admin
     * @Description pg准则脚本下载
     * @Date 17:56 2021/12/9
     * @Param [id, response]
     **/
    @Override
    public void download(String id, HttpServletResponse response) {
        Optional<StandardLabelsEntity> data = findById(id);
        if (!data.isPresent()) throw new BusinessException(ServiceConstant.NO_SUCK_OBJECT);
        StandardLabelsEntity standardLabelsEntity = data.get();
        try {
            FileUtils.download(standardLabelsEntity.getScriptPath(), response);
        } catch (Exception e) {
            throw new BusinessException("脚本下载失败!");
        }
    }

    /**
     * 根据id查询存储的pg标准信息
     *
     * @param indicatorId
     * @return
     */
    @Override
    public List<IndicatorStandardVO> findByIndicatorId(String indicatorId) {
        List<IndicatorStandardVO> vos = Lists.newArrayList();

        //根据指标id查询准则项实体
        List<IndicatorStandardEntity> list = indicatorStandardMapper.findByIndicatorId(indicatorId);
        //根据labelId在map中取出对应的值
        if (list != null && !list.isEmpty()) {
            list.stream().forEach(s -> {
                String labelId = s.getLabelId();
//                Optional<StandardLabelsEntity> labelsEntityOptional = standardLabelsRepository.findById(labelId);
                Optional<StandardLabelsEntity> labelsEntityOptional = Optional.ofNullable(this.getById(labelId));
                if (!labelsEntityOptional.isPresent()) {
                    return;
                }
                StandardLabelsEntity labelsEntity = labelsEntityOptional.get();
                String lableName = labelsEntity.getName();
                Integer flag = labelsEntity.getFlag();
//                if (flag == null) {
//                    flag = 1;
//                }
                String value = labelsEntity.getValue();
                //根据lableId获取名称
                IndicatorStandardVO vo = IndicatorStandardVO.valueOf(value, flag, s.getId(), lableName, labelId, indicatorId,
                        s.getMax(), s.getMin(), s.getMaxSymbol(), s.getMinSymbol(), s.getScriptPath(), s.getStatus(), s.getFunc(), s.getIndicatorStanName());
                vos.add(vo);
            });
        }

        return vos;
    }

    /**
     * 根据指标id和准则类型获取pg准则信息
     *
     * @param indicatorId
     * @return
     */
    @Override
    public Map<String, List<IndicatorStandardVO>> findByIndicatorIdAndFlag(String indicatorId, Integer flag) {
        List<IndicatorStandardVO> vos = Lists.newArrayList();

        //根据指标id查询准则项实体
        List<IndicatorStandardEntity> list = indicatorStandardMapper.findByIndicatorIdAndFlag(indicatorId, flag);
        //根据labelId在map中取出对应的值
        if (list != null && !list.isEmpty()) {
            list.stream().forEach(s -> {
                String labelId = s.getLabelId();
//                Optional<StandardLabelsEntity> labelsEntityOptional = standardLabelsRepository.findById(labelId);
                Optional<StandardLabelsEntity> labelsEntityOptional = Optional.ofNullable(this.getById(labelId));
                if (!labelsEntityOptional.isPresent()) {
                    return;
                }
                StandardLabelsEntity labelsEntity = labelsEntityOptional.get();
                String lableName = labelsEntity.getName();
                Integer flagResult = labelsEntity.getFlag();
//                if (flag == null) {
//                    flag = 1;
//                }
                String value = labelsEntity.getValue();
                //根据lableId获取名称
                IndicatorStandardVO vo = IndicatorStandardVO.valueOf(value, flagResult, s.getId(), lableName, labelId, indicatorId,
                        s.getMax(), s.getMin(), s.getMaxSymbol(), s.getMinSymbol(), s.getScriptPath(), s.getStatus(), s.getFunc(),
                        null == s.getIndicatorStanName() ? "" : s.getIndicatorStanName());
                vo.setOpenFlag(s.getOpenFlag());
                vos.add(vo);
            });
        }

        Map<String, List<IndicatorStandardVO>> voListMap = vos.stream().collect(Collectors.groupingBy(IndicatorStandardVO::getIndicatorStanName));
        int i = 1;
        Map<String, List<IndicatorStandardVO>> resultMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(voListMap)) {
            for (Map.Entry<String, List<IndicatorStandardVO>> entry : voListMap.entrySet()) {
                if (StringUtils.isBlank(entry.getKey())) {
                    while (voListMap.containsKey("指标准则" + i)) {
                        i++;
                    }
//                    String standardName = UUIDGenerator.getUUID();
                    resultMap.put("指标准则" + i++, entry.getValue());
                } else {
                    resultMap.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return resultMap;
    }

    /**
     * 根据指标树id查询存储的pg标准信息
     *
     * @param treeId
     * @return
     */
    @Override
    public IndicatorStandardGroupDTO findByTreeId(String treeId) {
        IndicatorStandardGroupDTO dto = new IndicatorStandardGroupDTO();
        List<IndicatorStandardVO> vos = Lists.newArrayList();
        //查询所有pg等级实体
//        List<StandardLabelsEntity> all = standardLabelsRepository.findAll();
        List<StandardLabelsEntity> all = this.list();
        Map<String, String> labelsMap = Maps.newHashMap();
        Map<String, Integer> labelsFlag = Maps.newHashMap();
        Map<String, String> valueMap = Maps.newHashMap();
        //把所有pg等级实体的name,flag,value根据id存入各自map集合中
        all.stream().forEach(labels -> {
            String labelId = labels.getId();
            labelsMap.put(labelId, labels.getName());
            labelsFlag.put(labelId, labels.getFlag());
            String value = labels.getValue();
            if (!StringUtils.isEmpty(value)) {
                valueMap.put(labelId, value);
            }
        });
        List<IndicatorEntity> indicatorEntities = indicatorService.find(IndicatorEntity.builder().treeId(treeId).build());
        for (IndicatorEntity indicatorEntity : indicatorEntities) {
            String indicatorId = indicatorEntity.getId();
            List<IndicatorStandardEntity> list = indicatorStandardMapper.findByIndicatorId(indicatorId);
            if (list != null && !list.isEmpty()) {
                list.stream().forEach(s -> {
                    String labelId = s.getLabelId();
                    String lableName = labelsMap.get(labelId);
                    Integer flag = labelsFlag.get(labelId);
                    if (flag == null) {
                        flag = 1;
                    }
                    String value = valueMap.get(labelId);
                    //根据lableId获取名称
                    IndicatorStandardVO vo = IndicatorStandardVO.valueOf(value, flag, s.getId(), lableName, labelId,
                            indicatorId, s.getMax(), s.getMin(), s.getMaxSymbol(), s.getMinSymbol(), s.getScriptPath(), s.getStatus(), s.getFunc(), s.getIndicatorStanName());
                    vos.add(vo);
                });
            }
        }
        dto.setStandardMap(vos.stream().collect(Collectors.groupingBy(IndicatorStandardVO::getIndicatorId)));
        return dto;
    }


    /**
     * 通过方案id获取当前方案下所有的pg准则等级
     *
     * @param sid
     * @return
     */
    @Override
    public List<StandardLabelsEntity> evaluationCriteriaLevel(String sid) {
//        Example<IndicatorStandardEntity> of = Example.of(IndicatorStandardEntity.builder().sid(sid).build());
        //根据pg任务id查出所有单个指标标准
//        List<IndicatorStandardEntity> all = indicatorStandardRepository.findAll(of);
        List<IndicatorStandardEntity> all = indicatorStandardMapper.selectList(new QueryWrapper<IndicatorStandardEntity>().eq("SID", sid));
        //根据lableId去重
        List<IndicatorStandardEntity> indicatorStanList = all.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(
                        () -> new TreeSet<>(Comparator.comparing(IndicatorStandardEntity::getLabelId))),
                ArrayList::new));
        //获取lableId
        Collection<String> collections = indicatorStanList.stream().map(IndicatorStandardEntity::getLabelId).collect(Collectors.toList());
//        List<StandardLabelsEntity> standardList = standardLabelsRepository.findAll();
        List<StandardLabelsEntity> standardList = standardLabelsMapper.selectList(new QueryWrapper<>());
        //根据lableId过滤pg等级实体
        List<StandardLabelsEntity> resultList = standardList.stream().filter(s -> collections.contains(s.getId())).collect(Collectors.toList());
        return resultList;
    }

    /**
     * 保存所有指标信息
     *
     * @param dtos
     * @return
     */
    @Override
    public Map<String, List<IndicatorStandardVO>> saveAll(List<StandardIndicatorDTO> dtos) {
        Map<String, List<IndicatorStandardVO>> map = Maps.newHashMap();
        for (StandardIndicatorDTO dto : dtos) {
            String indicatorId = dto.getIndicatorId();
            String sid = dto.getSid();
            if (StringUtils.isEmpty(indicatorId) || StringUtils.isEmpty(sid)) {
                LOGGER.error("Indicator or scheme ID cannot be null");
                throw new BusinessException("指标或方案Id不能为null");
            }
            //获取存储的pg指标信息
            List<StandardInsertDTO> standardInsertDTOS = dto.getDtos();
            if (standardInsertDTOS != null && !standardInsertDTOS.isEmpty()) {
                standardInsertDTOS.stream().forEach(s -> {
                    int flag = s.getFlag();
                    //做存储操作
                    String standardId = s.getLabelId();
                    if (flag != 0) {
                        if (StringUtils.isEmpty(standardId)) {
                            standardId = UUID.randomUUID().toString().replace("-", "");
                        }
                        StandardLabelsEntity entity = StandardLabelsEntity.valueOf(standardId, s);
                        //保存pg准则等级实体信息
//                        standardLabelsRepository.saveAndFlush(entity);
                        this.saveOrUpdate(entity);
                    }
                    IndicatorStandardEntity indicatorStandardEntity = IndicatorStandardEntity.valueOf(indicatorId, standardId, sid, s);
                    //保存指标标准信息
//                    indicatorStandardRepository.saveAndFlush(indicatorStandardEntity);
                    if (indicatorStandardMapper.selectCount(new QueryWrapper<IndicatorStandardEntity>().setEntity(indicatorStandardEntity)) > 0) {
                        indicatorStandardMapper.updateById(indicatorStandardEntity);
                    } else {
                        indicatorStandardMapper.insert(indicatorStandardEntity);
                    }
                });
            }
            List<IndicatorStandardVO> data = findByIndicatorId(indicatorId);
            map.put(indicatorId, data);
        }
        return map;
    }


    @Override
    public Map<String, List<IndicatorStandardVO>> findBySid(String sid) {
        List<IndicatorStandardEntity> list = indicatorStandardMapper.findBySid(sid);
        if (CollUtil.isEmpty(list)) {
            return Maps.newHashMap();
        }
        List<String> labelsIds = Lists.newArrayList();
        for (IndicatorStandardEntity entity : list) {
            labelsIds.add(entity.getLabelId());
        }
//        List<StandardLabelsEntity> labelsEntities = standardLabelsRepository.findAllById(labelsIds);
        List<StandardLabelsEntity> labelsEntities = this.listByIds(labelsIds);
        Map<String, StandardLabelsEntity> labelsEntityMap = Maps.newHashMap();
        for (StandardLabelsEntity entity : labelsEntities) {
            labelsEntityMap.put(entity.getId(), entity);
        }
        Map<String, List<IndicatorStandardVO>> vos = Maps.newHashMap();

        //根据指标id查询准则项实体
        //根据labelId在map中取出对应的值
        list.forEach(s -> {
            String indicatorId = s.getIndicatorId();
            String labelId = s.getLabelId();
            StandardLabelsEntity labelsEntity = labelsEntityMap.get(labelId);
            if (Objects.isNull(labelsEntity)) {
                return;
            }
            //根据lableId获取名称
            IndicatorStandardVO vo = IndicatorStandardVO.valueOf(labelsEntity.getValue(), labelsEntity.getFlag(), s.getId(), labelsEntity.getName(), labelId, indicatorId,
                    s.getMax(), s.getMin(), s.getMaxSymbol(), s.getMinSymbol(), s.getScriptPath(), s.getStatus(), s.getFunc(), s.getIndicatorStanName());
            List<IndicatorStandardVO> indicatorStandardVOS = vos.get(indicatorId);
            if (CollUtil.isEmpty(indicatorStandardVOS)) {
                indicatorStandardVOS = Lists.newArrayList();
                vos.put(indicatorId, indicatorStandardVOS);
            }
            indicatorStandardVOS.add(vo);
        });
        return vos;
    }
}
