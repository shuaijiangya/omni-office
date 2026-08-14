package cn.bugstack.office.docx.exception;

/**
 * docx 结构校验异常。
 */
public class DocxValidationException extends RuntimeException {

    /**
     * 创建校验异常。
     *
     * @param message 异常消息
     */
    public DocxValidationException(String message) {
        super(message);
    }
}
