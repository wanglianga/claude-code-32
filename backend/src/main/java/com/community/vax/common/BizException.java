package com.community.vax.common;

import org.springframework.http.HttpStatus;

/** 业务异常：消息会直接展示给前端用户 */
public class BizException extends RuntimeException {
    private final int code;
    private final HttpStatus status;

    public BizException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BizException(String message, HttpStatus status) {
        super(message);
        this.code = status.value();
        this.status = status;
    }

    public int getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
