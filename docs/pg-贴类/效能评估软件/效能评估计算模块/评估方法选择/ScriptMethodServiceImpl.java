package com.yw.module.calculation.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yw.common.utils.FileUtils;
import com.yw.framework.common.base.api.R;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.contributionRate.domain.vo.ScriptMethodInfoVO;
import com.yw.module.calculation.contributionRate.domain.vo.ScriptMethodVO;
import com.yw.module.calculation.domain.dto.ScriptMethodDTO;
import com.yw.module.calculation.domain.entity.ScriptMethodEntity;
import com.yw.module.calculation.domain.enums.EvaluationType;
import com.yw.module.calculation.indicatorCalculation.strategy.weight.impl.PyScriptALG;
import com.yw.module.calculation.mapper.ScriptMethodMapper;
import com.yw.module.calculation.service.ScriptMethodService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 算法脚本实现层
 *
 * @author lj
 * @description 算法脚本实现层
 * @date 2021-10-25 15:58
 */
@Service
public class ScriptMethodServiceImpl extends ServiceImpl<ScriptMethodMapper, ScriptMethodEntity> implements ScriptMethodService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(ScriptMethodServiceImpl.class);
    /**
     * 算法配置仓储
     */
    @Autowired
    private ScriptMethodMapper scriptMethodMapper;

    public boolean isEmpty(Integer id) {
        if (id != null && id > 0) {
            return true;
        }
        return false;
    }

    @Value("${pg-general.module.python-file.path}")
    private String path;

    /**
     * 保存操作步骤
     * 1.将脚本信息进行文件存储
     * 2.将脚本信息保存在数据库中
     *
     * @param dto 请求对象
     */
    @Transactional
    @Override
    public String save(ScriptMethodDTO dto) {
        //校验文件是否可执行，否则删除文件
        String execScriptPath = dto.getScriptInfo();
        if (FileUtils.exist(execScriptPath)) {
            if (BooleanUtil.isFalse(checkExecScript(execScriptPath, dto.getScriptInput()))) {
                FileUtils.delete(new File(dto.getScriptInfo()));
                throw new BusinessException("脚本算法不可执行");
            }
        }
        ScriptMethodEntity entity = ScriptMethodEntity.valueOf(dto);
        //根据名称查看是否存在相同命名的脚本算法
        String scriptName = entity.getScriptName();
        if (StringUtils.isEmpty(scriptName)) {
            LOGGER.error("Script algorithm name cannot be null");
            throw new BusinessException("脚本算法名称不能为空值");
        }
        String entityId = entity.getId();
        EvaluationType byName = EvaluationType.getTypeByChName(scriptName);
        int count = scriptMethodMapper.queryByName(scriptName, entityId);
        if (byName != EvaluationType.NONE || count > 0) {
            LOGGER.error("Script algorithm name already exists, please re-enter");
            throw new BusinessException("脚本算法名称已存在,请重新输入");
        }
//        entity.setScriptPath(scriptName + ".py");
        if (!StringUtils.isEmpty(entityId)) {
            scriptMethodMapper.update(entity);
        } else {
            entityId = UUID.randomUUID().toString().replace("-", "");
            entity.setId(entityId);
            scriptMethodMapper.save(entity);
        }
        return entityId;
    }


    /**
     * 校验执行脚本
     *
     * @param path        路径
     * @param scriptInput 算法脚本
     * @return boolean
     * @Author admin
     * @Description 校验执行脚本
     * @Date 11:23 2021/11/30
     * @Param [entity]
     **/
    public boolean checkExecScript(String path, String scriptInput) {
        String ret = PyScriptALG.execScript(path, "[]", scriptInput);
        return "" == ret ? false : true;
    }


    /**
     * 脚本算法删除操作
     * 1.根据id查询该纪录是否可被删除（0 不可被删除 1 可被删除）
     * 2.如果是否被修改为1则不可以被删除需要将信息响应给前端
     * 3.如果可以被修改则进行删除操作
     * 4.删除文件
     *
     * @param id 删除id
     * @return
     */
    @Transactional
    @Override
    public R delete(String id) {
        ScriptMethodEntity entity = scriptMethodMapper.queryById(id);
        String oldId = entity.getId();
        if (StringUtils.isEmpty(oldId)) {
            LOGGER.error("The current operation is not feasible. There is no such information ID {}", oldId);
            throw new BusinessException("当前操作不可行, 暂无该信息ID:" + oldId);
        }
        int whetherEdit = entity.getWhetherEdit();
        if (!isEmpty(whetherEdit)) {
            LOGGER.error("This script algorithm cannot be deleted. The algorithm name is {}", entity.getScriptName());
            throw new BusinessException("此脚本算法不允许被删除,算法名称为:" + entity.getScriptName());
        }
        scriptMethodMapper.delete(id);
        String scriptPath = entity.getScriptPath();
        if (StringUtils.isNotEmpty(scriptPath)) {
            File file = new File(path + "/" + scriptPath);
            file.delete();
        }
        return R.success();
    }

    /**
     * 根据id查询脚本算法基础信息
     *
     * @param id 算法脚本ID
     * @return
     */
    @Override
    public ScriptMethodEntity findById(String id) {
        EvaluationType byName = EvaluationType.getTypeByName(id);
        ScriptMethodEntity entity = null;
        if (byName != EvaluationType.NONE) {
            entity = ScriptMethodEntity.valueOf(id, byName.getChName(), byName.getCalculationScript());
        } else {
            entity = scriptMethodMapper.queryById(id);
        }
        if (entity != null) {
            String scriptPath = entity.getScriptPath();
            if (!StringUtils.isEmpty(scriptPath)) {
                String scriptInfo = fileDownLoad(scriptPath);
                entity.setScriptInfo(scriptInfo);
            }
        }
        return entity;
    }

    /**
     * 脚本信息可视化
     * 1.从数据库中获取所有脚本算法信息
     * 2.从枚举类型中获取八种内置脚本算法信息
     * 3.响应给前端
     *
     * @return
     */
    @Override
    public List<ScriptMethodVO> findList() {
        List<ScriptMethodVO> vo = scriptMethodMapper.findList();
        //去除逼近理想法METHOD_APPROACH_IDEAL
        Arrays.stream(EvaluationType.values()).filter(m -> m != EvaluationType.NONE && m != EvaluationType.PRINCIPAL
                && m != EvaluationType.CLUSTER && m != EvaluationType.ANP_MEANS
                && m != EvaluationType.EXPERT_SCORE && m != EvaluationType.UPLOAD

                //&& m != EvaluationType.WEIGHT_NORM
                && m != EvaluationType.DICTIONARY_ORDER_METHOD
                //&& m != EvaluationType.AHP_MEANS
                && m != EvaluationType.ADC_MEANS
                && m != EvaluationType.SEA_MEANS && m != EvaluationType.VTF_MEANS
                && m != EvaluationType.IDEAL_POINT && m != EvaluationType.GEA_METHOD
                && m != EvaluationType.BP_METHOD

        ).forEach(s -> {
            vo.add(ScriptMethodVO.valueOf(s.getName(), s.getChName(), null, null, null));
        });
//        vo.add(ScriptMethodVO.valueOf(EvaluationType.AHP_MEANS.getName(),EvaluationType.AHP_MEANS.getChName(),EvaluationType.AHP_MEANS.getDefinitionDesc()
//        ,EvaluationType.AHP_MEANS.getContentDesc(),EvaluationType.AHP_MEANS.getScopeDesc()));
//        vo.add(ScriptMethodVO.valueOf(EvaluationType.WEIGHT_NORM.getName(),EvaluationType.WEIGHT_NORM.getChName(),EvaluationType.WEIGHT_NORM.getDefinitionDesc(),
//                EvaluationType.WEIGHT_NORM.getContentDesc(),EvaluationType.WEIGHT_NORM.getScopeDesc()));
//        vo.add(ScriptMethodVO.valueOf(EvaluationType.PRINCIPAL.getName(),EvaluationType.PRINCIPAL.getChName(),EvaluationType.PRINCIPAL.getDefinitionDesc(),
//                EvaluationType.PRINCIPAL.getContentDesc(), EvaluationType.PRINCIPAL.getScopeDesc()));
//        vo.add(ScriptMethodVO.valueOf(EvaluationType.ADC_MEANS.getName(),EvaluationType.ADC_MEANS.getChName(),EvaluationType.ADC_MEANS.getDefinitionDesc(),
//                EvaluationType.ADC_MEANS.getContentDesc(),EvaluationType.ADC_MEANS.getScopeDesc()));
//        vo.add(ScriptMethodVO.valueOf(EvaluationType.IDEAL_POINT.getName(),EvaluationType.IDEAL_POINT.getChName(),EvaluationType.IDEAL_POINT.getDefinitionDesc(),
//                EvaluationType.IDEAL_POINT.getContentDesc(),EvaluationType.IDEAL_POINT.getScopeDesc()));
        return vo;
    }

    /**
     * 根据综合pg方法名称查询信息
     *
     * @param scriptName 脚本名称
     */
    @Override
    public List<ScriptMethodVO> findByName(String scriptName) {

        if (StringUtils.isEmpty(scriptName)) {
            List<ScriptMethodVO> methodMapperList = scriptMethodMapper.findList();
            methodMapperList.add(ScriptMethodVO.valueOf(EvaluationType.AHP_MEANS.getName(), EvaluationType.AHP_MEANS.getChName(), EvaluationType.AHP_MEANS.getDefinitionDesc()
                    , EvaluationType.AHP_MEANS.getContentDesc(), EvaluationType.AHP_MEANS.getScopeDesc()));
            methodMapperList.add(ScriptMethodVO.valueOf(EvaluationType.WEIGHT_NORM.getName(), EvaluationType.WEIGHT_NORM.getChName(), EvaluationType.WEIGHT_NORM.getDefinitionDesc(),
                    EvaluationType.WEIGHT_NORM.getContentDesc(), EvaluationType.WEIGHT_NORM.getScopeDesc()));
            methodMapperList.add(ScriptMethodVO.valueOf(EvaluationType.PRINCIPAL.getName(), EvaluationType.PRINCIPAL.getChName(), EvaluationType.PRINCIPAL.getDefinitionDesc(),
                    EvaluationType.PRINCIPAL.getContentDesc(), EvaluationType.PRINCIPAL.getScopeDesc()));
            methodMapperList.add(ScriptMethodVO.valueOf(EvaluationType.ADC_MEANS.getName(), EvaluationType.ADC_MEANS.getChName(), EvaluationType.ADC_MEANS.getDefinitionDesc(),
                    EvaluationType.ADC_MEANS.getContentDesc(), EvaluationType.ADC_MEANS.getScopeDesc()));
            methodMapperList.add(ScriptMethodVO.valueOf(EvaluationType.IDEAL_POINT.getName(), EvaluationType.IDEAL_POINT.getChName(), EvaluationType.IDEAL_POINT.getDefinitionDesc(),
                    EvaluationType.IDEAL_POINT.getContentDesc(), EvaluationType.IDEAL_POINT.getScopeDesc()));
            return methodMapperList;
        }
        List<ScriptMethodVO> vos = Lists.newArrayList();

        EvaluationType[] values = new EvaluationType[]{EvaluationType.AHP_MEANS, EvaluationType.WEIGHT_NORM, EvaluationType.PRINCIPAL, EvaluationType.ADC_MEANS, EvaluationType.IDEAL_POINT};
        for (EvaluationType type : values) {
            if (type.getChName().contains(scriptName)) {
                vos.add(ScriptMethodVO.valueOf(type.getName(), type.getChName(), type.getDefinitionDesc(), type.getContentDesc(), type.getScopeDesc()));
            }
        }
        vos.addAll(scriptMethodMapper.findByName(scriptName));
        return vos;
    }

    /**
     * 综合pg算法脚本信息展示（只响应id 与 名称）
     */
    @Override
    public List<ScriptMethodInfoVO> scriptList() {
        List<ScriptMethodInfoVO> vo = scriptMethodMapper.scriptList();
        Arrays.stream(EvaluationType.values()).filter(m -> m != EvaluationType.NONE).forEach(s -> {
            vo.add(ScriptMethodInfoVO.valueOf(s.getName(), s.getChName()));
        });
        return vo;
    }

    /**
     * 下载脚本模板文件
     */
    @Override
    public void downloadPyScriptTemplate(HttpServletResponse response) {
        // 1. 设置响应头
        response.setContentType("application/octet-stream"); // 二进制流下载
        response.setHeader("Content-Disposition", "attachment; filename=\"avg.py\"");
        response.setCharacterEncoding("UTF-8");

        // 2. 加载 classpath 下的模板文件 (对应 src/main/resources/template/avg.py)
        ClassPathResource resource = new ClassPathResource("template/avg.py");
        if (!resource.exists()) {
            throw new BusinessException("模板文件不存在");
        }

        // 3. 读取文件并写入响应输出流
        try (InputStream inputStream = resource.getInputStream()) {
            OutputStream outputStream = response.getOutputStream();
            StreamUtils.copy(inputStream, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            log.error("下载模板文件失败", e);
            throw new BusinessException("下载模板文件失败");
        }
    }

    @Override
    public void downloadVtfTemplate(HttpServletResponse response) {
        // 1. 设置响应头
        response.setContentType("application/octet-stream"); // 二进制流下载
        response.setHeader("Content-Disposition", "attachment; filename=VTF_template.py");
        response.setCharacterEncoding("UTF-8");

        // 2. 加载 classpath 下的模板文件 (对应 src/main/resources/template/VTF_template.py)
        ClassPathResource resource = new ClassPathResource("template/VTF_template.py");
        if (!resource.exists()) {
            throw new BusinessException("模板文件不存在");
        }

        // 3. 读取文件并写入响应输出流
        try (InputStream inputStream = resource.getInputStream()) {
            OutputStream outputStream = response.getOutputStream();
            StreamUtils.copy(inputStream, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            log.error("下载VTF模板文件失败", e);
            throw new BusinessException("下载VTF模板文件失败");
        }
    }

    /**
     * 算法脚本信息展示
     * 1.根据算法脚本名称去磁盘中获取文件
     * 2.根据文件进行io读取
     * 3.返回String
     *
     * @param scriptPath 脚本路径
     * @return
     */
    public String fileDownLoad(String scriptPath) {
        File file = new File(path + "/" + scriptPath);
        if (!file.exists()) {
            return new String();
        }
        InputStream fileInputStream = null;
        StringBuffer sb = new StringBuffer();
        try {
            fileInputStream = new FileInputStream(file);
            if (fileInputStream.available() == 0) {
                return new String();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, "utf-8"));
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }


    public static void main(String[] args) {
        Map<String, String> maps = Maps.newHashMap();
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 10, 1000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), Executors.defaultThreadFactory());
        Map<Integer, List<Integer>> selectMap = Maps.newHashMap();

        for (int i = 0; i < 10000; i++) {
            //分段处理 不局限于 5 如果数据量庞大的话可以将 分段数量设置较大一些
            int flag = i % 5;
            List<Integer> integers = selectMap.get(flag);
            if (CollUtil.isEmpty(integers)) {
                integers = Lists.newArrayList();
            }
            integers.add(i);
            selectMap.put(flag, integers);
        }
        // 获取selectMap key size
        if (CollUtil.isEmpty(selectMap)) {
            return;
        }
        // 用于做阻塞
        int size = selectMap.keySet().size();
        CountDownLatch countDownLatch = new CountDownLatch(size);
        selectMap.forEach((key, value) -> {
            // 具体的业务
            // 需要查询的id
            List<Integer> integerList = value;
            for (Integer i : integerList) {
                maps.put(i + "", i + "");
            }
            //统一做查询
            countDownLatch.countDown();
        });
//
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.err.println(JSON.toJSONString(maps));
        //释放线程资源
        threadPoolExecutor.shutdown();
        threadPoolExecutor.shutdownNow();

    }
}
