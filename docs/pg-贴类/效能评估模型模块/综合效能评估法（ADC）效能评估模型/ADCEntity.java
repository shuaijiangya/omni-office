package com.yw.module.calculation.indicatorCalculation.strategy.algParser.entity;

import com.yw.module.calculation.indicatorCalculation.strategy.algParser.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * @author superman
 * ADC算法参数实体
 */
@Getter
@Setter
public class ADCEntity extends BaseEntity {
    /**
     * 正常状态下能力发挥作用的概率
     */
    private float pr_norm;
    /**
     * 异常状态下能力发挥作用的概率
     */
    private float pr_abn;
    /**
     * 权重
     */
    private double weight;
    /**
     * 指标能力矩阵作用概率值
     */
    private List<Double> status;
    /**
     * 叶子指标标识
     * 0:其他指标
     * 1:叶子指标
     */
    private int flag;

    public static ADCEntity valueOf() {
        ADCEntity entity = new ADCEntity();
        entity.initEntity();
        return entity;
    }

    @Override
    protected void initEntity(float initValue) {
        this.value = initValue;
        this.pr_norm = initValue;
        this.pr_abn = initValue;
    }

    public String getStatus() {
        return StringUtils.join(status, "_");
    }

//    public static void main(String[] args) throws Exception {
//        String p1 = "{compInfo:[{combId:1,combName:idc-1,failureRate:0.1,repairRate:0.1}" +
//                ",{combId:2,combName:idc-2,failureRate:0.1,repairRate:0.2}" +
//                ",{combId:3,combName:idc-3,failureRate:0.2,repairRate:0.3}]" +
//                ",statusList:{ids-1:[0,0,0],ids-2:[1,0,0],ids-3:[0,1,0],ids-4:[0,0,1]}" +
//                ",executionTime:10,judge_repair:1,judge_weight:0}";
//        String p2 = "[{id:1,name:根指标,pid:0,value:-1,status:-1_-1_-1_-1],weight:-1,flag:0}" +
//                ",{id:2,name:指标2,pid:1,value:100,status:1_0.4_0.6_0.8,weight:0.5,flag:1}" +
//                ",{id:3,name:指标3,pid:1,value:200,status:1_0.8_0.6_0.2,weight:0.5,flag:1}]"+
//                ",{id:4,name:指标4,pid:2,value:300,status:1_0.3_0.7_0.4,weight:0.5,flag:1}" +
//                ",{id:5,name:指标5,pid:3,value:400,status:1_0.6_0.3_0.5,weight:0.5,flag:1}]";
//        String result = PyScriptALG.execScript("pythonFile/ADC_NEW.py", p1, p2).trim();
//        StringBuilder stringBuilder = new StringBuilder();
//        while (true) {
//            int endIndex = result.indexOf(" ");
//            if (endIndex < 0) {
//                stringBuilder.append(" " + result);
//                break;
//            }
//            stringBuilder.append(" " + result.substring(0, endIndex));
//            result = result.substring(endIndex).trim();
//        }
//        result = stringBuilder.toString();
//        String[] resultItem = result.split(";");
//        // A矩阵解析
//        String A_matrix = resultItem[0].trim();
//        List<?> list = JSON.parseObject(A_matrix, List.class);
//        // D矩阵解析
//        String D_matrix = resultItem[1].trim();
//        String[] strArray = D_matrix.split(" ");
//        List<String> headArray = Lists.newArrayList();
//        Map<String, List<String>> bodyMap = Maps.newLinkedHashMap();
//        boolean flag = false;
//        int index = 0;
//        for (int i = 0; i < strArray.length; i++) {
//            String data = strArray[i];
//            String head = strArray[index];
//            if (!flag) {
//                if (!data.equals(head) && data.contains(head)) {
//                    data = data.replace(head, "");
//                    flag = true;
//                }
//                headArray.add(data);
//                bodyMap.put(data, Lists.newArrayList());
//            } else {
//                int next = index + 1;
//                String currHeadKey = headArray.get(index);
//                String nextHeadKey = currHeadKey;
//                if (next < headArray.size()){
//                    nextHeadKey = headArray.get(next);
//                }
//                if (data.contains(nextHeadKey)) {
//                    data = data.replace(nextHeadKey, "");
//                    index++;
//                }
//                List<String> body = bodyMap.get(currHeadKey);
//                if (body == null) {
//                    body = Lists.newArrayList();
//                    bodyMap.put(headArray.get(index), body);
//                }
//                body.add(data);
//
//            }
//        }
//        // C矩阵解析
//        String C_matrix = resultItem[2].trim();
//        String indicatorValues = resultItem[3].trim();
//        System.err.println(indicatorValues);
//    }
}
