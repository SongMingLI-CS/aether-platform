package com.aether.aether_backend.common.exception;

/**
 * Central error catalogue. The numeric code is the stable contract for API clients;
 * the HTTP status is derived from the code by {@link GlobalExceptionHandler}.
 */
public enum ErrorCode {

    BAD_REQUEST(40000, 400, "参数错误"),
    RESOURCE_NOT_FOUND(40400, 404, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, 405, "请求方法不支持"),
    VERSION_CONFLICT(40900, 409, "数据已被他人修改，请刷新后重试"),
    INTERNAL_ERROR(50000, 500, "系统内部错误");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
