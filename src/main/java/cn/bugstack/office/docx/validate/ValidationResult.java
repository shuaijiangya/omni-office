package cn.bugstack.office.docx.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文档结构校验结果。
 */
public class ValidationResult {

    private final List<String> messages = new ArrayList<>();

    /**
     * 创建空的文档结构校验结果。
     */
    public ValidationResult() {
    }

    /**
     * 添加一条校验消息。
     *
     * @param message 校验失败消息
     */
    public void addMessage(String message) {
        messages.add(message);
    }

    /**
     * 判断校验是否通过。
     *
     * @return 无校验消息时返回 {@code true}
     */
    public boolean isValid() {
        return messages.isEmpty();
    }

    /**
     * 获取校验消息列表。
     *
     * @return 不可修改的校验消息列表
     */
    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }
}
