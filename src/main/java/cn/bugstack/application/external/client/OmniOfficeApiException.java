package cn.bugstack.application.external.client;

/** REST SDK 的结构化非 2xx 响应。 */
public final class OmniOfficeApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final String retryAfter;

    /**
     * 创建结构化 API 异常。
     *
     * @param status HTTP 状态码
     * @param code 服务端业务错误码
     * @param detail 错误详情
     * @param retryAfter 服务端返回的 {@code Retry-After} 值
     */
    public OmniOfficeApiException(int status, String code, String detail, String retryAfter) {
        super(detail == null || detail.isBlank() ? "Omni Office API request failed" : detail);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    /** @return HTTP 状态码 */
    public int getStatus() { return status; }

    /** @return 服务端业务错误码，未提供时为 {@code null} */
    public String getCode() { return code; }

    /** @return {@code Retry-After} 响应头，未提供时为 {@code null} */
    public String getRetryAfter() { return retryAfter; }
}
