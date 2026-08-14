package cn.bugstack.office.docx.exception;

/**
 * docx 渲染异常。
 */
public class DocxRenderException extends RuntimeException {

    /**
     * 创建渲染异常。
     *
     * @param message 异常消息
     * @param cause 原始异常
     */
    public DocxRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
