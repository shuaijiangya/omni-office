package com.yw.module.calculation.indicatorCalculation.strategy.weight.impl;

import com.google.common.collect.Lists;
import com.yw.framework.common.defines.exception.custom.BusinessException;
import com.yw.module.calculation.indicatorCalculation.domain.dto.AnalyticHierarchyDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @Author admin
 * @Description: 层次分析法
 * @Date: 2020/12/21 9:09
 */
@Slf4j
public class AnalyticHierarchy {

    private final int scale = 5;

    private final HashMap<Integer, Double> RIMap = new HashMap<Integer, Double>() {
        {
            put(1, 0.0D);
            put(2, 0.0D);
            put(3, 0.52D);
            put(4, 0.89D);
            put(5, 1.12D);
            put(6, 1.26D);
            put(7, 1.36D);
            put(8, 1.41D);
            put(9, 1.46D);
            put(10, 1.49D);
            put(11, 1.52D);
            put(12, 1.54D);
            put(13, 1.56D);
            put(14, 1.58D);
            put(15, 1.59D);
        }
    };

    /**
     * @param flag    是否是目标-准测层特征向量
     * @param n       向量行数
     * @param m       向量列数
     * @param armNorm 需计算的向量
     * @param bo      层次分析法封装bo
     * @Author 刘乐法
     * @Description 计算特征向量，一致性验证
     * @formatter:off 计算特征向量(列归一化 ， 按行求和法)
     * ↓
     * 列归一化 ->每列求和->每个元素/该元素所在列的总和
     * ↓
     * 按行求和 -> 求列归一化后每行平均值
     * ↓
     * 特征向量 ->并且得到λ=(sum(AW/nW))
     * ↓
     * 一致性检验 ->根据公式((λ-n)/(n-1))得CI->根据CI查表得RI->根据公式CR=CI/RI =>CI<0.1&&CR<0.1
     * @formatter:on
     * @Return void
     * @Date 2020/12/22 16:46
     */
    void createFeatureVectors(Boolean flag, int n, int m, Double[][] armNorm, AnalyticHierarchyDTO bo) {
        Double[] armNormSpecial = new Double[n];//特征向量
        Double[][] armNormColNormalized = new Double[n][m];//按列归一化后向量

        ///////////////////计算特征向量,列归一化，按行求和
        //每列求和
        Double[] col = new Double[m];//存取每列总和
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                col[j] = col[j] == null ? armNorm[i][j] : BigDecimal.valueOf(col[j]).add(BigDecimal.valueOf(armNorm[i][j])).doubleValue();
            }
        }
        //每个元素/该元素所在列的总和
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                armNormColNormalized[i][j] = BigDecimal.valueOf(armNorm[i][j]).divide(BigDecimal.valueOf(col[j]), scale, BigDecimal.ROUND_HALF_DOWN).doubleValue();
            }
        }
        //求列归一化后每行平均值,得到特征向量
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                armNormSpecial[i] = armNormSpecial[i] == null ? armNormColNormalized[i][j] : BigDecimal.valueOf(armNormSpecial[i]).add(BigDecimal.valueOf(armNormColNormalized[i][j])).doubleValue();
            }
            armNormSpecial[i] = BigDecimal.valueOf(armNormSpecial[i]).divide(BigDecimal.valueOf(n), scale, BigDecimal.ROUND_HALF_DOWN).doubleValue();
        }
        /////////////////////计算特征根
        BigDecimal[] armNormLatentRoot = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                BigDecimal value = BigDecimal.valueOf(armNorm[i][j]).multiply(BigDecimal.valueOf(armNormSpecial[j]));
                armNormLatentRoot[i] = armNormLatentRoot[i] == null ? value : armNormLatentRoot[i].add(value);
            }
        }
        /////////////////////目标一致性检验
        BigDecimal maximumCharacteristicRoot = null;// λ
        for (int i = 0; i < n; i++) {
            BigDecimal value = armNormLatentRoot[i].divide(BigDecimal.valueOf(armNormSpecial[i]), scale, BigDecimal.ROUND_HALF_UP);
            maximumCharacteristicRoot = maximumCharacteristicRoot == null ?
                    value : maximumCharacteristicRoot.add(value);
        }
        BigDecimal nBig = BigDecimal.valueOf(n);
        maximumCharacteristicRoot = maximumCharacteristicRoot.divide(nBig, scale, BigDecimal.ROUND_HALF_UP);
        Double CI = (maximumCharacteristicRoot.subtract(nBig))
                .divide(nBig.subtract(BigDecimal.valueOf(1D))).doubleValue();
        Double RI = RIMap.get(n);
        Double CR = BigDecimal.valueOf(CI).divide(BigDecimal.valueOf(RI), scale, BigDecimal.ROUND_HALF_UP).doubleValue();

        System.out.println("*********************");
        System.out.println("特征向量:" + Arrays.deepToString(armNormSpecial));
        System.out.println("拉姆慕达:" + maximumCharacteristicRoot.doubleValue());
        System.out.println("CI:" + CI);
        System.out.println("RI:" + RI);
        System.out.println("CR:" + CR);
        if (CI < 0.1 && CR < 0.1) {
            System.out.println("符合一致性检验" + Arrays.deepToString(armNorm));
        } else {
            System.out.println("不符合一致性检验" + Arrays.deepToString(armNorm));
            throw new BusinessException("不符合一致性检验" + Arrays.deepToString(armNorm));
        }
        System.out.println("*********************");
        if (flag) {
            bo.setArmNormSpecial(armNormSpecial);
        } else {
            List<Double[]> featureVectors = bo.getFeatureVectors();
            featureVectors.add(armNormSpecial);
        }
    }

    /**
     * @param armNormN  目标准则层向量行数
     * @param armNormM  目标准则层向量列数
     * @param armNorm   目标准则层向量
     * @param normPlanN 准则方案层向量行数
     * @param normPlanM 准则方案层向量列数
     * @param normPlan  准则方案层向量集合
     * @Author 刘乐法
     * @Description 层次分析法计算入口
     * @Return void
     * @Date 2020/12/21 14:00
     */
    void doAnalyticHierarchy(int armNormN, int armNormM, Double[][] armNorm, int normPlanN, int normPlanM, List<Double[][]> normPlan) {
        if (armNormM > RIMap.size() || normPlanN > RIMap.size()) {
            throw new BusinessException("向量阶数,超出RI平均随机一致性表大小(" + RIMap.size() + ")");
        }

        AnalyticHierarchyDTO bo = new AnalyticHierarchyDTO();
        bo.setFeatureVectors(Lists.newArrayListWithExpectedSize(normPlan.size()));
        createFeatureVectors(true, armNormN, armNormM, armNorm, bo);

        normPlan.forEach(f -> {
            createFeatureVectors(false, normPlanN, normPlanM, f, bo);
        });

        Double[] armWeight = bo.getArmWeight();
        System.out.println("目标权重" + Arrays.asList(armWeight));
    }

//    public static void main(String[] args) {
//        Double[][] arm = {{1.0D, 1 / 2.0D, 4.0D, 3D, 3D}, {2.0D, 1D, 7.0D, 5D, 5D}, {1 / 4D, 1 / 7.0D, 1.0D, 1 / 2D, 1 / 3D},
//                {1.0 / 3D, 1 / 5.0D, 2.0D, 1D, 1D}, {1.0 / 3D, 1 / 5.0D, 3.0D, 1D, 1D}};
//
//        Double[][] b1 = {{1.0D, 2.0D, 5.0D}, {0.5D, 1.0D, 2.0D}, {0.2D, 0.5D, 1.0D}};
//        Double[][] b2 = {{1.0D, 1 / 3D, 1 / 8D}, {3.0D, 1.0D, 1 / 3D}, {8.0D, 3.0D, 1.0D}};
//        Double[][] b3 = {{1.0D, 1.0D, 3.0D}, {1.0D, 1.0D, 3.0D}, {1 / 3D, 1 / 3D, 1.0D}};
//        Double[][] b4 = {{1.0D, 3.0D, 4.0D}, {1 / 3D, 1.0D, 1.0D}, {1 / 4D, 1D, 1.0D}};
//        Double[][] b5 = {{1.0D, 1.0D, 1 / 4D}, {1.0D, 1.0D, 1 / 4D}, {4.0D, 4.0D, 1.0D}};
//        List<Double[][]> normPlan = new ArrayList<>();
//        normPlan.add(b1);
//        normPlan.add(b2);
//        normPlan.add(b3);
//        normPlan.add(b4);
//        normPlan.add(b5);
//        AnalyticHierarchy analyticHierarchy = new AnalyticHierarchy();
//        analyticHierarchy.doAnalyticHierarchy(5, 5, arm, 3, 3, normPlan);
//    }


}
